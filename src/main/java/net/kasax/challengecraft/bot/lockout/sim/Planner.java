package net.kasax.challengecraft.bot.lockout.sim;

/**
 * Works out how a player gets a goal from where it stands, the way Bob's ObtainPlanner does it on
 * a copy of his inventory: the milestones still missing (each with what it needs first), the
 * resources short (one trip each, to where they are found), the walk to where the work is, the
 * work. It either sums the expected seconds ({@link #estimate}) or writes the steps down as the
 * player's plan ({@link #plan}). Not thread-safe: one per {@link Sim}.
 *
 * <p>Expected seconds follow a {@link Profile}: {@link Profile#naive()} gives Bob's own figures
 * (every step at a good player's pace), the true profile gives what it really takes on average.
 */
public final class Planner {
    public static final double INF = 1e9;
    /** Step kinds of a plan. */
    static final int S_MS = 0, S_RES = 1, S_GO = 2, S_ENTER = 3, S_LEAVE = 4, S_WORK = 5, S_WAIT = 6,
            /** Looking for the monster of a goal (the fight is its S_WORK). */
            S_SEARCH = 7;
    /** A walk to the river or the lake: about this far. */
    static final double WATER_WALK = 40;
    static final double DAY = 1200, NIGHT_FROM = 650, NIGHT_TO = 1150;

    // ---- the scratch copy of the player ----
    private Sim sim;
    private Profile prof;
    private int ms, layer, dim;
    private final int[] res = new int[Res.N];
    private double x, z, t;
    private long known, missing, access;
    private boolean portalLit;

    // ---- what one call works out ----
    private boolean emit, fail;
    private double total, luck;
    private int[] demand;
    private int n;
    private final int[] kind = new int[64], arg = new int[64], amount = new int[64];

    /** The luck part (searching) of the last estimate, in seconds. */
    public double lastLuck() {
        return luck;
    }

    /** Where the last estimated goal's work is done (x, z, dimension). */
    public double lastX() {
        return x;
    }

    public double lastZ() {
        return z;
    }

    public int lastDim() {
        return dim;
    }

    public static boolean night(double t) {
        double c = t % DAY;
        return c >= NIGHT_FROM && c < NIGHT_TO;
    }

    /** Seconds from {@code t} until the next night begins (0 at night). */
    public static double untilNight(double t) {
        double c = t % DAY;
        if (c >= NIGHT_FROM && c < NIGHT_TO) return 0;
        return c < NIGHT_FROM ? NIGHT_FROM - c : DAY - c + NIGHT_FROM;
    }

    /** Seconds from {@code t} until morning (0 by day). */
    public static double untilMorning(double t) {
        double c = t % DAY;
        return c >= NIGHT_FROM && c < NIGHT_TO ? NIGHT_TO - c : 0;
    }

    /** How much longer monsters take to find here and now: 1 in the dark (night, caves, the Nether). */
    static double monsterFactor(double t, int layer, int dim) {
        if (dim == 1) return 1;
        if (layer >= 1) return 1.5;
        return night(t) ? 1 : Econ.DAY_MONSTERS;
    }

    private void load(Sim s, int player, Profile p, int[] demandOrNull, boolean emitSteps) {
        sim = s;
        prof = p;
        PlayerState ps = s.players[player];
        ms = ps.ms;
        System.arraycopy(ps.res, 0, res, 0, Res.N);
        x = ps.x;
        z = ps.z;
        layer = ps.layer;
        dim = ps.dim;
        known = ps.known;
        missing = ps.missing;
        access = ps.access;
        t = s.now;
        portalLit = s.portalLit;
        emit = emitSteps;
        demand = demandOrNull;
        fail = false;
        total = 0;
        luck = 0;
        n = 0;
    }

    // ---- public entry points ------------------------------------------------------------------------

    /** Expected seconds for {@code player} to get goal {@code r} from where it stands; {@link #INF} if no way. */
    public double estimate(Sim s, int player, Recipe r, Profile p) {
        load(s, player, p, null, false);
        goal(r);
        return fail ? INF : total;
    }

    /** Expected seconds for a milestone (a chore, an investment). */
    public double estimate(Sim s, int player, Ms m, Profile p) {
        load(s, player, p, null, false);
        milestone(m);
        return fail ? INF : total;
    }

    /** A milestone as a chore; the Nether as a trip there (also when it has been there before). */
    private void milestone(Ms m) {
        if (m == Ms.NETHER_VISITED) go(Place.NETHER);
        else ensureMs(m.ordinal(), 0);
    }

    /** Expected seconds to have {@code count} of a resource in hand. */
    public double estimate(Sim s, int player, Res r, int count, Profile p) {
        load(s, player, p, null, false);
        ensureRes(r.ordinal(), count, false, 0);
        return fail ? INF : total;
    }

