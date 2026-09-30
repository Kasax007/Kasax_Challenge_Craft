"""The House Always Wins (challenge 50) - final slot configuration.

Prints the exact RTP of the shipped reel strips and pay table. With an argument, also runs a
Monte Carlo check:  python3 slot_final.py SPINS SEED
Re-run after ANY change to strips or pays; the concept promises ~100% RTP.
"""
import sys
import slot_math as sm

IN  = {'NE': 2, 'DI': 5, 'EM': 6, 'AU': 8, 'LA': 9, 'FE': 9, 'CU': 10, 'CO': 14, 'W': 1, 'S': 2}
OUT = {'NE': 4, 'DI': 4, 'EM': 5, 'AU': 4, 'LA': 8, 'FE': 10, 'CU': 15, 'CO': 12, 'W': 1, 'S': 2}
sm.SCATTER_PAY = {3: 2, 4: 17, 5: 500}

strips = [sm.build_strip(c, 100 + i) for i, c in enumerate([OUT, IN, IN, IN, OUT])]
r = sm.exact(strips)
for k, v in r.items():
    print(f"{k:10s} {float(v):.6f}" + (f"   exact {v}" if k == "rtp" else ""))
print("strip lengths:", [len(s) for s in strips])
for s in strips:
    print(" ".join(s))
if len(sys.argv) > 1:
    print(sm.simulate(strips, int(sys.argv[1]), seed=int(sys.argv[2]) if len(sys.argv) > 2 else 1))
