"""The look-ahead's picks in a league (search=1 or 2) against what they really took.

    python3 scripts/bot/analysis/lookahead.py <league dir>     # logs/*.log and journal-*/*.jsonl, as league.sh keeps them

Per tile pick (the "look-ahead: X (~believed s, own ~estimate s" line): what the look-ahead believed
it takes, Bob's own estimate, and from the journal (t=goal, matched by bot, goal and figure) the game
seconds the goal ran and how it ended. Chore picks (search=2: "look-ahead: shield (~140 s; ...") are
listed and counted.
The last line: the sums over the matched tile picks and the real seconds per tile claimed - for the
default side, goals.py --params - gives the same figure (round 1's look-ahead: 519 s a claim, its
picks claimed 7 of 18; the route's 129 s).
"""
import collections
import glob
import json
import re
import sys


def main(d):
    starts = collections.defaultdict(list)
    seen = set()
    for f in glob.glob(d + "/journal-*/*.jsonl"):
        for line in open(f):
            try:
                j = json.loads(line)
            except ValueError:
                continue
            if j.get("t") != "goal" or j["id"] in seen:
                continue
            seen.add(j["id"])
            starts[(j["bot"], j["goal"])].append(j)
    for v in starts.values():
        v.sort(key=lambda j: j.get("wall", 0))
    rows, chores = [], collections.Counter()
    for f in sorted(glob.glob(d + "/logs/*.log")):
        game = f.split("/")[-1].replace("versus_", "").replace(".log", "")
        for line in open(f, errors="replace"):
            # (A chore: "look-ahead: shield (~140 s; ...", "look-ahead: a stock of food (~...".)
            m = re.search(r"\[Bot\] (\w+): look-ahead: ([a-z_ ]+?) \(~(-?\d+) s; ", line)
            if m:
                chores[m.group(2)] += 1
                rows.append((game, m.group(1), "chore: " + m.group(2) + " ~" + m.group(3) + " s", None, None, None, None))
                continue
            m = re.search(r"\[Bot\] (\w+): look-ahead: (\S+) \(~(-?\d+) s, own ~(-?\d+) s", line)
            if not m:
                continue
            bot, goal, bel, own = m.group(1), m.group(2), int(m.group(3)), int(m.group(4))
            # (The journal's estimate is the believed figure, or Bob's own when nothing was learnt.)
            cands = [j for j in starts.get((bot, goal), []) if not j.get("_used")
                     and (abs(j["estimate"] - bel) <= 1.5 or abs(j["estimate"] - own) <= 1.5 or abs(j.get("plain", -9) - own) <= 1.5)]
            j = cands[0] if cands else None
            if j:
                j["_used"] = True
            rows.append((game, bot, goal, bel, own, j and round(j["secs"]), j and j["outcome"]))
    tiles = [r for r in rows if r[3] is not None]
    matched = [r for r in tiles if r[5] is not None]
    done = [r for r in matched if r[6] == "done"]
    for r in rows:
        if r[3] is None:
            print("  %-22s %-6s %s" % r[:3])
        else:
            print("  %-22s %-6s %-30s believed %5d own %5d real %5s %s" % r)
    print(f"{len(tiles)} tile picks ({len(matched)} found in the journals, {len(done)} claimed), "
          f"{sum(chores.values())} chore picks {dict(chores)}")
    if matched:
        print("over the tile picks found: believed %d s, own %d s, real %d s; real seconds a tile claimed %.0f"
              % (sum(r[3] for r in matched), sum(r[4] for r in matched), sum(r[5] for r in matched),
                 sum(r[5] for r in matched) / max(1, len(done))))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
