package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;

/** Flag holder for the inventory-weight movement penalty. */
public class Chal_30_HeavyPockets {
    private static boolean active = false;

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal30] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }
}
