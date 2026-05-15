package net.kasax.challengecraft.challenges;

/** Flag holder for the mixin that suppresses mob drops. */
public class Chal_3_NoMobDrops {
    private static boolean active = false;
    public static void setActive(boolean on) { active = on; }
    public static boolean isActive() { return active; }
}
