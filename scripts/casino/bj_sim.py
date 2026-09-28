"""Blackjack Monte Carlo with basic strategy, fresh shuffle every hand (continuous shuffler).
Usage: python3 bj_sim.py DECKS H17(0/1) DAS(0/1) LS(0/1) BJPAY hands seed
Prints mean return per initial unit (RTP) and, for the revival mode, P(net>0 | net!=0)."""
import random, sys

decks, h17, das, ls = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
bjpay = float(sys.argv[5]); N = int(sys.argv[6]); rnd = random.Random(int(sys.argv[7]))
SHOE = [1,2,3,4,5,6,7,8,9,10,10,10,10] * 4 * decks


def total(h):
    t = sum(h); soft = 1 in h and t + 10 <= 21
    return (t + 10 if soft else t), soft


def hard_action(t, up, can_d):
    if t >= 17: return "S"
    if t >= 13: return "S" if 2 <= up <= 6 else "H"
    if t == 12: return "S" if 4 <= up <= 6 else "H"
    if t == 11: return ("D" if can_d else "H") if (up != 1 or h17 or decks == 1) else "H"
    if t == 10: return ("D" if can_d else "H") if 2 <= up <= 9 else "H"
    if t == 9: return ("D" if can_d else "H") if 3 <= up <= 6 else "H"
    return "H"


def soft_action(t, up, can_d):
    if t >= 20: return "S"
    if t == 19: return ("D" if can_d else "S") if (h17 and up == 6) else "S"
    if t == 18:
        if 3 <= up <= 6 or (h17 and up == 2): return "D" if can_d else "S"
        return "S" if up in (2, 7, 8) else "H"
    if t == 17: return ("D" if can_d else "H") if 3 <= up <= 6 else "H"
    if t in (15, 16): return ("D" if can_d else "H") if 4 <= up <= 6 else "H"
    return ("D" if can_d else "H") if 5 <= up <= 6 else "H"


def split_ok(v, up):
    if v == 1 or v == 8: return True
    if v == 10 or v == 5: return False
    if v == 9: return up in (2, 3, 4, 5, 6, 8, 9)
    if v == 7: return 2 <= up <= 7
    if v == 6: return (2 if das else 3) <= up <= 6
    if v == 4: return das and up in (5, 6)
    return (2 if das else 4) <= up <= 7  # 2,2 / 3,3


def surrender(t, soft, up, pair):
    if not ls or soft: return False
    if t == 16 and not (pair == 8) and up in (9, 10, 1): return True
    if t == 15 and up == 10: return True
    if h17 and ((t == 15 and up == 1) or (t == 17 and up == 1) or (pair == 8 and up == 1)): return True
    return False


def play_hand():
    shoe = SHOE[:]  # draw without replacement from a freshly shuffled shoe
    def draw():
        i = rnd.randrange(len(shoe)); shoe[i], shoe[-1] = shoe[-1], shoe[i]; return shoe.pop()
    p = [draw(), draw()]; up = draw(); hole = draw()
    dealer = [up, hole]
    pbj = total(p)[0] == 21; dbj = total(dealer)[0] == 21
    if dbj: return 0.0 if pbj else -1.0  # dealer peeks
    if pbj: return bjpay
    t, soft = total(p)
    if surrender(t, soft, up, p[0] if p[0] == p[1] else 0): return -0.5
    hands = [[p, 1.0, False]]  # cards, stake, from_split_aces
    i = 0
    while i < len(hands):
        h, stake, sa = hands[i]
        while True:
            if len(h) == 1: h.append(draw())
            if sa: break
            t, soft = total(h)
            if len(h) == 2 and h[0] == h[1] and len(hands) < 4 and split_ok(h[0], up):
                c = h[0]; aces = c == 1
                hands[i] = [[c], stake, aces]; hands.append([[c], stake, aces])
                h, stake, sa = hands[i]
                continue
            can_d = len(h) == 2 and (das or len(hands) == 1)
            a = soft_action(t, up, can_d) if soft else hard_action(t, up, can_d)
            if a == "S": break
            if a == "D":
                hands[i][1] = stake = stake * 2; h.append(draw()); break
            h.append(draw())
            if total(h)[0] > 21: break
        i += 1
    live = [x for x in hands if total(x[0])[0] <= 21]
    if live:
        while True:
            t, soft = total(dealer)
            if t > 17 or (t == 17 and not (soft and h17)): break
            dealer.append(draw())
    dt = total(dealer)[0]
    net = 0.0
    for h, stake, _ in hands:
        t = total(h)[0]
        if t > 21: net -= stake
        elif dt > 21 or t > dt: net += stake
        elif t < dt: net -= stake
    return net


s = 0.0; s2 = 0.0; win = lose = 0
for _ in range(N):
    r = play_hand(); s += r; s2 += r * r
    if r > 0: win += 1
    elif r < 0: lose += 1
m = s / N; sd = (s2 / N - m * m) ** 0.5
print(f"decks={decks} h17={h17} das={das} ls={ls} bj={bjpay} N={N} RTP={100*(1+m):.3f}% "
      f"(±{100*1.96*sd/N**0.5:.3f}) win={win/N:.4f} lose={lose/N:.4f} push={(N-win-lose)/N:.4f} "
      f"revive={win/(win+lose):.4f}")
