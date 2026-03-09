package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;

public class Chal_34_UpsideDownDrops {
    private static boolean active = false;

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal34] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }
}
