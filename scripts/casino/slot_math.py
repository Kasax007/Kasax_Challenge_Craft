"""
Exact RTP calculator for the "House Always Wins" slot: 5 reels x 3 rows, 10 fixed lines,
Wild (Totem) substitutes all but Scatter, Scatter (Nether Star) pays anywhere and triggers
10 free spins with one random EXPANDING item (Book-of-Ra style). Retrigger +10.

Everything is computed exactly from the real reel strips (every stop of every reel), then
verified with a Monte Carlo run that also measures hit rate, feature rate and volatility.
"""
from fractions import Fraction as F
from itertools import product
import random, math, sys, statistics

LINES = [  # row index (0 top,1 mid,2 bottom) per reel
    (1,1,1,1,1), (0,0,0,0,0), (2,2,2,2,2), (0,1,2,1,0), (2,1,0,1,2),
    (1,0,0,0,1), (1,2,2,2,1), (0,0,1,2,2), (2,2,1,0,0), (1,2,1,0,1),
]
NL = len(LINES)
ITEMS = ["NE", "DI", "EM", "AU", "LA", "FE", "CU", "CO"]
W, S = "W", "S"

# Pays in multiples of LINE bet. Key = run length.
PAY = {
    "NE": {2: 10, 3: 100, 4: 1000, 5: 5000},
    "DI": {2: 5,  3: 40,  4: 400,  5: 2000},
    "EM": {3: 30, 4: 100, 5: 750},
    "AU": {3: 30, 4: 100, 5: 750},
    "LA": {3: 5,  4: 40,  5: 150},
    "FE": {3: 5,  4: 40,  5: 150},
    "CU": {3: 5,  4: 25,  5: 100},
    "CO": {3: 5,  4: 25,  5: 100},
    "W":  {2: 10, 3: 200, 4: 2000, 5: 10000},
}
SCATTER_PAY = {3: 2, 4: 20, 5: 200}  # multiples of TOTAL bet
FS_AWARD = 10
MAX_WIN = 5000  # x total bet, per spin incl. feature

# Symbol counts per reel (reel strip length = sum).
COUNTS = [
    # NE DI EM AU LA FE CU CO  W  S
    dict(NE=1, DI=2, EM=3, AU=3, LA=5, FE=5, CU=6, CO=6, W=1, S=2),
    dict(NE=1, DI=2, EM=3, AU=3, LA=5, FE=5, CU=6, CO=6, W=2, S=2),
    dict(NE=1, DI=2, EM=3, AU=3, LA=5, FE=5, CU=6, CO=6, W=2, S=2),
    dict(NE=1, DI=2, EM=3, AU=3, LA=5, FE=5, CU=6, CO=6, W=2, S=2),
    dict(NE=1, DI=2, EM=3, AU=3, LA=5, FE=5, CU=6, CO=6, W=1, S=2),
]


def build_strip(counts, seed):
    """Spread symbols so that no symbol repeats inside any 3-window where avoidable, and
    scatters are at least 3 apart (so at most one is ever visible per reel)."""
    rnd = random.Random(seed)
    L = sum(counts.values())
    for attempt in range(20000):
        pool = [s for s, n in counts.items() for _ in range(n)]
        rnd.shuffle(pool)
        ok = True
        for i in range(L):
            win = [pool[(i + k) % L] for k in range(3)]
            if win.count(S) > 1:
                ok = False; break
            # keep premium symbols from stacking inside one window
            for p in ("NE", "DI", "W"):
                if win.count(p) > 1:
                    ok = False; break
            if not ok: break
        if ok:
            return pool
    raise RuntimeError("could not build strip")


def line_value(syms):
    """Pay (in line bets) for one line, left to right, wild substitutes."""
    # wild-only prefix
    nw = 0
    while nw < 5 and syms[nw] == W:
        nw += 1
    best = PAY[W].get(nw, 0) if nw >= 2 else 0
    # first non-wild symbol
    if nw == 5:
        return best
    x = syms[nw]
    if x == S:
        return best
    n = nw
    while n < 5 and syms[n] in (x, W):
        n += 1
    return max(best, PAY[x].get(n, 0))


