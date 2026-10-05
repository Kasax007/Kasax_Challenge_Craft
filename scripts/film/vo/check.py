import sys, os, json, re
from faster_whisper import WhisperModel
m = WhisperModel("base.en", device="cpu", compute_type="int8", cpu_threads=2)
d = sys.argv[1]
meta = json.load(open(os.path.join(d, 'lines.json')))
def norm(s): return re.sub(r'[^a-z0-9 ]', '', s.lower().replace('-', ' ')).split()
for ln in meta['lines']:
    segs, _ = m.transcribe(os.path.join(d, f"{ln['i']:02d}.wav"), language='en')
    heard = ' '.join(s.text.strip() for s in segs)
    want, got = norm(ln['text']), norm(heard)
    miss = [w for w in want if w not in got]
    print(f"{ln['i']:02d} {'OK ' if len(miss) <= 1 else 'BAD'} heard: {heard!r}  missing: {miss}")
