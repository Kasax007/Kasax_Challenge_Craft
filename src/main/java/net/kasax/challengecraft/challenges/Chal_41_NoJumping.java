package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.chat.Component;

/** Flag holder for the mixin-driven jump cancellation; NoJumpingMixin does the actual work. */
public class Chal_41_NoJumping {
    public static final Component BLOCKED_MESSAGE = Component.translatable("challengecraft.worldcreate.challenge41.blocked");
    private static boolean active = false;

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal41] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }
}
