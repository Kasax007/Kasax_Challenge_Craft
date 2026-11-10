package net.kasax.challengecraft.bot.lockout.sim;

import java.util.SplittableRandom;

/**
 * One game of abstract Lockout between two players: a discrete-event simulation step by step (a
 * walk, a trip down for iron, a milestone made, a goal's own work). Each player follows its plan;
 * when it is free it decides anew in Bob's order - food, the night, a quick tile, the opening, the
 * kit, the brain's investments, the shield and the armour, then the board ({@link Policy}).
 *
 * <p>What happens on the way: a tile claimed by the other player stops whoever was going for it;
 * whatever a step brings may claim a tile by itself (iron smelted claims "Iron Ingot"); a goal that
 * runs past its time allowance is given up, as Bob gives up (three times the estimate, at most
 * seven and a half minutes, two extensions while it comes on); a death takes the things (unless
 * they are fetched back) and the tools.
 *
 * <p>The game ends when one player has clinched it (more tiles than the other could still reach)
 * or at {@link #length} seconds: the one with more tiles wins.
 */
public final class Sim {
    public static final String[] ACCOUNTS = {"goal", "quick", "food", "night", "opening", "bundle", "shield", "copper", "invest", "dead", "idle"};
    static final int A_GOAL = 0, A_QUICK = 1, A_FOOD = 2, A_NIGHT = 3, A_OPENING = 4, A_BUNDLE = 5, A_SHIELD = 6, A_COPPER = 7,
            A_INVEST = 8, A_DEAD = 9, A_IDLE = 10;
    /** Rest after a goal is given up (LockoutBrain.REST_TICKS), tries before it is left for the end. */
    static final double REST = 180;
    static final int MAX_TRIES = 2;
    /** Looking for a place that is not there: given up after this long (a good player's seconds). */
    static final double EXPLORE_GIVE_UP = 420;

    public final World world;
    public final Board board;
    public final double length;
    public final PlayerState[] players = new PlayerState[2];
    public final Profile[] profiles = new Profile[2];
    /** Each player's own figures: what its estimates assume ({@link Profile#naive()} for Bob's). */
    public final Profile[] naive = new Profile[2];
    public final Policy[] policies = new Policy[2];
    public final Planner planner = new Planner();
    final SplittableRandom rng;
    public double now;
    public boolean over;
    /** -1 draw (or not over). */
    public int winner = -1;
    /** A portal stands (whoever cast it): the other player may walk through it too. */
    public boolean portalLit;
    /** Whether the players have made their first decisions. */
    private boolean started;
    /** What a player knows of the Nether on arriving there. */
    final long netherKnown;
    /** Per player, per resource: what the open tiles and the plan want in all ("shopping list"). */
    final int[][] demand = new int[2][Res.N];
    private final long[] demandKey = {-1, -1};
    /** Optional log of decisions and claims (null: none). */
    public StringBuilder log;

    public Sim(World world, Board board, Profile p0, Profile p1, Policy a, Policy b, double length, long seed) {
        this.world = world;
        this.board = board;
        this.length = length;
        profiles[0] = p0;
        profiles[1] = p1;
        naive[0] = p0.naive();
        naive[1] = p1.naive();
        policies[0] = a;
        policies[1] = b;
        rng = new SplittableRandom(seed);
        netherKnown = world.knownInNether(p0.worldsense);
        for (int i = 0; i < 2; i++) {
            PlayerState p = new PlayerState();
            p.known = world.knownAtStart(profiles[i].worldsense);
            p.res[Res.FOOD.ordinal()] = 0;
            players[i] = p;
        }
    }

    private Sim(Sim o, World w, Policy a, Policy b, long seed) {
        world = w;
        board = o.board.copy();
        length = o.length;
        profiles[0] = o.profiles[0];
        profiles[1] = o.profiles[1];
        naive[0] = o.naive[0];
        naive[1] = o.naive[1];
        policies[0] = a;
        policies[1] = b;
        rng = new SplittableRandom(seed);
        now = o.now;
        over = o.over;
        winner = o.winner;
        portalLit = o.portalLit;
        // (A copy is always taken in a game under way: nobody decides afresh at its start.)
        started = true;
        netherKnown = w == o.world ? o.netherKnown : w.knownInNether(o.profiles[0].worldsense);
        players[0] = o.players[0].copy();
        players[1] = o.players[1].copy();
        // (A picture of a real game has no steps under way: both players are free from now on.)
        for (PlayerState p : players) if (p.busyUntil < now) p.busyUntil = now;
    }

