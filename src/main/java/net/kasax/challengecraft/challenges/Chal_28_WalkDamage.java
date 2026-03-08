package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;

/**
 * Chal_28_WalkDamage
 *
 * For every block the player walks, he gets a heart of damage (2.0f damage).
 */
public class Chal_28_WalkDamage {
    private static boolean active = false;

    public static void register() {
        // No tick registration needed as MovementAndDamageMixin handles the logic
    }

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal28] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }
}
