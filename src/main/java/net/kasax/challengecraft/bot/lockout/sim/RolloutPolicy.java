package net.kasax.challengecraft.bot.lockout.sim;

/**
 * The quick policy the look-ahead plays the rest of a game with: the open tile that costs least
 * from where the player stands, by its true average ({@code trueFigures}, what Bob should know) or
 * by its own figures (what Bob's estimates say - how the opponent Bob plays today, roughly).
 * Resting tiles wait their turn, as in the real brain.
 */
public final class RolloutPolicy implements Policy {
    private final boolean trueFigures;

    public RolloutPolicy(boolean trueFigures) {
        this.trueFigures = trueFigures;
    }

    @Override
    public String name() {
        return "cheapest" + (trueFigures ? "-true" : "");
    }

    @Override
    public Policy fresh() {
        return this;
    }

    @Override
    public Decision choose(Sim sim, int me) {
        Profile f = trueFigures ? sim.profiles[me] : sim.naive[me];
        PlayerState p = sim.players[me];
        int best = -1, rested = -1;
        double bestCost = Planner.INF, restedCost = Planner.INF;
        for (int t = 0; t < Board.SIZE; t++) {
            if (!sim.usable(me, t)) continue;
            double c = sim.planner.estimate(sim, me, sim.board.goal[t], f);
            if (c >= Planner.INF / 2) continue;
            if (p.restUntil[t] > sim.now) {
                if (c < restedCost && p.tries[t] <= Sim.MAX_TRIES) {
                    rested = t;
                    restedCost = c;
                }
                continue;
            }
            if (c < bestCost) {
                best = t;
                bestCost = c;
            }
        }
        if (best < 0 && rested >= 0) return Decision.tile(rested, restedCost, "rested");
        return best < 0 ? null : Decision.tile(best, bestCost, null);
    }
}
