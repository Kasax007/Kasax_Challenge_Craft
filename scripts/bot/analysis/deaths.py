"""Every death in self-play logs: who (with which knobs), the death message, and the hits and the guardian's words before it."""
import re, sys

for path in sys.argv[1:]:
    lines = open(path, errors="replace").read().splitlines()
    if not any("[BOTVS] result" in l for l in lines):
        continue
    params = {}
    for l in lines:
        m = re.search(r"\[BOTVS\] (\w+) plays \w+ with (\S+)", l)
        if m: params[m.group(1)] = m.group(2)
    for i, l in enumerate(lines):
        m = re.search(r"System chat: (?:\[Lvl \d+\] )?(\w+) (was slain|was shot|died|fell|drowned|burned|blew up|hit the ground|tried to swim|went up in flames|was blown up|walked into|suffocated|starved|was killed|was fireballed)(.*)", l)
        if not m or m.group(1) not in params: continue
        name = m.group(1)
        print(f"=== {path.split('/')[-1]} {name} ({params[name]}): {m.group(1)} {m.group(2)}{m.group(3)}")
        before = [x for x in lines[max(0, i - 400):i] if f"] {name}: " in x and re.search(r"hit [0-9.]+ ->|guardian|no cover|low \(|away|walled|pillar|dug in|box|night|->", x)]
        for x in before[-10:]:
            print("   " + re.sub(r"^\[[\d:]+\] \[Server thread/INFO\] \(ChallengeCraft-Bot\) \[Bot\] ", "", x)[:220])
