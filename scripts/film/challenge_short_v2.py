"""Challenge short V2 (fast): 'I coded viral challenges into Minecraft'. Cuts every 0.4-1.2 s,
phrase captions, one short voice line per beat.   python edit/challenge_short_v2.py [--draft|--plan]"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import v2lib
from cut import YELLOW, GOLD, RED, GREEN

H, C = 'chal/', 'casino/'
W_ = lambda g=0.5: (0, 'whoosh', None, g)
BEATS = [
    # hook: the wildest frames first, title on from frame 0
    dict(hook='I CODED *50* VIRAL CHALLENGES INTO MINECRAFT', vo=("I coded fifty viral challenges into Minecraft!", 0.85, 0.28), clips=[
        (H + 'size_matters', 0.2, 0.5, dict(zoom=(1.15, 1.0), shake=0.5), [(0, 'mc', 'mob/creeper/say1', 0.6)]),
        (H + 'chunk_blocks', 1.2, 0.5, dict(zoom=(1.0, 1.2)), [W_(0.4)]),
        (C + 'casino_slot_epic_show', 0.1, 0.5, dict(flash=True), [(0, 'casino', 'win_epic', 0.7)]),
        (H + 'double_trouble', 2.0, 0.5, dict(zoom=(1.3, 1.5)), [(0, 'mc', 'mob/zombie/say1', 0.6)]),
        (H + 'floor_lava', 1.0, 0.5, dict(zoom=(1.0, 1.25), focus=(0.5, 0.4)), [(0, 'mc', 'fire/ignite', 0.7)]),
        (H + 'skyblock', 2.0, 0.7, dict(zoom=(1.2, 1.0)), [(0, 'impact', None, 0.6)]),
    ]),
    dict(accent=RED, vo=("Red light? Move... and you're dead.", 0.75, 0.28), caps=[('*RED* LIGHT?', 0), ('MOVE...', 0.8), ("YOU'RE *DEAD*", 1.5)], clips=[
        (H + 'red_light', 3.9, 0.8, dict(speed=1.5, zoom=(1.1, 1.3), focus=(0.5, 0.35)), [(0.0, 'mc', 'note/bell', 0.6)]),
        (H + 'red_light', 5.0, 0.7, dict(zoom=(1.3, 1.1), focus=(0.5, 0.35)), [(0.2, 'mc', 'note/bass', 0.9)]),
        (H + 'red_light', 5.7, 0.7, dict(shake=0.8, flash=True), [(0.0, 'mc', 'damage/hit1', 0.9)]),
    ]),
    dict(vo=("Dice decide how far you walk!", 0.7, 0.28), caps=['*DICE* DECIDE', 'HOW FAR YOU *WALK*'], clips=[
        (H + 'dice_throw', 0.4, 0.9, dict(zoom=(1.0, 1.2)), [(0.2, 'mc', 'random/bow', 0.6)]),
        (H + 'dice_throw', 1.7, 1.0, dict(zoom=(1.15, 1.3)), [(0.0, 'mc', 'random/pop', 0.7), (0.5, 'mc', 'random/levelup', 0.4)]),
    ]),
    dict(vo=("No legs? Cushion!", 0.8, 0.28), caps=['NO *LEGS*?', '*CUSHION*!'], clips=[
        (H + 'cushion', 1.0, 0.8, dict(speed=2, zoom=(1.0, 1.2)), [(0.1, 'mc', 'dig/cloth1', 0.8)]),
        (H + 'cushion', 2.8, 0.8, dict(speed=2, zoom=(1.2, 1.0)), [(0.1, 'mc', 'dig/cloth2', 0.8)]),
    ]),
    dict(vo=("Every chunk, one random block.", 0.65, 0.28), caps=['EVERY *CHUNK*', 'ONE RANDOM *BLOCK*'], clips=[
        (H + 'chunk_blocks', 0.8, 1.0, dict(speed=2, zoom=(1.0, 1.2)), [W_(0.5)]),
        (H + 'chunk_blocks', 3.0, 1.0, dict(speed=2, zoom=(1.2, 1.0)), [W_(0.4)]),
    ]),
    dict(accent=RED, vo=("Stand still, and the floor burns!", 0.75, 0.28), caps=['STAND *STILL*', 'FLOOR *BURNS*'], clips=[
        (H + 'floor_lava', 0.4, 0.9, dict(zoom=(1.0, 1.3), focus=(0.5, 0.4)), [(0.6, 'mc', 'fire/ignite', 0.9)]),
        (H + 'floor_lava', 1.6, 0.9, dict(zoom=(1.3, 1.1), focus=(0.5, 0.4), shake=0.4), [(0.0, 'mc', 'damage/hit2', 0.8)]),
    ]),
    dict(vo=("Giant mobs. Or tiny ones.", 0.75, 0.28), caps=['*GIANT* MOBS', 'OR *TINY*'], clips=[
        (H + 'size_matters', 0.0, 0.8, dict(zoom=(1.0, 1.15)), [(0.1, 'mc', 'mob/creeper/say2', 0.8)]),
        (H + 'size_matters', 1.3, 0.8, dict(zoom=(1.15, 1.0), focus=(0.4, 0.6)), [(0.0, 'mc', 'random/pop', 0.7)]),
    ]),
    dict(vo=("Your drops fly into the sky!", 0.7, 0.28), caps=['DROPS FLY', 'INTO THE *SKY*'], clips=[
        (H + 'upside_down', 1.0, 0.8, dict(zoom=(1.0, 1.2)), [(0.3, 'mc', 'random/pop', 0.7)]),
        (H + 'upside_down', 2.0, 0.8, dict(zoom=(1.2, 1.0)), [(0.2, 'mc', 'random/pop', 0.6)]),
    ]),
    dict(accent=RED, vo=("One zombie? Ten zombies!", 0.9, 0.28), caps=[('ONE *ZOMBIE*?', 0), ('*TEN* ZOMBIES!', 1.2)], clips=[
        (H + 'double_trouble', 0.5, 0.7, dict(zoom=(1.5, 1.6), focus=(0.4, 0.55)), [(0.0, 'mc', 'mob/zombie/say1', 0.8)]),
        (H + 'double_trouble', 1.5, 0.5, dict(zoom=(1.5, 1.3), focus=(0.5, 0.55), shake=0.4), [(0.3, 'mc', 'mob/zombie/say2', 0.9)]),
        (H + 'double_trouble', 1.9, 1.5, dict(zoom=(1.4, 1.2), focus=(0.5, 0.55), shake=0.7, flash=True, payoff=True), [(0.0, 'impact', None, 0.7), (0.5, 'mc', 'mob/skeleton/say2', 0.7)]),
    ]),
    dict(vo=("Skyblock, anywhere.", 0.6, 0.3), caps=['*SKYBLOCK*', 'ANYWHERE'], clips=[
        (H + 'skyblock', 0.3, 0.7, dict(zoom=(1.0, 1.15)), [W_(0.5)]),
        (H + 'skyblock', 1.5, 0.7, dict(zoom=(1.15, 1.0)), []),
    ]),
    dict(vo=("Race for random items... or play Lockout against Bob, my AI!", 0.7, 0.28), caps=[('RACE FOR *ITEMS*', 0), ('OR *LOCKOUT*', 1.9), ('VS *BOB*, MY AI', 2.9)], clips=[
        (H + 'force_item', 0.5, 0.8, dict(zoom=(1.0, 1.2), focus=(0.5, 0.45)), [(0.3, 'mc', 'random/orb', 0.7)]),
        (H + 'force_item', 1.8, 0.8, dict(zoom=(1.2, 1.0), focus=(0.5, 0.45)), [(0.2, 'mc', 'random/orb', 0.7)]),
        (H + 'lockout_bob_run', 0.0, 0.9, dict(zoom=(1.0, 1.15)), [W_(0.5)]),
        (H + 'lockout_bob_run', 0.9, 0.8, dict(zoom=(1.15, 1.3), focus=(0.4, 0.5)), []),
        (H + 'lockout_board', 0.6, 0.8, dict(zoom=(1.2, 1.35)), [(0, 'mc', 'random/click', 0.6)]),
    ]),
    dict(accent=GOLD, vo=("There's even a casino!", 0.8, 0.28), caps=['EVEN A *CASINO*!'], clips=[
        (C + 'casino_slot_epic', 0.25, 0.7, dict(zoom=(1.05, 1.25), focus=(0.5, 0.42)), [(0, 'casino', 'reel_spin', 0.6)]),
        (C + 'casino_slot_epic_show', 0.0, 0.8, dict(flash=True, shake=0.5), [(0, 'casino', 'win_epic', 0.9), (0.1, 'mc', 'fireworks/blast1', 0.5)]),
    ]),
    dict(vo=("Stack them for more XP... and unlock all fifty!", 0.75, 0.28), caps=[('STACK THEM', 0), ('FOR MORE *XP*', 0.9), ('UNLOCK ALL *50*', 2.1)], clips=[
        ('ui/ui_select', 0.5, 0.9, dict(zoom=(1.7, 1.9), focus=(0.5, 0.35)), [(0.2, 'mc', 'random/click', 0.7)]),
        ('ui/ui_select', 1.4, 0.9, dict(zoom=(1.9, 1.7), focus=(0.5, 0.6)), [(0.2, 'mc', 'random/click', 0.7)]),
        ('ui/ui_journey', 0.3, 0.9, dict(zoom=(1.25, 1.35), focus=(0.55, 0.45)), [(0.0, 'mc', 'random/levelup', 0.5)]),
        ('ui/ui_journey', 1.8, 0.9, dict(zoom=(1.35, 1.25), focus=(0.55, 0.6)), []),
    ]),
    dict(vo=("Free on CurseForge. Which one next?", 0.8, 0.3), end='WHICH ONE *NEXT*?', end_at=1.3, clips=[
        (SP_CARD := os.path.join(v2lib.SP, 'film/cards/end_challenges'), 0, 3.0, dict(zoom=(1.0, 1.06), payoff=True), [(0, 'impact', None, 0.9)]),
    ]),
]
if __name__ == '__main__':
    v2lib.build('challenges_v2', BEATS)
