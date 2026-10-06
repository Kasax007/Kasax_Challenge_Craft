"""Short A (fast, ~18 s): 'Minecraft, but dying is a blackjack hand' - the casino's death rule.
python edit/blackjack_short.py [--draft|--plan]"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import v2lib
from cut import YELLOW, GOLD, RED, GREEN

C = 'casino/'
W_ = lambda g=0.5: (0, 'whoosh', None, g)
CARD = os.path.join(v2lib.SP, 'film/cards/end_casino')
BEATS = [
    dict(hook='MINECRAFT, BUT *DYING* IS A CARD GAME', hook_y=360, vo=("Minecraft, but dying is a card game!", 0.85, 0.28), clips=[
        (C + 'casino_wave', 6.0, 0.5, dict(shake=0.6, zoom=(1.1, 1.3)), [(0, 'mc', 'mob/ravager/roar1', 0.8)]),
        (C + 'casino_blackjack', 0.0, 0.45, dict(flash=True, shake=0.6, zoom=(1.15, 1.0)), [(0, 'mc', 'damage/hit1', 0.9)]),
        (C + 'casino_blackjack', 0.9, 0.5, dict(zoom=(1.3, 1.45), focus=(0.5, 0.42)), [(0, 'casino', 'card_slide1', 0.8)]),
        (C + 'casino_blackjack', 8.9, 0.5, dict(zoom=(1.0, 1.2), flash=True), [(0, 'mc', 'item/totem/use_totem', 0.7)]),
    ]),
    dict(accent=GOLD, vo=("Can't pay the House's fee? That's game over.", 0.7, 0.28), caps=[("CAN'T PAY THE *FEE*?", 0), ('*GAME OVER*', 1.6)], clips=[
        (C + 'casino_fee', 3.5, 0.7, dict(zoom=(1.35, 1.5), focus=(0.5, 0.12), shake=0.4), [(0.1, 'casino', 'register', 0.9)]),
        (C + 'casino_bankrupt', 2.4, 0.7, dict(zoom=(1.3, 1.45), focus=(0.5, 0.12), shake=0.6), [(0.2, 'casino', 'bankrupt', 1.0), (0.2, 'impact', None, 0.8)]),
        (C + 'casino_bankrupt', 3.1, 0.7, dict(zoom=(1.45, 1.3), focus=(0.5, 0.12)), []),
    ]),
    dict(accent=RED, vo=("You die... but the House makes you an offer.", 0.7, 0.28), caps=[('YOU *DIE*...', 0), ('THE HOUSE', 1.0), ('MAKES AN *OFFER*', 1.6)], clips=[
        (C + 'casino_wave', 6.6, 0.7, dict(shake=0.5, zoom=(1.0, 1.25)), [(0.1, 'casino', 'house_sends', 0.9)]),
        (C + 'casino_blackjack', 0.0, 0.7, dict(flash=True, shake=0.7, zoom=(1.2, 1.0)), [(0.0, 'mc', 'damage/hit1', 0.9)]),
        (C + 'casino_blackjack', 0.3, 0.9, dict(zoom=(1.2, 1.35), focus=(0.4, 0.35)), [(0.2, 'casino', 'lever', 0.6)]),
    ]),
    dict(vo=("Beat the dealer at blackjack!", 0.7, 0.28), caps=['*BEAT* THE DEALER', 'AT *BLACKJACK*'], clips=[
        (C + 'casino_blackjack', 0.8, 0.8, dict(speed=1.5, zoom=(1.25, 1.4), focus=(0.5, 0.42)), [(0.0, 'casino', 'card_slide1', 0.9), (0.4, 'casino', 'card_place1', 0.9)]),
        (C + 'casino_blackjack', 2.0, 0.8, dict(zoom=(1.4, 1.25), focus=(0.5, 0.42)), [(0.3, 'casino', 'card_flip', 0.9)]),
    ]),
    dict(vo=("Hit, stand, or double down!", 0.8, 0.28), caps=[('*HIT*', 0), ('*STAND*', 0.55), ('DOUBLE *DOWN*', 1.1)], clips=[
        (C + 'casino_blackjack', 1.6, 0.55, dict(zoom=(1.5, 1.7), focus=(0.15, 0.55)), [(0.0, 'casino', 'card_place2', 0.9)]),
        (C + 'casino_blackjack', 2.0, 0.55, dict(zoom=(1.5, 1.7), focus=(0.4, 0.55)), [(0.0, 'casino', 'card_place3', 0.9)]),
        (C + 'casino_blackjack', 2.4, 0.6, dict(zoom=(1.5, 1.7), focus=(0.65, 0.55)), [(0.0, 'casino', 'card_slide2', 0.9)]),
    ]),
    dict(accent=GREEN, vo=("The dealer busts... and you live!", 0.9, 0.28), caps=[('DEALER *BUSTS*', 0), ('YOU *LIVE*!', 1.1)], clips=[
        (C + 'casino_blackjack', 3.0, 0.9, dict(zoom=(1.2, 1.1), focus=(0.5, 0.42)), [(0.0, 'casino', 'card_flip', 0.9)]),
        (C + 'casino_blackjack', 3.9, 1.0, dict(zoom=(1.1, 1.3), focus=(0.5, 0.42), flash=True), [(0.0, 'casino', 'win_small', 0.9), (0.0, 'impact', None, 0.5)]),
    ]),
    dict(accent=GREEN, vo=("Back on your feet, with everything!", 0.85, 0.28), caps=[('BACK WITH', 0), ('*EVERYTHING*', 0.9)], clips=[
        (C + 'casino_blackjack', 8.55, 1.8, dict(zoom=(1.0, 1.15), flash=True, payoff=True), [(0.5, 'mc', 'item/totem/use_totem', 1.0), (0.5, 'casino', 'revive', 0.8)]),
    ]),
    dict(vo=("Would you gamble?", 0.85, 0.3), end='WOULD YOU *GAMBLE*?', end_at=0.5, clips=[
        (CARD, 0, 2.2, dict(zoom=(1.0, 1.06), payoff=True), [(0, 'impact', None, 0.9)]),
    ]),
]
if __name__ == '__main__':
    v2lib.build('blackjack_life', BEATS)
