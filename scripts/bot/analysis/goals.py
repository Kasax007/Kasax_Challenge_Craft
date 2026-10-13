"""Per goal started in a self-play game: the estimate, how long it really ran (game seconds), and how it ended."""
import re, sys, collections, datetime

def secs(ts):
    h, m, s = map(int, ts.split(":"))
    return h * 3600 + m * 60 + s

rows = []
for path in sys.argv[1:]:
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
            rows.append((params.get(name, "?"), g[0], g[1], (t - g[2]) * rate, how, g[3]))
    for l in lines:
        m = re.match(r"\[(\d\d:\d\d:\d\d)\]", l)
        if not m: continue
        t = secs(m.group(1))
        if t < start: continue
        m = re.search(r"\[Bot\] (\w+): (quick tile, before anything else: |goal: )(.+?) \(~(\d+) s", l)
        if m and m.group(2) == "goal: ":
            name = m.group(1)
            close(name, t, "replaced")
            quick = bool(open_goal.get(name + "#quick"))
            open_goal[name] = (m.group(3), int(m.group(4)), t, "quick" if open_goal.pop(name + "#quick", None) else "plan")
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

by = collections.defaultdict(list)
for v, title, est, real, how, kind in rows:
    by[title].append((est, real, how, kind, v))
print(f"{'tile':28s} {'n':>3s} {'est':>5s} {'real':>5s} {'ratio':>5s}  outcomes")
for title, xs in sorted(by.items(), key=lambda kv: -sum(x[1] for x in kv[1])):
    est = sum(x[0] for x in xs) / len(xs); real = sum(x[1] for x in xs) / len(xs)
    outs = collections.Counter(x[2] for x in xs)
    kinds = collections.Counter(x[3] for x in xs)
    print(f"{title[:28]:28s} {len(xs):3d} {est:5.0f} {real:5.0f} {real / max(1, est):5.1f}  {dict(outs)} {dict(kinds)}")
tot = collections.Counter(x[4] for x in rows)
claimed = sum(1 for x in rows if x[4] == "claimed")
print(f"\n{len(rows)} goals started, {claimed} claimed; time on goals {sum(x[3] for x in rows):.0f} s, on claimed ones {sum(x[3] for x in rows if x[4] == 'claimed'):.0f} s")
