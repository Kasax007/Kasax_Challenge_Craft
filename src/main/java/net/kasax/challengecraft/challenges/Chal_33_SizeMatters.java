package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;

/** Flag holder for size scaling applied by mixins. */
public class Chal_33_SizeMatters {
    private static boolean active = false;

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal33] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }
}
