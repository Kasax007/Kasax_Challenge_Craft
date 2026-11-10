package net.kasax.challengecraft.bot.lockout.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * The new board brain: look ahead by playing the game out. For each of the few most promising
 * tiles it plays the rest of the game (or the next {@link #horizon} seconds) many times on copies
 * of the game - itself and the opponent each picking the cheapest open tile from then on - and goes
 * for the tile whose games end best: tiles ahead of the opponent, and the win.
 *
 * <p>Everything the old brain weighs by rules comes out of the playing: shared steps (the iron
 * that makes the bucket and then the shears cheap), the walk between places, the chance of failing
 * or dying on the way, the race for a tile the opponent is about to take (going for it is wasted
 * time if it gets there first; taking it first denies it), the endgame (the thirteenth tile). The
 * plan works with what a player can know: places it has not seen are drawn anew for every game
 * played ahead (the world is guessed), and of the opponent only its tiles, its advancements and the
 * time are known ({@link #observe}).
 *
 * <p>The work is split by successive halving: all candidates get a few games, the better half
 * twice as many again, and so on; every candidate plays the same guessed worlds and the same luck
 * (common random numbers), so differences between them are differences of the choice.
 */
public final class SearchPolicy implements Policy {
    /**
     * First tiles looked at; games each in the first round; rounds of halving (8, 5, 3: about 120
     * games played ahead per decision, some 3 ms; 6, 4, 3 was 0.15 tiles a game worse).
     */
    public int candidates = 8, firstRound = 5, rounds = 3;
    /**
     * Game seconds played ahead in each game (at most to the end). A quarter of an hour: the games
     * played ahead use a plain cheapest-first brain, and further out they say more about its
     * habits than about the choice. 2000 games (90 minutes, Bob's pace, against today's brain):
     * 600 s +1.76 tiles a game, 900 s +1.84, 1200 s +1.58, 2400 s +1.24, 3600 s +1.16.
     */
    public double horizon = 900;
    /** Only what a player could know of the opponent (tiles, advancements, time); false: its true state. */
    public boolean observe = true;
    /** The opponent is believed to choose by the true averages (a strong player) rather than by Bob's figures. */
    public boolean opponentTrue;
    /** What a won game is worth beyond its tiles, in tiles. */
    public double winBonus = 2;
    /**
     * For testing how much the plan relies on a right picture of the game: the paces and failure
     * chances it plays ahead with are off by this much (log-normal spread, drawn once per game).
     */
    public double modelError;
    private Profile[] believed;
    /** The opponent plays ahead with Bob's present brain (strategist and route) rather than cheapest-first. */
    public boolean greedyOpponent;
    /** It plays ahead itself with the route (true figures) rather than cheapest-first. */
    public boolean rolloutRoute;
    /** The tiles it may set out for now (bit per tile; Bob's own choices in a real game): the rest only later in the look-ahead. */
    public long allowed = -1L;
    /** Nobody to play against (a game alone): the other player stands still in the look-ahead. */
    public boolean solo;
    /**
     * Per tile, the least it is believed to take (seconds; 0: no floor), from what the real game
     * knows beyond the simulator: Bob's own planner sees the world (no stone to be had down here,
     * no gold known), the simulator only its picture of it. Null: none (the bench).
     */
    public double[] floor;
    /**
     * Varies the luck of the games played ahead from one decision to the next where a fresh
     * instance makes each decision (the real game: the decision's number).
     */
    public long salt;
    /**
     * Games played ahead so far in the decision under way (or the last one): the look-ahead's work.
     * Read from another thread (the real game counts it against the game's ticks in self-play).
     */
    public volatile int progress;
    /** Set from another thread when the answer is no longer wanted: the decision stops and returns null. */
    public volatile boolean cancelled;

    /** The other player of a game alone: never goes for a tile. */
    private static final Policy IDLE = new Policy() {
        @Override
        public String name() {
            return "idle";
        }

        @Override
        public Decision choose(Sim sim, int me) {
            return null;
        }

        @Override
        public Policy fresh() {
            return this;
        }
    };
    private long decisions;
    /** Wall-clock nanoseconds spent deciding, and decisions made (for the reports). */
    public long nanos, calls;

    public SearchPolicy() {
    }

    @Override
    public String name() {
        return "search";
    }

    @Override
    public SearchPolicy fresh() {
        SearchPolicy s = new SearchPolicy();
        s.candidates = candidates;
        s.firstRound = firstRound;
        s.rounds = rounds;
        s.horizon = horizon;
        s.observe = observe;
        s.opponentTrue = opponentTrue;
        s.winBonus = winBonus;
        s.modelError = modelError;
        s.greedyOpponent = greedyOpponent;
        s.rolloutRoute = rolloutRoute;
        s.solo = solo;
        s.allowed = allowed;
        s.floor = floor;
        return s;
    }

    @Override
    public Decision choose(Sim sim, int me) {
        long t0 = System.nanoTime();
        try {
            return decide(sim, me);
        } finally {
            nanos += System.nanoTime() - t0;
            calls++;
        }
    }

    /** The profiles the plan believes in: the game's own, or with {@link #modelError} put in. */
    private Profile[] believed(Sim sim) {
        if (modelError <= 0) return sim.profiles;
        if (believed == null) {
            java.util.SplittableRandom r = new java.util.SplittableRandom(sim.world.seed ^ 0xC0FFEEL);
            believed = new Profile[2];
            for (int i = 0; i < 2; i++) {
                Profile b = sim.profiles[i].copy(sim.profiles[i].name + "-believed");
                for (int k = 0; k < b.pace.length; k++) b.pace[k] *= Math.exp(modelError * r.nextGaussian());
                for (int k = 0; k < b.fail.length; k++) b.fail[k] = Math.min(0.9, b.fail[k] * Math.exp(modelError * r.nextGaussian()));
                b.accessPace *= Math.exp(modelError * r.nextGaussian());
                b.perPace *= Math.exp(modelError * r.nextGaussian());
                b.speed *= Math.exp(modelError * r.nextGaussian());
                b.hzFight *= Math.exp(modelError * r.nextGaussian());
                believed[i] = b;
            }
        }
        return believed;
    }

    private Decision decide(Sim sim, int me) {
        progress = 0;
        PlayerState p = sim.players[me];
        Profile[] belief = believed(sim);
        Profile own = belief[me];
        // The candidates: the cheapest few by what they really take; resting ones only if nothing else.
        List<int[]> order = new ArrayList<>();
        // (cost: what the tile is believed to take - the simulator's figure, or the floor if that is
        // more; extra: by how much the floor lengthens the goal in the games played ahead.)
        double[] cost = new double[Board.SIZE], extra = new double[Board.SIZE];
        for (int pass = 0; pass < 2 && order.isEmpty(); pass++) {
            for (int t = 0; t < Board.SIZE; t++) {
                if (!sim.usable(me, t) || (allowed & (1L << t)) == 0) continue;
                if (pass == 0 && p.restUntil[t] > sim.now) continue;
                if (pass == 1 && p.tries[t] > Sim.MAX_TRIES) continue;
                double c = sim.planner.estimate(sim, me, sim.board.goal[t], own);
                if (c >= Planner.INF / 2) continue;
                extra[t] = floor != null && floor[t] > c ? floor[t] - c : 0;
                cost[t] = c + extra[t];
                order.add(new int[]{t});
            }
        }
        if (order.isEmpty()) return null;
        order.sort((a, b) -> Double.compare(cost[a[0]], cost[b[0]]));
        int n = Math.min(candidates, order.size());
        int[] tiles = new int[n];
        for (int i = 0; i < n; i++) tiles[i] = order.get(i)[0];
        // (The tile the opponent is likely after next, if it is not among them: perhaps worth taking first.)
        int theirs = opponentNext(sim, me);
        if (theirs >= 0 && sim.usable(me, theirs) && (allowed & (1L << theirs)) != 0 && cost[theirs] > 0 && !contains(tiles, theirs)) {
            tiles = java.util.Arrays.copyOf(tiles, n + 1);
            tiles[n++] = theirs;
        }
        if (n == 1) return Decision.tile(tiles[0], cost[tiles[0]], "only one");
        double[] sum = new double[n];
        int[] cnt = new int[n];
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < n; i++) alive.add(i);
        double until = Math.min(sim.length, sim.now + horizon);
        OpponentModel model = observe ? OpponentModel.of(sim.profiles[1 - me]) : null;
        PlayerState plain = observe ? observed(sim, 1 - me) : null;
        int sure = observe ? plain.ms : 0;
        int per = firstRound, game = 0;
        long base = sim.world.seed * 31 + me * 7919L + (salt + decisions++) * 104729L;
        // (The work is a count of games, never a time: the same decision however fast the machine.)
        for (int round = 0; round < rounds && alive.size() > 1; round++) {
            for (int g = 0; g < per; g++, game++) {
                if (cancelled) return null;
                long seed = mix(base + game);
                World w = sim.world.guessed(p.known, seed);
                for (int i : alive) {
                    Policy mine = rolloutRoute ? new GreedyPolicy(true, true) : new RolloutPolicy(true);
                    Policy other = solo ? IDLE : greedyOpponent ? new GreedyPolicy(opponentTrue, true) : new RolloutPolicy(opponentTrue);
                    Sim f = me == 0 ? sim.fork(w, mine, other, seed) : sim.fork(w, other, mine, seed);
                    if (belief != sim.profiles) f.believe(belief[0], belief[1]);
                    if (observe) f.players[1 - me] = model != null ? model.draw(sim, 1 - me, sure, new java.util.SplittableRandom(seed ^ 0x51L)) : plain.copy();
                    f.players[me].idle();
                    f.players[me].busyUntil = sim.now;
                    if (!f.startGoal(me, tiles[i], cost[tiles[i]], false)) {
                        sum[i] -= 1;
                        cnt[i]++;
                        progress++;
                        continue;
                    }
                    f.lengthen(me, extra[tiles[i]]);
                    f.runUntil(until);
                    sum[i] += value(f, me);
                    cnt[i]++;
                    progress++;
                }
            }
            alive.sort((a, b) -> Double.compare(sum[b] / Math.max(1, cnt[b]), sum[a] / Math.max(1, cnt[a])));
            int keep = Math.max(1, (alive.size() + 1) / 2);
            alive = new ArrayList<>(alive.subList(0, keep));
            per *= 2;
        }
        int best = alive.get(0);
        return Decision.tile(tiles[best], cost[tiles[best]], String.format("%.2f over %d games", sum[best] / Math.max(1, cnt[best]), cnt[best]));
    }

    /** What a game played ahead is worth to {@code me}: the tiles ahead of the opponent, and a won game. */
    private double value(Sim f, int me) {
        double v = f.score(me) - f.score(1 - me);
        if (f.over) v += f.winner == me ? winBonus : f.winner < 0 ? 0 : -winBonus;
        return v;
    }

    /** The tile the opponent would go for next by the cheapest-first rule, from what is known of it. */
    private int opponentNext(Sim sim, int me) {
        if (solo) return -1;
        int o = 1 - me;
        Sim view = sim.fork(sim.world, null, null, 0);
        if (observe) view.players[o] = observed(sim, o);
        Decision d = new RolloutPolicy(opponentTrue).choose(view, o);
        return d == null ? -1 : d.tile;
    }

    /** Milestones a player's advancements give away (Stone Age, Isn't It Iron Pick, We Need to Go Deeper, Suit Up). */
    static final int PUBLIC = Ms.STONE.bit | Ms.IRON_PICK.bit | Ms.NETHER_VISITED.bit | Ms.PORTAL.bit | Ms.ARMOR.bit;

    /**
     * The opponent as a player could picture it: its tiles and advancements, what those imply, the
     * basics anyone has after a few minutes; nothing of its pack; at the spawn, or at the portal in
     * the Nether; free to choose now.
     */
    static PlayerState observed(Sim sim, int o) {
        PlayerState t = sim.players[o];
        PlayerState v = new PlayerState();
        int ms = t.ms & PUBLIC;
        for (int i = 0; i < Board.SIZE; i++) {
            if (sim.board.owner[i] != o) continue;
            Recipe r = sim.board.goal[i];
            // (What the tile took was in hand when it was claimed.)
            int need = r.needs;
            for (int m = need; m != 0; m &= m - 1) need |= Econ.MS_CLOSURE[Integer.numberOfTrailingZeros(m)];
            ms |= need;
            if (r.auto >= 0) ms |= (1 << r.auto) | Econ.MS_CLOSURE[r.auto];
        }
        if (sim.now > 90) ms |= Ms.WOOD.bit;
        if (sim.now > 150) ms |= Ms.STONE.bit | Ms.WOOD.bit;
        if ((ms & Ms.NETHER_VISITED.bit) != 0) ms |= Ms.PORTAL.bit;
        v.ms = ms;
        v.res[Res.FOOD.ordinal()] = 4;
        v.dim = t.dim;
        v.known = sim.world.knownAtStart(sim.profiles[o].worldsense) | (t.dim == 1 ? sim.netherKnown : 0);
        v.fedAt = sim.now;
        v.busyUntil = sim.now;
        v.firstTile = t.firstTile;
        v.deaths = t.deaths;
        return v;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
