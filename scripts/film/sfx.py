"""Sound effects for the shorts: Minecraft's own sounds, the mod's casino sounds, and a few
synthesised transitions (whoosh, riser, impact). Everything as float32 mono at 48 kHz."""
import json, os, subprocess
import numpy as np

SR = 48000
MC_INDEX = '/root/.gradle/caches/fabric-loom/assets/indexes/26.3-34.json'
MC_OBJECTS = '/root/.gradle/caches/fabric-loom/assets/objects'
MOD_SOUNDS = '/home/user/bob-dev/src/main/resources/assets/challengecraft/sounds/casino'
_index = None
_cache = {}


def _decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()


def mc(name):
    """A vanilla sound by its file path under minecraft/sounds, e.g. 'random/levelup'."""
    global _index
    if name in _cache:
        return _cache[name]
    if _index is None:
        _index = json.load(open(MC_INDEX))['objects']
    h = _index['minecraft/sounds/' + name + '.ogg']['hash']
    a = _decode(f'{MC_OBJECTS}/{h[:2]}/{h}')
    _cache[name] = a
    return a


def casino(name):
    key = 'casino:' + name
    if key not in _cache:
        _cache[key] = _decode(f'{MOD_SOUNDS}/{name}.ogg')
    return _cache[key]


def _env(n, attack, release):
    e = np.ones(n, dtype=np.float32)
    a = int(attack * SR); r = int(release * SR)
    if a: e[:a] = np.linspace(0, 1, a)
    if r: e[-r:] *= np.linspace(1, 0, r)
    return e


def whoosh(dur=0.35, up=True, seed=1):
    """Filtered noise sweeping up (or down) in pitch: the cut transition."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    noise = rng.standard_normal(n).astype(np.float32)
    # a moving one-pole low-pass: cutoff sweeps 300 Hz -> 6 kHz
    f = np.linspace(300, 6000, n) if up else np.linspace(6000, 300, n)
    alpha = 1 - np.exp(-2 * np.pi * f / SR)
    out = np.empty(n, dtype=np.float32); y = 0.0
    for i in range(n):
        y += alpha[i] * (noise[i] - y)
        out[i] = y
    shape = np.sin(np.linspace(0, np.pi, n)) ** 1.5
    out *= shape
    return (out / (np.abs(out).max() + 1e-9) * 0.6).astype(np.float32)


def riser(dur=1.2, seed=2):
    """Noise plus a rising tone: tension before a reveal."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR); t = np.arange(n) / SR
    freq = np.linspace(180, 900, n)
    tone = np.sin(2 * np.pi * np.cumsum(freq) / SR) * 0.35
    noise = rng.standard_normal(n) * 0.25
    out = (tone + noise) * np.linspace(0, 1, n) ** 2
    return (out * _env(n, 0.0, 0.03)).astype(np.float32)


def impact(dur=0.9, seed=3):
    """A low boom: the hit on a big reveal."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR); t = np.arange(n) / SR
    freq = 70 * np.exp(-t * 3) + 35
    body = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-t * 4.5)
    click = rng.standard_normal(n) * np.exp(-t * 60) * 0.5
    out = body + click
    return (out / np.abs(out).max() * 0.9).astype(np.float32)


def pop(dur=0.08):
    """A short tick for words popping in."""
    n = int(dur * SR); t = np.arange(n) / SR
    out = np.sin(2 * np.pi * 1400 * t) * np.exp(-t * 70) * 0.35
    return out.astype(np.float32)


class Mix:
    def __init__(self, seconds):
        self.buf = np.zeros(int(seconds * SR) + SR, dtype=np.float32)

    def add(self, t, sound, gain=1.0, pitch=1.0):
        if pitch != 1.0:
            idx = np.arange(0, len(sound) - 1, pitch)
            sound = np.interp(idx, np.arange(len(sound)), sound).astype(np.float32)
        i = int(t * SR)
        if i >= len(self.buf):
            return
        j = min(len(self.buf), i + len(sound))
        self.buf[i:j] += sound[:j - i] * gain

    def write(self, path, seconds):
        out = self.buf[:int(seconds * SR)]
        peak = np.abs(out).max()
        if peak > 0.89:
            out = out / peak * 0.89
        pcm = (out * 32767).astype(np.int16)
        import wave
        with wave.open(path, 'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes(pcm.tobytes())
