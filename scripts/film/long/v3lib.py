"""V3 builder: the voice comes first, the cut follows the voice.

1. voice:   paragraph wavs (tts_v3.py) -> one voice track + word times (Whisper) aligned to the script text
2. captions: the script text, in readable chunks (sentence / half sentence, max ~9 words, 2 lines), timed by the words
3. cut:     beats are anchored to phrases of the script ('at'), the clips of a beat share its time by weight
4. audio:   voice + SFX (ducked under the voice) -> loudness-normalised mix
5. render:  *_final (captions burnt in), *_clean, *.srt, *_voice.m4a

beat = dict(at='first words of the phrase where the beat starts' (None = start), hook='TITLE' (shown during the beat),
            clips=[(shot, start s, weight, {Clip options}, [cues])], end='CARD TEXT')
Clip options as in cut.Clip, plus  ui=True (a readable UI crop may zoom further) and punch=True (a real highlight,
zoom up to 1.12).  Everything else is held to the gentle zoom of V3 (<= 1.08) and warned about / clamped.
cue = (offset s, kind, name, gain), kind in casino | mc | whoosh | riser | impact"""
import os, sys, json, re, difflib, subprocess
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cut, sfx
from cut import Clip, Caption, YELLOW, GOLD, RED, GREEN

SP = cut.SP
VO = SP + '/vo3'
OUT = SP + '/out3'
FPS = cut.FPS
SR = 48000
os.makedirs(OUT, exist_ok=True)
SCRIPTS = json.load(open(os.path.dirname(os.path.abspath(__file__)) + '/scripts_v3.json'))
SLOW = 0.95        # the TTS speaks fast; a touch slower (pitch kept) is easier to follow
GAP = 0.32          # pause between paragraphs, s
TAIL = 0.9          # picture after the last word, s
LEAD = 0.07         # a cut lands this much before the word it belongs to
MAX_Z, PUNCH_Z = 1.08, 1.12
norm1 = lambda s: re.sub(r'[^a-z0-9]', '', s.lower())


# ------------------------------------------------------------------ voice + words

