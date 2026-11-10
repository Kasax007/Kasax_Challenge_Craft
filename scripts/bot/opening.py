#!/usr/bin/env python3
"""The opening's stopwatch over several worlds: Bob alone in a Lockout game per seed (bench.sh with
SIM=1 at tick sprint), then when each milestone came and where the time went (see OpeningClock).

    scripts/bot/opening.py run --seeds 1,2,3 --seconds 600 --parallel 2 --out opening.jsonl [--knobs fastopen=0]
    scripts/bot/opening.py show opening.jsonl [more.jsonl ...]       # a table of what was run
    scripts/bot/opening.py compare before.jsonl after.jsonl           # two runs, seed by seed

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
              "furnace", "food_stock", "shield", "iron_ingot", "iron_pickaxe", "bucket", "first_tile"]
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
    speeds = [r[3]["speed"] for r in rows if r[3]["speed"] > 0]
    print(f"  walking pace {statistics.mean(speeds):.2f} blocks/s; reflexes {sum(r[3]['reflexes'] for r in rows) / n:.1f} a game; "
          f"tiles {sum(r[4] for r in rows) / n:.2f}, deaths {sum(r[5] for r in rows) / n:.2f} a game")


def compare(a_path, b_path):
    a = {(r[0], r[1]): r for r in load([a_path])}
    b = {(r[0], r[1]): r for r in load([b_path])}
    keys = sorted(set(a) & set(b))
    print(f"{len(keys)} openings in both")
    print(f"  {'milestone':16s}  before  after  (median; paired mean difference, standard error, both reached)")
    for m in MILESTONES:
        xa = [a[k][3]["ms"][m] for k in keys if m in a[k][3]["ms"]]
        xb = [b[k][3]["ms"][m] for k in keys if m in b[k][3]["ms"]]
        d = [b[k][3]["ms"][m] - a[k][3]["ms"][m] for k in keys if m in a[k][3]["ms"] and m in b[k][3]["ms"]]
        se = statistics.stdev(d) / math.sqrt(len(d)) if len(d) >= 2 else float("nan")
        med = lambda xs: f"{statistics.median(xs):6.0f}" if xs else "     -"
        print(f"  {m:16s}  {med(xa)} {med(xb)}   {statistics.mean(d) if d else float('nan'):+6.0f} s ({se:4.0f}, {len(d)})"
              f"   reached {len(xa)} -> {len(xb)}")
    for key, label in (("pre", "before the first tile"), ("act", "first ten minutes")):
        print(f"  {label} (s a game):")
        for act in ACTIVITIES:
            ta = sum(a[k][3][key].get(act, 0) for k in keys) / max(1, len(keys))
            tb = sum(b[k][3][key].get(act, 0) for k in keys) / max(1, len(keys))
            if ta >= 0.5 or tb >= 0.5:
                print(f"    {act:8s} {ta:6.0f} -> {tb:6.0f}")
    ta = sum(a[k][4] for k in keys) / max(1, len(keys))
    tb = sum(b[k][4] for k in keys) / max(1, len(keys))
    da = sum(a[k][5] for k in keys) / max(1, len(keys))
    db = sum(b[k][5] for k in keys) / max(1, len(keys))
    print(f"  tiles {ta:.2f} -> {tb:.2f}, deaths {da:.2f} -> {db:.2f} a game")


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
