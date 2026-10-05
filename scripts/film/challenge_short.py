"""The challenge short: 'I coded viral challenges into Minecraft'. One beat per challenge, each
with its caption and sound; renders final (captions) and clean versions, the SFX track and an SRT."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import cut, sfx
from cut import Clip, Caption, YELLOW, GOLD, RED, GREEN

SP = cut.SP
OUT = SP + '/out'
os.makedirs(OUT, exist_ok=True)
H = 'chal/'
C = 'casino/'


def have(shot):
    return os.path.isdir(os.path.join(SP, 'film', shot))


# Each beat: clips (with sfx cues relative to the clip), the caption over the whole beat.
# (shot, start s, length s, Clip options, cues)
BEATS = [
    # 0. hook: a blitz of the wildest ones under the title
    dict(cap=None, clips=[
        (H + 'red_light', 5.3, 0.45, dict(zoom=(1.2, 1.3), focus=(0.5, 0.35)), [(0, 'whoosh', None, 0.5)]),
        (H + 'size_matters', 2.0, 0.45, dict(zoom=(1.0, 1.1)), [(0, 'mc', 'mob/creeper/say1', 0.6)]),
        (H + 'dice_throw', 1.8, 0.45, dict(zoom=(1.1, 1.2)), [(0, 'mc', 'random/pop', 0.6)]),
        (H + 'chunk_blocks', 2.0, 0.45, {}, [(0, 'whoosh', None, 0.4)]),
        (C + 'casino_slot_epic_show', 0.2, 0.45, dict(flash=True), [(0, 'casino', 'win_epic', 0.7)]),
        (H + 'skyblock', 2.0, 0.45, {}, [(0, 'impact', None, 0.7)]),
    ], hook='I CODED *50* VIRAL CHALLENGES INTO MINECRAFT'),
    dict(cap='RED LIGHT: *MOVE* AND YOU DIE', accent=RED, clips=[
        (H + 'red_light', 3.9, 3.6, dict(zoom=(1.1, 1.2), focus=(0.5, 0.35)), [(0.0, 'mc', 'note/bell', 0.6), (1.3, 'mc', 'note/bass', 0.9), (1.8, 'mc', 'damage/hit1', 0.9)]),
    ]),
    dict(cap='*DICE*: WALK ONLY WHAT YOU ROLL', clips=[
        (H + 'dice_throw', 0.5, 3.0, dict(zoom=(1.0, 1.15)), [(0.2, 'mc', 'random/bow', 0.6), (0.5, 'mc', 'random/pop', 0.7), (1.6, 'mc', 'random/levelup', 0.4)]),
    ]),
    dict(cap='NO LEGS? TRAVEL BY *CUSHION*', clips=[
        (H + 'cushion', 1.0, 3.0, dict(speed=1.6), [(0.2, 'mc', 'dig/cloth1', 0.8), (1.2, 'mc', 'dig/cloth2', 0.8)]),
    ]),
    dict(cap='EVERY CHUNK IS *ONE RANDOM BLOCK*', clips=[
        (H + 'chunk_blocks', 0.8, 2.8, dict(speed=1.5), [(0, 'whoosh', None, 0.5)]),
    ]),
    dict(cap='STAND STILL... THE FLOOR *BURNS*', accent=RED, clips=[
        (H + 'floor_lava', 0.5, 2.8, dict(zoom=(1.1, 1.25), focus=(0.5, 0.4)), [(1.0, 'mc', 'fire/ignite', 0.9), (1.4, 'mc', 'damage/hit2', 0.7)]),
    ]),
    dict(cap='MOBS SPAWN *GIANT*... OR TINY', clips=[
        (H + 'size_matters', 0.2, 2.9, dict(zoom=(1.0, 1.1)), [(0.3, 'mc', 'mob/creeper/say2', 0.8)]),
    ]),
    dict(cap='YOUR DROPS FLY *INTO THE SKY*', clips=[
        (H + 'upside_down', 1.0, 2.6, {}, [(0.3, 'mc', 'random/pop', 0.7), (0.8, 'mc', 'random/pop', 0.6)]),
    ]),
    dict(cap='*10x* THE MOBS', accent=RED, clips=[
        (H + 'double_trouble', 0.6, 3.0, dict(speed=1.4, zoom=(1.35, 1.45), focus=(0.5, 0.5)), [(0.0, 'mc', 'mob/zombie/say1', 0.8), (0.85, 'mc', 'mob/zombie/say2', 0.9), (0.9, 'impact', None, 0.5), (1.5, 'mc', 'mob/skeleton/say2', 0.7), (2.9, 'mc', 'random/fuse', 0.6)]),
    ]),
    dict(cap='*SKYBLOCK* IN ANY WORLD', clips=[
        (H + 'skyblock', 0.3, 2.6, dict(zoom=(1.0, 1.08)), [(0, 'whoosh', None, 0.5)]),
    ]),
    dict(cap='RACE FOR *RANDOM ITEMS*...', clips=[
        (H + 'force_item', 0.5, 2.8, dict(zoom=(1.0, 1.1), focus=(0.5, 0.45)), [(0.3, 'mc', 'random/orb', 0.7)]),
    ]),
    dict(cap='...OR LOCKOUT VS *BOB*', clips=[
        (H + 'lockout_bob_run', 0.0, 2.4, dict(zoom=(1.0, 1.1)), [(0, 'whoosh', None, 0.5)]),
        (H + 'lockout_board', 0.5, 2.0, dict(zoom=(1.15, 1.25), focus=(0.5, 0.5)), [(0, 'mc', 'random/click', 0.6)]),
    ], sub=('AN AI I\'M STILL *TEACHING*', 2.6)),
    dict(cap='EVEN A *CASINO*', accent=GOLD, clips=[
        (C + 'casino_slot_epic', 0.25, 1.1, dict(zoom=(1.05, 1.2), focus=(0.5, 0.42)), [(0, 'casino', 'reel_spin', 0.6)]),
        (C + 'casino_slot_epic_show', 0.0, 1.5, dict(flash=True, shake=0.5), [(0, 'casino', 'win_epic', 0.9), (0.1, 'mc', 'fireworks/blast1', 0.5)]),
        (C + 'casino_crash_cashout', 0.4, 1.4, dict(zoom=(1.2, 1.3), focus=(0.62, 0.45)), [(0.3, 'casino', 'cash_out', 0.8)]),
    ], sub=('THE HOUSE *ALWAYS* WINS', 2.0)),
    dict(cap='STACK THEM FOR *MORE XP*', clips=[
        ('ui/ui_select', 0.5, 2.0, dict(zoom=(1.7, 1.9), focus=(0.5, 0.4)), [(0.3, 'mc', 'random/click', 0.7), (1.2, 'mc', 'random/click', 0.7)]),
        ('ui/ui_journey', 0.3, 2.8, dict(zoom=(1.25, 1.35), focus=(0.55, 0.45)), [(0.2, 'mc', 'random/levelup', 0.5)]),
    ], sub=('UNLOCK ALL *50*', 2.4)),
    dict(cap=None, clips=[
        (SP + '/film/cards/end_challenges', 0, 6.5, dict(zoom=(1.0, 1.06)), [(0, 'impact', None, 0.9)]),
    ], end='WHICH CHALLENGE SHOULD I CODE *NEXT*?'),
]

plan, caps = [], []
t = 0.0
for beat in BEATS:
    t0 = t
    for shot, start, length, opts, cues in beat['clips']:
        path = shot if os.path.isabs(shot) else shot
        if not (os.path.isabs(shot) and os.path.isdir(shot)) and not have(shot):
            print('missing shot, skipped:', shot)
            continue
        c = Clip(shot, start, length, **opts)
        plan.append((c, t, cues))
        t += c.frames / cut.FPS
    if t == t0:
        continue
    if beat.get('hook'):
        caps.append(Caption(t0, t, beat['hook'], 'hook', y=330))
    if beat.get('cap'):
        sub = beat.get('sub')
        if sub:
            caps.append(Caption(t0, t0 + sub[1], beat['cap'], accent=beat.get('accent', YELLOW)))
            caps.append(Caption(t0 + sub[1], t, sub[0], accent=beat.get('accent', YELLOW)))
        else:
            caps.append(Caption(t0, t, beat['cap'], accent=beat.get('accent', YELLOW)))
    if beat.get('end'):
        caps.append(Caption(t0 + 2.5, t, beat['end'], y=1580))
TOTAL = t
clips = [c for c, _, _ in plan]
print(f'cut: {len(clips)} clips, {TOTAL:.1f} s')

mix = sfx.Mix(TOTAL + 1)
for clip, s, cues in plan:
    if s > 0:
        mix.add(s - 0.06, sfx.whoosh(0.2, seed=int(s * 10)), 0.22)
    for off, kind, name, gain in cues:
        try:
            snd = {'casino': lambda: sfx.casino(name), 'mc': lambda: sfx.mc(name), 'whoosh': sfx.whoosh,
                   'riser': sfx.riser, 'impact': sfx.impact}[kind]()
        except Exception as e:  # a sound that is not in this version's assets
            print('sound missing:', kind, name, e)
            continue
        mix.add(s + off, snd, gain)
for c in caps:
    if c.style == 'cap':
        mix.add(c.t0, sfx.pop(), 0.35)
wav = OUT + '/challenges_sfx.wav'
mix.write(wav, TOTAL)


def srt(path):
    def ts(x):
        h = int(x // 3600); m = int(x % 3600 // 60); s = x % 60
        return f'{h:02d}:{m:02d}:{int(s):02d},{int((s % 1) * 1000):03d}'
    with open(path, 'w') as f:
        for n, c in enumerate(caps, 1):
            f.write(f'{n}\n{ts(c.t0)} --> {ts(c.t1)}\n{c.text.replace("*", "")}\n\n')


srt(OUT + '/challenges.srt')
draft = '--draft' in sys.argv
if '--clean' in sys.argv or not draft:
    cut.render(clips, [], OUT + '/challenges_clean.mp4', audio=wav)
cut.render(clips, caps, OUT + ('/challenges_draft.mp4' if draft else '/challenges_final.mp4'), audio=wav, crf=24 if draft else 18)
print('done', TOTAL)
