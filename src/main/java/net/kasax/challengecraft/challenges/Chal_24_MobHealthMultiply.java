package net.kasax.challengecraft.challenges;

import net.minecraft.util.Mth;

/** Holds the configured health multiplier consumed by mob-spawn hooks. */
public class Chal_24_MobHealthMultiply {
    private static boolean active = false;
    private static int multiplier = 1;

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void setMultiplier(int mult) {
        multiplier = Mth.clamp(mult, 1, 100);
    }

    public static int getMultiplier() {
        return multiplier;
    }
}
