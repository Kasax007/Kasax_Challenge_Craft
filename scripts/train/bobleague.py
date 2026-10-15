"""Bob's strategy, learnt by playing against himself: the champion (the knobs Bob plays with) against a
challenger (the same knobs with one or two changed), on the same board in the same world, game after
game. The challenger takes over only once it has won clearly - more tiles, game for game - and the
knobs that win go into bob-models/champion.json, which every Bob then plays with.

The knobs (see BotParams.java): how the guardian works, quick tiles first, the shield early, food
stocks, armour for the night, copper armour, the staircase in the plan, the world sense, the learnt
cover and goal models.
"""
import json
import math
import os
import random
import time

# name: (kind, range or values, default)
KNOBS = {
    "guardian": ("choice", [0, 1], 1),
    "quick": ("int", (0, 120), 30),
    "shield": ("choice", [0, 1], 1),
    "foodcap": ("choice", [-1, 30, 60, 120, 240], -1),
    "foodstock": ("float", (0.5, 3.0), 1.0),
    "nightarmour": ("choice", [0, 1], 0),
    "copper": ("choice", [0, 1], 1),
    "descent": ("choice", [0, 1], 1),
    "worldsense": ("choice", [0, 1], 1),
    "goalmodel": ("choice", [0, 1], 1),
    "cover": ("choice", [0, 1], 1),
}


def defaults():
    return {k: v[2] for k, v in KNOBS.items()}


def text(params):
    """The knobs as the mod takes them: k=v,k=v (every knob written, so nothing falls back)."""
    out = []
    for k in sorted(params):
        v = params[k]
        out.append("%s=%s" % (k, ("%g" % v) if isinstance(v, float) else v))
    return ",".join(out)


def parse(s):
    p = {}
    for part in (s or "").split(","):
        if "=" in part:
            k, v = part.split("=", 1)
            try:
                p[k.strip()] = int(v) if v.strip().lstrip("-").isdigit() else float(v)
            except ValueError:
                pass
    return p


def mutate(params, rng, tried):
    """A challenger: one knob changed (sometimes two), not one already beaten lately."""
    for _ in range(50):
        p = dict(params)
        names = rng.sample(sorted(KNOBS), 2 if rng.random() < 0.3 else 1)
        for k in names:
            kind, rng_or_vals, _ = KNOBS[k]
            if kind == "choice":
                p[k] = rng.choice([v for v in rng_or_vals if v != p.get(k)] or rng_or_vals)
            elif kind == "int":
                lo, hi = rng_or_vals
                step = max(1, int((hi - lo) * 0.25))
                p[k] = int(min(hi, max(lo, p.get(k, KNOBS[k][2]) + rng.choice([-1, 1]) * rng.randint(1, step))))
            else:
                lo, hi = rng_or_vals
                p[k] = round(min(hi, max(lo, p.get(k, KNOBS[k][2]) * math.exp(rng.gauss(0, 0.35)))), 2)
        t = text(p)
        if t != text(params) and t not in tried:
            return p
    return None


class League:
    """The champion and the challenger it is up against, the games between them, kept in state.json."""

    # (A league of twelve games has a standard error of 0.6 to 0.9 tiles - the mirror match showed
    # 0.8 tiles between the same code: a challenger must win by more than the noise, looked at no
    # earlier than after six games.)
    MIN_GAMES, MAX_GAMES = 6, 16

    def __init__(self, state, paths, seconds=1800):
        self.paths = paths
        self.seconds = seconds
        self.state = state.setdefault("league", {})
        self.state.setdefault("champion", defaults())
        self.state.setdefault("challenger", None)
        self.state.setdefault("games", [])
        self.state.setdefault("tried", {})
        self.state.setdefault("history", [])
        self.rng = random.Random()
        self.pending = 0
        # (A champion of an older version may lack knobs added since: their defaults.)
        for k, v in defaults().items():
            self.state["champion"].setdefault(k, v)

    @property
    def champion(self):
        return self.state["champion"]

    def next_game(self):
        """The next game to play: (seed, sides) - each side (name, knobs text); the names swap each game."""
        if self.state["challenger"] is None:
            ch = mutate(self.champion, self.rng, self.state["tried"])
            if ch is None:
                ch = mutate(defaults(), self.rng, {})
            self.state["challenger"] = ch
            self.state["games"] = []
        seed = self.rng.randrange(-2 ** 63, 2 ** 63)
        a, b = text(self.champion), text(self.state["challenger"])
        sides = [("BobA", a), ("BobB", b)] if self.rng.random() < 0.5 else [("BobA", b), ("BobB", a)]
        self.pending += 1
        return seed, sides, {"champion": a, "challenger": b}

    def record(self, game, result):
        """A finished game: its result counted for the duel it belongs to (if the duel is still on)."""
        self.pending = max(0, self.pending - 1)
        if result is None or self.state["challenger"] is None or game["challenger"] != text(self.state["challenger"]):
            return None
        by = {bot["params"]: bot for bot in result.get("bots", [])}
        # (The mod writes the knobs back as it read them: matched by their text.)
        champ = next((b for p, b in by.items() if parse(p) == parse(game["champion"])), None)
        chall = next((b for p, b in by.items() if parse(p) == parse(game["challenger"])), None)
        if champ is None or chall is None:
            return None
        g = {"seed": result.get("seed"), "tiles": [champ["tiles"], chall["tiles"]], "deaths": [champ["deaths"], chall["deaths"]],
             "hearts": [champ.get("hearts", 0), chall.get("hearts", 0)], "secs": result.get("secs")}
        self.state["games"].append(g)
        with open(os.path.join(self.paths.league, "results.jsonl"), "a", encoding="utf-8") as f:
            f.write(json.dumps({"time": time.time(), "champion": game["champion"], "challenger": game["challenger"], "result": result}) + "\n")
        return self.decide()

    def decide(self):
        """After enough games: the challenger takes over (clearly better), is turned down, or plays on."""
        games = self.state["games"]
        n = len(games)
        if n < self.MIN_GAMES:
            return None
        # (Tiles count; a death costs half a tile on top - it is the player's own worst moment.)
        diffs = [(g["tiles"][1] - 0.5 * g["deaths"][1]) - (g["tiles"][0] - 0.5 * g["deaths"][0]) for g in games]
        mean = sum(diffs) / n
        var = sum((d - mean) ** 2 for d in diffs) / max(1, n - 1)
        se = math.sqrt(var / n) if var > 0 else 0.25
        wins = sum(1 for d in diffs if d > 0)
        losses = sum(1 for d in diffs if d < 0)
        verdict = None
        if mean - 1.64 * se > 0 and wins > losses:
            verdict = "promoted"
        elif mean + se < 0 or n >= self.MAX_GAMES:
            verdict = "rejected"
        if verdict is None:
            return None
        ch = self.state["challenger"]
        entry = {"time": time.time(), "challenger": text(ch), "champion": text(self.champion), "games": n,
                 "mean_diff": round(mean, 2), "se": round(se, 2), "wins": wins, "losses": losses, "verdict": verdict}
        self.state["history"].append(entry)
        self.state["tried"][text(ch)] = verdict
        if verdict == "promoted":
            self.state["champion"] = ch
            self.state["tried"] = {}
        self.state["challenger"] = None
        self.state["games"] = []
        return entry