    /** Expected seconds for a goal as if the player also held these milestones and resources (for "what it leaves in hand"). */
    public double estimateWith(Sim s, int player, Recipe r, Profile p, int extraMs, int[] extraRes) {
        load(s, player, p, null, false);
        ms |= extraMs;
        if (extraRes != null) for (int i = 0; i < Res.N; i++) res[i] += extraRes[i];
        goal(r);
        return fail ? INF : total;
    }

    /** What getting this goal leaves in hand (the milestones made on the way, resources over): into the arrays. */
    public void yields(Sim s, int player, Recipe r, Profile p, int[] msOut, int[] resOut) {
        load(s, player, p, null, false);
        int before = ms;
        int[] had = s.players[player].res;
        goal(r);
        msOut[0] = ms & ~before;
        for (int i = 0; i < Res.N; i++) resOut[i] = Math.max(0, res[i] - had[i]);
    }

    /** Writes the steps to goal {@code r} into the player's plan; false if there is no way. */
    public boolean plan(Sim s, int player, Recipe r, Profile p, int[] demandOrNull) {
        load(s, player, p, demandOrNull, true);
        goal(r);
        return !fail && store(s.players[player]);
    }

    public boolean plan(Sim s, int player, Ms m, Profile p, int[] demandOrNull) {
        load(s, player, p, demandOrNull, true);
        milestone(m);
        return !fail && store(s.players[player]);
    }

    public boolean plan(Sim s, int player, Res r, int count, Profile p, int[] demandOrNull) {
        load(s, player, p, demandOrNull, true);
        ensureRes(r.ordinal(), count, false, 0);
        return !fail && store(s.players[player]);
    }

    private boolean store(PlayerState ps) {
        if (ps.planKind.length < n) {
            ps.planKind = new int[n];
            ps.planArg = new int[n];
            ps.planAmount = new int[n];
        }
        System.arraycopy(kind, 0, ps.planKind, 0, n);
        System.arraycopy(arg, 0, ps.planArg, 0, n);
        System.arraycopy(amount, 0, ps.planAmount, 0, n);
        ps.planLength = n;
        ps.cursor = 0;
        return true;
    }

    private void step(int k, int a, int amt, double seconds, double luckPart) {
        total += seconds;
        luck += luckPart;
        t += seconds;
        if (!emit) return;
        if (n >= kind.length) {
            fail = true;
            return;
        }
        kind[n] = k;
        arg[n] = a;
        amount[n] = amt;
        n++;
    }

    // ---- the recursion -------------------------------------------------------------------------------

    private void goal(Recipe r) {
        if (r.impossible) {
            fail = true;
            return;
        }
        if (r.notBefore > 0 && t < r.notBefore) {
            fail = true;
            return;
        }
        for (int m = r.needs; m != 0; m &= m - 1) ensureMs(Integer.numberOfTrailingZeros(m), 0);
        int[] u = r.uses;
        boolean keep = r.autoRes >= 0;
        for (int i = 0; i < u.length && !fail; i += 2) ensureRes(u[i], u[i + 1], !keep, 0);
        if (fail) return;
        // (Crafting is done wherever it stands; gathering on the surface; nothing to do: no walk at all.)
        Place where = r.place != null ? r.place : r.work <= 0 || r.kind == Econ.Work.CRAFT || r.kind == Econ.Work.TRADE ? null : Place.HERE;
        if (r.monster && where == Place.HERE) {
            // (The Overworld's monsters: wherever it is up there, in the dark.)
            if (dim == 1) go(Place.HERE);
            where = null;
        }
        go(where);
        if (fail) return;
        if (r.nightOnly && !night(t)) step(S_WAIT, 0, (int) Math.ceil(untilNight(t)), untilNight(t), 0);
        double pace = prof.pace(r.kind);
        if (r.monster) {
            // The search (in the dark it is quicker), then the fight: two steps, so that finding it
            // counts as coming on (a time allowance is extended for that).
            double search = r.work * r.luck * pace * monsterFactor(t, layer, dim);
            step(S_SEARCH, r.index, 0, search, search);
            step(S_WORK, r.index, 0, r.work * (1 - r.luck) * pace, 0);
            return;
        }
        double secs = r.work * pace;
        step(S_WORK, r.index, 0, secs, secs * r.luck);
    }

    private void ensureMs(int m, int depth) {
        if (fail || (ms & (1 << m)) != 0) return;
        if (depth > 10) {
            fail = true;
            return;
        }
        if (m == Ms.NETHER_VISITED.ordinal()) {
            go(Place.NETHER);
            return;
        }
        if (m == Ms.PORTAL.ordinal() && portalLit) {
            // (A portal stands already, cast by the other player: walked to and used.)
            go(Place.LAVA_POOL);
            ms |= 1 << m;
            step(S_MS, m, 0, 2, 0);
            return;
        }
        for (int need = Econ.MS_NEEDS[m]; need != 0; need &= need - 1) ensureMs(Integer.numberOfTrailingZeros(need), depth + 1);
        int[] u = Econ.MS_USES[m];
        for (int i = 0; i < u.length && !fail; i += 2) ensureRes(u[i], u[i + 1], true, depth + 1);
        if (fail) return;
        Econ.Work w = Econ.MS_WORK[m];
        Place where = Econ.MS_PLACE[m];
        if (!(w == Econ.Work.CRAFT && where == Place.HERE)) go(where);
        if (fail) return;
        ms |= 1 << m;
        if (m == Ms.PORTAL.ordinal()) portalLit = true;
        step(S_MS, m, 0, Econ.MS_TIME[m] * prof.pace(w), 0);
    }

