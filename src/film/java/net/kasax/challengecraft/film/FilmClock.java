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
    /** The same for the client's own tick (see {@code ClientLockstepMixin}). */
    private static final AtomicInteger CLIENT_PERMITS = new AtomicInteger();
    /** Server ticks that ran / were held back while gated (for the log). */
    public static final AtomicInteger RAN = new AtomicInteger(), HELD = new AtomicInteger();

    /** The film's clock in milliseconds, or -1 for the real one (see {@code UiClockMixin}). */
    private static volatile long filmMillis = -1;

    private FilmClock() {
    }

    /**
     * The time the mod's own UI animations run on (banners, the blackjack table, timers). A frame
     * takes far longer to draw than its 1/30 s, so on the real clock a 4.5 s banner was gone after
     * six frames; while a world is filmed they run on game time instead.
     */
    public static long millis() {
        long t = filmMillis;
        return t < 0 ? System.currentTimeMillis() : t;
    }

    /** Whether the film's clock is on (see {@link #setMillis(long)}). */
    public static boolean isSet() {
        return filmMillis >= 0;
    }

    /** Sets the film's clock; -1 hands back to the real one. */
    public static void setMillis(long t) {
        filmMillis = t;
    }

    public static void gate(boolean on) {
        PERMITS.set(0);
        CLIENT_PERMITS.set(0);
        gated = on;
    }

    public static boolean gated() {
        return gated;
    }

    /** One more server tick may run. */
    public static void allow() {
        if (gated) {
            PERMITS.incrementAndGet();
            CLIENT_PERMITS.incrementAndGet();
        }
    }

    /**
     * Client thread, at the start of a client tick: may it run? Every screenshot the game test takes
     * costs the client a tick or two of its own (it renders a frame, and a frame brings a tick), so
     * a sprinting player ran four times as fast as the server and the film's clock; gated, only the
     * ticks the director asks for ({@link #allow()}) are run.
     */
    public static boolean mayTickClient() {
        if (!gated) return true;
        return CLIENT_PERMITS.getAndUpdate(p -> p > 0 ? p - 1 : 0) > 0;
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