def _load(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()


def script_tokens(para):
    """[(display word incl. punctuation, hot, core)] from a paragraph with *accent* spans."""
    toks, inside = [], False
    for raw in para.split():
        hot = inside or '*' in raw
        if raw.count('*') % 2:
            inside = not inside
        w = raw.replace('*', '')
        toks.append([w, hot, norm1(w)])
    return toks


def voice(name, whisper=None):
    """Concatenate the paragraphs; returns (wav float32, [(token, hot, t0, t1)] for the whole script, para starts)."""
    cache = f'{VO}/{name}/voice.json'
    paras = SCRIPTS[name]
    chunks, t = [], 0.12
    allw, starts = [], []
    wav = [np.zeros(int(0.12 * SR), np.float32)]
    wm = None
    for i, para in enumerate(paras):
        src = f'{VO}/{name}/p{i:02d}.wav'
        slow = f'{VO}/{name}/p{i:02d}.s.wav'
        if not os.path.exists(slow):
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-filter:a', f'atempo={SLOW}', slow], check=True)
        a = _load(slow)
        a = a / (np.abs(a).max() + 1e-6) * 0.85
        starts.append(t)
        jf = f'{VO}/{name}/p{i:02d}.words.json'
        if os.path.exists(jf):
            words = json.load(open(jf))
        else:
            if wm is None:
                from faster_whisper import WhisperModel
                wm = WhisperModel('base.en', device='cpu', compute_type='int8', cpu_threads=2)
            segs, _ = wm.transcribe(slow, language='en', word_timestamps=True)
            words = [(w.word.strip(), w.start, w.end) for s in segs for w in s.words]
            json.dump(words, open(jf, 'w'))
        allw.extend(align(script_tokens(para), words, len(a) / SR, t))
        wav.append(a)
        t += len(a) / SR
        wav.append(np.zeros(int(GAP * SR), np.float32))
        t += GAP
    wav = np.concatenate(wav)
    return wav, allw, t - GAP


def align(toks, words, dur, off):
    """Script tokens -> (display, hot, t0, t1) with the times Whisper heard; gaps are interpolated."""
    a = [x[2] for x in toks]
    b = [norm1(w[0]) for w in words]
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    t0 = [None] * len(a)
    t1 = [None] * len(a)
    for blk in sm.get_matching_blocks():
        for k in range(blk.size):
            t0[blk.a + k] = words[blk.b + k][1]
            t1[blk.a + k] = words[blk.b + k][2]
    miss = sum(x is None for x in t0)
    if miss:
        print(f'  align: {miss}/{len(a)} script words not matched (interpolated)')
    # interpolate the unmatched
    i = 0
    while i < len(a):
        if t0[i] is None:
            j = i
            while j < len(a) and t0[j] is None:
                j += 1
            lo = t1[i - 1] if i > 0 else 0.0
            hi = t0[j] if j < len(a) else dur
            for k in range(i, j):
                t0[k] = lo + (hi - lo) * (k - i) / (j - i)
                t1[k] = lo + (hi - lo) * (k - i + 1) / (j - i)
            i = j
        else:
            i += 1
    return [(toks[k][0], toks[k][1], off + t0[k], off + t1[k]) for k in range(len(a))]


# ------------------------------------------------------------------ captions

MAXC, MAXW = 42, 9
BREAK_BEFORE = {'and', 'or', 'but', 'so', 'then', 'until', 'into', 'in', 'on', 'of', 'to', 'for', 'with', 'against', 'from', 'where', 'that', 'if', 'as'}


def _chunks(words):
    """Split the aligned words into caption chunks: sentences, then clauses, then balanced halves."""
    sents, cur = [], []
    for w in words:
        cur.append(w)
        if re.search(r'[.?!]$', w[0]):
            sents.append(cur)
            cur = []
    if cur:
        sents.append(cur)
    size = lambda ws: sum(len(x[0]) + 1 for x in ws) - 1

    def ok(ws):
        return size(ws) <= MAXC and len(ws) <= MAXW

    out = []
    for s in sents:
        if ok(s):
            out.append(s)
            continue
        clauses, cur = [], []
        for w in s:
            cur.append(w)
            if w[0].endswith(','):
                clauses.append(cur)
                cur = []
        if cur:
            clauses.append(cur)
        merged = []
        for c in clauses:
            if merged and ok(merged[-1] + c):
                merged[-1] = merged[-1] + c
            else:
                merged.append(c)
        for c in merged:
            while not ok(c):
                # break at a conjunction nearest the middle, else the middle
                mid = len(c) / 2
                cands = [k for k in range(2, len(c) - 1) if c[k][0].lower().strip(',.') in BREAK_BEFORE]
                k = min(cands, key=lambda k: abs(k - mid)) if cands else int(round(mid))
                if cands and abs(k - mid) > 3:
                    k = int(round(mid))
                out.append(c[:k])
                c = c[k:]
            out.append(c)
    # glue a very short chunk to its neighbour if they fit together
    res = []
    for c in out:
        if res and len(res[-1]) < 4 and ok(res[-1] + c):
            res[-1] = res[-1] + c
        else:
            res.append(c)
    return res


def chunk_text(ws):
    out, i = [], 0
    while i < len(ws):
        if ws[i][1]:
            j = i
            while j + 1 < len(ws) and ws[j + 1][1]:
                j += 1
            seg = [w[0] for w in ws[i:j + 1]]
            m = re.match(r"^(.*?)([^\w']*)$", seg[-1])
            seg[-1] = m.group(1) + '*' + m.group(2)
            seg[0] = '*' + seg[0]
            out.extend(seg)
            i = j + 1
        else:
            out.append(ws[i][0])
            i += 1
    return ' '.join(out)


def captions(words, total, size=72, y=None):
    ch = _chunks(words)
    caps = []
    for k, c in enumerate(ch):
        t0 = c[0][2] - 0.04
        nxt = ch[k + 1][0][2] - 0.04 if k + 1 < len(ch) else total
        t1 = min(nxt, c[-1][3] + 0.45)
        if nxt - c[-1][3] < 0.6:
            t1 = nxt
        caps.append(Caption(max(0, t0), t1, chunk_text(c), 'cap', y=y, size=size))
    return caps


# ------------------------------------------------------------------ cut

def _find(words, phrase, start):
    want = [norm1(x) for x in phrase.split()]
    toks = [norm1(w[0]) for w in words]
    for i in range(start, len(toks) - len(want) + 1):
        if toks[i:i + len(want)] == want:
            return i
    raise SystemExit(f'anchor not found: {phrase!r}')


def plan(beats, words, total):
    """-> list of (Clip, start time, cues), caption extras (hook/end), beat start times"""
    n_total = int(round(total * FPS))
    starts, k = [], 0
    for b in beats:
        if b.get('at') is None:
            starts.append(0)
            continue
        k = _find(words, b['at'], k)
        starts.append(int(round(max(0, words[k][2] - LEAD) * FPS)))
        k += 1
    starts.append(n_total)
    out, extra, capy = [], [], []
    for bi, b in enumerate(beats):
        f0, f1 = starts[bi], starts[bi + 1]
        if f1 - f0 < 6:
            raise SystemExit(f'beat {bi} too short ({f1 - f0} frames)')
        cl = b['clips']
        wsum = sum(c[2] for c in cl)
        acc, f = 0.0, f0
        for ci, (shot, st, wgt, opts, cues) in enumerate(cl):
            acc += wgt
            fe = f1 if ci == len(cl) - 1 else f0 + int(round((f1 - f0) * acc / wsum))
            length = (fe - f) / FPS
            o = {k2: v for k2, v in opts.items() if k2 not in ('ui', 'punch', 'payoff')}
            z = o.get('zoom', (1.0, 1.0))
            lim = PUNCH_Z if opts.get('punch') else (9 if opts.get('ui') else MAX_Z)
            if max(z) > lim + 1e-6:
                print(f'  zoom clamped {z} -> max {lim} at {f / FPS:.1f}s ({shot})')
                o['zoom'] = tuple(min(x, lim) for x in z)
            c = Clip(shot, st, length, **o)
            d = shot if os.path.isabs(shot) else os.path.join(SP, 'film', shot)
            if os.path.isdir(d):
                n = len(cut._files(d))
                last = c.source_index(c.frames - 1)
                if n > 1 and last > n - 1 + 3:
                    print(f'  note: {shot} runs past its end ({last} > {n - 1}) at {f / FPS:.1f}s (last frame held)')
            else:
                raise SystemExit('missing shot ' + shot)
            if length < 0.45 or length > 9:
                print(f'  note: clip {length:.2f}s at {f / FPS:.1f}s ({shot})')
            out.append((c, f / FPS, cues))
            f = fe
        if b.get('cap_y'):
            capy.append((f0 / FPS, f1 / FPS, b['cap_y']))
        if b.get('hook'):
            extra.append(Caption(f0 / FPS, f1 / FPS, b['hook'], 'hook', y=b.get('hook_y', 330)))
        if b.get('end'):
            extra.append(Caption(f0 / FPS + b.get('end_at', 0.0), f1 / FPS, b['end'], y=b.get('end_y', 1580)))
    return out, extra, [s / FPS for s in starts], capy


def sfx_track(pl, total, whoosh_gain=0.14):
    mix = sfx.Mix(total + 1)
    for clip, s, cues in pl:
        if s > 0:
            mix.add(s - 0.05, sfx.whoosh(0.18, seed=int(s * 10)), whoosh_gain)
        for off, kind, nm, gain in cues:
            try:
                snd = {'casino': lambda: sfx.casino(nm), 'mc': lambda: sfx.mc(nm), 'whoosh': sfx.whoosh,
                       'riser': sfx.riser, 'impact': sfx.impact}[kind]()
            except Exception as e:
                print('sound missing:', kind, nm, e)
                continue
            mix.add(s + off, snd, gain)
    return mix


def duck_mix(voice_wav, sfx_wav_path, total):
    n = int(total * SR)
    v = np.pad(voice_wav, (0, max(0, n - len(voice_wav))))[:n]
    s = _load(sfx_wav_path)
    s = np.pad(s, (0, max(0, n - len(s))))[:n]
    env = np.convolve(np.abs(v) > 0.02, np.ones(int(0.3 * SR)) / int(0.3 * SR), mode='same')
    gain = 1.0 - 0.8 * np.clip(env * 3, 0, 1)
    m = v + s * gain
    m = m / max(1.0, np.abs(m).max() / 0.95)
    return v, m.astype(np.float32)


def write_wav(path, a):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', '1', '-i', '-', path],
                   input=a.astype(np.float32).tobytes(), check=True)


