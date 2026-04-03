package net.kasax.challengecraft.util;

import net.kasax.challengecraft.challenges.Chal_37_GameSpeed;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;

public final class ChallengeTimeUtil {
    private ChallengeTimeUtil() {
    }

    public static int getDisplayPlayTicks(ServerPlayerEntity player) {
        int rawTicks = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
        if (!Chal_37_GameSpeed.isActive()) {
            return rawTicks;
        }

        return Math.max(0, Math.round(rawTicks / (float)Chal_37_GameSpeed.getMultiplier()));
    }
}
