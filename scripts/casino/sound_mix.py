"""Builds and mixes every sound of "The House Always Wins" (challenge 50).

  python3 sound_mix.py SAMPLE_DIR OUTPUT_SOUNDS_DIR

Recorded material (all CC0): Kenney Boardgame Pack (cards, chips), Kenney RPG Audio (handled
coins), Kenney Impact Sounds (metal hits), StarNinjas "12 coin sound effects". Everything else is
synthesised here: reels, lever, jingles, roulette ball, rocket, explosion, cash register, gong.

Mixing rules, applied to every file so no cue jumps out of the game's mix:
  * mono (Minecraft only attenuates mono sounds with distance), 44.1 kHz;
  * DC removed, leading/trailing silence trimmed, 4 ms fade-in and a short fade-out (no clicks);
  * loudness matched by RMS to a per-category target, then a soft limiter keeps peaks <= -1 dBFS;
  * a light room reverb on musical cues so they sit in the world instead of in your head.
"""
import os
import sys

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
SRC, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(50)


# ---- helpers -----------------------------------------------------------------------------------

def load(path):
    data, sr = sf.read(path, always_2d=True)
    mono = data.mean(axis=1)
    if sr != SR:
        mono = signal.resample_poly(mono, SR, sr)
    return mono.astype(np.float64)


def t(sec):
    return np.arange(int(sec * SR)) / SR


def env(n, a=0.005, d=0.1, s=0.0, r=0.1, sustain_time=0.0):
    """ADSR envelope of n samples (times in seconds)."""
    out = np.zeros(n)
    ai, di, si = int(a * SR), int(d * SR), int(sustain_time * SR)
    ri = int(r * SR)
    i = 0
    seg = min(ai, n - i)
    out[i:i + seg] = np.linspace(0, 1, seg, endpoint=False)
    i += seg
    seg = min(di, n - i)
    out[i:i + seg] = np.linspace(1, s, seg, endpoint=False)
    i += seg
    seg = min(si, n - i)
    out[i:i + seg] = s
    i += seg
    seg = min(ri, n - i)
    if seg > 0:
        out[i:i + seg] = np.linspace(s, 0, seg)
    return out


def expdecay(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def bell(freq, dur, amp=1.0, bright=1.0):
    """FM bell: inharmonic partials, long decay."""
    x = t(dur)
    mod = np.sin(2 * np.pi * freq * 3.5 * x) * 2.2 * bright * expdecay(len(x), dur * 0.35)
    y = np.sin(2 * np.pi * freq * x + mod)
    y += 0.35 * np.sin(2 * np.pi * freq * 2.76 * x) * expdecay(len(x), dur * 0.2)
    y += 0.2 * np.sin(2 * np.pi * freq * 5.4 * x) * expdecay(len(x), dur * 0.08)
    return amp * y * expdecay(len(x), dur * 0.4) * env(len(x), 0.002, 0.0, 1.0, 0.02, dur)


def marimba(freq, dur=0.6, amp=1.0):
    x = t(dur)
    y = np.sin(2 * np.pi * freq * x) + 0.25 * np.sin(2 * np.pi * freq * 4 * x) * expdecay(len(x), 0.05)
    return amp * y * expdecay(len(x), dur * 0.3) * env(len(x), 0.002, 0.0, 1.0, 0.03, dur)


def brass(freq, dur, amp=1.0):
    """Saw-ish brass with a filter swell."""
    x = t(dur)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * x) * np.clip(x / 0.3, 0, 1)
    phase = np.cumsum(freq * vib / SR)
    saw = 2 * (phase % 1) - 1
    e = env(len(x), 0.04, 0.12, 0.8, 0.12, dur - 0.28)
    b, a = signal.butter(2, min(0.99, (1200 + 2600 * 1) / (SR / 2)), "low")
    y = signal.lfilter(b, a, saw) * e
    return amp * y


def noise(dur):
    return rng.standard_normal(int(dur * SR))


