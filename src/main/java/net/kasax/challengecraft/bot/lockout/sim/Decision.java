package net.kasax.challengecraft.bot.lockout.sim;

/** What a {@link Policy} decided: a tile to go for, or a milestone to invest in, with the seconds it believes it takes. */
public final class Decision {
    public final int tile;
    public final Ms invest;
    public final double estimate;
    public final String why;

    private Decision(int tile, Ms invest, double estimate, String why) {
        this.tile = tile;
        this.invest = invest;
        this.estimate = estimate;
        this.why = why;
    }

    public static Decision tile(int tile, double estimate, String why) {
        return new Decision(tile, null, estimate, why);
    }

    public static Decision invest(Ms m, double estimate, String why) {
        return new Decision(-1, m, estimate, why);
    }

    @Override
    public String toString() {
        return invest != null ? "invest " + invest + " (~" + Math.round(estimate) + " s, " + why + ")"
                : "tile " + tile + " (~" + Math.round(estimate) + " s" + (why == null ? "" : ", " + why) + ")";
    }
}
