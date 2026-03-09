package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;

public class Chal_35_DoubleTrouble {
    private static boolean active = false;
    private static int multiplier = 2;

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal35] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }

    public static int getMultiplier() {
        return multiplier;
    }

    public static void setMultiplier(int mult) {
        multiplier = mult;
    }
}