    /** Plays this copy with other paces (what a look-ahead believes of the players). */
    public void believe(Profile p0, Profile p1) {
        profiles[0] = p0;
        profiles[1] = p1;
        naive[0] = p0.naive();
        naive[1] = p1.naive();
    }

    /** A copy to play on in a look-ahead: another (guessed) world, other policies, its own luck. */
    public Sim fork(World w, Policy a, Policy b, long seed) {
        return new Sim(this, w, a, b, seed);
    }

    // ---- running ------------------------------------------------------------------------------------

    /** Plays the game to its end. */
    public Sim run() {
        return runUntil(Double.MAX_VALUE);
    }

    /** Plays on until the game is over or {@code until} game seconds. */
    public Sim runUntil(double until) {
        if (!started) {
            started = true;
            for (int i = 0; i < 2; i++) next(i);
        }
        while (!over) {
            int i = players[0].busyUntil <= players[1].busyUntil ? 0 : 1;
            double t = players[i].busyUntil;
            if (t >= length) {
                now = length;
                finish();
                break;
            }
            if (t > until) {
                now = until;
                break;
            }
            now = t;
            complete(i);
            if (!over && players[i].busyUntil <= now) next(i);
        }
        return this;
    }

    private void finish() {
        over = true;
        int a = board.score[0], b = board.score[1];
        winner = a > b ? 0 : b > a ? 1 : -1;
    }

    // ---- deciding ---------------------------------------------------------------------------------------

    /** The player is free: what next, in Bob's order of deciders. */
    void next(int i) {
        PlayerState p = players[i];
        Profile prof = profiles[i];
        p.idle();
        eat(i);
        p.decisions++;
        int food = p.res[Res.FOOD.ordinal()];
        // Food: a stock at the start, topped up when low - and before the night (dusk: ready for it).
        boolean dusk = prof.nightShelter && p.dim == 0 && Planner.untilNight(now) < 150 && food < 1;
        boolean hungry = food < prof.foodLow && now > p.fedAt - 60;
        if (hungry || now < 1 && food < prof.foodStart || dusk) {
            int want = now < 1 ? prof.foodStart : food + prof.foodBatch;
            if (startChore(i, A_FOOD, () -> planner.plan(this, i, Res.FOOD, want, prof, null))) return;
        }
        // The night, not ready for it (a weapon and food): dug in until morning.
        if (prof.nightShelter && p.dim == 0 && Planner.night(now) && !nightReady(p)) {
            startWait(i, A_NIGHT, Planner.untilMorning(now) + 1);
            return;
        }
        if (prof.quick > 0 && quickTile(i)) return;
        if (!p.has(Ms.WOOD) && startChore(i, A_OPENING, () -> planner.plan(this, i, Ms.WOOD, prof, null))) return;
        if (!p.has(Ms.STONE)) {
            if (startChore(i, A_OPENING, () -> planner.plan(this, i, Ms.STONE, prof, null))) {
                appendWait(p, prof.openingExtra);
                return;
            }
        }
        if (prof.bundle && !p.has(Ms.BUNDLE) && now >= prof.bundleAfter && now >= p.choreRetryAt) {
            p.choreRetryAt = now + 300;
            if (planner.estimate(this, i, Ms.BUNDLE, naive[i]) < 120
                    && startChore(i, A_BUNDLE, () -> planner.plan(this, i, Ms.BUNDLE, prof, demand(i)))) return;
        }
        Decision inv = policies[i].invest(this, i);
        if (inv != null && inv.invest != null) {
            if (log != null) log(i, "invests: " + inv);
            if (startChore(i, A_INVEST, () -> planner.plan(this, i, inv.invest, prof, demand(i)), Econ.MS_WORK[inv.invest.ordinal()])) return;
        }
        if (prof.shieldFirst && !p.has(Ms.SHIELD) && p.dim == 0
                && startChore(i, A_SHIELD, () -> planner.plan(this, i, Ms.SHIELD, prof, demand(i)))) return;
        if (prof.copper && p.has(Ms.SHIELD) && !p.has(Ms.COPPER_ARMOR) && p.dim == 0 && now >= p.choreRetryAt
                && planner.estimate(this, i, Ms.COPPER_ARMOR, naive[i]) <= 300
                && startChore(i, A_COPPER, () -> planner.plan(this, i, Ms.COPPER_ARMOR, prof, null))) return;
        Decision d = policies[i].choose(this, i);
        if (d != null && d.tile >= 0 && board.isOpen(d.tile)) {
            if (log != null) log(i, "goal " + board.goal[d.tile].id + " " + d);
            if (startGoal(i, d.tile, d.estimate, false)) return;
        }
        startWait(i, A_IDLE, 20);
    }

