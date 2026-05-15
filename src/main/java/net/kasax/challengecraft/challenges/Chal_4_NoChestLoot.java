package net.kasax.challengecraft.challenges;

/** Flag holder for the mixin that blocks deferred chest loot generation. */
public class Chal_4_NoChestLoot {
    private static boolean active = false;
    public static void setActive(boolean on) { active = on; }
    public static boolean isActive() { return active; }
}
