"""Expert time against Bob's, per goal: the table of docs/bob-reference-times.md.

    python3 scripts/bot/analysis/reference_times.py [--lang <minecraft en_us.json>] run-*/versus_*.log ...

The expert times come from src/main/resources/bob-knowledge/speedrun.json; Bob's from the self-play
logs given (scripts/bot/analysis/goals.py reads them): per goal the median game seconds of the
starts that got the tile, how many starts there were and how many got it. Goal lines written since
round 21 end in "[goal_id]"; for older logs the board titles are matched to the ids by the game's
names - pass the vanilla en_us.json (assets/minecraft/lang/en_us.json of the client jar) with --lang.
"""
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, HERE)
import goals  # noqa: E402


def titles(vanilla_lang):
    """goal id -> the title the board shows (as far as it can be told without the game)."""
    van = json.load(open(vanilla_lang)) if vanilla_lang else {}
    mod = json.load(open(os.path.join(ROOT, "src/main/resources/assets/challengecraft/lang/en_us.json")))
    src = open(os.path.join(ROOT, "src/main/java/net/kasax/challengecraft/challenges/lockout/LockoutBingoGoalPool.java")).read()
    body = src[src.index("GOALS = List.of("):src.index("private static final Map<String, LockoutBingoGoal> BY_ID")]
    out = {}
    for m in re.finditer(r'^\s+([a-zA-Z]+)\((?:LockoutBingoGoalCategory\.[A-Z_]+,\s*)?"([a-z0-9_]+)",\s*LockoutBingoGoalDifficulty\.[A-Z]+,\s*(.*)$', body, re.M):
        kind, gid, rest = m.groups()
        key = f"challengecraft.lockout.goal.{gid}.title"
        if key in mod:
            out[gid] = mod[key]
            continue
        targets = re.findall(r'"([a-z_]+:[a-z0-9_/]+)"', rest)
        t = targets[0] if targets else ""
        ns, _, path = t.partition(":")
        if kind in ("item", "craft", "consume", "equip") and t:
            out[gid] = van.get(f"item.{ns}.{path}") or van.get(f"block.{ns}.{path}") or path
        elif kind == "itemAmount" and t:
            n = re.match(r"\s*(\d+),", rest)
            out[gid] = f"{n.group(1) if n else '?'} " + (van.get(f"item.{ns}.{path}") or van.get(f"block.{ns}.{path}") or path)
        elif kind == "kill" and t:
            out[gid] = van.get(f"entity.{ns}.{path}", path)
        elif kind == "biome" and t:
            out[gid] = van.get(f"biome.{ns}.{path}", path)
        else:
            out[gid] = gid
    return out


def ids_in_logs(paths):
    """title -> goal id, from the "[goal_id]" the newer goal lines end in."""
    out = {}
    for p in paths:
        for l in open(p, errors="replace"):
            m = re.search(r"\[Bot\] \w+: goal: (.+?) \(~\d+ s.*\) \[([a-z0-9_]+)\]\s*$", l)
            if m:
                out[m.group(1)] = m.group(2)
    return out


def median(xs):
    xs = sorted(xs)
    if not xs:
        return None
    n = len(xs)
    return xs[n // 2] if n % 2 else (xs[n // 2 - 1] + xs[n // 2]) / 2


def main(argv):
    lang = None
    if "--lang" in argv:
        i = argv.index("--lang")
        lang = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    kb = json.load(open(os.path.join(ROOT, "src/main/resources/bob-knowledge/speedrun.json")))["goals"]
    names = titles(lang)
    by_title = {v: k for k, v in names.items()}
    by_title.update(ids_in_logs(argv))
    runs = collections.defaultdict(list)
    for _, title, est, real, how, kind, plan, expert in goals.parse(argv):
        gid = by_title.get(title)
        if gid:
            runs[gid].append((real, how))
    print("| goal | title | expert | Bob (median, got it) | starts | got | Bob / expert |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for gid, entry in kb.items():
        e = entry.get("expert")
        r = runs.get(gid, [])
        got = [x for x, how in r if how == "claimed"]
        m = median(got)
        bob = f"{m:.0f}" if m is not None else ("not got" if r else "-")
        gap = f"{m / e:.1f}" if m is not None and e else "-"
        print(f"| {gid} | {names.get(gid, gid)} | {e:.0f} | {bob} | {len(r) or '-'} | {len(got) if r else '-'} | {gap} |")


if __name__ == "__main__":
    main(sys.argv[1:])
