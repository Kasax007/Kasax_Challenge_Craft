"""sync.py NAME: copies start times / slots / total from vo/NAME_plan.json into vo/NAME/lines.json
(run after the cut changed, before mix.py)."""
import json, os, sys
H = os.path.dirname(os.path.abspath(__file__)); n = sys.argv[1]
plan = json.load(open(f'{H}/{n}_plan.json')); meta = json.load(open(f'{H}/{n}/lines.json'))
meta['total'] = plan['total']
for k, ln in enumerate(meta['lines']):
    st = plan['lines'][k][0]
    nxt = plan['lines'][k + 1][0] if k + 1 < len(plan['lines']) else plan['total']
    ln['t0'] = st; ln['slot'] = round(nxt - st - 0.12, 2)
json.dump(meta, open(f'{H}/{n}/lines.json', 'w'), indent=1)
print('synced', n)
