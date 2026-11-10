"""The goal priors shipped with the mod (src/main/resources/bob-knowledge/goal-priors.json): per goal,
what the self-play leagues measured - the seconds a tile took per time it was got, the failed starts'
seconds counted too, against the plan's estimate - as GoalExperience reads it.

    python3 scripts/bot/analysis/goal_priors.py [--exclude id,id] [--per-start 0.5] journals/**/*.jsonl > goal-priors.json

Per goal from the journal's goal lines of the newest estimates version (ObtainPlanner.ESTIMATES):
starts that ended done, failed, overrun or died count (dropped and replaced ones were cut short by
something else: the other player claimed it, the night, food). log = log((seconds of the counted
starts / max(done, 0.5)) / mean plan estimate), clamped to [log --min-ratio, log 8]; games =
--per-start x counted starts. --exclude: goals whose estimates changed since the journals were written.
"""
import argparse
import collections
import glob
import json
import math
import sys

COUNTED = {"done", "failed", "overrun", "died"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("journals", nargs="+")
    ap.add_argument("--exclude", default="")
    ap.add_argument("--per-start", type=float, default=0.5)
    ap.add_argument("--min-starts", type=int, default=2)
    ap.add_argument("--min-ratio", type=float, default=0.5, help="the least a prior may believe of the plan (few starts, lucky ones)")
    a = ap.parse_args()
    exclude = {x for x in a.exclude.split(",") if x}
    rows, seen = [], set()
    files = []
    for pattern in a.journals:
        files += glob.glob(pattern, recursive=True) or [pattern]
    for f in files:
        for line in open(f, errors="replace"):
            try:
                d = json.loads(line)
            except ValueError:
                continue
            if d.get("t") != "goal" or d.get("id") in seen:
                continue
            seen.add(d.get("id"))
            rows.append(d)
    version = max((int(r.get("estimates", 1)) for r in rows), default=1)
    rows = [r for r in rows if int(r.get("estimates", 1)) == version and not r.get("proxy") and r.get("outcome") in COUNTED]
    by = collections.defaultdict(list)
    for r in rows:
        by[r["goal"]].append(r)
    goals = {}
    for g, xs in sorted(by.items()):
        if g in exclude or len(xs) < a.min_starts:
            continue
        done = sum(1 for x in xs if x["outcome"] == "done")
        spent = sum(max(0.0, float(x.get("secs", 0))) for x in xs)
        plan = sum(max(1.0, float(x.get("plain", 1))) for x in xs) / len(xs)
        ratio = spent / max(done, 0.5) / plan
        log = max(math.log(max(0.25, a.min_ratio)), min(math.log(8), math.log(max(1e-6, ratio))))
        goals[g] = {"log": round(log, 4), "games": round(a.per_start * len(xs), 2), "starts": len(xs), "done": done,
                    "plan": round(plan, 1), "per_claim": round(spent / max(done, 0.5), 1)}
    json.dump({"estimates": version, "journal_goals": len(rows), "per_start": a.per_start, "excluded": sorted(exclude), "goals": goals},
              sys.stdout, indent=1, sort_keys=True)
    print()


if __name__ == "__main__":
    main()
