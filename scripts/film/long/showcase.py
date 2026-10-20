"""The long-form showcase cut: beats anchored to phrases of the narration (long/script.py).
  tts/bin/python long/showcase.py [--plan] [--audio] [--captions]
New shots (film/long/...) fall back to older footage while they do not exist yet."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import longlib
SP = longlib.v3lib.SP
FILM = SP + '/film/'

def shot(name, fallback, fb_start=None):
    """film/long/<name> if it was filmed, else the fallback (shot, start)."""
    return ('long/' + name, None) if os.path.isdir(FILM + 'long/' + name) else (fallback, fb_start)

def C(name, st=0.0, w=1.0, z=(1.0, 1.05), cues=(), fb=None, **o):
    """A clip; name 'long/x' uses the new footage if there, with fb=(shot, start) as stand-in."""
    if name.startswith('long/') and fb:
        s, fst = shot(name[5:], *fb)
        if s != name:
            name, st = s, fst
    return (name, st, w, dict(zoom=z, **o), list(cues))

def UI(name, st=0.0, w=1.0, z=(1.0, 1.06), focus=(0.5, 0.5), cues=(), fb=None, **o):
    return C(name, st, w, z, cues, fb, focus=focus, ui=True, **o)
W = lambda g=0.2: (0, 'whoosh', None, g)
mc = lambda off, n, g=0.4: (off, 'mc', n, g)
ca = lambda off, n, g=0.5: (off, 'casino', n, g)

L, H, K, U = 'long/', 'chal/', 'casino/', 'ui/'
def T(text):
    return dict(hook=text, hook_y=300)
BEATS = [
 # ---------------------------------------------------------------- cold open
 dict(at=None, **T('EVERY VIRAL CHALLENGE. *ONE MOD.*'), clips=[C(L + 'chunk_air_v2', 0.0, z=(1.0, 1.06))]),
 dict(at='Minecraft, but the floor is lava', clips=[C(H + 'floor_lava', 0.8, cues=[mc(0.2, 'fire/ignite')])]),
 dict(at='Minecraft, but every chunk', clips=[C(L + 'chunk_walk_v2', 0.5)]),
 dict(at='Minecraft, but if you move', clips=[C(L + 'red_light_v2', 5.8, w=0.6), C(H + 'red_light', 5.5, w=0.4, cues=[mc(0.1, 'damage/hit1', 0.6)])]),
 dict(at='They look like the most fun', clips=[
     C(K + 'casino_slot_epic_show', 0.0, flash=True, cues=[ca(0, 'win_epic', 0.45)]),
     C(H + 'double_trouble', 1.4, cues=[mc(0.3, 'mob/zombie/say1')])]),
 dict(at='And for a long time', clips=[C(H + 'size_matters', 0.3), C(H + 'upside_down', 1.2)]),
 dict(at='Not anymore', clips=[C(U + 'ui_title', 0.0, z=(1.0, 1.04))]),
 dict(at='a free mod that puts', clips=[UI(L + 'ui_stack', 0.2, z=(1.0, 1.04)), C(L + 'hud_run', 0.0)]),
 dict(at='alone or with your friends', clips=[UI(L + 'nametags', 0.0, z=(1.3, 1.38), focus=(0.5, 0.42))]),
 # ---------------------------------------------------------------- how it works
 dict(at="Here's how it works", **T('HOW IT WORKS'), clips=[
     UI(L + 'ui_stack', 0.0, z=(1.0, 1.04)), UI(L + 'ui_stack', 1.0, z=(1.7, 1.75), focus=(0.85, 0.02))]),
 dict(at='Every challenge is a card', clips=[
     UI(L + 'ui_stack', 2.0, z=(1.3, 1.36), focus=(0.5, 0.42), cues=[mc(0.6, 'random/click')]),
     UI(L + 'ui_stack', 5.0, z=(1.3, 1.34), focus=(0.5, 0.5), cues=[mc(0.8, 'random/click')])]),
 dict(at='Every combination gets', clips=[
     UI(L + 'ui_stack', 7.0, z=(1.9, 1.95), focus=(0.5, 0.03), cues=[mc(0.4, 'random/orb', 0.3)]),
     UI(L + 'ui_stack', 9.2, z=(1.9, 1.95), focus=(0.5, 0.03), cues=[mc(0.4, 'random/orb', 0.3)])]),
 dict(at='A gentle twist', clips=[C(H + 'cushion', 1.5)]),
 dict(at='A truly brutal', clips=[C(H + 'double_trouble', 1.5, cues=[mc(0.2, 'mob/zombie/say2')])]),
 dict(at='Then you play', clips=[C(L + 'hud_run', 0.0, cues=[mc(1.2, 'dig/wood1', 0.3), mc(2.2, 'dig/wood2', 0.3)])]),
 dict(at='beat the Ender Dragon', clips=[C(L + 'dragon', 3.4, cues=[mc(0.1, 'mob/enderdragon/growl1', 0.35)])]),
 dict(at='A timer tracks your run', clips=[
     C(L + 'hud_run', 4.0), UI(L + 'red_light_v2', 5.0, z=(1.45, 1.5), focus=(0.5, 0.06))]),
 dict(at='When the dragon falls', clips=[C(L + 'dragon', 6.0)]),
 dict(at='run summary card', clips=[
     UI(U + 'ui_summary', 0.0, z=(1.3, 1.36), focus=(0.5, 0.55), cues=[mc(0.1, 'random/levelup', 0.3)]),
     UI(U + 'ui_summary', 0.6, z=(1.65, 1.7), focus=(0.5, 0.5), speed=0.75)]),
 # ---------------------------------------------------------------- progression
 dict(at='And that XP is the clever part', **T('PROGRESSION'), clips=[
     UI(L + 'ui_hub', 0.0, z=(1.15, 1.2), focus=(0.5, 0.58)), UI(L + 'ui_hub', 3.5, z=(1.35, 1.4), focus=(0.48, 0.55))]),
 dict(at='It carries over', clips=[C(L + 'nametags_close', 0.0), C(L + 'chunk_air_v2', 3.0)]),
 dict(at='Lifetime XP fills a level tree', clips=[UI(U + 'ui_journey', 0.0, z=(1.2, 1.25), focus=(0.55, 0.45))]),
 dict(at='Every level unlocks', clips=[UI(L + 'ui_perks', 1.5, z=(1.1, 1.15), focus=(0.5, 0.45))]),
 dict(at='work your way up', clips=[UI(L + 'ui_stack', 8.8, z=(1.2, 1.25), focus=(0.5, 0.5), cues=[mc(0.2, 'random/click')])]),
 dict(at='Levels also unlock permanent perks', clips=[
     UI(L + 'ui_perks', 3.4, z=(1.0, 1.05), cues=[mc(0.3, 'random/levelup', 0.35)]),
     UI(L + 'ui_perks', 3.6, z=(1.55, 1.6), focus=(0.45, 0.9))]),
 dict(at='And past level twenty', clips=[
     C(L + 'nametags_close', 0.5), UI(L + 'nametags', 1.0, z=(1.4, 1.48), focus=(0.5, 0.42)), C(L + 'nametags_close', 3.8, z=(1.05, 1.1))]),
 # ---------------------------------------------------------------- the tour
 dict(at='So what can you actually play', **T('THE CHALLENGES'), clips=[
     C(H + 'skyblock', 0.5, cues=[W()]), C(H + 'chunk_blocks', 2.5, cues=[W()]), C(K + 'casino_square', 1.0, cues=[W()])]),
 dict(at='Red Light, Green Light turns', clips=[C(L + 'red_light_v2', 0.0), C(L + 'red_light_v2', 2.6, z=(1.15, 1.2), focus=(0.5, 0.45))]),
 dict(at='When it turns yellow', clips=[UI(L + 'red_light_v2', 5.0, z=(1.35, 1.4), focus=(0.5, 0.1), cues=[mc(0.1, 'note/bell', 0.35)])]),
 dict(at='And on red', clips=[C(L + 'red_light_v2', 7.7)]),
 dict(at='Not a step', clips=[C(L + 'red_light_v2', 8.4, z=(1.05, 1.12))]),
 dict(at='In Dice Movement', clips=[C(H + 'dice_throw', 0.0, cues=[mc(0.4, 'dig/wood1', 0.4)])]),
 dict(at='However many pips', clips=[C(H + 'dice_throw', 1.0, z=(1.06, 1.1))]),
 dict(at='and a ring around you', clips=[C(H + 'dice_throw', 3.0, z=(1.0, 1.05), speed=0.5)]),
 dict(at='And in Cushion Only', clips=[C(H + 'cushion', 0.0, cues=[mc(0.5, 'dig/cloth1', 0.4)]), C(H + 'cushion', 3.6)]),
 dict(at='Every journey becomes', clips=[C(H + 'cushion', 6.0)]),
 dict(at='Some challenges change the world', clips=[C(L + 'chunk_air_v2', 0.0), C(H + 'chunk_blocks', 0.8)]),
 dict(at="One step you're walking", clips=[C(L + 'chunk_walk_v2', 0.0), C(L + 'chunk_walk_v2', 5.6)]),
 dict(at='Skyblock drops you', clips=[C(H + 'skyblock', 0.0)]),
 dict(at='And in The Floor is Lava', clips=[
     C(H + 'floor_lava', 0.0, cues=[mc(0.5, 'fire/ignite', 0.5)]), C(H + 'floor_lava', 2.6, z=(1.1, 1.15)), C(H + 'floor_lava', 4.0, z=(1.0, 1.05))]),
 dict(at='Chunk Hunt is even stranger', clips=[C(L + 'chunk_hunt', 0.4), C(L + 'chunk_hunt', 3.4)]),
 dict(at='and you have to defeat', clips=[C(L + 'chunk_hunt', 5.0, cues=[mc(0.5, 'damage/hit2', 0.4)]), C(L + 'chunk_hunt', 7.8)]),
 dict(at='Then there are the mob challenges', clips=[C(H + 'size_matters', 0.0), C(H + 'size_matters', 3.5)]),
 dict(at='Tiny ones are fast', clips=[C(H + 'size_matters', 1.0, z=(1.12, 1.18), speed=0.8)]),
 dict(at='And Double Trouble', clips=[C(H + 'double_trouble', 0.3, cues=[mc(0.3, 'mob/zombie/say1')])]),
 dict(at='One zombie is never', clips=[C(H + 'double_trouble', 1.6, z=(1.0, 1.08), cues=[mc(0.1, 'mob/zombie/say3', 0.5)])]),
 dict(at='Drops get the same treatment', clips=[
     C(H + 'upside_down', 0.5, cues=[mc(0.6, 'random/pop', 0.4)]), C(H + 'upside_down', 3.4, speed=0.8, z=(1.08, 1.12))]),
 dict(at='Random Block Drops makes', clips=[C(L + 'hud_run', 3.6, cues=[mc(2.4, 'random/pop', 0.4)])]),
 dict(at='and Progressive Block Drops', clips=[
     UI(L + 'progressive_drops', 0.0, z=(1.4, 1.45), focus=(0.5, 0.08)), C(L + 'progressive_drops', 4.2)]),
 dict(at='And some challenges are complete game modes', clips=[
     C(H + 'force_item', 0.0), UI(H + 'force_item', 0.6, z=(1.3, 1.36), focus=(0.5, 0.36))]),
 dict(at='Whoever collects', clips=[C(H + 'force_item', 1.0, cues=[mc(0.3, 'random/orb', 0.3)])]),
 dict(at='Lockout Bingo puts', clips=[UI(H + 'lockout_board', 0.0, z=(1.3, 1.36), focus=(0.5, 0.45), speed=0.7)]),
 dict(at='Every goal can only be claimed', clips=[
     UI(L + 'bob_board', 0.0, z=(1.5, 1.55), focus=(0.5, 0.6)), C(H + 'lockout_bob_run', 0.0)]),
 dict(at="There's also All Biomes", clips=[C(L + 'all_biomes', 0.5), UI(L + 'all_biomes', 3.1, z=(1.1, 1.15))]),
 dict(at='Minecraft Trivia', clips=[UI(L + 'trivia', 0.8, z=(1.15, 1.2), speed=0.8, cues=[mc(1.9, 'random/orb', 0.35)])]),
 dict(at='and the ultimate grinds', clips=[C(L + 'all_items', 0.5), UI(L + 'all_items', 3.1, z=(1.1, 1.15))]),
 # ---------------------------------------------------------------- the casino
 dict(at='But the challenge everyone talks about', **T('*THE HOUSE* ALWAYS WINS'), clips=[
     C(K + 'casino_square', 0.0, cues=[(0, 'impact', None, 0.4)]), C(K + 'casino_slot_epic', 0.0, cues=[ca(0.2, 'reel_spin', 0.3)])]),
 dict(at='It turns your survival world', clips=[C(K + 'casino_square', 3.0)]),
 dict(at='Every item you own', clips=[C(K + 'casino_counter', 0.2), C(K + 'casino_counter', 3.3, cues=[ca(0.2, 'chip_stack1', 0.5)])]),
 dict(at='and he will happily buy', clips=[C(K + 'casino_counter', 6.0)]),
 dict(at='But every ten minutes', clips=[UI(K + 'casino_fee', 0.5, z=(1.2, 1.3), focus=(0.5, 0.18), cues=[ca(0.2, 'fee_warning', 0.5)])]),
 dict(at="If you can't pay", clips=[UI(K + 'casino_bankrupt', 2.0, z=(1.15, 1.25), focus=(0.5, 0.2), cues=[ca(0.1, 'bankrupt', 0.5)])]),
 dict(at='So you can farm the money', clips=[C(K + 'casino_slot_epic', 4.0)]),
 dict(at='Mine Fever is', clips=[C(K + 'casino_slot_spins', 0.0, cues=[ca(0.2, 'reel_spin', 0.35)])]),
 dict(at='with free spins', clips=[C(K + 'casino_slot_freespins_intro', 0.0, cues=[ca(0.1, 'free_spins', 0.45)])]),
 dict(at='and a top prize', clips=[C(K + 'casino_slot_legendary', 9.2, z=(1.0, 1.1), punch=True, cues=[ca(0.4, 'win_epic', 0.5), ca(0.6, 'coin_shower1', 0.4)])]),
 dict(at='You can even bet a whole stack', clips=[C(K + 'casino_slot_epic_show', 0.5)]),
 dict(at="There's Plinko", clips=[C(K + 'casino_plinko', 0.0, cues=[ca(0.5, 'tick', 0.3), ca(1.0, 'tick', 0.3), ca(1.5, 'tick', 0.3)])]),
 dict(at="There's Crash", clips=[C(K + 'casino_crash_launch', 0.0, cues=[ca(0.2, 'rocket_launch', 0.45)]), C(K + 'casino_crash_climb', 0.0, speed=3)]),
 dict(at='until you cash out', clips=[C(K + 'casino_crash_cashout', 0.0, cues=[ca(0.3, 'cash_out', 0.5)])]),
 dict(at="And there's a full roulette table", clips=[C(K + 'casino_roulette_bets', 0.0), C(K + 'casino_roulette_spin', 9.0, cues=[ca(0.1, 'roulette_spin', 0.35)])]),
 dict(at='Of course, the House always wins', clips=[C(K + 'casino_roulette_spin', 11.0, cues=[ca(0.0, 'roulette_drop', 0.45)]), C(K + 'casino_slot_spins', 5.0)]),
 dict(at='and every lost bet', clips=[C(K + 'casino_wave', 2.5, cues=[ca(0.3, 'house_sends', 0.45)]), C(K + 'casino_wave', 5.6, cues=[mc(0.3, 'mob/zombie/say2', 0.4)])]),
 dict(at='And if you die', clips=[C(K + 'casino_blackjack', 0.0, cues=[ca(0.7, 'card_place1', 0.4)]), C(K + 'casino_blackjack', 2.9, cues=[ca(0.4, 'card_place2', 0.4)])]),
 dict(at='Win, and you stand up', clips=[C(K + 'casino_blackjack', 8.6, cues=[ca(0.2, 'revive', 0.45), mc(0.7, 'item/totem/use_totem', 0.4)])]),
 dict(at="Lose, and you're dead", clips=[C(H + 'red_light', 5.6)]),
 # ---------------------------------------------------------------- together
 dict(at='Every single challenge works', **T('PLAY *TOGETHER*'), clips=[
     C(L + 'nametags_close', 0.0), UI(L + 'nametags', 0.0, z=(1.35, 1.4), focus=(0.5, 0.42)), C(L + 'nametags_close', 4.0, z=(1.05, 1.1))]),
 dict(at='You can save your favorite setups', clips=[
     UI(L + 'ui_presets', 0.0, z=(1.15, 1.2), focus=(0.47, 0.5)), UI(L + 'ui_code', 0.0, z=(1.08, 1.12))]),
 dict(at='Send it to a friend', clips=[UI(L + 'ui_code', 0.8, z=(1.2, 1.25), focus=(0.5, 0.45))]),
 dict(at='And every day', clips=[
     UI(L + 'ui_hub', 2.0, z=(1.4, 1.48), focus=(0.5, 0.52)), C(L + 'chunk_air_v2', 4.0),
     UI(U + 'ui_summary', 0.5, z=(1.5, 1.55), focus=(0.5, 0.5))]),
 dict(at="And if nobody's online", **T('MEET *BOB*'), clips=[UI(L + 'bob_board', 2.0, z=(1.5, 1.55), focus=(0.5, 0.6)), C(L + 'bob_1', 0.0)]),
 dict(at='Bob is an AI player', clips=[C(L + 'bob_1', 2.6, cues=[mc(1.0, 'dig/wood1', 0.3), mc(2.0, 'dig/wood3', 0.3)])]),
 dict(at='He reads the goals', clips=[
     C(L + 'bob_2', 2.6, cues=[mc(0.8, 'dig/stone1', 0.3)]), C(L + 'bob_3', 1.0, cues=[mc(0.6, 'damage/hit1', 0.3)]), C(H + 'lockout_bob_run', 0.0)]),
 dict(at='Bob is still in an early stage', clips=[C(L + 'bob_3', 3.4, cues=[mc(2.6, 'damage/hit2', 0.4)]), C(H + 'lockout_bob_run', 2.8)]),
 dict(at='But he learns new tricks', clips=[C(L + 'bob_1', 4.0)]),
 # ---------------------------------------------------------------- outro
 dict(at='So whether you want', clips=[
     C(L + 'red_light_v2', 1.0, cues=[W()]), C(K + 'casino_slot_legendary', 10.0, cues=[W()]),
     C(L + 'bob_2', 4.0, cues=[W()]), C(L + 'chunk_walk_v2', 6.0, cues=[W()])]),
 dict(at='Challenge Craft is completely free', clips=[C(L + 'chunk_air_v2', 2.0, z=(1.0, 1.05))]),
 dict(at='The link is in the description', cap_y=1560, clips=[C(L + 'card_end', 0, z=(1.0, 1.05), cues=[(0, 'impact', None, 0.5)])]),
]

MUSIC = {s: SP + '/long/music/' + f for s, f in dict(
    open='itty_bitty_8_bit.wav', how='itty_bitty_8_bit.wav', prog='itty_bitty_8_bit.wav',
    tour='overworld.wav', move='overworld.wav', world='overworld.wav', mobs='overworld.wav', modes='overworld.wav',
    casino='hep_cats.wav', multi='itty_bitty_8_bit.wav', bob='itty_bitty_8_bit.wav', outro='itty_bitty_8_bit.wav').items()}

if __name__ == '__main__':
    a = sys.argv
    longlib.build(BEATS, music=MUSIC, plan_only='--plan' in a, render=('captions' if '--captions' in a else (False if '--audio' in a else True)))
