"""Per goal started in a self-play game: the estimate, how long it really ran (game seconds), and how it ended.

    python3 scripts/bot/analysis/goals.py run-*/versus_*.log            # the table
    python3 scripts/bot/analysis/goals.py --md run-*/versus_*.log       # the same as a markdown table

Columns: n starts; est = the believed estimate when it was started (the "goal: X (~N s" line);
plan = the planner's own figure (logged since round 21 as "plan ~N s"); expert = the knowledge
base's expert time; real = mean game seconds it ran; med = their median; ratio = real / est (of the
means); ok = the share of starts that ended with the tile claimed. The last lines: how wrong the
estimates are on the whole - the median and mean of |log(real / est)| over the claimed starts (0:
exact; 0.69: off by a factor of two), and the share under- and overestimated.
"""
import collections
import math
import re
import sys


def secs(ts):
    h, m, s = map(int, ts.split(":"))
    return h * 3600 + m * 60 + s


def parse(paths):
    """(params, title, est, real, outcome, kind, plan, expert) per goal start."""
    rows = []
    for path in paths:
        lines = open(path, errors="replace").read().splitlines()
        start = end = None
        gamesecs = None
        params = {}
        team = {}
        for l in lines:
            m = re.match(r"\[(\d\d:\d\d:\d\d)\].*\[BOTVS\] start:", l)
            if m: start = secs(m.group(1))
            m = re.match(r"\[(\d\d:\d\d:\d\d)\].*\[BOTVS\] result .*\"secs\":(\d+)", l)
            if m: end, gamesecs = secs(m.group(1)), int(m.group(2))
            m = re.search(r"\[BOTVS\] (\w+) plays (\w+) with (\S+)", l)
            if m:
                params[m.group(1)] = m.group(3)
                team[m.group(2).lower()] = m.group(1)
        if start is None or end is None or end <= start:
            continue
        rate = gamesecs / (end - start)
        open_goal = {}

        def close(name, t, how):
            g = open_goal.pop(name, None)
            if g:
                rows.append((params.get(name, "?"), g[0], g[1], (t - g[2]) * rate, how, g[3], g[4], g[5]))

        for l in lines:
            m = re.match(r"\[(\d\d:\d\d:\d\d)\]", l)
            if not m: continue
            t = secs(m.group(1))
            if t < start: continue
            m = re.search(r"\[Bot\] (\w+): (quick tile, before anything else: |goal: )(.+?) \(~(\d+) s", l)
            if m and m.group(2) == "goal: ":
                name = m.group(1)
                close(name, t, "replaced")
                plan = re.search(r"plan ~(\d+) s", l)
                expert = re.search(r"expert ~(\d+) s", l)
                open_goal[name] = (m.group(3), int(m.group(4)), t, "quick" if open_goal.pop(name + "#quick", None) else "plan",
                                   int(plan.group(1)) if plan else None, int(expert.group(1)) if expert else None)
                continue
            if m:
                open_goal[m.group(1) + "#quick"] = True
                continue
            m = re.search(r"\[BOTVS\] (\w+) claimed (.+) at \d+ s", l)
            if m and m.group(1) in open_goal and open_goal[m.group(1)][0] == m.group(2):
                close(m.group(1), t, "claimed")
                continue
            m = re.search(r"\[Bot\] (\w+): (\S+) takes too long", l)
            if m and m.group(1) in open_goal:
                close(m.group(1), t, "too long")
                continue
            m = re.search(r"\[Lockout Bingo\] (\w+) claimed (.+)!", l)
            if m:
                by_team = team.get(m.group(1).lower())
                for name, g in list(open_goal.items()):
                    if "#" not in name and g[0] == m.group(2):
                        close(name, t, "claimed" if name == by_team else "lost (other)")
        for name in list(open_goal):
            if "#" not in name: close(name, end, "open at end")
    return rows


def mean(xs):
    xs = [x for x in xs if x is not None]
    return sum(xs) / len(xs) if xs else None


def median(xs):
    xs = sorted(x for x in xs if x is not None)
    if not xs: return None
    n = len(xs)
    return xs[n // 2] if n % 2 else (xs[n // 2 - 1] + xs[n // 2]) / 2


def fmt(x, w=5):
    return f"{x:{w}.0f}" if x is not None else f"{'-':>{w}s}"


def main(argv):
    md = "--md" in argv
    paths = [a for a in argv if not a.startswith("--")]
    rows = parse(paths)
    by = collections.defaultdict(list)
    for v, title, est, real, how, kind, plan, expert in rows:
        by[title].append((est, real, how, kind, v, plan, expert))
    order = sorted(by.items(), key=lambda kv: -sum(x[1] for x in kv[1]))
    if md:
        print("| tile | n | est | plan | expert | real | median | real/est | claimed |")
        print("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    else:
        print(f"{'tile':28s} {'n':>3s} {'est':>5s} {'plan':>5s} {'exp':>5s} {'real':>5s} {'med':>5s} {'ratio':>5s} {'ok':>4s}  outcomes")
    for title, xs in order:
        est = mean([x[0] for x in xs]); real = mean([x[1] for x in xs]); med = median([x[1] for x in xs])
        plan = mean([x[5] for x in xs]); expert = mean([x[6] for x in xs])
        outs = collections.Counter(x[2] for x in xs)
        ok = outs.get("claimed", 0) / len(xs)
        ratio = real / max(1, est)
        if md:
            print(f"| {title} | {len(xs)} | {fmt(est, 0)} | {fmt(plan, 0)} | {fmt(expert, 0)} | {fmt(real, 0)} | {fmt(med, 0)} | {ratio:.1f} | {100 * ok:.0f}% |")
        else:
            print(f"{title[:28]:28s} {len(xs):3d} {fmt(est)} {fmt(plan)} {fmt(expert)} {fmt(real)} {fmt(med)} {ratio:5.1f} {100 * ok:3.0f}%  {dict(outs)}")
    claimed = [x for x in rows if x[4] == "claimed" and x[2] > 0 and x[3] > 0]
    errs = [abs(math.log(x[3] / x[2])) for x in claimed]
    under = sum(1 for x in claimed if x[3] > 1.5 * x[2])
    over = sum(1 for x in claimed if x[3] < x[2] / 1.5)
    tot_claimed = sum(1 for x in rows if x[4] == "claimed")
    print(f"\n{len(rows)} goals started, {tot_claimed} claimed; time on goals {sum(x[3] for x in rows):.0f} s, "
          f"on claimed ones {sum(x[3] for x in rows if x[4] == 'claimed'):.0f} s")
    if errs:
        print(f"estimate error on the claimed ones: median |log(real/est)| {median(errs):.2f}, mean {mean(errs):.2f} "
              f"(a factor of {math.exp(median(errs)):.1f} / {math.exp(mean(errs)):.1f}); "
              f"real over 1.5x the estimate {under} of {len(claimed)}, under 2/3 of it {over}")
    plans = [abs(math.log(x[3] / x[6])) for x in claimed if x[6]]
    if plans:
        print(f"the plan's own figure: median |log(real/plan)| {median(plans):.2f}, mean {mean(plans):.2f} over {len(plans)}")


if __name__ == "__main__":
    main(sys.argv[1:])