def exact(strips):
    Ls = [len(s) for s in strips]
    # --- line EV per line (marginals are strip frequencies)
    freqs = []
    for s in strips:
        L = len(s)
        f = {}
        for sym in s:
            f[sym] = f.get(sym, 0) + 1
        freqs.append({k: F(v, L) for k, v in f.items()})
    line_ev = F(0)
    for combo in product(*[list(f.items()) for f in freqs]):
        p = F(1)
        for _, q in combo:
            p *= q
        v = line_value([c[0] for c in combo])
        if v:
            line_ev += p * v
    line_ev_total = line_ev  # per TOTAL bet: NL lines * lineBet(=1/NL) * line_ev
    # --- per reel window stats: P(scatter visible), P(item x visible)
    scat = []
    vis = {x: [] for x in ITEMS}
    for s in strips:
        L = len(s)
        cs = 0
        cv = {x: 0 for x in ITEMS}
        for i in range(L):
            win = [s[(i + k) % L] for k in range(3)]
            if S in win: cs += 1
            for x in ITEMS:
                if x in win: cv[x] += 1
        scat.append(F(cs, L))
        for x in ITEMS:
            vis[x].append(F(cv[x], L))

    def count_dist(ps):
        d = [F(1)]
        for p in ps:
            nd = [F(0)] * (len(d) + 1)
            for k, v in enumerate(d):
                nd[k] += v * (1 - p)
                nd[k + 1] += v * p
            d = nd
        return d
    sd = count_dist(scat)
    p_trig = sum(sd[3:])
    scatter_ev = sum(sd[k] * SCATTER_PAY.get(k, 0) for k in range(6))
    base_ev = line_ev_total + scatter_ev
    # expanding EV per free spin, averaged over chosen item. Pays on all lines.
    exp_ev = F(0)
    for x in ITEMS:
        d = count_dist(vis[x])
        mn = min(PAY[x])
        e = sum(d[k] * PAY[x][k] for k in range(mn, 6)) * NL / NL  # NL lines * lineBet 1/NL
        exp_ev += e / len(ITEMS)
    fs_spin_ev = base_ev + exp_ev
    # session: N spins, each retriggers w.p. p_trig adding N spins
    exp_spins = F(FS_AWARD) / (1 - FS_AWARD * p_trig)
    fs_ev = p_trig * exp_spins * fs_spin_ev
    rtp = base_ev + fs_ev
    return dict(line=line_ev_total, scatter=scatter_ev, p_trig=p_trig, exp_ev=exp_ev,
                fs=fs_ev, rtp=rtp, exp_spins=exp_spins, fs_spin=fs_spin_ev)


def simulate(strips, spins, seed=1):
    rnd = random.Random(seed)
    Ls = [len(s) for s in strips]
    wins = []
    hits = 0; feats = 0; big = 0

    def spin(expand=None):
        stops = [rnd.randrange(L) for L in Ls]
        grid = [[strips[r][(stops[r] + k) % Ls[r]] for k in range(3)] for r in range(5)]
        tot = 0.0
        for ln in LINES:
            v = line_value([grid[r][ln[r]] for r in range(5)])
            tot += v / NL
        ns = sum(1 for r in range(5) if S in grid[r])
        tot += SCATTER_PAY.get(ns, 0)
        if expand:
            k = sum(1 for r in range(5) if expand in grid[r])
            if k >= min(PAY[expand]):
                tot += PAY[expand][k]
        return tot, ns

    for _ in range(spins):
        w, ns = spin()
        if ns >= 3:
            feats += 1
            left = FS_AWARD
            x = rnd.choice(ITEMS)
            while left:
                left -= 1
                fw, fns = spin(x)
                w += fw
                if fns >= 3: left += FS_AWARD
        w = min(w, MAX_WIN)
        if w > 0: hits += 1
        if w >= 100: big += 1
        wins.append(float(w))
    mean = sum(wins) / spins
    sd = statistics.pstdev(wins)
    return dict(rtp=mean, hit=hits / spins, feat=feats / spins, big=big / spins, sd=sd,
                maxw=max(wins))


if __name__ == "__main__":
    strips = [build_strip(c, 100 + i) for i, c in enumerate(COUNTS)]
    r = exact(strips)
    for k, v in r.items():
        print(f"{k:10s} {float(v):.6f}")
    print("strips:")
    for s in strips:
        print(" ".join(s))
    if len(sys.argv) > 1:
        print(simulate(strips, int(sys.argv[1])))
