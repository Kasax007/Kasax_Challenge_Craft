#!/usr/bin/env python3
"""Sums the [BOTBENCH] reports of several runs: where the time went and what was claimed.
   analyse.py run/bench_*.log"""
import re, sys, collections
time_on = collections.Counter(); claims = collections.Counter(); tiles = []
fails = deaths = idle = 0
for path in sys.argv[1:]:
    text = open(path, errors='replace').read()
    for m in re.finditer(r'\[BOTBENCH\] time on (.*): (\d+) s', text):
        key = re.sub(r'\d+', 'N', m.group(1))
        time_on[key] += int(m.group(2))
    m = re.search(r'\[BOTBENCH\] tiles claimed: (\d+) \((.*)\)', text)
    if m:
        tiles.append(int(m.group(1)))
        for c in filter(None, m.group(2).split(', ')): claims[c] += 1
    m = re.search(r'idle: (\d+) s, failed tasks: (\d+), deaths: (\d+)', text)
    if m:
        idle += int(m.group(1)); fails += int(m.group(2)); deaths += int(m.group(3))
n = max(1, len(tiles))
print(f"runs {len(tiles)}  tiles avg {sum(tiles)/n:.1f}  per run {tiles}  idle {idle}s  failed {fails}  deaths {deaths}")
print("where the time went (all runs):")
for k, v in time_on.most_common(25): print(f"  {v:6d} s  {k}")
print("claimed:", dict(claims.most_common()))
