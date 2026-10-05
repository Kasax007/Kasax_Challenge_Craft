"""The casino short: 'I built a casino in Minecraft'. Builds the cut, the captions, the SFX track,
then renders final (captions) and clean (no captions) versions plus an SRT."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import cut, sfx
from cut import Clip, Caption, YELLOW, GOLD, RED, GREEN

SP = cut.SP
OUT = SP + '/out'
os.makedirs(OUT, exist_ok=True)
C = 'casino/'


def have(shot):
    return os.path.isdir(os.path.join(SP, 'film', shot))


def pick(*shots):
    for s in shots:
        if have(s):
            return s
    raise SystemExit('missing: ' + ' / '.join(shots))


LEG = pick(C + 'casino_slot_legendary', C + 'casino_slot_epic')
SHOW = C + 'casino_slot_epic_show'

# (clip, sfx cues relative to the clip start)
plan = []


def add(clip, *cues):
    plan.append((clip, cues))


# --- 0. HOOK: reels, then the jackpot show
add(Clip(C + 'casino_slot_epic', 0.25, 0.9, zoom=(1.05, 1.2), focus=(0.5, 0.42)), (0.0, 'casino', 'reel_spin', 0.7), (0.0, 'whoosh', None, 0.5))
add(Clip(SHOW, 0.0, 1.0, zoom=(1.0, 1.08), flash=True, shake=0.6), (0.0, 'casino', 'win_epic', 1.0), (0.0, 'impact', None, 0.8), (0.1, 'mc', 'fireworks/blast1', 0.6))
add(Clip(C + 'casino_slot_epic', 1.0, 0.75, zoom=(1.25, 1.35), focus=(0.5, 0.62)), (0.0, 'casino', 'coins3', 0.8))
# --- 1. items are chips
CTR = pick(C + 'casino_counter', C + 'casino_counter_old')
add(Clip(CTR, 0.2, 2.5, speed=1.3, zoom=(1.15, 1.25), focus=(0.4, 0.55)), (0.08, 'casino', 'chip1', 0.7), (0.46, 'casino', 'chip2', 0.7), (0.85, 'casino', 'chip3', 0.7), (1.23, 'casino', 'chip1', 0.7))
add(Clip(CTR, 3.3, 2.2, zoom=(1.1, 1.0), focus=(0.5, 0.45)), (0.0, 'mc', 'block/bell/bell_use01', 0.9), (0.2, 'casino', 'register', 0.8), (0.4, 'casino', 'coin_shower1', 0.7))
# --- 2. the fee
if have(C + 'casino_fee'):
    add(Clip(C + 'casino_fee', 1.7, 3.0, zoom=(1.3, 1.4), focus=(0.5, 0.12)), (1.6, 'casino', 'register', 0.9), (1.7, 'mc', 'block/bell/bell_use01', 0.6))
if have(C + 'casino_bankrupt'):
    add(Clip(C + 'casino_bankrupt', 2.4, 2.6, zoom=(1.3, 1.45), focus=(0.5, 0.12), shake=0.5), (0.3, 'casino', 'bankrupt', 1.0), (0.3, 'impact', None, 0.8))
# --- 3. grind or gamble
add(Clip(pick(C + 'casino_square', C + 'casino_square_old'), 0.5, 2.6), (0.0, 'whoosh', None, 0.5))
# --- 4. games
add(Clip(C + 'casino_slot_freespins_intro', 1.4, 2.0, zoom=(1.1, 1.2), focus=(0.5, 0.45)), (0.0, 'casino', 'free_spins', 0.9))
add(Clip(C + 'casino_slot_freespins_intro', 2.6, 1.4, zoom=(1.15, 1.25), focus=(0.5, 0.45)), (0.0, 'casino', 'expand', 0.9))
add(Clip(C + 'casino_plinko', 0.2, 2.6, zoom=(1.3, 1.45), focus=(0.5, 0.45)), (0.0, 'mc', 'note/hat', 0.3), (1.0, 'casino', 'win_big', 0.9))
add(Clip(C + 'casino_crash_launch', 0.5, 1.6, zoom=(1.0, 1.15), focus=(0.4, 0.4)), (0.1, 'casino', 'rocket_launch', 1.0))
add(Clip(C + 'casino_crash_climb', 0.0, 1.6, speed=4.0, zoom=(1.0, 1.1), focus=(0.62, 0.42)), (0.0, 'casino', 'rocket_flight', 0.7), (0.0, 'riser', None, 0.6))
add(Clip(C + 'casino_crash_cashout', 0.4, 2.2, zoom=(1.2, 1.3), focus=(0.62, 0.45)), (0.4, 'casino', 'cash_out', 1.0), (0.5, 'casino', 'coin_shower2', 0.8), (1.6, 'casino', 'crash', 0.8))
add(Clip(C + 'casino_roulette_bets', 0.3, 1.4, speed=1.4), (0.0, 'casino', 'chip_stack1', 0.8), (0.6, 'casino', 'chip_stack2', 0.8))
add(Clip(C + 'casino_roulette_spin', 3.6, 1.6, speed=2.0), (0.0, 'casino', 'roulette_spin', 0.8))
add(Clip(C + 'casino_roulette_spin', 10.9, 1.4, zoom=(1.0, 1.15)), (0.0, 'casino', 'roulette_drop', 0.9), (0.4, 'casino', 'win_small', 0.8))
# --- 5. lose -> monsters
add(Clip(pick(C + 'casino_wave', C + 'casino_wave_old'), 5.3, 3.7, shake=0.4, zoom=(1.05, 1.15), focus=(0.5, 0.5)), (0.2, 'casino', 'house_sends', 1.0), (0.6, 'mc', 'mob/ravager/roar1', 0.8))
# --- 6. death -> blackjack -> revival
BJ = pick(C + 'casino_blackjack', C + 'casino_blackjack_old')
add(Clip(BJ, 0.0, 0.9, zoom=(1.15, 1.0), flash=True, shake=0.6), (0.0, 'mc', 'damage/hit1', 0.9), (0.1, 'whoosh', None, 0.6))
add(Clip(BJ, 0.75, 3.9, zoom=(1.2, 1.3), focus=(0.5, 0.42)), (0.0, 'casino', 'card_slide1', 0.9), (0.5, 'casino', 'card_place1', 0.9), (2.6, 'casino', 'card_flip', 0.9), (3.2, 'casino', 'win_small', 0.7))
add(Clip(BJ, 8.6, 2.6, zoom=(1.0, 1.1)), (0.6, 'mc', 'item/totem/use_totem', 1.0), (0.6, 'casino', 'revive', 0.8))
# --- 7. 96 % / the House always wins
add(Clip(pick(C + 'casino_slot_spins', C + 'casino_slot_epic'), 0.1, 3.0, zoom=(1.1, 1.2), focus=(0.5, 0.45)), (0.0, 'casino', 'reel_spin', 0.6), (2.0, 'casino', 'lose', 0.8))
add(Clip(SP + '/film/cards/end_casino', 0, 7.5, zoom=(1.0, 1.06)), (0.0, 'impact', None, 0.9), (0.1, 'casino', 'win_epic', 0.6))

clips = [c for c, _ in plan]
starts = []
t = 0.0
for c in clips:
    starts.append(t)
    t += c.frames / cut.FPS
TOTAL = t
print(f'cut: {len(clips)} clips, {TOTAL:.1f} s')

# --- captions, on the cut's time line (start of clip index + offset)
def at(i, off=0.0):
    return starts[i] + off

caps = [
    Caption(0.0, at(3), 'I BUILT A REAL *CASINO* IN MINECRAFT', 'hook', y=330),
    Caption(at(3), at(4), 'EVERY ITEM = *CHIPS* 💎'.replace(' 💎', '')),
    Caption(at(4), at(5), 'THE *CROUPIER* BUYS YOUR LOOT'),
]
i = 5
if have(C + 'casino_fee'):
    caps.append(Caption(at(i), at(i + 1), 'EVERY 10 MIN THE HOUSE WANTS ITS *FEE*', accent=GOLD)); i += 1
if have(C + 'casino_bankrupt'):
    caps.append(Caption(at(i), at(i + 1), "CAN'T PAY? *GAME OVER*", accent=RED)); i += 1
caps += [
    Caption(at(i), at(i + 1), 'GRIND... OR *GAMBLE*?'),
    Caption(at(i + 1), at(i + 3), '*SLOTS* WITH FREE SPINS'),
    Caption(at(i + 3), at(i + 4), '*PLINKO*'),
    Caption(at(i + 4), at(i + 7), '*CRASH*: CASH OUT BEFORE IT BLOWS', accent=RED),
    Caption(at(i + 7), at(i + 10), '*ROULETTE* ON THE FELT', accent=GREEN),
    Caption(at(i + 10), at(i + 11), 'LOSE A BET = *MONSTERS*', accent=RED),
    Caption(at(i + 11), at(i + 12), 'DIE...', accent=RED),
    Caption(at(i + 12), at(i + 13), 'PLAY *BLACKJACK* FOR YOUR LIFE'),
    Caption(at(i + 13), at(i + 14), 'WIN = *BACK WITH EVERYTHING*', accent=GREEN),
    Caption(at(i + 14), at(i + 15), 'EVERY GAME PAYS BACK *96%*'),
    # The end card itself says THE HOUSE ALWAYS WINS; the question goes under it.
    Caption(at(i + 15) + 3.0, TOTAL, 'WOULD YOU *GAMBLE*?', y=1580),
]

# --- sound effects
mix = sfx.Mix(TOTAL + 1)
for (clip, cues), s in zip(plan, starts):
    if s > 0:
        mix.add(s - 0.06, sfx.whoosh(0.22, seed=int(s * 10)), 0.25)
    for off, kind, name, gain in cues:
        if kind == 'casino':
            snd = sfx.casino(name)
        elif kind == 'mc':
            snd = sfx.mc(name)
        elif kind == 'whoosh':
            snd = sfx.whoosh()
        elif kind == 'riser':
            snd = sfx.riser()
        elif kind == 'impact':
            snd = sfx.impact()
        mix.add(s + off, snd, gain)
for c in caps:
    if c.style == 'cap':
        mix.add(c.t0, sfx.pop(), 0.35)
wav = OUT + '/casino_sfx.wav'
mix.write(wav, TOTAL)


def srt(path):
    def ts(x):
        h = int(x // 3600); m = int(x % 3600 // 60); s = x % 60
        return f'{h:02d}:{m:02d}:{int(s):02d},{int((s % 1) * 1000):03d}'
    with open(path, 'w') as f:
        for n, c in enumerate(caps, 1):
            f.write(f'{n}\n{ts(c.t0)} --> {ts(c.t1)}\n{c.text.replace("*", "")}\n\n')


srt(OUT + '/casino.srt')
draft = '--draft' in sys.argv
if '--clean' in sys.argv or not draft:
    cut.render(clips, [], OUT + '/casino_clean.mp4', audio=wav)
cut.render(clips, caps, OUT + ('/casino_draft.mp4' if draft else '/casino_final.mp4'), audio=wav, crf=24 if draft else 18)
print('done', TOTAL)
