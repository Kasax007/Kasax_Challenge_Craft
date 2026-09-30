package net.kasax.challengecraft.casino;

import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Admin test hooks ({@code /casino force ...}): the outcome of the next Plinko ball of a player,
 * the next crash point of a pad and the next roulette number, so the win shows of rare outcomes
 * can be seen on demand. Each forced outcome is used once, and only ever by an admin's command.
 */
public final class CasinoTestHooks {
    private static final Map<UUID, Integer> PLINKO_BUCKET = new HashMap<>();
    private static Double crashPoint;
    private static Integer rouletteNumber;

    private CasinoTestHooks() {
    }

    public static void forcePlinko(UUID player, int bucket) {
        PLINKO_BUCKET.put(player, Math.max(0, Math.min(PlinkoMath.ROWS, bucket)));
    }

    public static void forceCrash(double multiplier) {
        crashPoint = Math.max(1.0, Math.min(CrashMath.CAP, multiplier));
    }

    public static void forceRoulette(int number) {
        rouletteNumber = Math.max(0, Math.min(36, number));
    }

    /** A random path into the forced bucket (that many right turns), or a normal drop. */
    static int plinkoPath(UUID player, RandomSource random) {
        Integer bucket = PLINKO_BUCKET.remove(player);
        if (bucket == null) return PlinkoMath.drop(random);
        int path;
        do {
            path = PlinkoMath.drop(random);
        } while (PlinkoMath.bucket(path) != bucket);
        return path;
    }

    static double crashPoint(RandomSource random) {
        Double forced = crashPoint;
        crashPoint = null;
        return forced != null ? forced : CrashMath.crashPoint(random);
    }

    static int rouletteNumber(RandomSource random) {
        Integer forced = rouletteNumber;
        rouletteNumber = null;
        return forced != null ? forced : random.nextInt(37);
    }
}
