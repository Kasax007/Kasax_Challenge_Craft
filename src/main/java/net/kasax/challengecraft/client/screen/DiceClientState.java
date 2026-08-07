package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.network.DiceSyncPacket;

/**
 * Client cache for the local player's Würfel movement budget.
 *
 * <p>Deliberately a <b>separate</b> store from the server-side map in {@code Chal_46_Dice}: on an
 * integrated (singleplayer) server both logical sides share this JVM and its statics, so letting
 * the network thread write the server's authoritative map would be a genuine data race.
 */
public final class DiceClientState {
    private static volatile float remaining = 0f;
    private static volatile int lastRoll = 0;
    private static volatile boolean rolling = false;

    private DiceClientState() {
    }

    public static void update(DiceSyncPacket packet) {
        remaining = packet.remaining;
        lastRoll = packet.lastRoll;
        rolling = packet.rolling;
    }

    public static void clear() {
        remaining = 0f;
        lastRoll = 0;
        rolling = false;
    }

    public static float getRemaining() {
        return remaining;
    }

    public static int getLastRoll() {
        return lastRoll;
    }

    public static boolean isRolling() {
        return rolling;
    }
}
