package net.kasax.challengecraft.util;

import net.kasax.challengecraft.challenges.Chal_37_GameSpeed;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/** Converts vanilla playtime into the display timer used by challenge HUDs and rewards. */
public final class ChallengeTimeUtil {
    private ChallengeTimeUtil() {
    }

    public static int getDisplayPlayTicks(ServerPlayer player) {
        int rawTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        if (!Chal_37_GameSpeed.isActive()) {
            return rawTicks;
        }

        return Math.max(0, Math.round(rawTicks / (float)Chal_37_GameSpeed.getMultiplier()));
    }
}