def bandpass(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(0.99, hi / (SR / 2))], "band")
    return signal.lfilter(b, a, x)


def lowpass(x, f, order=2):
    b, a = signal.butter(order, min(0.99, f / (SR / 2)), "low")
    return signal.lfilter(b, a, x)


def highpass(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), "high")
    return signal.lfilter(b, a, x)


def place(buf, x, at, gain=1.0):
    i = max(0, int(at * SR))
    need = i + len(x)
    if need > len(buf):
        buf = np.concatenate([buf, np.zeros(need - len(buf))])
    buf[i:i + len(x)] += x * gain
    return buf


def pitch(x, factor):
    """Resample to shift pitch (and length) - fine for short recorded one-shots."""
    n = int(len(x) / factor)
    return signal.resample(x, max(8, n))


def reverb(x, wet=0.18, room=0.6):
    """Small Schroeder room: 4 combs + 2 allpasses."""
    x = np.concatenate([x, np.zeros(int(SR * (0.4 + room)))])
    out = np.zeros_like(x)
    for delay_ms, g in [(29.7, 0.78), (37.1, 0.76), (41.1, 0.74), (43.7, 0.72)]:
        d = int(SR * delay_ms / 1000)
        b = np.zeros(d + 1)
        b[0] = 1
        a = np.zeros(d + 1)
        a[0] = 1
        a[d] = -min(0.93, g * (0.85 + 0.2 * room))  # feedback < 1 keeps the comb stable
        out += signal.lfilter(b, a, x)
    for delay_ms, g in [(5.0, 0.7), (1.7, 0.7)]:
        d = int(SR * delay_ms / 1000)
        b = np.zeros(d + 1)
        b[0] = -g
        b[d] = 1
        a = np.zeros(d + 1)
        a[0] = 1
        a[d] = -g
        out = signal.lfilter(b, a, out)
    out = lowpass(out, 6000)
    return (1 - wet) * x + wet * out / 4


def finish(x, rms_db, fade_out=0.03, trim=True):
    x = x - np.mean(x)
    if trim:
        thr = np.max(np.abs(x)) * 0.004 if np.max(np.abs(x)) > 0 else 0
        idx = np.where(np.abs(x) > thr)[0]
        if len(idx):
            x = x[max(0, idx[0] - 32): idx[-1] + int(0.02 * SR)]
    fi = int(0.004 * SR)
    x[:fi] *= np.linspace(0, 1, fi)
    fo = min(len(x), int(fade_out * SR))
    x[-fo:] *= np.linspace(1, 0, fo)
    rms = np.sqrt(np.mean(x ** 2)) + 1e-12
    x = x * (10 ** (rms_db / 20) / rms)
    # Soft limiter: tanh knee above -3 dBFS, hard ceiling at -1 dBFS.
    ceiling = 10 ** (-1 / 20)
    x = np.tanh(x / ceiling * 1.0) * ceiling if np.max(np.abs(x)) > 10 ** (-3 / 20) else x
    return np.clip(x, -ceiling, ceiling)


written = []


