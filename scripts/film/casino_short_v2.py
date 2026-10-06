"""Casino short V2 (fast): 'I built a casino in Minecraft', ending on the challenge select, the
level tree and a Challenge Craft call to action.   python edit/casino_short_v2.py [--draft|--plan]"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import v2lib
from cut import YELLOW, GOLD, RED, GREEN

C = 'casino/'
W_ = lambda g=0.5: (0, 'whoosh', None, g)
CARD = os.path.join(v2lib.SP, 'film/cards/end_challenges')
BEATS = [
    dict(hook='I BUILT A REAL *CASINO* IN MINECRAFT', vo=("I built a real casino in Minecraft!", 0.85, 0.28), clips=[
        (C + 'casino_slot_epic_show', 0.0, 0.5, dict(flash=True, shake=0.5), [(0, 'casino', 'win_epic', 0.9), (0.1, 'mc', 'fireworks/blast1', 0.5)]),
        (C + 'casino_plinko', 0.8, 0.45, dict(zoom=(1.3, 1.5), focus=(0.5, 0.45)), [(0, 'casino', 'win_big', 0.6)]),
        (C + 'casino_crash_cashout', 0.5, 0.45, dict(zoom=(1.2, 1.35), focus=(0.62, 0.45)), [(0, 'casino', 'cash_out', 0.7)]),
        (C + 'casino_roulette_spin', 10.9, 0.45, dict(zoom=(1.0, 1.2)), [(0, 'casino', 'roulette_drop', 0.7)]),
        (C + 'casino_blackjack', 8.9, 0.45, dict(zoom=(1.0, 1.15)), [(0, 'mc', 'item/totem/use_totem', 0.6)]),
        (C + 'casino_slot_epic', 1.0, 0.5, dict(zoom=(1.25, 1.4), focus=(0.5, 0.62)), [(0, 'casino', 'coins3', 0.8)]),
    ]),
    dict(vo=("Every item you own is a chip.", 0.65, 0.28), caps=['EVERY ITEM', '= *CHIPS*'], clips=[
        (C + 'casino_counter', 0.3, 0.75, dict(speed=1.6, zoom=(1.15, 1.3), focus=(0.4, 0.55)), [(0.08, 'casino', 'chip1', 0.7), (0.4, 'casino', 'chip2', 0.7)]),
        (C + 'casino_counter', 1.5, 0.75, dict(speed=1.6, zoom=(1.3, 1.15), focus=(0.4, 0.55)), [(0.08, 'casino', 'chip3', 0.7), (0.4, 'casino', 'chip1', 0.7)]),
    ]),
    dict(vo=("The dealer buys your loot.", 0.65, 0.28), caps=['*DEALER*', 'BUYS YOUR LOOT'], clips=[
        (C + 'casino_counter', 3.4, 0.8, dict(zoom=(1.1, 1.0), focus=(0.5, 0.45)), [(0.0, 'mc', 'block/bell/bell_use01', 0.8), (0.2, 'casino', 'register', 0.8)]),
        (C + 'casino_counter', 4.2, 0.8, dict(zoom=(1.0, 1.2), focus=(0.5, 0.45)), [(0.0, 'casino', 'coin_shower1', 0.7)]),
    ]),
    dict(accent=GOLD, vo=("But every ten minutes... the House takes its fee!", 0.6, 0.3), caps=[('EVERY *10 MIN*', 0), ('THE HOUSE', 1.3), ('TAKES ITS *FEE*', 1.9)], clips=[
        (C + 'casino_fee', 1.7, 0.9, dict(zoom=(1.3, 1.45), focus=(0.5, 0.12)), [(0.2, 'mc', 'block/bell/bell_use01', 0.6)]),
        (C + 'casino_fee', 2.7, 0.8, dict(zoom=(1.45, 1.35), focus=(0.5, 0.12)), [(0.0, 'casino', 'fee_warning', 0.6)]),
        (C + 'casino_fee', 3.5, 0.9, dict(zoom=(1.35, 1.5), focus=(0.5, 0.12), shake=0.4), [(0.1, 'casino', 'register', 0.9)]),
    ]),
    dict(accent=RED, vo=("Can't pay? Game over.", 0.75, 0.28), caps=["CAN'T PAY?", '*GAME OVER*'], clips=[
        (C + 'casino_bankrupt', 2.4, 0.85, dict(zoom=(1.3, 1.45), focus=(0.5, 0.12), shake=0.5), [(0.2, 'casino', 'bankrupt', 1.0), (0.2, 'impact', None, 0.8)]),
        (C + 'casino_bankrupt', 3.3, 0.85, dict(zoom=(1.45, 1.3), focus=(0.5, 0.12)), []),
    ]),
    dict(vo=("So... grind, or gamble?", 0.65, 0.3), caps=['*GRIND*...', 'OR *GAMBLE*?'], clips=[
        (C + 'casino_square', 0.5, 1.0, dict(speed=1.5, zoom=(1.0, 1.15)), [W_(0.5)]),
        (C + 'casino_square', 2.0, 1.0, dict(speed=1.5, zoom=(1.15, 1.0)), []),
    ]),
    dict(vo=("Slots with free spins!", 0.8, 0.28), caps=['*SLOTS*', 'FREE SPINS'], clips=[
        (C + 'casino_slot_freespins_intro', 1.4, 0.85, dict(zoom=(1.1, 1.25), focus=(0.5, 0.45)), [(0.0, 'casino', 'free_spins', 0.9)]),
        (C + 'casino_slot_freespins_intro', 2.6, 0.75, dict(zoom=(1.2, 1.3), focus=(0.5, 0.45)), [(0.0, 'casino', 'expand', 0.8)]),
    ]),
    dict(vo=("Plinko! Drop the ball!", 0.85, 0.28), caps=[('*PLINKO*!', 0), ('DROP THE *BALL*', 0.7)], clips=[
        (C + 'casino_plinko', 0.2, 0.6, dict(zoom=(1.3, 1.45), focus=(0.5, 0.45)), [(0.0, 'mc', 'note/hat', 0.3)]),
        (C + 'casino_plinko', 1.2, 0.6, dict(zoom=(1.45, 1.3), focus=(0.5, 0.5)), [(0.2, 'casino', 'win_big', 0.9)]),
    ]),
    dict(accent=RED, vo=("Crash: cash out before it blows!", 0.85, 0.28), caps=[('*CRASH*!', 0), ('CASH OUT', 0.9), ('BEFORE IT *BLOWS*', 1.7)], clips=[
        (C + 'casino_crash_launch', 0.5, 0.7, dict(zoom=(1.0, 1.2), focus=(0.4, 0.4)), [(0.1, 'casino', 'rocket_launch', 1.0)]),
        (C + 'casino_crash_climb', 0.0, 0.8, dict(speed=4.0, zoom=(1.0, 1.15), focus=(0.62, 0.42)), [(0.0, 'casino', 'rocket_flight', 0.7), (0.0, 'riser', None, 0.6)]),
        (C + 'casino_crash_cashout', 0.4, 0.9, dict(zoom=(1.2, 1.35), focus=(0.62, 0.45), flash=True), [(0.1, 'casino', 'cash_out', 1.0), (0.2, 'casino', 'coin_shower2', 0.8)]),
    ]),
    dict(accent=GREEN, vo=("Roulette! Place your bets!", 0.85, 0.28), caps=[('*ROULETTE*!', 0), ('PLACE YOUR *BETS*', 1.0)], clips=[
        (C + 'casino_roulette_bets', 0.3, 0.6, dict(speed=1.5), [(0.0, 'casino', 'chip_stack1', 0.8)]),
        (C + 'casino_roulette_spin', 3.6, 0.6, dict(speed=2.5), [(0.0, 'casino', 'roulette_spin', 0.8)]),
        (C + 'casino_roulette_spin', 10.8, 1.0, dict(zoom=(1.0, 1.2)), [(0.1, 'casino', 'roulette_drop', 0.9), (0.5, 'casino', 'win_small', 0.8)]),
    ]),
    dict(accent=RED, vo=("Lose a bet, and the House sends monsters!", 0.75, 0.28), caps=[('LOSE A *BET*?', 0), ('*MONSTERS*', 1.2)], clips=[
        (C + 'casino_wave', 5.3, 0.8, dict(shake=0.5, zoom=(1.05, 1.2)), [(0.2, 'casino', 'house_sends', 1.0)]),
        (C + 'casino_wave', 6.2, 0.8, dict(shake=0.3, zoom=(1.2, 1.1)), [(0.2, 'mc', 'mob/ravager/roar1', 0.8)]),
        (C + 'casino_wave', 7.2, 0.8, dict(zoom=(1.1, 1.25)), []),
    ]),
    dict(accent=RED, vo=("Die? Play blackjack... for your life!", 0.75, 0.28), caps=[('*DIE*?', 0), ('BLACKJACK', 0.8), ('FOR YOUR *LIFE*', 1.6)], clips=[
        (C + 'casino_blackjack', 0.0, 0.7, dict(zoom=(1.15, 1.0), flash=True, shake=0.6), [(0.0, 'mc', 'damage/hit1', 0.9)]),
        (C + 'casino_blackjack', 0.8, 0.9, dict(speed=1.5, zoom=(1.2, 1.3), focus=(0.5, 0.42)), [(0.0, 'casino', 'card_slide1', 0.9), (0.4, 'casino', 'card_place1', 0.9)]),
        (C + 'casino_blackjack', 2.6, 0.9, dict(zoom=(1.3, 1.2), focus=(0.5, 0.42)), [(0.3, 'casino', 'card_flip', 0.9)]),
    ]),
    dict(accent=GREEN, vo=("Win, and you're back!", 0.85, 0.28), caps=['WIN =', '*BACK*!'], clips=[
        (C + 'casino_blackjack', 3.5, 0.7, dict(zoom=(1.2, 1.1), focus=(0.5, 0.42)), [(0.1, 'casino', 'win_small', 0.8)]),
        (C + 'casino_blackjack', 8.8, 1.5, dict(zoom=(1.0, 1.15), flash=True, payoff=True), [(0.5, 'mc', 'item/totem/use_totem', 1.0), (0.5, 'casino', 'revive', 0.8)]),
    ]),
    dict(vo=("Ninety-six percent payback. The House always wins.", 0.6, 0.3), caps=[('*96%* PAYBACK', 0), ('THE HOUSE', 1.9), ('ALWAYS *WINS*', 2.5)], clips=[
        (C + 'casino_slot_spins', 0.1, 1.1, dict(zoom=(1.1, 1.25), focus=(0.5, 0.45)), [(0.0, 'casino', 'reel_spin', 0.6)]),
        (C + 'casino_slot_spins', 1.3, 1.1, dict(zoom=(1.25, 1.1), focus=(0.5, 0.45)), [(0.5, 'casino', 'reel_stop1', 0.6)]),
        (C + 'casino_slot_spins', 2.5, 1.1, dict(zoom=(1.1, 1.3), focus=(0.5, 0.45)), [(0.3, 'casino', 'lose', 0.9)]),
    ]),
    dict(accent=YELLOW, vo=("Play this, and forty-nine other challenges, in Challenge Craft!", 0.75, 0.28), caps=[('PLAY *THIS*', 0), ('+ *49* OTHER', 0.9), ('CHALLENGES', 1.8), ('IN *CHALLENGE CRAFT*', 2.6)], clips=[
        ('ui/ui_select', 0.5, 0.9, dict(zoom=(1.7, 1.9), focus=(0.5, 0.35)), [(0.0, 'mc', 'random/click', 0.7)]),
        ('ui/ui_select', 1.4, 0.9, dict(zoom=(1.9, 1.7), focus=(0.5, 0.6)), [(0.2, 'mc', 'random/click', 0.7)]),
        ('ui/ui_journey', 0.3, 0.9, dict(zoom=(1.25, 1.35), focus=(0.55, 0.45)), [(0.0, 'mc', 'random/levelup', 0.5)]),
        ('ui/ui_journey', 1.8, 0.9, dict(zoom=(1.35, 1.25), focus=(0.55, 0.6)), []),
    ]),
    dict(vo=("Free on CurseForge. Would you gamble?", 0.8, 0.3, 0.1), end='WOULD YOU *GAMBLE*?', end_at=1.3, clips=[
        (CARD, 0, 3.0, dict(zoom=(1.0, 1.06), payoff=True), [(0, 'impact', None, 0.9)]),
    ]),
]
if __name__ == '__main__':
    v2lib.build('casino_v2', BEATS)
