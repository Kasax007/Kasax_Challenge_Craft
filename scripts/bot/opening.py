#!/usr/bin/env python3
"""The opening's stopwatch over several worlds: Bob alone in a Lockout game per seed (bench.sh with
SIM=1 at tick sprint), then when each milestone came and where the time went (see OpeningClock).

    scripts/bot/opening.py run --seeds 1,2,3 --seconds 600 --parallel 2 --out opening.jsonl [--knobs fastopen=0]
    scripts/bot/opening.py show opening.jsonl [more.jsonl ...]       # a table of what was run
    scripts/bot/opening.py compare before.jsonl after.jsonl           # two runs, seed by seed
    scripts/bot/opening.py compare b1.jsonl,b2.jsonl a1.jsonl,a2.jsonl  # several runs a side: per seed their mean

"show" and "compare" also read league results (league.py --out): every bot of every game then
counts as one opening. Run "gradlew classes bobLaunchSpec" first (the servers are started from
its launch files, one JVM each), and only under the build lock: every server is a JVM.
"""
import argparse
import concurrent.futures
import json
import math
import os
import statistics
import subprocess
import sys
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

MILESTONES = ["log", "table", "wooden_pickaxe", "stone_pickaxe", "stone_sword", "stone_axe", "stone_shovel", "stone_tools",
              "furnace", "food_stock", "raw_iron", "shield", "iron_ingot", "iron_pickaxe", "bucket", "copper_ingot", "copper_armour",
              "first_tile"]
# The errands of the kit and the food, over the whole game (the stopwatch's "kit" field, newer builds).
KIT = ["invest", "armour", "kit", "food", "opening", "raid", "night", "side"]
ACTIVITIES = ["walk", "search", "path", "dig", "craft", "smelt", "hunt", "eat", "reflex", "wait", "idle", "dead"]


def launch_files():
    """The server's argument file from the launch spec (gradlew bobLaunchSpec): no server started for it."""
    args = os.path.join(ROOT, "build", "loom-cache", "argFiles", "runServer")
    spec = os.path.join(ROOT, "build", "bob", "launch-spec.json")
    if not os.path.exists(spec):
        sys.exit("no build/bob/launch-spec.json: run 'gradlew classes bobLaunchSpec' first")
    with open(spec) as f:
        cp = json.load(f)["classpath"]
    os.makedirs(os.path.dirname(args), exist_ok=True)
    with open(args, "w") as f:
        f.write("-classpath\n" + ":".join(cp) + "\n")


def bench(seed, seconds, difficulty, knobs, slot):
    env = os.environ.copy()
    run = os.path.join(ROOT, f"run-{slot}")
    os.makedirs(run, exist_ok=True)
    env.update(DIRECT="1", SIM="1", RUNDIR=run, SERVER_PORT=str(25565 + slot), RCON_PORT=str(25575 + slot))
    cmd = ["sh", os.path.join(HERE, "bench.sh"), str(seed), str(seconds), difficulty] + ([knobs] if knobs else [])
    out = subprocess.run(cmd, capture_output=True, text=True, env=env)
    for line in out.stdout.splitlines():
        if line.startswith("OPENING "):
            return json.loads(line[len("OPENING "):])
    print(f"seed {seed}: no result\n{out.stdout[-1500:]}\n{out.stderr[-1500:]}", file=sys.stderr)
    return None


def run(args):
    launch_files()
    seeds = [int(s) for s in args.seeds.split(",") if s]
    lock = threading.Lock()
    slots = list(range(args.parallel))
    done = []

    def one(seed):
        with lock:
            slot = slots.pop()
        try:
            r = bench(seed, args.seconds, args.difficulty, args.knobs, slot)
        finally:
            with lock:
                slots.append(slot)
        if r is None:
            return
        with lock:
            done.append(r)
            with open(args.out, "a") as f:
                f.write(json.dumps(r) + "\n")
            ms = r["opening"]["ms"]
            print(f"{len(done)}/{len(seeds)} seed {seed}: stone pickaxe {ms.get('stone_pickaxe', '-')} s, food {ms.get('food_stock', '-')} s, "
                  f"shield {ms.get('shield', '-')} s, first tile {ms.get('first_tile', '-')} s, {r['tiles']} tiles, {r['deaths']} deaths", flush=True)

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.parallel) as pool:
        list(pool.map(one, seeds))
    show([args.out])


def load(paths):
    """Openings from bench lines or league results: (seed, name, params, opening, tiles, deaths)."""
    out = []
    for p in paths:
        with open(p) as f:
            for line in f:
                line = line.strip()
                if not line.startswith("{"):
                    continue
                r = json.loads(line)
                if "opening" in r:
                    out.append((r["seed"], r["name"], r.get("params", "-"), r["opening"], r["tiles"], r["deaths"]))
                for b in r.get("bots", []):
                    if "opening" in b:
                        out.append((r["seed"], b["name"], b.get("params", "-"), b["opening"], b["tiles"], b["deaths"]))
    return out


