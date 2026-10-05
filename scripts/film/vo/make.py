"""Voice-over for both shorts with Chatterbox (open-source TTS, MIT): one line per caption beat,
each with its own energy (exaggeration) and pacing (cfg_weight), fitted into its time slot.
   python vo/make.py challenges|casino"""
import os, sys, json, subprocess, time
import torch, torchaudio as ta

torch.set_num_threads(2)
from chatterbox.tts import ChatterboxTTS

HERE = os.path.dirname(os.path.abspath(__file__))
SHORT = sys.argv[1]
# (start s, text, exaggeration, cfg_weight): high exaggeration = hype, low cfg = faster, livelier
LINES = {
    'challenges': (51.6, [
        (0.0, "I coded fifty viral challenges into Minecraft!", 0.85, 0.3),
        (2.8, "Red light, green light. Move on red... you're dead.", 0.7, 0.3),
        (6.4, "Dice: you only walk what you roll.", 0.6, 0.35),
        (9.4, "No legs at all? Travel by cushion!", 0.7, 0.3),
        (12.4, "Every chunk is one random block.", 0.6, 0.35),
        (15.2, "Stand still, and the floor burns you.", 0.65, 0.3),
        (18.0, "Mobs spawn giant... or tiny.", 0.7, 0.3),
        (20.9, "Your drops fly into the sky.", 0.6, 0.35),
        (23.5, "One zombie? Ten zombies!", 0.85, 0.3),
        (26.5, "Skyblock, in any world.", 0.55, 0.35),
        (29.1, "Race your friends for random items...", 0.6, 0.3),
        (31.9, "or play Lockout against Bob, an AI I'm still teaching.", 0.6, 0.3),
        (36.3, "There's even a casino, and the House always wins.", 0.65, 0.3),
        (40.3, "Stack them for more XP, and unlock all fifty.", 0.6, 0.3),
        (45.1, "It's free: Challenge Craft, on CurseForge.", 0.6, 0.35),
        (47.6, "Which challenge should I code next?", 0.75, 0.35),
    ]),
    'casino': (52.9, [
        (0.0, "I built a real casino in Minecraft!", 0.85, 0.3),
        (2.6, "Every item you own is worth chips,", 0.55, 0.35),
        (5.1, "and the croupier buys your loot.", 0.55, 0.35),
        (7.3, "But every ten minutes, the House collects its fee.", 0.5, 0.35),
        (10.3, "Can't pay? The House wins. Run over.", 0.45, 0.45),
        (12.9, "So you grind... or you gamble.", 0.6, 0.35),
        (15.5, "Slots with free spins!", 0.75, 0.3),
        (18.9, "Plinko!", 0.8, 0.3),
        (21.5, "Crash: cash out before it blows!", 0.85, 0.3),
        (26.9, "Roulette, right on the felt.", 0.6, 0.35),
        (31.3, "But lose a bet, and the House sends its regards.", 0.5, 0.4),
        (35.0, "And when you die...", 0.35, 0.5),
        (35.9, "you play blackjack for your life.", 0.55, 0.35),
        (39.8, "Win, and you're back. With everything!", 0.8, 0.3),
        (42.4, "Every game pays back ninety-six percent,", 0.5, 0.35),
        (45.4, "so in the long run, the House always wins.", 0.55, 0.4),
        (48.4, "Would you gamble?", 0.75, 0.35),
    ]),
}[SHORT]
TOTAL, LINES = LINES
OUT = os.path.join(HERE, SHORT)
os.makedirs(OUT, exist_ok=True)

model = ChatterboxTTS.from_pretrained(device="cpu")
SR = model.sr
report = []
for i, (t0, text, ex, cfg) in enumerate(LINES):
    slot = (LINES[i + 1][0] if i + 1 < len(LINES) else TOTAL) - t0 - 0.12
    path = os.path.join(OUT, f"{i:02d}.wav")
    best = None
    for attempt in range(2):
        torch.manual_seed(1000 * i + attempt)
        wav = model.generate(text, exaggeration=ex, cfg_weight=cfg)
        # trim silence at both ends
        a = wav[0]
        thr = a.abs().max() * 0.02
        idx = (a.abs() > thr).nonzero()
        a = a[max(0, int(idx[0]) - 400): int(idx[-1]) + 800] if len(idx) else a
        dur = a.shape[-1] / SR
        if best is None or abs(dur - min(dur, slot)) < abs(best[1] - min(best[1], slot)):
            best = (a, dur)
        if dur <= slot * 1.15:
            break
    a, dur = best
    ta.save(path, a.unsqueeze(0), SR)
    report.append(dict(i=i, t0=t0, slot=round(slot, 2), dur=round(dur, 2), text=text))
    print(f"{i:02d} {t0:5.1f}s slot {slot:4.2f} got {dur:4.2f}  {text}", flush=True)
json.dump(dict(total=TOTAL, sr=SR, lines=report), open(os.path.join(OUT, 'lines.json'), 'w'), indent=1)
print('done')
