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

    /** A stock of food (a chore the look-ahead may decide on, see {@link Policy#decidesChores}). */
    public static Decision food(double estimate, String why) {
        Decision d = new Decision(-1, null, estimate, why);
        d.food = true;
        return d;
    }

    /** Whether this is the food chore. */
    public boolean food;

    /** A chore rather than a tile (an investment or the food). */
    public boolean chore() {
        return invest != null || food;
    }

    @Override
    public String toString() {
        if (food) return "food (~" + Math.round(estimate) + " s, " + why + ")";
        return invest != null ? "invest " + invest + " (~" + Math.round(estimate) + " s, " + why + ")"
                : "tile " + tile + " (~" + Math.round(estimate) + " s" + (why == null ? "" : ", " + why) + ")";
    }
}
