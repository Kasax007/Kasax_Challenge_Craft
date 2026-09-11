package net.kasax.challengecraft.util;

import net.kasax.challengecraft.challenges.Chal_37_GameSpeed;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.server.MinecraftServer;

/** Converts the world's run clock into the display timer used by challenge HUDs and rewards. */
public final class ChallengeTimeUtil {
    private ChallengeTimeUtil() {
    }

    /**
     * How long this world's run has lasted, in display ticks — the same value for every player.
     *
     * <p>This used to be {@code getDisplayPlayTicks(ServerPlayer)}, reading that one player's
     * {@code Stats.PLAY_TIME}. Three things were wrong with that, and they are the reason this is
     * now keyed on the server rather than a player: two players in the same world saw two different
     * timers; the clock stopped for anyone who logged out even though the world carried on; and
     * because a late joiner's statistic starts at zero, inviting a friend just before the dragon
     * died handed them a personal best of a few seconds. The run clock lives on
     * {@link ChallengeSavedData} and is ticked by the server, so it is shared, monotonic, and
     * survives everyone leaving.
     */
    /**
     * mm:ss below an hour, h:mm:ss above it — nobody wants to read "184:07".
     *
     * <p>Lives here rather than on the summary screen so the server-side commands and the client
     * card cannot drift into formatting the same number two different ways.
     */
    public static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0, ticks) / 20L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    public static int getDisplayRunTicks(MinecraftServer server) {
        // Guarded rather than wrapped in a catch-all: the overworld really can be absent while a
        // server is starting or shutting down, and that is not an error worth a stack trace. Any
        // OTHER failure is left to throw, because silently returning 0 would hide it.
        if (server == null || server.overworld() == null) return 0;

        long rawTicks = ChallengeSavedData.get(server.overworld()).getRunTicks();
        if (Chal_37_GameSpeed.isActive()) {
            rawTicks = Math.round(rawTicks / (float) Chal_37_GameSpeed.getMultiplier());
        }
        // The timer is displayed and sent as an int; clamp rather than let it wrap after ~3 years
        // of continuous running.
        return (int) Math.max(0, Math.min(rawTicks, Integer.MAX_VALUE));
    }
}
