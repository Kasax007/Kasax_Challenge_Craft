"""Builder for the long-form showcase (on top of v3lib): the voice comes first, the cut follows it.
1. voice:    long/vo/uNNN.wav (tts_long.py) -> one voice track; pauses by unit / paragraph / section;
             word times (Whisper) aligned to the script text of each paragraph (accents kept)
2. captions: v3lib's sentence chunks
3. cut:      beats anchored to phrases (v3lib.plan), long clips allowed
4. audio:    voice + SFX (ducked) [+ music bed, ducked under the voice] -> two mixes: music / no music
5. render:   video once with captions, once without; the two mixes muxed onto each (no re-encode)"""
import os, sys, json, subprocess
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import v3lib, cut
from v3lib import _load, script_tokens, align, captions, plan, sfx_track, write_wav, ts, SR, FPS
from script import S

VO = HERE + '/vo'
OUT = HERE + '/out'
os.makedirs(OUT, exist_ok=True)
SLOW = float(os.environ.get('LONG_SLOW', '1.06'))
GAP_UNIT, GAP_PARA, GAP_SECTION = 0.30, 0.5, 0.95
LEAD_IN = 0.6           # picture (and music) before the first word
TAIL = 2.5
v3lib.TAIL = TAIL


def _wm():
    from faster_whisper import WhisperModel
    return WhisperModel('base.en', device='cpu', compute_type='int8', cpu_threads=2)


def voice():
    """-> (wav, words [(display, hot, t0, t1)], end of speech, paragraph start times, section start times)"""
    units = json.load(open(VO + '/units.json'))
    wm = None
    wav = [np.zeros(int(LEAD_IN * SR), np.float32)]
    t = LEAD_IN
    words, pstart, sstart = [], [], {}
    for pi, (sec, para) in enumerate(S):
        if pi > 0:
            g = GAP_SECTION if sec != S[pi - 1][0] else GAP_PARA
            wav.append(np.zeros(int(g * SR), np.float32)); t += g
        pstart.append(t)
        sstart.setdefault(sec, t)
        heard = []
        ulist = [i for i, (p, _) in enumerate(units) if p == pi]
        for k, ui in enumerate(ulist):
            if k > 0:
                wav.append(np.zeros(int(GAP_UNIT * SR), np.float32)); t += GAP_UNIT
            src = f'{VO}/u{ui:03d}.wav'
            use = src
            if abs(SLOW - 1) > 1e-3:
                use = f'{VO}/u{ui:03d}.s{SLOW:.2f}.wav'
                if not os.path.exists(use):
                    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-filter:a', f'atempo={SLOW}', use], check=True)
            a = _load(use)
            a = a / (np.abs(a).max() + 1e-6) * 0.85
            jf = use[:-4] + '.words.json'
            if os.path.exists(jf):
                ws = json.load(open(jf))
            else:
                wm = wm or _wm()
                segs, _ = wm.transcribe(use, language='en', word_timestamps=True)
                ws = [(w.word.strip(), w.start, w.end) for s in segs for w in s.words]
                json.dump(ws, open(jf, 'w'))
            heard += [(w, s0 + t, s1 + t) for w, s0, s1 in ws]
            wav.append(a); t += len(a) / SR
        words += align(script_tokens(para), [(w, s0 - pstart[pi], s1 - pstart[pi]) for w, s0, s1 in heard],
                       t - pstart[pi], pstart[pi])
    return np.concatenate(wav), words, t, pstart, sstart


