package net.kasax.challengecraft.challenges;

/** Flag holder for hardcore behavior enforced elsewhere in the mod. */
public class Chal_21_Hardcore {
    private static boolean active = false;

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }
}
