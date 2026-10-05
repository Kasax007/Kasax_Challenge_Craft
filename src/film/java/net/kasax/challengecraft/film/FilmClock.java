package net.kasax.challengecraft.film;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lockstep for the integrated server while a scene is filmed. The client game test steps the
 * client one tick at a time, but the server ran three or four ticks per client tick (a crash
 * rocket burst before the cash-out it was filmed for, plinko balls fell in a third of their time).
 * Gated, a server tick only runs on a permit, and the director hands out one permit per client
 * tick; a tick without one is skipped, never waited for, so the server keeps working off its
 * tasks and the game test's own synchronisation is left alone.
 */
public final class FilmClock {
    private static volatile boolean gated;
    private static final AtomicInteger PERMITS = new AtomicInteger();
    /** Server ticks that ran / were held back while gated (for the log). */
    public static final AtomicInteger RAN = new AtomicInteger(), HELD = new AtomicInteger();

    private FilmClock() {
    }

    public static void gate(boolean on) {
        PERMITS.set(0);
        gated = on;
    }

    public static boolean gated() {
        return gated;
    }

    /** One more server tick may run. */
    public static void allow() {
        if (gated) PERMITS.incrementAndGet();
    }

    /** Server thread, at the start of a tick: may it run? */
    public static boolean mayTick() {
        if (!gated) return true;
        if (PERMITS.getAndUpdate(p -> p > 0 ? p - 1 : 0) > 0) {
            RAN.incrementAndGet();
            return true;
        }
        HELD.incrementAndGet();
        return false;
    }
}
