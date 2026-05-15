package net.kasax.challengecraft.challenges;

/** Flag holder for the mixin that suppresses block drops. */
public class Chal_2_NoBlockDrops {
    private static boolean active = false;
    public static void setActive(boolean on) { active = on; }
    public static boolean isActive() { return active; }
}
