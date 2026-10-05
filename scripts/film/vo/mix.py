"""Puts the voice lines on the time line, mixes them over the SFX (ducked under the voice) and muxes
the result into the final and clean videos.   python vo/mix.py challenges|casino"""
import os, sys, json, subprocess
import numpy as np, torch, torchaudio as ta, torchaudio.functional as AF

HERE = os.path.dirname(os.path.abspath(__file__))
SP = os.path.dirname(HERE)
SHORT = sys.argv[1]
SR = 48000
meta = json.load(open(os.path.join(HERE, SHORT, 'lines.json')))
n = int((meta['total'] + 0.5) * SR)
voice = np.zeros(n, np.float32)
for ln in meta['lines']:
    a, sr = ta.load(os.path.join(HERE, SHORT, f"{ln['i']:02d}.wav"))
    a = AF.resample(a, sr, SR)[0]
    dur = a.shape[-1] / SR
    if dur > ln['slot']:
        # a line that runs long is sped up a little (pitch kept) - at most 1.3x, beyond that it overlaps
        f = min(1.3, dur / ln['slot'])
        tmp_in, tmp_out = '/tmp/claude-0/vo_in.wav', '/tmp/claude-0/vo_out.wav'
        ta.save(tmp_in, a.unsqueeze(0), SR)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', tmp_in, '-filter:a', f'atempo={f:.3f}', tmp_out], check=True)
        a = ta.load(tmp_out)[0][0]
        if dur / f > ln['slot'] * 1.02:
            print('still long:', ln)
    a = a.numpy()
    a = a / (np.abs(a).max() + 1e-6) * 0.85
    s = int(ln['t0'] * SR)
    e = min(n, s + len(a))
    voice[s:e] += a[: e - s]
# SFX, ducked to 25 % while the voice speaks (smoothed envelope)
sfx, sr = ta.load(os.path.join(SP, 'out', f'{SHORT}_sfx.wav'))
sfx = AF.resample(sfx.mean(0, keepdim=True), sr, SR)[0].numpy()
sfx = np.pad(sfx, (0, max(0, n - len(sfx))))[:n]
env = np.abs(voice)
k = int(0.25 * SR)
env = np.convolve(env > 0.02, np.ones(k) / k, mode='same')
gain = 1.0 - 0.75 * np.clip(env * 3, 0, 1)
mix = voice + sfx * gain
mix = mix / max(1.0, np.abs(mix).max() / 0.95)
out = os.path.join(SP, 'out')
ta.save(os.path.join(out, f'{SHORT}_voice.wav'), torch.from_numpy(voice).unsqueeze(0), SR)
ta.save(os.path.join(out, f'{SHORT}_mix.wav'), torch.from_numpy(mix.astype(np.float32)).unsqueeze(0), SR)
for kind in ('final', 'clean'):
    src = os.path.join(out, f'{SHORT}_{kind}.mp4')
    dst = os.path.join(out, 'send', f'{SHORT}_{kind}_vo.mp4')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-i', os.path.join(out, f'{SHORT}_mix.wav'),
                    '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-preset', 'slow', '-crf', '25',
                    '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', dst], check=True)
print('mixed', SHORT)
