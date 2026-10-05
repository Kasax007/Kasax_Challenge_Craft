"""Re-takes single lines: python vo/regen.py SHORT i "text" ex cfg seed"""
import sys, os, json, torch, torchaudio as ta
torch.set_num_threads(2)
from chatterbox.tts import ChatterboxTTS
d = os.path.join(os.path.dirname(os.path.abspath(__file__)), sys.argv[1])
i, text, ex, cfg, seed = int(sys.argv[2]), sys.argv[3], float(sys.argv[4]), float(sys.argv[5]), int(sys.argv[6])
m = ChatterboxTTS.from_pretrained(device="cpu")
torch.manual_seed(seed)
a = m.generate(text, exaggeration=ex, cfg_weight=cfg)[0]
thr = a.abs().max() * 0.02; idx = (a.abs() > thr).nonzero()
a = a[max(0, int(idx[0]) - 400): int(idx[-1]) + 800]
ta.save(os.path.join(d, f"{i:02d}.wav"), a.unsqueeze(0), m.sr)
meta = json.load(open(os.path.join(d, 'lines.json')))
meta['lines'][i].update(text=text, dur=round(a.shape[-1] / m.sr, 2))
json.dump(meta, open(os.path.join(d, 'lines.json'), 'w'), indent=1)
print(i, round(a.shape[-1] / m.sr, 2), text)
