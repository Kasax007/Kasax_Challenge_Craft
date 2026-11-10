"""Two leagues on the same seeds, variant by variant: tiles, deaths, and the time sinks, before and after.

Figures are per bot (in a mirror league the mean of the two bots of a game).

    python3 compare.py before.jsonl after.jsonl
"""
import json, math, sys

def load(path):
    """seed -> variant -> its bots' figures, averaged (a mirror league - "-" against "-" - has two bots of one variant)."""
    out = {}
    for line in open(path):
        r = json.loads(line)
        by = {}
        for b in r["bots"]:
            by.setdefault(b["params"], []).append(b)
        out[r["seed"]] = {p: merged(bots) for p, bots in by.items()}
    return out


def merged(bots):
    """One variant's figures in a game: the mean over its bots (the first tile: of those that got one)."""
    n = len(bots)
    firsts = [b["first"] for b in bots if b["first"] >= 0]
    time = {}
    for b in bots:
        for k, v in b.get("time", {}).items():
            time[k] = time.get(k, 0) + v / n
    return {"tiles": sum(b["tiles"] for b in bots) / n, "deaths": sum(b["deaths"] for b in bots) / n,
            "hearts": sum(b["hearts"] for b in bots) / n, "first": sum(firsts) / len(firsts) if firsts else -1, "time": time}

before, after = load(sys.argv[1]), load(sys.argv[2])
seeds = [s for s in before if s in after]
params = sorted({p for s in seeds for p in before[s]} & {p for s in seeds for p in after[s]})
SINKS = ["dug in for the night", "boxed in", "boxed in for the night", "get apple|… (#) x#", "get shield x#",
         "get iron_pickaxe x#", "get diamond_pickaxe|iron_pickaxe|stone_pickaxe x", "get cobbled_deepslate|cobblestone x#"]
print(f"{len(seeds)} seeds in both")
for p in params:
    pairs = [(before[s][p], after[s][p]) for s in seeds if p in before[s] and p in after[s]]
    n = len(pairs)
    if not n:
        continue
    d = [b2["tiles"] - b1["tiles"] for b1, b2 in pairs]
    mean = sum(d) / n
    sd = math.sqrt(sum((x - mean) ** 2 for x in d) / (n - 1)) if n > 1 else 0
    se = sd / math.sqrt(n) if n > 1 else 0
    print(f"\n{p}: {n} games")
    print(f"  tiles   {sum(b1['tiles'] for b1, _ in pairs) / n:5.2f} -> {sum(b2['tiles'] for _, b2 in pairs) / n:5.2f}   (paired {mean:+.2f}, se {se:.2f})")
    print(f"  deaths  {sum(b1['deaths'] for b1, _ in pairs) / n:5.2f} -> {sum(b2['deaths'] for _, b2 in pairs) / n:5.2f}")
    print(f"  hearts  {sum(b1['hearts'] for b1, _ in pairs) / n:5.1f} -> {sum(b2['hearts'] for _, b2 in pairs) / n:5.1f}")
    f1 = [b1["first"] for b1, _ in pairs if b1["first"] >= 0]
    f2 = [b2["first"] for _, b2 in pairs if b2["first"] >= 0]
    print(f"  first   {sum(f1) / len(f1) if f1 else float('nan'):5.0f} -> {sum(f2) / len(f2) if f2 else float('nan'):5.0f} s  ({len(f1)} / {len(f2)} games with a tile)")
    for k in SINKS:
        t1 = sum(b1.get("time", {}).get(k, 0) for b1, _ in pairs) / n
        t2 = sum(b2.get("time", {}).get(k, 0) for _, b2 in pairs) / n
        if t1 or t2:
            print(f"  {k:52s} {t1:5.0f} -> {t2:5.0f} s/game")