def music_bed(path_by_section, sstart, total, voice_wav, under=0.115, gaps=0.34, xfade=2.5):
    """One music track: per section the given file (looped if short), crossfades at changes,
    ducked under the voice (gain `under`), fuller in the pauses (`gaps`), faded in and out."""
    n = int(total * SR)
    secs = sorted(sstart.items(), key=lambda kv: kv[1])
    segs = []  # (t0, t1, file) merged where the same file continues
    for i, (sec, t0) in enumerate(secs):
        f = path_by_section[sec]
        t1 = secs[i + 1][1] if i + 1 < len(secs) else total
        t0 = 0.0 if i == 0 else t0 - 0.4
        if segs and segs[-1][2] == f:
            segs[-1] = (segs[-1][0], t1, f)
        else:
            segs.append((t0, t1, f))
    out = np.zeros(n, np.float32)
    for i, (t0, t1, f) in enumerate(segs):
        a = _load(f)
        a = a / (np.abs(a).max() + 1e-6)
        x0 = int(t0 * SR)
        x1 = min(n, int((t1 + (xfade if i + 1 < len(segs) else 0)) * SR))
        L = x1 - x0
        reps = int(np.ceil(L / len(a)))
        seg = np.tile(a, reps)[:L].copy()
        fi = int((xfade if i > 0 else 1.5) * SR)
        seg[:fi] *= np.linspace(0, 1, fi) ** 0.8
        if i + 1 < len(segs):
            fo = int(xfade * SR)
            seg[-fo:] *= np.linspace(1, 0, fo) ** 0.8
        out[x0:x1] += seg
    fo = int(3.0 * SR)
    out[-fo:] *= np.linspace(1, 0, fo)
    v = np.pad(voice_wav, (0, max(0, n - len(voice_wav))))[:n]
    env = np.convolve((np.abs(v) > 0.02).astype(np.float32), np.ones(int(0.5 * SR)) / int(0.5 * SR), mode='same')
    env = np.clip(env * 4, 0, 1)
    gain = gaps + (under - gaps) * env
    return out * gain


def loudnorm(src, dst, target=-14):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-af', f'loudnorm=I={target}:TP=-1.5:LRA=11',
                    '-ar', str(SR), dst], check=True)


def build(beats, music=None, plan_only=False, render=True, cap_size=68, cap_y=1390, name='showcase'):
    wav, words, vend, pstart, sstart = voice()
    total = round((vend + TAIL) * FPS) / FPS
    caps = captions(words, total, size=cap_size, y=cap_y)
    pl, extra, bstarts, capy = plan(beats, words, total)
    for c in caps:
        for a, b2, y in capy:
            if a - 0.01 <= c.t0 < b2:
                c.y = y
    clips = [c for c, _, _ in pl]
    print(f'{name}: voice {vend:.1f} s, cut {total:.1f} s ({total/60:.2f} min), {len(clips)} clips, '
          f'avg {total / len(clips):.2f} s/clip, {len(words)} words, {len(words)/(vend-LEAD_IN):.2f} w/s')
    json.dump(dict(total=total, voice_end=vend, paragraphs=pstart, sections=sstart, beats=bstarts,
                   captions=[dict(t0=c.t0, t1=c.t1, text=c.text) for c in caps],
                   words=[dict(w=w[0], t0=w[2], t1=w[3]) for w in words],
                   clips=[dict(shot=c.shot, start=c.start, length=c.length, t=t, speed=c.speed) for c, t, _ in pl]),
              open(f'{OUT}/{name}_timeline.json', 'w'), indent=1)
    if plan_only:
        return
    mix = sfx_track(pl, total, whoosh_gain=0.06)
    swav = f'{OUT}/{name}_sfx.wav'
    mix.write(swav, total)
    n = int(total * SR)
    v = np.pad(wav, (0, max(0, n - len(wav))))[:n]
    s = np.pad(_load(swav), (0, max(0, n - len(_load(swav)))))[:n]
    env = np.convolve((np.abs(v) > 0.02).astype(np.float32), np.ones(int(0.3 * SR)) / int(0.3 * SR), mode='same')
    s = s * (1.0 - 0.75 * np.clip(env * 3, 0, 1))
    write_wav(f'{OUT}/{name}_voice.wav', v)
    for tag, bed in (('nomusic', None), ('music', music)):
        if tag == 'music' and not music:
            continue
        m = v + s
        if bed:
            mb = music_bed(bed, sstart, total, v)
            write_wav(f'{OUT}/{name}_musicbed.wav', mb)
            m = m + mb
        m = m / max(1.0, np.abs(m).max() / 0.95)
        write_wav(f'{OUT}/{name}_{tag}0.wav', m)
        loudnorm(f'{OUT}/{name}_{tag}0.wav', f'{OUT}/{name}_{tag}.wav')
        os.remove(f'{OUT}/{name}_{tag}0.wav')
    with open(f'{OUT}/{name}.srt', 'w') as f:
        for k, c in enumerate(caps, 1):
            f.write(f'{k}\n{ts(c.t0)} --> {ts(c.t1)}\n{c.text.replace("*", "")}\n\n')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', f'{OUT}/{name}_voice.wav', '-c:a', 'aac', '-b:a', '160k',
                    f'{OUT}/{name}_voice.m4a'], check=True)
    if not render:
        return
    render_parallel(clips, caps, extra, f'{OUT}/{name}', workers=int(os.environ.get('LONG_WORKERS', '4')))
    for vid in ('captions', 'clean'):
        silent = f'{OUT}/{name}_{vid}_video.mp4'
        for tag in ('music', 'nomusic'):
            a = f'{OUT}/{name}_{tag}.wav'
            if os.path.exists(a):
                subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', silent, '-i', a, '-c:v', 'copy', '-c:a', 'aac',
                                '-b:a', '192k', '-shortest', '-movflags', '+faststart', f'{OUT}/{name}_{vid}_{tag}.mp4'],
                               check=True)
    cut.contact_sheet(f'{OUT}/{name}_captions_video.mp4', f'{OUT}/{name}_sheet.png', every=4.0, cols=12, thumb=140)
    print('done', total)


