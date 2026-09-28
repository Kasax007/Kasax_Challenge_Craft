package net.kasax.challengecraft.casino;

import net.minecraft.util.RandomSource;

/**
 * The crash curve without a house edge. The crash point is {@code M = 1 / U} with U uniform in
 * (0, 1], so {@code P(M ≥ x) = 1 / x}: whoever cashes out at x wins with probability 1/x and gets
 * x times the stake, an expected return of exactly 100 % for every possible strategy.
 *
 * <p>The multiplier grows as {@code e^(0.07 · seconds)} (2× after ~10 s, 10× after ~33 s). At
 * {@link #CAP} the rocket reaches space and everybody still aboard is paid the cap; that keeps the
 * return at 100 % for any target up to the cap.
 */
public final class CrashMath {
    public static final double GROWTH_PER_SECOND = 0.07;
    public static final double CAP = 1000.0;

    private CrashMath() {
    }

    /** A fresh crash point, ≥ 1.0 and ≤ {@link #CAP}. */
    public static double crashPoint(RandomSource random) {
        double u = 1.0 - random.nextDouble(); // (0, 1]
        return Math.min(CAP, 1.0 / u);
    }

    public static double multiplierAt(int flightTicks) {
        return Math.min(CAP, Math.exp(GROWTH_PER_SECOND * flightTicks / 20.0));
    }

    /** First flight tick at which the multiplier reaches {@code m}. */
    public static int ticksToReach(double m) {
        if (m <= 1.0) return 0;
        return (int) Math.ceil(Math.log(m) / GROWTH_PER_SECOND * 20.0);
    }
}
