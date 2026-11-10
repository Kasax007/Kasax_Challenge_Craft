package net.kasax.challengecraft.bot.lockout.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bob's board brain as it is today ({@code LockoutBrain.choices/route} with
 * {@code LockoutStrategist}), rebuilt on the abstract game as the baseline:
 * <ul>
 * <li>every open tile costed from where he stands (his own figures: {@link Profile#naive()}),
 * believed dearer after overruns and failures (as the cortex does);</li>
 * <li>the strategist's bonus: the iron kit (pickaxe, bucket, furnace) and the diamonds when they
 * pay for themselves on two tiles or more, and the Nether trip shared among the Nether tiles;</li>
 * <li>the route: a beam search (depth 4, width 10) over the cheapest ten, legs costed as odds
 * (the chance to be done before giving up, the chance to die), the best tiles per second wins;</li>
 * <li>the investments that come before the choice: the iron kit when the plan wants it, the
 * Nether phase with two Nether tiles or more and a bucket in hand.</li>
 * </ul>
 * With {@code trueFigures} it costs tiles by what they really take on average instead of by his
 * own figures - the same algorithm with better knowledge, to tell the two apart.
 */
public final class GreedyPolicy implements Policy {
    private static final int ROUTE_CANDIDATES = 10, ROUTE_DEPTH = 4, BEAM = 10;
    private static final double DEATH_SECONDS = 600, MAX_COST = 1800, REPLAN = 120;

    private final boolean trueFigures, route;
    private final double[] bonus = new double[Board.SIZE];
    private boolean wantsIron;
    private double plannedAt = -1e9;
    private int plannedState = -1;

    public GreedyPolicy() {
        this(false, true);
    }

    public GreedyPolicy(boolean trueFigures, boolean route) {
        this.trueFigures = trueFigures;
        this.route = route;
    }

    @Override
    public String name() {
        return (route ? "greedy" : "cheapest") + (trueFigures ? "-true" : "");
    }

    @Override
    public Policy fresh() {
        return new GreedyPolicy(trueFigures, route);
    }

    private Profile figures(Sim sim, int me) {
        return trueFigures ? sim.profiles[me] : sim.naive[me];
    }

    // ---- the strategist ------------------------------------------------------------------------------

    private void strategist(Sim sim, int me) {
        int state = sim.board.score[0] * 64 + sim.board.score[1];
        if (state == plannedState && sim.now - plannedAt < REPLAN) return;
        plannedState = state;
        plannedAt = sim.now;
        Arrays.fill(bonus, 0);
        wantsIron = false;
        PlayerState p = sim.players[me];
        Profile f = figures(sim, me);
        Planner pl = sim.planner;
        double[] now = new double[Board.SIZE];
        int open = 0;
        for (int t = 0; t < Board.SIZE; t++) {
            now[t] = sim.board.isOpen(t) ? Math.min(MAX_COST, pl.estimate(sim, me, sim.board.goal[t], f)) : MAX_COST;
            if (sim.board.isOpen(t)) open++;
        }
        if (open == 0) return;
        // Kits that pay for themselves: the iron kit, the diamonds.
        kit(sim, me, now, Ms.IRON_PICK.bit | Ms.BUCKET.bit | Ms.FURNACE.bit, Res.IRON, true);
        kit(sim, me, now, Ms.DIAMOND_PICK.bit, Res.DIAMOND, false);
        // The Nether trip, shared by the Nether tiles.
        int nether = 0;
        for (int t = 0; t < Board.SIZE; t++) if (sim.board.isOpen(t) && sim.board.nether(t)) nether++;
        if (nether >= 2 && p.dim == 0) {
            double trip = pl.estimate(sim, me, Ms.NETHER_VISITED, f);
            if (trip < Planner.INF / 2) {
                double share = trip * (1 - 1.0 / nether);
                for (int t = 0; t < Board.SIZE; t++) {
                    if (sim.board.isOpen(t) && sim.board.nether(t) && !"DIMENSION".equals(sim.board.type[t])) bonus[t] += share;
                }
            }
            for (int t = 0; t < Board.SIZE; t++) {
                String id = sim.board.goal[t].id;
                if (sim.board.isOpen(t) && (id.equals("enter_nether") || id.equals("advancement_we_need_to_go_deeper"))) bonus[t] += Math.min(400, 60 * nether);
            }
        }
    }

    private void kit(Sim sim, int me, double[] now, int kitMs, Res kitRes, boolean iron) {
        PlayerState p = sim.players[me];
        if ((p.ms & kitMs) == kitMs && p.res[kitRes.ordinal()] > 0) return;
        Profile f = figures(sim, me);
        Planner pl = sim.planner;
        double kitCost = 0;
        for (Ms m : Ms.ALL) if ((kitMs & m.bit) != 0 && !p.has(m)) kitCost += Math.min(MAX_COST, pl.estimate(sim, me, m, f));
        // (The strategist's kit holds an ingot or a diamond as well, each costed on its own.)
        if (p.res[kitRes.ordinal()] == 0) kitCost += Math.min(MAX_COST, pl.estimate(sim, me, kitRes, 1, f));
        int[] extra = new int[Res.N];
        extra[kitRes.ordinal()] = 1;
        double savings = 0;
        int helped = 0;
        for (int t = 0; t < Board.SIZE; t++) {
            if (!sim.board.isOpen(t)) continue;
            double with = Math.min(MAX_COST, pl.estimateWith(sim, me, sim.board.goal[t], f, kitMs, extra));
            if (with < now[t] - 5) {
                savings += now[t] - with;
                helped++;
            }
        }
        if (helped < 2 || savings <= 1.5 * kitCost) return;
        if (iron) wantsIron = true;
        for (int t = 0; t < Board.SIZE; t++) {
            if (!sim.board.isOpen(t)) continue;
            Recipe r = sim.board.goal[t];
            boolean onTheWay = r.auto >= 0 && (kitMs & (1 << r.auto)) != 0 || r.autoRes == kitRes.ordinal()
                    || iron && r.id.equals("obtain_raw_iron_block");
            if (onTheWay) bonus[t] += Math.min(300, savings / 2);
        }
    }

    // ---- investments before the choice -------------------------------------------------------------

    @Override
    public Decision invest(Sim sim, int me) {
        strategist(sim, me);
        PlayerState p = sim.players[me];
        Profile f = figures(sim, me);
        // The Nether phase: a bucket in hand and several Nether tiles open.
        if (p.dim == 0 && sim.now >= p.netherRetryAt && (p.has(Ms.BUCKET) || p.has(Ms.WATER_BUCKET))) {
            int nether = 0;
            for (int t = 0; t < Board.SIZE; t++) if (sim.board.isOpen(t) && sim.board.nether(t)) nether++;
            if (nether >= 2) {
                double trip = sim.planner.estimate(sim, me, Ms.NETHER_VISITED, f);
                if (trip < 120 * nether) {
                    // (Each failed try waits longer for the next: 3, 6, 12 minutes...)
                    p.netherRetryAt = sim.now + (180L << Math.min(3, p.netherFails++));
                    return Decision.invest(Ms.NETHER_VISITED, trip, "Nether phase: " + nether + " tiles there");
                }
            }
        }
        // The iron kit the plan wants.
        if (wantsIron && sim.now >= p.kitRetryAt && p.dim == 0 && p.kitTries < 4) {
            for (Ms m : new Ms[]{Ms.IRON_PICK, Ms.BUCKET}) {
                if (p.has(m) || m == Ms.BUCKET && p.has(Ms.WATER_BUCKET)) continue;
                double est = sim.planner.estimate(sim, me, m, f);
                // (Dear, and quick tiles open early in the day: later - the real brain's investLater.)
                if (sim.investLater(me, est)) {
                    p.kitRetryAt = sim.now + 30;
                    return null;
                }
                p.kitRetryAt = sim.now + (60L << Math.min(3, p.kitTries++));
                return Decision.invest(m, est, "the plan wants the iron kit");
            }
        }
        return null;
    }

    // ---- the choice -------------------------------------------------------------------------------------

    /** A tile it could go for: its cost (believed), the plain figure, the luck in it, where the work is. */
    private record Choice(int tile, double cost, double plain, double luck, double x, double z, int dim) {
    }

    private List<Choice> choices(Sim sim, int me, boolean last) {
        PlayerState p = sim.players[me];
        Profile f = figures(sim, me);
        Planner pl = sim.planner;
        List<Choice> out = new ArrayList<>();
        for (int t = 0; t < Board.SIZE; t++) {
            if (!sim.usable(me, t)) continue;
            if (!last && p.restUntil[t] > sim.now) continue;
            if (last && p.tries[t] > Sim.MAX_TRIES) continue;
            double plain = pl.estimate(sim, me, sim.board.goal[t], f);
            if (plain >= Planner.INF / 2 || !last && plain > MAX_COST) continue;
            double luck = pl.lastLuck();
            double c = plain;
            int over = p.overruns[t];
            if (over > 0 && c < 0.7 * p.overrunEstimate[t]) over = 0;
            if (over > 0) c = c * (1 + 0.5 * over) + 20 * over;
            // (The cortex: what this game has shown of it.)
            if (p.starts[t] > 0) c = Math.max(c * (1 + p.fails[t]), p.spent[t] / p.starts[t]);
            out.add(new Choice(t, c, plain, luck, pl.lastX(), pl.lastZ(), pl.lastDim()));
        }
        return out;
    }

    @Override
    public Decision choose(Sim sim, int me) {
        strategist(sim, me);
        List<Choice> cs = choices(sim, me, false);
        if (cs.isEmpty()) cs = choices(sim, me, true);
        if (cs.isEmpty()) return null;
        cs.sort((a, b) -> Double.compare(a.cost - bonus[a.tile], b.cost - bonus[b.tile]));
        Choice pick = route ? route(sim, me, cs) : cs.get(0);
        return Decision.tile(pick.tile, pick.cost, bonus[pick.tile] > 0 ? "bonus " + Math.round(bonus[pick.tile]) : null);
    }

    // ---- the route (LockoutBrain.route) -----------------------------------------------------------------

    private record Odds(double mean, double luck, double hazard) {
        double within(double t) {
            double sure = mean - luck;
            if (t < sure) return 0;
            if (luck < 1) return 1;
            return 1 - Math.exp(-(t - sure) / luck);
        }

        double spent(double t) {
            double sure = mean - luck;
            if (t <= sure) return t;
            return luck < 1 ? sure : sure + luck * within(t);
        }

        double death(double t) {
            return 1 - Math.exp(-hazard * t / 60);
        }
    }

    private record Route(int[] legs, int length, double x, double z, double tiles, double seconds) {
        double rate() {
            return seconds <= 0 ? 0 : tiles / seconds;
        }

        boolean has(int c) {
            for (int i = 0; i < length; i++) if (legs[i] == c) return true;
            return false;
        }
    }

    private Choice route(Sim sim, int me, List<Choice> sorted) {
        List<Choice> cand = sorted.subList(0, Math.min(ROUTE_CANDIDATES, sorted.size()));
        int n = cand.size();
        PlayerState p = sim.players[me];
        Profile f = figures(sim, me);
        double hz = hereHazard(sim, me);
        Odds[] odds = new Odds[n];
        for (int i = 0; i < n; i++) {
            Choice c = cand.get(i);
            double luck = Math.min(c.cost, Math.min(c.plain, c.luck) + Math.max(0, c.cost - c.plain));
            Recipe r = sim.board.goal[c.tile];
            double h = hz * (r.monster ? 1.5 : 1) + (r.nether() && p.dim == 0 ? 0.03 : 0) + r.hazard * 0.5;
            odds[i] = new Odds(c.cost, luck, h);
        }
        // What the first few leave in hand for the rest.
        double[][] after = new double[n][];
        int[] yMs = new int[1];
        int[] yRes = new int[Res.N];
        for (int i = 0; i < Math.min(4, n); i++) {
            sim.planner.yields(sim, me, sim.board.goal[cand.get(i).tile], f, yMs, yRes);
            if (yMs[0] == 0 && Arrays.stream(yRes).allMatch(v -> v == 0)) continue;
            after[i] = new double[n];
            for (int j = 0; j < n; j++) {
                after[i][j] = j == i ? Planner.INF : sim.planner.estimateWith(sim, me, sim.board.goal[cand.get(j).tile], f, yMs[0], yRes);
            }
        }
        List<Route> beam = List.of(new Route(new int[ROUTE_DEPTH], 0, p.x, p.z, 0, 0));
        Route best = null;
        for (int depth = 0; depth < ROUTE_DEPTH; depth++) {
            List<Route> next = new ArrayList<>();
            for (Route r : beam) {
                for (int i = 0; i < n; i++) {
                    if (r.has(i)) continue;
                    Choice c = cand.get(i);
                    Odds o = odds[i];
                    double mean = o.mean, luck = o.luck;
                    if (r.length > 0 && after[r.legs[0]] != null) {
                        double a = after[r.legs[0]][i];
                        if (a < mean) {
                            luck *= a / mean;
                            mean = a;
                        }
                    }
                    double work = Math.max(0.3 * mean, mean - walk(p.x, p.z, c.x, c.z, f));
                    double walk = walk(r.x, r.z, c.x, c.z, f);
                    Odds leg = new Odds(work + walk, Math.min(luck, work), o.hazard);
                    double horizon = Sim.budget(leg.mean);
                    double secs = leg.spent(horizon);
                    double dies = leg.death(secs);
                    double tiles = leg.within(horizon) * (1 - dies);
                    secs = Math.max(0.3 * secs, secs + dies * DEATH_SECONDS - bonus[c.tile]);
                    int[] legs = r.legs.clone();
                    legs[r.length] = i;
                    Route nr = new Route(legs, r.length + 1, c.x, c.z, r.tiles + tiles, r.seconds + secs);
                    next.add(nr);
                    if (best == null || nr.rate() > best.rate()) best = nr;
                }
            }
            next.sort((a, b) -> Double.compare(b.rate(), a.rate()));
            beam = next.subList(0, Math.min(BEAM, next.size()));
        }
        return best == null ? sorted.get(0) : cand.get(best.legs[0]);
    }

    private static double walk(double ax, double az, double bx, double bz, Profile f) {
        return Math.hypot(bx - ax, bz - az) / f.speed;
    }

    /** Deaths per minute where it is now (LockoutBrain.hazard, roughly). */
    private static double hereHazard(Sim sim, int me) {
        PlayerState p = sim.players[me];
        double h = 0.004;
        if (p.dim == 1) h += 0.03;
        else if (Planner.night(sim.now) && p.layer == 0) h += Sim.geared(p) ? 0.015 : 0.03;
        if (p.layer >= 1) h += 0.02;
        if (Sim.geared(p) || p.has(Ms.COPPER_ARMOR)) h *= 0.75;
        return h;
    }
}
