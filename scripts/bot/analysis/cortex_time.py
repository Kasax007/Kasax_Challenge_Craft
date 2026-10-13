"""Sums the cortex's time per category (the last summary line per bot per game) by variant."""
import re, sys, collections
tot = collections.defaultdict(lambda: collections.Counter())
games = collections.Counter()
for path in sys.argv[1:]:
    text = open(path, errors="replace").read()
    if "[BOTVS] result" not in text:
        continue
    params = dict(re.findall(r"\[BOTVS\] (\w+) plays \w+ with (\S+)", text))
    last = {}
    for m in re.finditer(r"\[Cortex\] (\w+): loops broken \d+, refused \d+; (.*)", text):
        last[m.group(1)] = m.group(2)
    for name, line in last.items():
        v = params.get(name, "?")
        games[v] += 1
        for part in line.split(";"):
            part = part.strip()
            mm = re.match(r"(\w+):(.*?) (\d+)x/(\d+) ok/(\d+) fail(?:/(\d+) died)? (\d+)s", part)
            if not mm:
                continue
            cat, what, n, ok, fail, died, secs = mm.groups()
            key = cat + ":" + re.sub(r"\d+", "#", what.strip())[:40]
            tot[v][key] += int(secs)
            tot[v]["=" + cat] += int(secs)
for v, c in tot.items():
    print(f"== {v}: {games[v]} games")
    for k, s in sorted(c.items(), key=lambda kv: -kv[1])[:25]:
        print(f"  {s / games[v]:7.0f} s/game  {k}")
