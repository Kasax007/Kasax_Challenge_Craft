"""Game seconds per root task ("-> ..." lines of the brain), per variant, summed over the games."""
import re, sys, collections

def secs(ts):
    h, m, s = map(int, ts.split(":"))
    return h * 3600 + m * 60 + s

tot = collections.defaultdict(collections.Counter)
games = collections.Counter()
for path in sys.argv[1:]:
    lines = open(path, errors="replace").read().splitlines()
    start = end = gamesecs = None
    params = {}
    for l in lines:
        m = re.match(r"\[(\d\d:\d\d:\d\d)\].*\[BOTVS\] start:", l)
        if m: start = secs(m.group(1))
        m = re.match(r"\[(\d\d:\d\d:\d\d)\].*\[BOTVS\] result .*\"secs\":(\d+)", l)
        if m: end, gamesecs = secs(m.group(1)), int(m.group(2))
        m = re.search(r"\[BOTVS\] (\w+) plays (\w+) with (\S+)", l)
        if m: params[m.group(1)] = m.group(3)
    if start is None or end is None or end <= start: continue
    rate = gamesecs / (end - start)
    cur = {}
    for l in lines:
        m = re.match(r"\[(\d\d:\d\d:\d\d)\]", l)
        if not m: continue
        t = secs(m.group(1))
        if t < start or t > end: continue
        m = re.search(r"\[Bot\] (\w+): (-> (.+)|goal: (.+?) \(~|dead at night|night, not geared|nightfall)", l)
        if not m: continue
        name = m.group(1)
        what = m.group(3) or ("GOAL " + m.group(4) if m.group(4) else m.group(2))
        what = re.sub(r"\d+", "#", what)
        what = re.sub(r"(get|mine) ([a-z_]+)\|… \(#\)", r"\1 \2|…", what)
        if name in cur:
            w0, t0 = cur[name]
            tot[params.get(name, "?")][w0] += (t - t0) * rate
        cur[name] = (what, t)
    for name, (w0, t0) in cur.items():
        tot[params.get(name, "?")][w0] += (end - t0) * rate
    for name in params: games[params[name]] += 1
for v, c in tot.items():
    print(f"== {v}: {games[v]} games, per game:")
    for k, s in sorted(c.items(), key=lambda kv: -kv[1])[:22]:
        print(f"  {s / games[v]:6.0f} s  {k[:70]}")