def write(name, x, rms_db, **kw):
    y = finish(np.asarray(x, dtype=np.float64), rms_db, **kw)
    path = os.path.join(OUT, name + ".ogg")
    sf.write(path, y.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    written.append((name, len(y) / SR, 20 * np.log10(np.sqrt(np.mean(y ** 2)) + 1e-12), 20 * np.log10(np.max(np.abs(y)) + 1e-12)))


K = lambda *p: load(os.path.join(SRC, *p))

# ---- recorded ------------------------------------------------------------------------------------

for i in (1, 2, 3):
    write(f"chip{i}", K("boardgame", f"chipsCollide{i}.ogg"), -20)
    write(f"card_slide{i}", K("boardgame", f"cardSlide{i}.ogg"), -22)
    write(f"card_place{i}", K("boardgame", f"cardPlace{i}.ogg"), -21)
# Chip stacks: three collides layered and slightly staggered.
for v in (1, 2):
    buf = np.zeros(1)
    for k, i in enumerate((1, 2, 3) if v == 1 else (3, 1, 2)):
        buf = place(buf, K("boardgame", f"chipsCollide{i}.ogg"), 0.045 * k, 0.8 - 0.15 * k)
    write(f"chip_stack{v}", buf, -19)
for i in (1, 3, 5, 7, 9, 11):
    write(f"coins{(i + 1) // 2}", K("coins", f"coin.{i}.ogg"), -21)
write("coin_shower1", K("coins", "handleCoins.ogg"), -18)
write("coin_shower2", K("coins", "handleCoins2.ogg"), -18)

# Card flip: a short, bright paper snap.
x = highpass(noise(0.07), 1800) * expdecay(int(0.07 * SR), 0.012)
x = place(x, bandpass(noise(0.05), 3000, 8000) * expdecay(int(0.05 * SR), 0.006) * 0.6, 0.012)
write("card_flip", x, -22)

# ---- slot machine --------------------------------------------------------------------------------

metal_light = K("impact", "impactMetal_light_002.ogg")
metal_med = K("impact", "impactMetal_medium_001.ogg")
metal_heavy = K("impact", "impactMetal_heavy_000.ogg")

# Lever: a ratchet down, a heavy clunk, springs back.
buf = np.zeros(int(0.7 * SR))
for k in range(9):
    click = bandpass(noise(0.012), 2000, 6000) * expdecay(int(0.012 * SR), 0.002)
    buf = place(buf, click, 0.02 + k * 0.028, 0.5 + 0.03 * k)
buf = place(buf, metal_heavy, 0.28, 0.9)
thump = np.sin(2 * np.pi * 70 * t(0.25)) * expdecay(int(0.25 * SR), 0.06)
buf = place(buf, thump, 0.28, 0.8)
buf = place(buf, pitch(metal_light, 1.3), 0.5, 0.35)
write("lever", buf, -17)

# Reel spin: 0.45 s of mechanical ticking over a soft whir; played back to back while reels turn.
dur = 0.45
buf = lowpass(noise(dur), 900) * 0.25 * (0.8 + 0.2 * np.sin(2 * np.pi * 11 * t(dur)))
for k in range(int(dur * 26)):
    click = bandpass(noise(0.008), 1500, 5000) * expdecay(int(0.008 * SR), 0.0015)
    buf = place(buf, click, k / 26 + rng.uniform(-0.002, 0.002), 0.35 + rng.uniform(0, 0.1))
write("reel_spin", buf[:int(dur * SR)], -26, fade_out=0.02, trim=False)

# Reel stop: a felt-damped thunk with a small metallic tick on top.
for v, f0 in ((1, 95), (2, 105), (3, 88)):
    x = np.sin(2 * np.pi * f0 * t(0.18)) * expdecay(int(0.18 * SR), 0.04)
    x += lowpass(noise(0.18), 800) * expdecay(int(0.18 * SR), 0.015) * 0.6
    x = place(x, pitch(metal_light, 1.1 + 0.1 * v) * 0.35, 0.003)
    write(f"reel_stop{v}", x, -18)

# Tension: a rising, trembling tone - "will the third one come?"
x = t(1.4)
f = 220 * 2 ** (x / 1.4 * 1.0)
ph = np.cumsum(f / SR)
tone = np.sin(2 * np.pi * ph) + 0.4 * np.sin(4 * np.pi * ph)
trem = 0.6 + 0.4 * np.sin(2 * np.pi * (6 + 6 * x / 1.4) * x)
write("reel_tension", reverb(tone * trem * env(len(x), 0.2, 0, 1, 0.2, 1.0), 0.22), -22)

# Scatter: a star-bright bell ping with sparkle.
x = place(bell(1568, 1.1, 1.0, 1.3), bell(2349, 0.8, 0.6, 1.0), 0, 0.5)
sparkle = np.zeros(1)
for k in range(8):
    sparkle = place(sparkle, np.sin(2 * np.pi * rng.uniform(3000, 6000) * t(0.05)) * expdecay(int(0.05 * SR), 0.01), 0.05 + k * 0.04, 0.2)
x = place(x, sparkle, 0)
write("scatter", reverb(x, 0.25), -20)

# Jingles. C major: C5 E5 G5 C6.
C5, E5, G5, C6, E6, G6 = 523.25, 659.25, 783.99, 1046.5, 1318.5, 1568.0
buf = np.zeros(1)
for k, fq in enumerate((C5, E5, G5)):
    buf = place(buf, marimba(fq, 0.5), k * 0.09, 0.8)
buf = place(buf, bell(C6, 0.9, 0.7), 0.27)
write("win_small", reverb(buf, 0.2), -19)

buf = np.zeros(1)
for k, fq in enumerate((C5, E5, G5, C6, E6)):
    buf = place(buf, brass(fq / 2, 0.35, 0.8), k * 0.11)
    buf = place(buf, marimba(fq, 0.4, 0.5), k * 0.11)
for fq in (C5, E5, G5):
    buf = place(buf, brass(fq / 2, 1.1, 0.6), 0.6)
buf = place(buf, bell(C6, 1.4, 0.8), 0.6)
buf = place(buf, K("coins", "handleCoins.ogg") * 0.35, 0.65)
write("win_big", reverb(buf, 0.24), -16)

buf = np.zeros(1)
seq = [C5, E5, G5, C6, G5, C6, E6, G6]
for k, fq in enumerate(seq):
    buf = place(buf, brass(fq / 2, 0.3, 0.8), k * 0.12)
    buf = place(buf, bell(fq, 0.5, 0.35), k * 0.12)
for fq in (C5, E5, G5, C6):
    buf = place(buf, brass(fq / 2, 2.2, 0.55), 1.0)
for fq in (C6, E6, G6):
    buf = place(buf, bell(fq, 2.0, 0.5), 1.0)
roll = lowpass(noise(1.0), 300) * np.linspace(0, 1, SR) * 0.5
buf = place(buf, roll, 0.0)
buf = place(buf, K("coins", "handleCoins.ogg") * 0.4, 1.05)
buf = place(buf, K("coins", "handleCoins2.ogg") * 0.4, 1.6)
write("win_epic", reverb(buf, 0.28, 0.8), -15)

# Free spins: a harp glissando up into a shimmering chord.
buf = np.zeros(1)
scale = [C5, 587.3, E5, 698.5, G5, 880, 987.8, C6, 1174.7, E6, 1396.9, G6]
for k, fq in enumerate(scale):
    buf = place(buf, marimba(fq, 0.7, 0.55), k * 0.055)
for fq in (C6, E6, G6, 2093.0):
    buf = place(buf, bell(fq, 2.0, 0.45, 0.8), 0.7)
write("free_spins", reverb(buf, 0.3, 0.8), -17)

# Expand: a whoosh up with a sparkle tail.
x = t(1.0)
w = bandpass(noise(1.0), 400, 6000) * np.sin(np.pi * np.clip(x / 0.7, 0, 1)) ** 2
sweep = np.sin(2 * np.pi * np.cumsum(300 * 2 ** (x * 2.5)) / SR) * env(len(x), 0.05, 0, 1, 0.3, 0.6) * 0.4
buf = w * 0.5 + sweep
buf = place(buf, bell(G6, 0.8, 0.5), 0.55)
write("expand", reverb(buf, 0.25), -19)

# ---- roulette ------------------------------------------------------------------------------------

# The whole run of the ball, 8 s: rolling on the track, the rattle over the diamonds, the drop.
dur = 8.0
x = t(dur)
roll_speed = np.clip(1 - x / 6.0, 0.15, 1)
rolling = bandpass(noise(dur), 300, 2500) * (0.25 + 0.2 * np.sin(2 * np.pi * (6 + 10 * roll_speed) * x)) * roll_speed
buf = rolling * env(len(x), 0.3, 0, 1, 1.0, dur - 1.3)
ts = 5.2
while ts < 7.6:
    click = bandpass(noise(0.02), 1500, 7000) * expdecay(int(0.02 * SR), 0.004)
    buf = place(buf, click, ts, 0.9)
    ts += rng.uniform(0.07, 0.25) * (1 + (ts - 5.2) * 0.3)
clack = bandpass(noise(0.05), 800, 5000) * expdecay(int(0.05 * SR), 0.01)
buf = place(buf, clack, 7.75, 1.2)
buf = place(buf, clack * 0.5, 7.86, 1.0)
write("roulette_spin", buf, -22, fade_out=0.2)

x = bandpass(noise(0.35), 900, 6000)
d = np.zeros(len(x))
for k, (at, g) in enumerate([(0.0, 1.0), (0.06, 0.6), (0.11, 0.4), (0.15, 0.25), (0.18, 0.15)]):
    i = int(at * SR)
    d[i:i + int(0.02 * SR)] += g * expdecay(int(0.02 * SR), 0.004)[:len(d) - i]
write("roulette_drop", x * d, -18)

# ---- crash ---------------------------------------------------------------------------------------

x = t(2.2)
rumble = lowpass(noise(2.2), 180) * env(len(x), 0.05, 0.2, 0.7, 1.4, 0.4) * 1.4
hiss = highpass(noise(2.2), 1500) * env(len(x), 0.1, 0.4, 0.4, 1.3, 0.3)
f = 200 + 900 * np.clip(x / 1.8, 0, 1) ** 1.5
sweep = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(len(x), 0.05, 0, 1, 0.6, 1.4) * 0.2
write("rocket_launch", rumble + hiss * 0.5 + sweep, -15)

x = t(3.0)
hum = lowpass(noise(3.0), 250) * 0.6 + np.sin(2 * np.pi * 55 * x) * 0.3
write("rocket_flight", hum * env(len(x), 0.3, 0, 1, 0.5, 2.2), -24)

x = t(2.6)
boom = lowpass(noise(2.6), 900) * expdecay(len(x), 0.35) * 1.3
sub = np.sin(2 * np.pi * (48 + 30 * expdecay(len(x), 0.1)) * x) * expdecay(len(x), 0.5)
crackle = np.zeros(len(x))
for k in range(40):
    at = rng.uniform(0.2, 2.0)
    c = highpass(noise(0.01), 2000) * expdecay(int(0.01 * SR), 0.002)
    crackle = place(crackle, c, at, rng.uniform(0.1, 0.4) * np.exp(-at))[:len(x)]
write("crash", boom + sub + crackle, -13, fade_out=0.4)

buf = np.zeros(1)
buf = place(buf, K("coins", "coin.2.ogg"), 0, 0.9)
buf = place(buf, bell(E6, 0.9, 0.7), 0.04)
buf = place(buf, bell(G6, 0.9, 0.5), 0.12)
write("cash_out", reverb(buf, 0.2), -18)

# ---- the House -----------------------------------------------------------------------------------

# Cash register: the drawer's mechanical clack, then the bell.
buf = np.zeros(1)
buf = place(buf, pitch(metal_med, 0.9) * 0.6, 0.0)
drawer = bandpass(noise(0.25), 400, 3000) * expdecay(int(0.25 * SR), 0.06) * 0.5
buf = place(buf, drawer, 0.03)
buf = place(buf, bell(2637, 1.2, 0.8, 1.4), 0.12)
buf = place(buf, bell(3136, 1.0, 0.5, 1.2), 0.13)
buf = place(buf, K("coins", "handleCoins2.ogg") * 0.35, 0.2)
write("register", reverb(buf, 0.18), -17)

buf = np.zeros(1)
buf = place(buf, bell(1318.5, 1.6, 0.9, 0.7), 0.0)
buf = place(buf, bell(1046.5, 1.8, 0.9, 0.7), 0.45)
write("fee_warning", reverb(buf, 0.3), -19)

x = t(1.0)
f = 330 * 2 ** (-x * 0.9)
tri = signal.sawtooth(2 * np.pi * np.cumsum(f) / SR, 0.5)
wah = lowpass(tri, 1200) * env(len(x), 0.02, 0.2, 0.6, 0.5, 0.3)
write("lose", reverb(wah, 0.2), -22)

x = t(3.0)
drone = sum(np.sin(2 * np.pi * fq * x) for fq in (55, 55.6, 82.4, 110.3)) * 0.25
swell = bandpass(noise(3.0), 2000, 8000) * np.clip(x / 1.2, 0, 1) ** 3 * (x < 1.25)
hit = lowpass(noise(3.0), 400) * expdecay(len(x), 0.5) * (x >= 1.25)
buf = drone * env(len(x), 0.4, 0, 1, 1.2, 1.4) + swell * 0.5 + hit * 1.2
write("house_sends", reverb(buf, 0.3, 0.9), -15, fade_out=0.5)

buf = np.zeros(1)
for fq in (C5, E5, G5, C6):
    x = t(2.2)
    pad = (np.sin(2 * np.pi * fq * x) + 0.3 * np.sin(4 * np.pi * fq * x)) * env(len(x), 0.6, 0, 1, 1.0, 0.6)
    buf = place(buf, pad * 0.3, 0)
for k in range(10):
    buf = place(buf, bell(rng.choice([C6, E6, G6, 2093.0]), 0.6, 0.3), 0.4 + k * 0.08)
write("revive", reverb(buf, 0.35, 0.9), -17)

buf = np.zeros(1)
for fq in (98, 104, 123.5, 146.8):
    x = t(1.3)
    buf = place(buf, np.sin(2 * np.pi * fq * x) * expdecay(len(x), 0.4) * 0.4, 0)
x = t(1.0)
buf = place(buf, np.sin(2 * np.pi * np.cumsum(260 * 2 ** (-x * 1.2)) / SR) * env(len(x), 0.01, 0.3, 0.5, 0.6, 0.1) * 0.4, 0.15)
write("bust", reverb(buf, 0.25), -18)

# Gong: many inharmonic partials, slow bloom, long tail.
x = t(5.0)
gong = np.zeros(len(x))
for k, (ratio, amp, tau) in enumerate([(1, 1, 2.5), (1.52, 0.7, 2.0), (2.31, 0.5, 1.6), (2.87, 0.4, 1.2),
                                       (3.66, 0.3, 1.0), (4.41, 0.25, 0.8), (5.2, 0.2, 0.6)]):
    gong += amp * np.sin(2 * np.pi * 73 * ratio * x + rng.uniform(0, 6)) * expdecay(len(x), tau)
bloom = 1 - np.exp(-x / 0.08)
gong = gong * bloom + lowpass(noise(5.0), 300) * expdecay(len(x), 0.2) * 0.8
write("bankrupt", reverb(gong, 0.3, 0.9), -14, fade_out=1.0)

x = t(0.06)
wood = np.sin(2 * np.pi * 1100 * x) * expdecay(len(x), 0.012) + bandpass(noise(0.06), 2000, 5000) * expdecay(len(x), 0.003) * 0.4
write("tick", wood, -22)

buf = np.zeros(1)
for k, fq in enumerate((G5, C6, E6, G6)):
    buf = place(buf, bell(fq, 0.9, 0.7), k * 0.08)
buf = place(buf, K("boardgame", "chipsCollide2.ogg") * 0.5, 0.0)
write("unlock", reverb(buf, 0.25), -18)

for name, dur, rms, peak in written:
    print(f"{name:16s} {dur:5.2f}s  rms {rms:6.1f} dBFS  peak {peak:5.1f} dBFS")