    /** Ready for a night out (nightarmour=0): a weapon and something to eat. */
    static boolean nightReady(PlayerState p) {
        return p.has(Ms.STONE) && p.res[Res.FOOD.ordinal()] >= 1;
    }

    /** The cheapest open tile if its estimate is at most the quick limit and mostly sure (not luck): started at once. */
    private boolean quickTile(int i) {
        PlayerState p = players[i];
        Profile own = naive[i];
        int best = -1;
        double bestCost = profiles[i].quick;
        for (int t = 0; t < Board.SIZE; t++) {
            if (!board.isOpen(t) || p.quickTries[t] >= 1 || !usable(i, t)) continue;
            Recipe r = board.goal[t];
            double c = planner.estimate(this, i, r, own);
            if (c > bestCost || planner.lastDim() != p.dim) continue;
            if (planner.lastLuck() > 0.5 * c + 2) continue;
            best = t;
            bestCost = c;
        }
        if (best < 0) return false;
        p.quickTries[best]++;
        if (log != null) log(i, "quick tile " + board.goal[best].id + " (~" + Math.round(bestCost) + " s)");
        return startGoal(i, best, bestCost, true);
    }

    /** Whether this player can go for the tile at all (Bob knows a way, it is not resting...). */
    public boolean usable(int i, int tile) {
        Recipe r = board.goal[tile];
        if (r.impossible || !board.isOpen(tile)) return false;
        if (profiles[i].bobGoals && !r.bob) return false;
        if (r.heavy && !geared(players[i])) return false;
        return true;
    }

    /** Fit for a hard fight: a shield or armour. */
    static boolean geared(PlayerState p) {
        return p.has(Ms.SHIELD) || p.has(Ms.ARMOR);
    }

    private boolean startChore(int i, int account, java.util.function.BooleanSupplier planIt) {
        return startChore(i, account, planIt, null);
    }

    /** A chore; one of the brain's investments may fail like a goal ({@code failKind}: the kind of work it is). */
    private boolean startChore(int i, int account, java.util.function.BooleanSupplier planIt, Econ.Work failKind) {
        PlayerState p = players[i];
        if (!planIt.getAsBoolean() || p.planLength == 0) {
            p.idle();
            return false;
        }
        p.chore = account;
        p.target = -1;
        p.goalFailsAt = Double.MAX_VALUE;
        if (failKind != null && rng.nextDouble() < profiles[i].fail[failKind.ordinal()]) {
            p.goalFailsAt = now + (0.3 + 0.9 * rng.nextDouble()) * 200;
        }
        return startStep(i);
    }

    private void startWait(int i, int account, double seconds) {
        PlayerState p = players[i];
        p.idle();
        p.chore = account;
        p.planLength = 0;
        p.cursor = 0;
        appendWait(p, seconds);
        startStep(i);
    }

    private static void appendWait(PlayerState p, double seconds) {
        if (seconds <= 0) return;
        if (p.planLength >= p.planKind.length) {
            p.planKind = java.util.Arrays.copyOf(p.planKind, p.planLength + 8);
            p.planArg = java.util.Arrays.copyOf(p.planArg, p.planLength + 8);
            p.planAmount = java.util.Arrays.copyOf(p.planAmount, p.planLength + 8);
        }
        p.planKind[p.planLength] = Planner.S_WAIT;
        p.planArg[p.planLength] = 0;
        p.planAmount[p.planLength] = (int) Math.ceil(seconds);
        p.planLength++;
    }

