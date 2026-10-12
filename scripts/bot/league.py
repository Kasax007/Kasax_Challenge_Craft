#!/usr/bin/env python3
"""A league of self-play matches: two variants of Bob against each other, seed after seed.

    scripts/bot/league.py --a "guardian=1,quick=30" --b "guardian=0,quick=0" --games 8 --seconds 1800

Each game is a fresh world on a random seed (or --seeds 1,2,3), both variants on the same board
in the same world, from the same spawn - a paired comparison by its nature. Which name plays which
variant changes every game (no edge from the order the bots are ticked in). Every result is kept
(--out, JSON lines); at the end, per variant: wins, tiles, deaths, hearts lost, the first tile,
the tiles after 5/10/20 minutes, and the sign test on the wins.
"""
import argparse
import json
import math
import os
import random
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def play(seed, seconds, difficulty, sides, env):
    cmd = [os.path.join(HERE, "versus.sh"), str(seed), str(seconds), difficulty] + sides
    out = subprocess.run(["sh"] + cmd, capture_output=True, text=True, env=env)
    lines = [l for l in out.stdout.splitlines() if l.startswith("{")]
    if not lines:
        print(f"seed {seed}: no result\n{out.stdout[-2000:]}\n{out.stderr[-2000:]}", file=sys.stderr)
        return None
    return json.loads(lines[-1])


def sign_test(wins, losses):
    """Two-sided p of a sign test: how likely this many wins (or more lopsided) from equal variants."""
    n = wins + losses
    if n == 0:
        return 1.0
    k = max(wins, losses)
    p = sum(math.comb(n, i) for i in range(k, n + 1)) / 2 ** n
    return min(1.0, 2 * p)


def summary(results, a, b):
    stats = {a: [], b: []}
    wins = {a: 0, b: 0}
    ties = 0
    for r in results:
        by = {bot["params"]: bot for bot in r["bots"]}
        if a not in by or b not in by:
            continue
        for v in (a, b):
            stats[v].append(by[v])
        if r["winner"] == "-":
            ties += 1
        else:
            winner = next(bot["params"] for bot in r["bots"] if bot["name"] == r["winner"])
            wins[winner] += 1
    print(f"\n{len(stats[a])} games, {ties} tied")
    for v in (a, b):
        s = stats[v]
        if not s:
            continue
        mean = lambda key: sum(x[key] for x in s) / len(s)
        firsts = [x["first"] for x in s if x["first"] >= 0]
        print(f"  {v:28s} wins {wins[v]:2d}  tiles {mean('tiles'):5.1f}  deaths {mean('deaths'):4.2f}  hearts lost {mean('hearts'):6.1f}"
              f"  first tile {sum(firsts) / len(firsts) if firsts else float('nan'):6.0f} s"
              f"  after 5/10/20 min {mean('m5'):4.1f} {mean('m10'):4.1f} {mean('m20'):4.1f}")
    print(f"  sign test on the wins: p = {sign_test(wins[a], wins[b]):.3f}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--a", default="-", help="variant A: key=value,... ('-' for the defaults)")
    ap.add_argument("--b", required=True, help="variant B")
    ap.add_argument("--games", type=int, default=8)
    ap.add_argument("--seeds", default="", help="comma separated; else random")
    ap.add_argument("--seconds", type=int, default=1800)
    ap.add_argument("--difficulty", default="hard")
    ap.add_argument("--out", default="league.jsonl")
    args = ap.parse_args()
    seeds = [int(s) for s in args.seeds.split(",") if s] or [random.randrange(-2**62, 2**62) for _ in range(args.games)]
    results = []
    for i, seed in enumerate(seeds):
        names = ("Bob", "Alice") if i % 2 == 0 else ("Alice", "Bob")
        sides = [f"{names[0]}:{args.a}", f"{names[1]}:{args.b}"]
        r = play(seed, args.seconds, args.difficulty, sides, os.environ.copy())
        if r is None:
            continue
        results.append(r)
        with open(args.out, "a") as f:
            f.write(json.dumps(r) + "\n")
        line = ", ".join(f"{bot['name']}({bot['params']}) {bot['tiles']} tiles {bot['deaths']} deaths" for bot in r["bots"])
        print(f"game {i + 1}/{len(seeds)} seed {seed}: {line} -> {r['winner']}", flush=True)
    summary(results, args.a, args.b)


if __name__ == "__main__":
    main()
