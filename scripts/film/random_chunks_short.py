"""Short B (fast, ~20 s): 'Minecraft, but every chunk is ONE random block'. Footage: the aerial
flight (chal/chunk_blocks) and the road over chunk floors (chal/chunk_walk, frames 0-125 are good).
python edit/random_chunks_short.py [--draft|--plan]"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import v2lib
from cut import YELLOW, GOLD, RED, GREEN

H = 'chal/'
W_ = lambda g=0.5: (0, 'whoosh', None, g)
CARD = os.path.join(v2lib.SP, 'film/cards/end_challenges')
BEATS = [
    dict(hook='MINECRAFT, BUT EVERY CHUNK IS *ONE RANDOM BLOCK*', hook_y=360, vo=("Minecraft, but every chunk is one random block!", 0.85, 0.28), clips=[
        (H + 'chunk_blocks', 1.2, 0.5, dict(zoom=(1.0, 1.25)), [W_(0.4)]),
        (H + 'chunk_walk', 0.2, 0.5, dict(zoom=(1.0, 1.2), flash=True), [(0, 'mc', 'random/orb', 0.5)]),
        (H + 'chunk_blocks', 4.0, 0.5, dict(zoom=(1.25, 1.0)), []),
        (H + 'chunk_walk', 1.5, 0.5, dict(zoom=(1.2, 1.0)), [(0, 'mc', 'random/orb', 0.5)]),
        (H + 'chunk_blocks', 6.2, 0.5, dict(zoom=(1.0, 1.25)), []),
        (H + 'chunk_walk', 2.6, 0.5, dict(zoom=(1.0, 1.2)), [(0, 'impact', None, 0.5)]),
    ]),
    dict(vo=("Take sixteen steps... and the whole ground changes!", 0.75, 0.28), caps=[('*16* STEPS...', 0), ('THE GROUND', 1.2), ('*CHANGES*', 1.9)], clips=[
        (H + 'chunk_walk', 0.0, 0.9, dict(speed=1.0, zoom=(1.0, 1.15)), [W_(0.5)]),
        (H + 'chunk_walk', 0.6, 0.9, dict(zoom=(1.15, 1.0)), [(0.2, 'mc', 'random/pop', 0.7)]),
        (H + 'chunk_walk', 1.3, 0.9, dict(zoom=(1.0, 1.15)), [(0.2, 'mc', 'random/pop', 0.7)]),
    ]),
    dict(accent=GOLD, vo=("Gold. Redstone. Diamond. Emerald!", 0.9, 0.28), caps=[('*GOLD*', 0), ('*REDSTONE*', 0.5), ('*DIAMOND*', 1.1), ('*EMERALD*', 1.7)], clips=[
        (H + 'chunk_walk', 0.0, 0.45, dict(zoom=(1.1, 1.25), focus=(0.5, 0.8)), [(0, 'casino', 'coins3', 0.6)]),
        (H + 'chunk_walk', 0.5, 0.6, dict(zoom=(1.1, 1.25), focus=(0.5, 0.8)), [(0, 'mc', 'random/orb', 0.6)]),
        (H + 'chunk_walk', 1.15, 0.6, dict(zoom=(1.1, 1.25), focus=(0.5, 0.8)), [(0, 'mc', 'random/levelup', 0.4)]),
        (H + 'chunk_walk', 1.8, 0.7, dict(zoom=(1.1, 1.25), focus=(0.5, 0.8)), [(0, 'mc', 'random/orb', 0.7)]),
    ]),
    dict(vo=("And from above... it's pure chaos!", 0.85, 0.28), caps=[('FROM ABOVE...', 0), ('PURE *CHAOS*', 1.1)], clips=[
        (H + 'chunk_blocks', 1.8, 0.9, dict(zoom=(1.0, 1.2)), [W_(0.5)]),
        (H + 'chunk_blocks', 3.0, 0.9, dict(zoom=(1.2, 1.0)), []),
        (H + 'chunk_blocks', 5.0, 0.9, dict(zoom=(1.0, 1.25), shake=0.2), [(0.1, 'impact', None, 0.5)]),
    ]),
    dict(vo=("It's one of fifty challenges in Challenge Craft!", 0.75, 0.28), caps=[("ONE OF *50*", 0), ('CHALLENGES', 1.0), ('IN *CHALLENGE CRAFT*', 1.8)], clips=[
        ('ui/ui_select', 0.5, 0.9, dict(zoom=(1.7, 1.9), focus=(0.5, 0.35)), [(0.0, 'mc', 'random/click', 0.7)]),
        ('ui/ui_select', 1.4, 0.9, dict(zoom=(1.9, 1.7), focus=(0.5, 0.6)), [(0.2, 'mc', 'random/click', 0.7)]),
        ('ui/ui_journey', 0.3, 0.9, dict(zoom=(1.25, 1.35), focus=(0.55, 0.45)), [(0.0, 'mc', 'random/levelup', 0.5)]),
    ]),
    dict(vo=("Free on CurseForge. Would you survive this?", 0.8, 0.3), end='WOULD YOU *SURVIVE*?', end_at=1.1, clips=[
        (CARD, 0, 2.6, dict(zoom=(1.0, 1.06), payoff=True), [(0, 'impact', None, 0.9)]),
    ]),
]
if __name__ == '__main__':
    v2lib.build('random_chunks', BEATS)