    /** Sets out for a tile: its plan, its time allowance (as LockoutBrain.budget). */
    boolean startGoal(int i, int tile, double estimate, boolean quick) {
        PlayerState p = players[i];
        Recipe r = board.goal[tile];
        if (!planner.plan(this, i, r, profiles[i], demand(i)) || p.planLength == 0) {
            p.idle();
            p.restUntil[tile] = now + REST;
            p.tries[tile]++;
            return false;
        }
        p.target = tile;
        p.chore = -1;
        p.quick = quick;
        p.starts[tile]++;
        p.goalStart = now;
        p.goalEstimate = estimate;
        p.goalBudget0 = quick ? clamp(estimate * 3, 20, 90) : budget(estimate);
        p.goalBudget = p.goalBudget0;
        p.extensions = 0;
        p.stepsSinceCheck = 0;
        // (Some attempts fail whatever the plan: then part of the way in.)
        p.goalFailsAt = Double.MAX_VALUE;
        if (rng.nextDouble() < profiles[i].fail[r.kind.ordinal()]) {
            double expected = planner.estimate(this, i, r, profiles[i]);
            p.goalFailsAt = now + (0.3 + 0.9 * rng.nextDouble()) * Math.max(20, Math.min(900, expected));
        }
        return startStep(i);
    }

    /**
     * The step under way takes {@code seconds} longer: a look-ahead's first goal believed dearer
     * than its recipe (see {@link SearchPolicy#floor}). Its time allowance still holds.
     */
    public void lengthen(int i, double seconds) {
        PlayerState p = players[i];
        if (seconds <= 0 || p.stepKind < 0) return;
        p.stepEnd += seconds;
        schedule(p);
    }

