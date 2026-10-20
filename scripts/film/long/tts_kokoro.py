"""Voice-over of the long-form showcase with Kokoro (am_michael, a warm male narrator), one call per
paragraph so the intonation runs through it; every paragraph checked with Whisper.
  tts/bin/python long/tts_kokoro.py [paragraph indices to redo]     (env HF_HOME=$SP/hf)
Output: long/vo/uNNN.wav (24 kHz) + uNNN.txt, long/vo/units.json [(paragraph, text)] (one unit per paragraph)."""
import os, sys, json, re, warnings
warnings.filterwarnings('ignore')
import numpy as np, soundfile as sf
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from script import S
VO = HERE + '/vo'
os.makedirs(VO, exist_ok=True)
VOICE, SPEED = os.environ.get('LONG_VOICE', 'am_michael'), float(os.environ.get('LONG_SPEED', '0.95'))
# How the narrator should say a few words (spelling only for the speech; captions keep the script).
SAY = {'XP': 'X P', 'AI': 'A I', 'CurseForge': 'Curse Forge', 'Plinko': 'Plinko', 'Skyblock': 'Sky block'}
U = [(pi, t.replace('*', '')) for pi, (_, t) in enumerate(S)]
json.dump(U, open(VO + '/units.json', 'w'), indent=0)
from kokoro import KPipeline
from faster_whisper import WhisperModel
pipe = KPipeline(lang_code='a', repo_id='hexgrad/Kokoro-82M')
wh = WhisperModel('base.en', device='cpu', compute_type='int8', cpu_threads=2)
NUM = {'50': 'fifty', '96': 'ninety six', '20': 'twenty', '10': 'ten', '5000': 'five thousand'}
def norm(s):
    s = s.lower().replace('-', ' ')
    for k, v in NUM.items():
        s = re.sub(r'\b%s\b' % k, v, s)
    return re.sub(r'[^a-z0-9 ]', '', s).split()
only = [int(x) for x in sys.argv[1:] if x.isdigit()]
for i, (pi, text) in enumerate(U):
    out = f'{VO}/u{i:03d}.wav'
    if only and i not in only:
        continue
    if os.path.exists(out) and not only:
        continue
    say = text
    for k, v in SAY.items():
        say = re.sub(r'\b%s\b' % re.escape(k), v, say)
    a = np.concatenate([x for _, _, x in pipe(say, voice=VOICE, speed=SPEED)])
    sf.write(out, a, 24000)
    open(out[:-4] + '.txt', 'w').write(text)
    segs, _ = wh.transcribe(out, language='en')
    got = norm(' '.join(s.text for s in segs).replace('Curse Forge', 'CurseForge').replace('Sky block', 'Skyblock'))
    want = norm(text)
    miss = [w for w in want if w not in got]
    print(f'u{i} {len(a)/24000:.1f}s {len(want)/(len(a)/24000):.2f} w/s miss={miss}', flush=True)
print('TTS DONE', flush=True)