def se(xs):
    """The standard error of the mean (nan for fewer than two)."""
    return statistics.stdev(xs) / math.sqrt(len(xs)) if len(xs) >= 2 else float("nan")


def fmt(xs):
    if not xs:
        return "   -      -      -  "
    return f"{statistics.median(xs):5.0f}  {statistics.mean(xs):5.0f}  {min(xs):4.0f}-{max(xs):<4.0f}"


def show(paths):
    rows = load(paths)
    if not rows:
        print("no openings")
        return
    n = len(rows)
    print(f"\n{n} openings ({', '.join(sorted({r[2] for r in rows}))})")
    print(f"  {'milestone':16s} {'reached':>7s}  median  mean   range")
    for m in MILESTONES:
        xs = [r[3]["ms"][m] for r in rows if m in r[3]["ms"]]
        print(f"  {m:16s} {len(xs):3d}/{n:<3d}  {fmt(xs)}")
    for key, label in (("pre", "before the first tile"), ("act", "first ten minutes")):
        tot = {a: sum(r[3][key].get(a, 0) for r in rows) / n for a in ACTIVITIES}
        all_ = sum(tot.values())
        print(f"  time {label} (s a game, {all_:.0f} in all): " + ", ".join(f"{a} {tot[a]:.0f}" for a in ACTIVITIES if tot[a] >= 0.5))
    deciders = {}
    for r in rows:
        for d, acts in r[3]["by"].items():
            for a, s in acts.items():
                deciders.setdefault(d, {}).setdefault(a, 0)
                deciders[d][a] += s / n
    print("  by decider (s a game, first ten minutes):")
    for d, acts in sorted(deciders.items(), key=lambda kv: -sum(kv[1].values())):
        total = sum(acts.values())
        if total < 1:
            continue
        top = sorted(acts.items(), key=lambda kv: -kv[1])
        print(f"    {d:10s} {total:5.0f}: " + ", ".join(f"{a} {s:.0f}" for a, s in top if s >= 0.5))
    detail = {}
    for r in rows:
        for k, v in r[3].get("detail", {}).items():
            detail[k] = detail.get(k, 0) + v / n
    if detail:
        top = sorted(detail.items(), key=lambda kv: -kv[1])[:12]
        print("  by the task at work (s a game): " + ", ".join(f"{k} {v:.0f}" for k, v in top))
    kits = [r for r in rows if "kit" in r[3]]
    if kits:
        print(f"  the kit's errands over the whole game (s a game, {len(kits)} games):")
        for d in KIT:
            per = [sum(r[3]["kit"].get(d, {}).values()) for r in kits]
            if sum(per) / len(kits) < 0.5:
                continue
            acts = {}
            for r in kits:
                for k, v in r[3]["kit"].get(d, {}).items():
                    acts[k] = acts.get(k, 0) + v / len(kits)
            top = sorted(acts.items(), key=lambda kv: -kv[1])[:8]
            print(f"    {d:8s} {statistics.mean(per):5.0f} ({se(per):3.0f}): " + ", ".join(f"{k} {v:.0f}" for k, v in top if v >= 0.5))
        paces = [r[3]["pace"] for r in kits if r[3].get("pace", {}).get("walk", 0) > 0]
        if paces:
            print(f"    walking {statistics.mean(p['walk'] for p in paces):.0f} s a game, sprinting {statistics.mean(p['sprint'] for p in paces):.0%} of it, "
                  f"{statistics.mean(p['level'] for p in paces):.2f} blocks/s on level ground")
    speeds = [r[3]["speed"] for r in rows if r[3]["speed"] > 0]
    print(f"  walking pace {statistics.mean(speeds):.2f} blocks/s; reflexes {sum(r[3]['reflexes'] for r in rows) / n:.1f} a game; "
          f"tiles {sum(r[4] for r in rows) / n:.2f}, deaths {sum(r[5] for r in rows) / n:.2f} a game")


def per_seed(paths):
    """The openings of one side by (seed, bot): a list each - several runs of a seed count as one, averaged."""
    out = {}
    for r in load(paths):
        out.setdefault((r[0], r[1]), []).append(r)
    return out


def mean_of(runs, value):
    """The mean of value(run) over the runs where it is not None, or None."""
    xs = [x for x in (value(r) for r in runs) if x is not None]
    return statistics.mean(xs) if xs else None


def paired(a, b, keys, value):
    """Per seed the mean over its runs of each side, then after minus before: (before, after, differences)."""
    xa, xb, d = [], [], []
    for k in keys:
        va, vb = mean_of(a[k], value), mean_of(b[k], value)
        if va is not None:
            xa.append(va)
        if vb is not None:
            xb.append(vb)
        if va is not None and vb is not None:
            d.append(vb - va)
    return xa, xb, d


