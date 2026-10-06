"""Shared builder for the fast V2 shorts: beats (clips + word captions + one voice line each)
become a cut, captions, an SFX track, an SRT and the voice-over plan (vo/<name>_plan.json).

beat = dict(vo=(text, exaggeration, cfg_weight[, offset s]) or None,
            caps=[ 'TEXT' | ('TEXT', offset s from beat start) ...], accent=YELLOW, hook='...', y=None,
            clips=[(shot, start, length, {Clip options}, [cues])])
cue = (offset s, kind, name, gain), kind in casino | mc | whoosh | riser | impact"""
import os, sys, json
sys.path.insert(0, os.path.dirname(__file__))
import cut, sfx
from cut import Clip, Caption, YELLOW, GOLD, RED, GREEN

SP = cut.SP
OUT = SP + '/out'
os.makedirs(OUT, exist_ok=True)


def have(shot):
    return os.path.isdir(shot if os.path.isabs(shot) else os.path.join(SP, 'film', shot))


def build(name, beats, cta=None, whoosh_gain=0.22, voice_lines_json=None):
    plan, caps, vo = [], [], []
    t = 0.0
    for b in beats:
        t0 = t
        for shot, start, length, opts, cues in b['clips']:
            if not have(shot):
                print('missing shot, skipped:', shot)
                continue
            if length > 1.55 and not opts.get('payoff'):
                print(f'  note: long clip {length:.2f}s at {t:.1f}s ({shot})')
            o = {k: v for k, v in opts.items() if k != 'payoff'}
            c = Clip(shot, start, length, **o)
            plan.append((c, t, cues))
            t += c.frames / cut.FPS
        if t == t0:
            continue
        if b.get('hook'):
            caps.append(Caption(t0, t, b['hook'], 'hook', y=b.get('hook_y', 330)))
        items = b.get('caps') or []
        norm = []
        for k, it in enumerate(items):
            if isinstance(it, str):
                norm.append(((t - t0) * k / len(items), it))
            else:
                norm.append((it[1], it[0]))
        for k, (off, text) in enumerate(norm):
            end = (norm[k + 1][0] if k + 1 < len(norm) else t - t0)
            caps.append(Caption(t0 + off, t0 + end, text, 'cap', y=b.get('y'), accent=b.get('accent', YELLOW),
                                size=b.get('size')))
        if b.get('end'):
            caps.append(Caption(t0 + b.get('end_at', 0.0), t, b['end'], y=b.get('end_y', 1580)))
        if b.get('vo'):
            v = b['vo']
            vo.append([round(t0 + (v[3] if len(v) > 3 else 0.0), 2), v[0], v[1], v[2]])
    total = t
    clips = [c for c, _, _ in plan]
    print(f'{name}: {len(clips)} clips, {total:.1f} s, avg {total / len(clips):.2f} s/clip')

    mix = sfx.Mix(total + 1)
    for clip, s, cues in plan:
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
    for c in caps:
        if c.style in ('cap', 'hook'):
            mix.add(c.t0, sfx.pop(), 0.22)
    wav = f'{OUT}/{name}_sfx.wav'
    mix.write(wav, total)

    def ts(x):
        h = int(x // 3600); m = int(x % 3600 // 60); s = x % 60
        return f'{h:02d}:{m:02d}:{int(s):02d},{int((s % 1) * 1000):03d}'
    with open(f'{OUT}/{name}.srt', 'w') as f:
        for n, c in enumerate(caps, 1):
            f.write(f'{n}\n{ts(c.t0)} --> {ts(c.t1)}\n{c.text.replace("*", "")}\n\n')
    os.makedirs(SP + '/vo', exist_ok=True)
    json.dump(dict(total=round(total, 2), lines=vo), open(f'{SP}/vo/{name}_plan.json', 'w'), indent=1)

    lj = f'{SP}/vo/{name}/lines.json'
    if os.path.exists(lj):
        L = json.load(open(lj))['lines']
        for k, ln in enumerate(L):
            st = vo[k][0] if k < len(vo) else ln['t0']
            nxt = vo[k + 1][0] if k + 1 < len(vo) else total
            flag = 'LONG' if ln['dur'] > nxt - st - 0.1 else 'ok'
            print(f"  vo {k:02d} start {st:5.1f} slot {nxt - st:4.2f} dur {ln['dur']:4.2f} {flag}  {ln['text'][:40]}")
    if '--plan' in sys.argv:
        return total
    draft = '--draft' in sys.argv
    if '--clean' in sys.argv or not draft:
        cut.render(clips, [], f'{OUT}/{name}_clean.mp4', audio=wav)
    cut.render(clips, caps, f'{OUT}/{name}_{"draft" if draft else "final"}.mp4', audio=wav, crf=26 if draft else 18)
    cut.contact_sheet(f'{OUT}/{name}_{"draft" if draft else "final"}.mp4', f'{OUT}/{name}_sheet.png', every=0.5, cols=12, thumb=120)
    print('done', total)
    return total
