package net.kasax.challengecraft.challenges.lockout;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum LockoutBingoTeam {
    RED("red", 0xFFFF5555, Formatting.RED),
    BLUE("blue", 0xFF5555FF, Formatting.BLUE),
    GREEN("green", 0xFF55FF55, Formatting.GREEN),
    YELLOW("yellow", 0xFFFFFF55, Formatting.YELLOW);

    private final String keySuffix;
    private final int color;
    private final Formatting formatting;

    LockoutBingoTeam(String keySuffix, int color, Formatting formatting) {
        this.keySuffix = keySuffix;
        this.color = color;
        this.formatting = formatting;
    }

    public int color() {
        return color;
    }

    public Formatting formatting() {
        return formatting;
    }

    public String translationKey() {
        return "challengecraft.lockout.team." + keySuffix;
    }

    public Text displayName() {
        return Text.translatable(translationKey()).formatted(formatting);
    }

    public static LockoutBingoTeam fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return null;
        }
        return values()[ordinal];
    }
}