def compare(a_paths, b_paths):
    """Two sides (each one or more result files, comma-separated: runs of the same seeds), seed by seed."""
    a, b = per_seed(a_paths.split(",")), per_seed(b_paths.split(","))
    keys = sorted(set(a) & set(b))
    ra, rb = sum(len(a[k]) for k in keys), sum(len(b[k]) for k in keys)
    print(f"{len(keys)} seeds in both ({ra} runs before, {rb} after); per seed the mean of its runs, then paired")
    print(f"  {'milestone':16s}  before  after  (median of the seeds; paired mean difference, standard error, seeds with both)")
    med = lambda xs: f"{statistics.median(xs):6.0f}" if xs else "     -"
    for m in MILESTONES:
        xa, xb, d = paired(a, b, keys, lambda r: r[3]["ms"].get(m))
        reached_a = sum(1 for k in keys for r in a[k] if m in r[3]["ms"])
        reached_b = sum(1 for k in keys for r in b[k] if m in r[3]["ms"])
        print(f"  {m:16s}  {med(xa)} {med(xb)}   {statistics.mean(d) if d else float('nan'):+6.0f} s ({se(d):4.0f}, {len(d)})"
              f"   reached {reached_a}/{ra} -> {reached_b}/{rb}")
    for key, label in (("pre", "before the first tile"), ("act", "first ten minutes")):
        print(f"  {label} (s a game; paired difference, standard error):")
        for act in ACTIVITIES:
            xa, xb, d = paired(a, b, keys, lambda r: r[3][key].get(act, 0))
            if statistics.mean(xa) >= 0.5 or statistics.mean(xb) >= 0.5:
                print(f"    {act:8s} {statistics.mean(xa):6.0f} -> {statistics.mean(xb):6.0f}   {statistics.mean(d):+6.0f} ({se(d):4.0f})")
    print("  by decider, first ten minutes (s a game):")
    deciders = sorted({d for k in keys for side in (a, b) for r in side[k] for d in r[3]["by"]})
    for dec in deciders:
        xa, xb, d = paired(a, b, keys, lambda r: sum(r[3]["by"].get(dec, {}).values()))
        if statistics.mean(xa) >= 0.5 or statistics.mean(xb) >= 0.5:
            print(f"    {dec:10s} {statistics.mean(xa):6.0f} -> {statistics.mean(xb):6.0f}   {statistics.mean(d):+6.0f} ({se(d):4.0f})")
    # (Older builds have no "kit" field: only the runs that have it count.)
    both = [k for k in keys if any("kit" in r[3] for r in a[k]) and any("kit" in r[3] for r in b[k])]
    if both:
        print(f"  the kit's errands over the whole game (s a game; paired difference, standard error; {len(both)} seeds):")
        for dec in KIT + ["invest+armour"]:
            parts = dec.split("+")
            xa, xb, d = paired(a, b, both, lambda r: sum(sum(r[3]["kit"].get(p, {}).values()) for p in parts) if "kit" in r[3] else None)
            if statistics.mean(xa) < 0.5 and statistics.mean(xb) < 0.5:
                continue
            print(f"    {dec:14s} {statistics.mean(xa):6.0f} -> {statistics.mean(xb):6.0f}   {statistics.mean(d):+6.0f} ({se(d):4.0f})")
        xa, xb, d = paired(a, b, both, lambda r: r[3]["pace"]["level"] if "pace" in r[3] else None)
        if d:
            print(f"    pace on level ground {statistics.mean(xa):.2f} -> {statistics.mean(xb):.2f} blocks/s ({statistics.mean(d):+.2f}, {se(d):.2f})")
    for i, label in ((4, "tiles"), (5, "deaths")):
        xa, xb, d = paired(a, b, keys, lambda r: r[i])
        print(f"  {label} {statistics.mean(xa):.2f} -> {statistics.mean(xb):.2f} a game ({statistics.mean(d):+.2f}, standard error {se(d):.2f})")


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--seeds", required=True)
    r.add_argument("--seconds", type=int, default=600)
    r.add_argument("--difficulty", default="hard")
    r.add_argument("--knobs", default="-", help="the bot's knobs ('-': the defaults, as a league side has them; not the champion's)")
    r.add_argument("--parallel", type=int, default=2)
    r.add_argument("--out", default="opening.jsonl")
    s = sub.add_parser("show")
    s.add_argument("files", nargs="+")
    c = sub.add_parser("compare")
    c.add_argument("before")
    c.add_argument("after")
    args = ap.parse_args()
    if args.cmd == "run":
        run(args)
    elif args.cmd == "show":
        show(args.files)
    else:
        compare(args.before, args.after)


if __name__ == "__main__":
    main()