    private void ensureRes(int r, int k, boolean consume, int depth) {
        if (fail) return;
        int have = res[r];
        if (have >= k) {
            if (consume) res[r] -= k;
            return;
        }
        int shortBy = k - have;
        for (int need = Econ.RES_NEEDS[r]; need != 0; need &= need - 1) ensureMs(Integer.numberOfTrailingZeros(need), depth + 1);
        if (fail) return;
        Place where = Econ.RES_PLACE[r];
        // (Monsters' drops: wherever it is, as long as it is the Overworld; in the dark.)
        if (Econ.RES_MONSTER[r] && where == Place.HERE) {
            if (dim == 1) go(Place.HERE);
        } else go(where);
        if (fail) return;
        int count = shortBy;
        // (Gathering for real: what the board wants later too, as far as the vein or the flock goes.)
        if (emit && demand != null) count += Math.max(0, Math.min(Econ.RES_TRIP[r], demand[r] - have - shortBy));
        double mf = Econ.RES_MONSTER[r] ? monsterFactor(t, layer, dim) : 1;
        double find = (access & (1L << r)) != 0 ? 0 : Econ.RES_ACCESS[r] * prof.accessPace * mf;
        double each = Econ.RES_PER[r] * prof.perPace * prof.pace(Econ.RES_WORK[r]) * mf;
        access |= 1L << r;
        res[r] = have + count;
        if (consume) res[r] -= k;
        step(S_RES, r, count, find + count * each, find);
    }

    /** Walks to a place (through the portal if it is in the other dimension), or fails if it is known to be missing. */
    private void go(Place p) {
        if (p == null || fail) return;
        if ((missing & World.bit(p)) != 0) {
            fail = true;
            return;
        }
        if (p.kind == Place.Kind.LAYER) {
            if (dim == 1) leave();
            if (fail) return;
            int target = p == Place.CAVE ? 1 : p == Place.DEEP ? 2 : 0;
            double secs = prof.layers(layer, target);
            if (p == Place.WATER && (access & WATER_AT_HAND) == 0) secs += WATER_WALK / prof.speed;
            if (secs > 0 || p == Place.WATER) step(S_GO, p.ordinal(), 0, secs, 0);
            layer = target;
            if (p == Place.WATER) access |= WATER_AT_HAND;
            else if (secs > 0) access = 0;
            return;
        }
        if (p == Place.NETHER) {
            if (dim == 0) enter();
            return;
        }
        if (p.dim == 1 && dim == 0) enter();
        else if (p.dim == 0 && dim == 1) leave();
        if (fail) return;
        double px, pz;
        boolean knows = (known & World.bit(p)) != 0;
        if (!knows && prof.seenOnly && p == Place.LAVA_POOL) {
            fail = true;
            return;
        }
        if (knows) {
            px = sim.world.x(p);
            pz = sim.world.z(p);
        } else {
            // Not known: somewhere about its usual distance from here, looked for on the way (longer).
            px = x + p.guess();
            pz = z;
        }
        double dist = Math.hypot(px - x, pz - z);
        double speed = p.dim == 1 ? prof.netherSpeed : prof.speed;
        double secs = dist / speed * (knows ? 1 : prof.explore) + prof.layers(layer, p.layer);
        if (!knows && p.near < 1) secs /= Math.max(0.15, p.near); // (it may not be there at all)
        x = px;
        z = pz;
        layer = p.layer;
        access = 0;
        step(S_GO, p.ordinal(), 0, secs, knows ? 0 : secs * 0.5);
    }

    /** Bit in {@link #access} for "the water is right here". */
    static final long WATER_AT_HAND = 1L << 62;

    private void enter() {
        ensureMs(Ms.PORTAL.ordinal(), 0);
        if (fail) return;
        if (dim == 0) {
            go(Place.LAVA_POOL);
            if (fail) return;
            step(S_ENTER, 0, 0, 6, 0);
            dim = 1;
            x = 0;
            z = 0;
            layer = 0;
            access = 0;
            ms |= Ms.NETHER_VISITED.bit;
            known |= sim.netherKnown;
        }
    }

    private void leave() {
        if (dim == 0) return;
        double d = Math.hypot(x, z);
        if (d > 1) step(S_GO, Place.NETHER.ordinal(), 0, d / prof.netherSpeed, 0);
        step(S_LEAVE, 0, 0, 6, 0);
        dim = 0;
        x = sim.world.x(Place.LAVA_POOL);
        z = sim.world.z(Place.LAVA_POOL);
        layer = 0;
        access = 0;
    }
}
