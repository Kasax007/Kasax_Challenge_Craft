"""Per plan step kind ("mine sugar_cane", "hunt ... for gunpowder", "craft x"): how often, and how long
it ran in game seconds (until the bot's next step, done or failure line).

    python3 steps.py run-*/versus_*.log
"""
import re, sys, collections, statistics

def secs(ts):
    h, m, s = map(int, ts.split(":"))
    return h * 3600 + m * 60 + s

STEP = re.compile(r"\[(\d\d:\d\d:\d\d)\].*\[Bot\] (\w+):   (mine|hunt|craft|smelt|kill|get|fill|trade|loot|barter|cast) (.*)$")
OTHER = re.compile(r"\[(\d\d:\d\d:\d\d)\].*\[Bot\] (\w+): (->|done:|x |  x |can't get|goal:|quick tile)")
dur = collections.defaultdict(list)
for path in sys.argv[1:]:
    lines = open(path, errors="replace").read().splitlines()
    start = end = gs = None
    for l in lines:
        m = re.match(r"\[(\d\d:\d\d:\d\d)\].*\[BOTVS\] start:", l)
        if m: start = secs(m.group(1))
        m = re.match(r"\[(\d\d:\d\d:\d\d)\].*\[BOTVS\] result .*\"secs\":(\d+)", l)
        if m: end, gs = secs(m.group(1)), int(m.group(2))
    if not start or not end or end <= start: continue
    rate = gs / (end - start)
    open_step = {}
    for l in lines:
        m = STEP.match(l)
        o = OTHER.match(l) if not m else None
        if m or o:
            t, who = secs((m or o).group(1)), (m or o).group(2)
            prev = open_step.pop(who, None)
            if prev:
                kind = prev[1]
                dur[kind].append((t - prev[0]) * rate)
            if m:
                what = m.group(4)
                what = re.sub(r"\(\d+\)", "(n)", what)
                what = re.sub(r" x\d+", " xN", what)
                what = re.sub(r"at y -?\d+", "at y Y", what)
                open_step[who] = (t, m.group(3) + " " + what)
rows = sorted(dur.items(), key=lambda kv: -sum(kv[1]))
print("%-60s %4s %7s %7s %7s" % ("step", "n", "median", "mean", "total"))
for k, v in rows[:60]:
    print("%-60s %4d %7.0f %7.0f %7.0f" % (k[:60], len(v), statistics.median(v), statistics.mean(v), sum(v)))