    /** LockoutBrain.budget: three times a short estimate, a little over twice a long one, one to seven and a half minutes. */
    public static double budget(double estimate) {
        double factor = estimate > 120 ? 2.2 : 3;
        return clamp(estimate * factor, 60, 450);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- steps ---------------------------------------------------------------------------------------------

    /** Starts the next step of the plan (false if there is none: the player is free). */
    private boolean startStep(int i) {
        PlayerState p = players[i];
        if (p.cursor >= p.planLength) return false;
        int k = p.planKind[p.cursor], a = p.planArg[p.cursor], amt = p.planAmount[p.cursor];
        p.cursor++;
        double dur = duration(i, k, a, amt);
        if (dur >= Planner.INF / 2) {
            // (No way after all, e.g. the place is not there: given up.)
            dropGoal(i, true);
            return false;
        }
        p.stepKind = k;
        p.stepArg = a;
        p.stepAmount = amt;
        p.stepStart = now;
        p.stepBeganAt = now;
        double fullEnd = now + dur;
        double death = Double.MAX_VALUE;
        double h = hazard(i, k, a);
        if (h > 0) {
            double tDeath = -Math.log(1 - rng.nextDouble()) / (h / 60);
            if (tDeath < dur) death = now + tDeath;
        }
        p.stepEnd = fullEnd;
        p.stepDies = death < Double.MAX_VALUE;
        p.stepDeathAt = death;
        schedule(p);
        if (logSteps && log != null) log(i, "  " + describe(k, a, amt) + " " + Math.round(dur) + " s" + (p.target >= 0 ? "" : " [" + ACCOUNTS[Math.max(0, p.chore)] + "]")
                + (p.stepDies ? " DIES" : ""));
        return true;
    }

    /** When the step under way ends: done, a death, or the goal's time allowance. */
    private void schedule(PlayerState p) {
        double end = Math.min(p.stepEnd, p.stepDeathAt);
        p.stepBudget = false;
        p.stepFails = false;
        if (p.target >= 0) {
            double budgetAt = p.goalStart + p.goalBudget;
            if (budgetAt < end) {
                end = Math.max(now, budgetAt);
                p.stepBudget = true;
            }
        }
        if ((p.target >= 0 || p.chore == A_INVEST) && p.goalFailsAt < end) {
            end = Math.max(now, p.goalFailsAt);
            p.stepBudget = false;
            p.stepFails = true;
        }
        p.busyUntil = end;
    }

    /** Seconds the step takes, drawn: travel as the land lies, finding by luck, work around its usual time. */
    private double duration(int i, int k, int a, int amt) {
        PlayerState p = players[i];
        Profile prof = profiles[i];
        switch (k) {
            case Planner.S_WAIT:
                return amt;
            case Planner.S_ENTER:
            case Planner.S_LEAVE:
                return 6;
            case Planner.S_MS: {
                if (a == Ms.PORTAL.ordinal() && portalLit) return 2;
                return Econ.MS_TIME[a] * prof.pace(Econ.MS_WORK[a]) * noise(0.25);
            }
            case Planner.S_RES: {
                double mf = Econ.RES_MONSTER[a] ? Planner.monsterFactor(now, p.layer, p.dim) : 1;
                double find = (p.access & (1L << a)) != 0 ? 0 : exp(Econ.RES_ACCESS[a] * prof.accessPace * mf);
                return find + amt * Econ.RES_PER[a] * prof.perPace * prof.pace(Econ.RES_WORK[a]) * mf * noise(0.2);
            }
            case Planner.S_GO:
                return travel(i, Place.ALL[a]);
            case Planner.S_SEARCH: {
                Recipe r = GoalBook.all().get(a);
                return exp(r.work * r.luck * prof.pace(r.kind) * Planner.monsterFactor(now, p.layer, p.dim));
            }
            case Planner.S_WORK: {
                Recipe r = GoalBook.all().get(a);
                double pace = prof.pace(r.kind);
                if (r.nightOnly && !Planner.night(now)) return Planner.untilNight(now) + r.work * pace;
                if (r.monster) return r.work * (1 - r.luck) * pace * noise(0.2);
                return r.work * (1 - r.luck) * pace * noise(0.2) + exp(r.work * r.luck * pace);
            }
            default:
                throw new IllegalStateException("step kind " + k);
        }
    }

    private double travel(int i, Place target) {
        PlayerState p = players[i];
        Profile prof = profiles[i];
        if (target.kind == Place.Kind.LAYER) {
            int to = target == Place.CAVE ? 1 : target == Place.DEEP ? 2 : 0;
            double s = prof.layers(p.layer, to);
            if (target == Place.WATER && (p.access & Planner.WATER_AT_HAND) == 0) s += Planner.WATER_WALK / prof.speed;
            return s * (0.9 + 0.3 * rng.nextDouble());
        }
        if (target == Place.NETHER) return Math.hypot(p.x, p.z) / prof.netherSpeed * (0.9 + 0.3 * rng.nextDouble());
        boolean knows = (p.known & World.bit(target)) != 0;
        double speed = target.dim == 1 ? prof.netherSpeed : prof.speed;
        if (!world.exists(target)) return knows ? Planner.INF : EXPLORE_GIVE_UP * prof.pace(Econ.Work.EXPLORE);
        double dist = Math.hypot(world.x(target) - p.x, world.z(target) - p.z);
        double s = dist / speed * (knows ? 1 : prof.explore) + prof.layers(p.layer, target.layer);
        return s * (0.9 + 0.3 * rng.nextDouble());
    }

    private double noise(double sigma) {
        // (Log-normal with mean 1.)
        double g = rng.nextGaussian();
        return Math.exp(sigma * g - sigma * sigma / 2);
    }

    private double exp(double mean) {
        return mean <= 0 ? 0 : -Math.log(1 - rng.nextDouble()) * mean;
    }

    /** Deaths per minute in this step. */
    double hazard(int i, int k, int a) {
        PlayerState p = players[i];
        Profile prof = profiles[i];
        if (k == Planner.S_WAIT && (p.chore == A_NIGHT || p.chore == A_DEAD)) return 0; // (dug in, or respawning)
        double h = prof.hzBase;
        if (p.dim == 1) h += prof.hzNether;
        else if (p.layer >= 1) h += prof.hzCave;
        else if (Planner.night(now)) h += prof.hzNight;
        if (k == Planner.S_WORK) {
            Recipe r = GoalBook.all().get(a);
            h += r.hazard * prof.hzFight;
        } else if (k == Planner.S_SEARCH) {
            // (Looking about among the monsters: some of the fight's danger.)
            h += 0.3 * GoalBook.all().get(a).hazard * prof.hzFight;
        } else if (k == Planner.S_RES && Econ.RES_MONSTER[a]) {
            h += 0.03 * prof.hzFight;
        }
        if (p.has(Ms.SHIELD) || p.has(Ms.ARMOR) || p.has(Ms.COPPER_ARMOR)) h *= prof.gear;
        return h;
    }

    // ---- steps ending ---------------------------------------------------------------------------------------

    private void complete(int i) {
        PlayerState p = players[i];
        int k = p.stepKind;
        if (k < 0) return; // (free: nothing under way)
        double ran = now - p.stepStart;
        account(i, ran);
        if (p.stepDies && p.stepDeathAt <= now && p.stepDeathAt <= p.stepEnd) {
            die(i);
            return;
        }
        if (p.stepFails && now < p.stepEnd) {
            // The attempt comes to nothing (no way through, nothing found): dropped, rested.
            partial(i, (now - p.stepBeganAt) / Math.max(1e-6, p.stepEnd - p.stepBeganAt));
            if (log != null) log(i, "fails at " + (p.target >= 0 ? board.goal[p.target].id : "?"));
            dropGoal(i, true);
            return;
        }
        if (p.stepBudget && now < p.stepEnd) {
            // The goal's time allowance is up: more time while it comes on, else given up.
            if (p.stepsSinceCheck > 0 && p.extensions < 2) {
                p.extensions++;
                p.stepsSinceCheck = 0;
                p.goalBudget += p.goalBudget0 / 2;
                schedule(p);
                p.stepStart = now;
                return;
            }
            partial(i, (now - p.stepBeganAt) / Math.max(1e-6, p.stepEnd - p.stepBeganAt));
            giveUp(i);
            return;
        }
        apply(i);
        p.stepsSinceCheck++;
        p.stepKind = -1;
        if (over) return;
        // The goal claimed meanwhile (by a step's side effect, or by the other player): done or dropped.
        if (p.target >= 0 && !board.isOpen(p.target)) {
            finishGoal(i);
            return;
        }
        if (!startStep(i)) {
            if (p.target >= 0 && board.isOpen(p.target)) {
                // (Plan done but the tile not claimed: try again later.)
                dropGoal(i, true);
            } else if (p.target >= 0) finishGoal(i);
        }
    }

    /** What a cut-off step still brought: a share of what was being gathered. */
    private void partial(int i, double share) {
        PlayerState p = players[i];
        if (p.stepKind == Planner.S_RES && share > 0) {
            int got = (int) Math.floor(p.stepAmount * Math.min(1, share));
            p.res[p.stepArg] += got;
            if (got > 0) autoClaims(i);
        }
        p.stepKind = -1;
    }

    /** The step's effects on the player and the board. */
    private void apply(int i) {
        PlayerState p = players[i];
        int a = p.stepArg;
        switch (p.stepKind) {
            case Planner.S_MS -> {
                int[] u = Econ.MS_USES[a];
                if (!(a == Ms.PORTAL.ordinal() && portalLit && !p.has(Ms.PORTAL))) {
                    for (int j = 0; j < u.length; j += 2) p.res[u[j]] = Math.max(0, p.res[u[j]] - u[j + 1]);
                }
                if (a == Ms.PORTAL.ordinal()) portalLit = true;
                p.ms |= 1 << a;
                autoClaims(i);
            }
            case Planner.S_RES -> {
                p.res[a] += p.stepAmount;
                p.access |= 1L << a;
                autoClaims(i);
            }
            case Planner.S_GO -> {
                Place pl = Place.ALL[a];
                if (pl.kind == Place.Kind.LAYER) {
                    int to = pl == Place.CAVE ? 1 : pl == Place.DEEP ? 2 : 0;
                    if (to != p.layer) p.access = 0;
                    p.layer = to;
                    if (pl == Place.WATER) p.access |= Planner.WATER_AT_HAND;
                } else if (pl == Place.NETHER) {
                    p.x = 0;
                    p.z = 0;
                    p.access = 0;
                } else if (!world.exists(pl)) {
                    // Looked for it and it is not there: known missing from now on, and the plan is off.
                    p.missing |= World.bit(pl);
                    if (p.target >= 0) dropGoal(i, true);
                    else p.idle();
                    return;
                } else {
                    p.x = world.x(pl);
                    p.z = world.z(pl);
                    p.layer = pl.layer;
                    p.known |= World.bit(pl);
                    p.access = 0;
                    discover(p);
                }
            }
            case Planner.S_ENTER -> {
                p.dim = 1;
                p.x = 0;
                p.z = 0;
                p.layer = 0;
                p.access = 0;
                p.ms |= Ms.NETHER_VISITED.bit;
                p.known |= netherKnown;
                autoClaims(i);
            }
            case Planner.S_LEAVE -> {
                p.dim = 0;
                p.x = world.x(Place.LAVA_POOL);
                p.z = world.z(Place.LAVA_POOL);
                p.layer = 0;
                p.access = 0;
            }
            case Planner.S_WORK -> {
                Recipe r = GoalBook.all().get(a);
                if (p.target >= 0 && board.goal[p.target] == r && board.isOpen(p.target)) {
                    if (r.autoRes < 0) {
                        for (int j = 0; j < r.uses.length; j += 2) p.res[r.uses[j]] = Math.max(0, p.res[r.uses[j]] - r.uses[j + 1]);
                    }
                    claim(i, p.target);
                }
            }
            default -> {
            }
        }
    }

    /** What comes into sight around a place it walked to: lava pools, structures, biomes nearby. */
    private void discover(PlayerState p) {
        if (p.dim != 0) return;
        for (Place q : Place.ALL) {
            if (q.kind != Place.Kind.SURFACE || !world.exists(q) || (p.known & World.bit(q)) != 0) continue;
            if (Math.hypot(world.x(q) - p.x, world.z(q) - p.z) <= World.SIGHT) p.known |= World.bit(q);
        }
    }

    /** Tiles that a player's progress claims by itself: a milestone made, resources in hand. */
    private void autoClaims(int i) {
        PlayerState p = players[i];
        for (int t = 0; t < Board.SIZE && !over; t++) {
            if (!board.isOpen(t)) continue;
            Recipe r = board.goal[t];
            boolean got = r.auto >= 0 && (p.ms & (1 << r.auto)) != 0
                    || r.autoRes >= 0 && p.res[r.autoRes] >= r.autoN && (r.needs & ~p.ms) == 0;
            if (got) claim(i, t);
        }
    }

    private void claim(int i, int tile) {
        board.claim(tile, i, now);
        PlayerState p = players[i];
        if (p.firstTile < 0) p.firstTile = now;
        if (log != null) log(i, "claims " + board.goal[tile].id + " (" + board.score[0] + ":" + board.score[1] + ")");
        // The other player's goal is gone: it stops at once.
        PlayerState o = players[1 - i];
        if (o.target == tile && o.stepKind >= 0) {
            double share = (now - o.stepBeganAt) / Math.max(1e-6, o.stepEnd - o.stepBeganAt);
            account(1 - i, now - o.stepStart);
            o.stepStart = now;
            partial(1 - i, share);
            o.spent[tile] += now - o.goalStart;
            o.idle();
            o.busyUntil = now;
        }
        int c = board.clinched();
        if (c >= 0) {
            over = true;
            winner = c;
        } else if (board.open == 0) {
            finish();
        }
    }

    private void finishGoal(int i) {
        PlayerState p = players[i];
        if (p.target >= 0) p.spent[p.target] += now - p.goalStart;
        p.idle();
        p.busyUntil = now;
    }

    /** The goal ran over its allowance: dropped, rested, believed dearer next time. */
    private void giveUp(int i) {
        PlayerState p = players[i];
        int t = p.target;
        if (t >= 0) {
            p.overruns[t]++;
            p.overrunEstimate[t] = p.goalEstimate;
            if (log != null) log(i, "gives up " + board.goal[t].id + " after " + Math.round(now - p.goalStart) + " s (est " + Math.round(p.goalEstimate) + ")");
        }
        dropGoal(i, true);
    }

    private void dropGoal(int i, boolean failed) {
        PlayerState p = players[i];
        int t = p.target;
        if (t >= 0) {
            p.spent[t] += now - p.goalStart;
            if (failed) {
                p.tries[t]++;
                p.fails[t]++;
                p.restUntil[t] = now + REST;
            }
        } else if (p.chore >= 0 && failed) {
            p.choreRetryAt = now + 300;
        }
        p.idle();
        p.busyUntil = now;
    }

    private void die(int i) {
        PlayerState p = players[i];
        p.deaths++;
        if (log != null) log(i, "dies (" + (p.target >= 0 ? board.goal[p.target].id : "chore " + p.chore) + ", step " + p.stepKind + ")");
        int t = p.target;
        if (t >= 0) {
            p.spent[t] += now - p.goalStart;
            p.tries[t]++;
            p.fails[t]++;
            p.restUntil[t] = now + 2 * REST;
        }
        Profile prof = profiles[i];
        double back = prof.respawn;
        boolean recovered = rng.nextDouble() < prof.recover;
        double dist = p.dim == 0 ? Math.hypot(p.x, p.z) : Math.hypot(world.x(Place.LAVA_POOL), world.z(Place.LAVA_POOL)) + Math.hypot(p.x, p.z);
        if (recovered) back += 2 * dist / prof.speed + 20 + prof.layers(0, p.layer) + prof.layers(p.layer, 0);
        else {
            p.ms &= Ms.KEPT;
            java.util.Arrays.fill(p.res, 0);
        }
        p.x = 0;
        p.z = 0;
        p.layer = 0;
        p.dim = 0;
        p.access = 0;
        p.idle();
        p.chore = A_DEAD;
        appendWait(p, back);
        startStep(i);
    }

    /** Food eaten as the time goes by (one unit per {@link Profile#foodEvery} seconds). */
    private void eat(int i) {
        PlayerState p = players[i];
        double every = profiles[i].foodEvery;
        if (every <= 0) return;
        int units = (int) ((now - p.fedAt) / every);
        if (units <= 0) return;
        int f = Res.FOOD.ordinal();
        p.res[f] = Math.max(0, p.res[f] - units);
        p.fedAt += units * every;
    }

    private void account(int i, double seconds) {
        PlayerState p = players[i];
        int a = p.target >= 0 ? (p.quick ? A_QUICK : A_GOAL) : p.chore >= 0 ? p.chore : A_IDLE;
        p.time[a] += Math.max(0, seconds);
    }

    // ---- what the brains look at --------------------------------------------------------------------------------

    /**
     * The shopping list: per resource, what the open tiles want in all, as Bob's strategist sums it
     * (a goal's own and its missing milestones'), so one trip brings it all. Worked out again when the
     * board changes.
     */
    public int[] demand(int i) {
        long key = ((long) players[i].ms << 16) | (board.score[0] << 8) | board.score[1];
        if (key == demandKey[i]) return demand[i];
        int[] d = demand[i];
        java.util.Arrays.fill(d, 0);
        PlayerState p = players[i];
        for (int t = 0; t < Board.SIZE; t++) {
            if (!board.isOpen(t)) continue;
            Recipe r = board.goal[t];
            for (int j = 0; j < r.uses.length; j += 2) d[r.uses[j]] += r.uses[j + 1];
            int missingMs = 0;
            for (int m = r.needs; m != 0; m &= m - 1) {
                int b = Integer.numberOfTrailingZeros(m);
                missingMs |= (1 << b) | Econ.MS_CLOSURE[b];
            }
            missingMs &= ~p.ms;
            for (int m = missingMs; m != 0; m &= m - 1) {
                int[] u = Econ.MS_USES[Integer.numberOfTrailingZeros(m)];
                for (int j = 0; j < u.length; j += 2) d[u[j]] += u[j + 1];
            }
        }
        demandKey[i] = key;
        return d;
    }

    /** Also every step in the log. */
    public boolean logSteps;

    static String describe(int k, int a, int amt) {
        return switch (k) {
            case Planner.S_MS -> "make " + Ms.ALL[a];
            case Planner.S_RES -> "gather " + amt + " " + Res.ALL[a];
            case Planner.S_GO -> "go " + Place.ALL[a];
            case Planner.S_ENTER -> "enter the Nether";
            case Planner.S_LEAVE -> "leave the Nether";
            case Planner.S_WORK -> "work on " + GoalBook.all().get(a).id;
            case Planner.S_SEARCH -> "look for " + GoalBook.all().get(a).id;
            case Planner.S_WAIT -> "wait " + amt;
            default -> "step " + k;
        };
    }

    void log(int i, String what) {
        log.append(String.format("%6.0f  P%d %s%n", now, i, what));
    }

    /** The tiles of player {@code i} so far. */
    public int score(int i) {
        return board.score[i];
    }
}