def ts(x):
    h = int(x // 3600); m = int(x % 3600 // 60); s = x % 60
    return f'{h:02d}:{m:02d}:{int(s):02d},{int((s % 1) * 1000):03d}'


def build(name, beats, final_only=False, plan_only=False, cap_y=None, cap_size=80):
    """name e.g. 'challenges_v3'"""
    wav, words, vend = voice(name)
    total = round((vend + TAIL) * FPS) / FPS
    caps = captions(words, total, size=cap_size, y=cap_y)
    pl, extra, bstarts, capy = plan(beats, words, total)
    for c in caps:
        for a, b2, y in capy:
            if a - 0.01 <= c.t0 < b2:
                c.y = y
    clips = [c for c, _, _ in pl]
    print(f'{name}: voice {vend:.1f} s, cut {total:.1f} s, {len(clips)} clips, avg {total / len(clips):.2f} s/clip, '
          f'{sum(len(w) for w in [words])} words')
    for c in caps:
        print(f'  cap {c.t0:5.2f}-{c.t1:5.2f} {c.text}')
    if plan_only:
        return
    mix = sfx_track(pl, total)
    swav = f'{OUT}/{name}_sfx.wav'
    mix.write(swav, total)
    v, m = duck_mix(wav, swav, total)
    write_wav(f'{OUT}/{name}_voice.wav', v)
    write_wav(f'{OUT}/{name}_mix0.wav', m)
    mixf = f'{OUT}/{name}_mix.wav'
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', f'{OUT}/{name}_mix0.wav', '-af',
                    'loudnorm=I=-15:TP=-1.5:LRA=9', '-ar', str(SR), mixf], check=True)
    os.remove(f'{OUT}/{name}_mix0.wav')
    with open(f'{OUT}/{name}.srt', 'w') as f:
        for n, c in enumerate(caps, 1):
            f.write(f'{n}\n{ts(c.t0)} --> {ts(c.t1)}\n{c.text.replace("*", "")}\n\n')
    json.dump(dict(total=total, voice_end=vend, beats=bstarts,
                   captions=[dict(t0=c.t0, t1=c.t1, text=c.text) for c in caps],
                   words=[dict(w=w[0], hot=w[1], t0=w[2], t1=w[3]) for w in words],
                   clips=[dict(shot=c.shot, start=c.start, length=c.length, t=t, speed=c.speed) for c, t, _ in pl]),
              open(f'{OUT}/{name}_timeline.json', 'w'), indent=1)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', f'{OUT}/{name}_voice.wav', '-c:a', 'aac', '-b:a', '160k',
                    f'{OUT}/{name}_voice.m4a'], check=True)
    if not final_only:
        cut.render(clips, [], f'{OUT}/{name}_clean.mp4', audio=mixf, crf=21)
    cut.render(clips, caps + extra, f'{OUT}/{name}_final.mp4', audio=mixf, crf=21)
    cut.contact_sheet(f'{OUT}/{name}_final.mp4', f'{OUT}/{name}_sheet.png', every=2.0, cols=8, thumb=170)
    print('done', total)
