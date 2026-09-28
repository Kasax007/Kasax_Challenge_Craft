package net.kasax.challengecraft.casino;

import net.minecraft.util.RandomSource;

/**
 * Plinko with 12 rows of pegs: every row sends the ball left or right with equal chance, so it lands
 * in bucket k (the number of "rights") with probability C(12, k) / 4096. The multipliers are chosen
 * for a return to player of <b>96.0 %</b> (exactly 393220/409600, see {@link #rtp()}):
 * <pre>
 *   30  10  3  2  1.3  0.5  0.3  0.5  1.3  2  3  10  30
 * </pre>
 * Nine of the thirteen buckets pay less than the stake — the middle is where most balls end.
 */
public final class PlinkoMath {
    public static final int ROWS = 12;
    public static final int BUCKETS = ROWS + 1;
    /** Payout per bucket in hundredths of the stake. */
    public static final int[] MULT100 = {3000, 1000, 300, 200, 130, 50, 30, 50, 130, 200, 300, 1000, 3000};

    private PlinkoMath() {
    }

    /** A random path: bit r set means the ball goes right at row r. */
    public static int drop(RandomSource random) {
        return random.nextInt(1 << ROWS);
    }

    public static int bucket(int path) {
        return Integer.bitCount(path & ((1 << ROWS) - 1));
    }

    /** What a ball on this path returns, stake included, in centi-chips. */
    public static long payout(long stake, int path) {
        return stake * MULT100[bucket(path)] / 100;
    }

    public static String label(int bucket) {
        int m = MULT100[bucket];
        return m % 100 == 0 ? Integer.toString(m / 100) : m >= 100 ? String.format(java.util.Locale.ROOT, "%.1f", m / 100.0)
                : String.format(java.util.Locale.ROOT, ".%d", m / 10);
    }

    public static double rtp() {
        double sum = 0;
        for (int k = 0; k <= ROWS; k++) sum += binomial(ROWS, k) / (double) (1 << ROWS) * MULT100[k] / 100.0;
        return sum;
    }

    private static long binomial(int n, int k) {
        long r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }
}