# ------------------------------------------------------------------ parallel render (both versions at once)

def _render_part(args):
    from PIL import Image
    import numpy as np
    k, clips, f0, caps, extra, prefix = args
    W, H = cut.W, cut.H
    def enc(path):
        return subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}',
                                 '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '20',
                                 '-pix_fmt', 'yuv420p', '-threads', '1', path], stdin=subprocess.PIPE)
    pc, pk = enc(f'{prefix}_part{k:02d}_captions.mp4'), enc(f'{prefix}_part{k:02d}_clean.mp4')
    n = f0
    for clip in clips:
        shot_dir = os.path.join(cut.SP, 'film', clip.shot) if not os.path.isabs(clip.shot) else clip.shot
        rng = np.random.default_rng(n)
        for i in range(clip.frames):
            t = n / FPS
            f = i / max(1, clip.frames - 1)
            z = clip.zoom[0] + (clip.zoom[1] - clip.zoom[0]) * (f * f * (3 - 2 * f))
            dx = dy = 0
            if clip.shake:
                decay = max(0.0, 1 - i / (FPS * 0.5))
                dx = rng.normal() * clip.shake * decay * 20
                dy = rng.normal() * clip.shake * decay * 20
                z = max(z, 1.0 + clip.shake * 0.06)
            im = cut._zoomed(cut.shot_frame(shot_dir, clip.source_index(i)), z, clip.focus, dx, dy).convert('RGBA')
            if clip.flash and i < 4:
                im.alpha_composite(Image.new('RGBA', (W, H), (255, 255, 255, int(255 * (1 - i / 4) * 0.85))))
            pk.stdin.write(im.convert('RGB').tobytes())
            cut._draw_captions(im, t, caps + extra)
            pc.stdin.write(im.convert('RGB').tobytes())
            n += 1
    for p in (pc, pk):
        p.stdin.close(); p.wait()
    return k


def render_parallel(clips, caps, extra, prefix, workers=4):
    """-> {prefix}_captions_video.mp4 and {prefix}_clean_video.mp4 (silent), rendered in `workers` parts."""
    import multiprocessing as mp
    total = sum(c.frames for c in clips)
    parts, cur, f0, start = [], [], 0, 0
    for c in clips:
        cur.append(c); f0 += c.frames
        if f0 - start >= total / workers and len(parts) < workers - 1:
            parts.append((cur, start)); cur, start = [], f0
    if cur:
        parts.append((cur, start))
    jobs = [(k, cl, st, caps, extra, prefix) for k, (cl, st) in enumerate(parts)]
    with mp.get_context('fork').Pool(len(jobs)) as pool:
        for k in pool.imap_unordered(_render_part, jobs):
            print('  part', k, 'done', flush=True)
    for vid in ('captions', 'clean'):
        lst = f'{prefix}_{vid}_parts.txt'
        with open(lst, 'w') as f:
            for k in range(len(jobs)):
                f.write(f"file '{prefix}_part{k:02d}_{vid}.mp4'\n")
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', lst, '-c', 'copy',
                        f'{prefix}_{vid}_video.mp4'], check=True)
        for k in range(len(jobs)):
            os.remove(f'{prefix}_part{k:02d}_{vid}.mp4')
        os.remove(lst)
