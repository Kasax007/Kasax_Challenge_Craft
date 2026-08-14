package net.kasax.challengecraft.challenges.lockout;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Fixed team palette and lookup helpers for lockout matches. */
public enum LockoutBingoTeam {
    RED("red", 0xFFFF5555, ChatFormatting.RED),
    BLUE("blue", 0xFF5555FF, ChatFormatting.BLUE),
    GREEN("green", 0xFF55FF55, ChatFormatting.GREEN),
    YELLOW("yellow", 0xFFFFFF55, ChatFormatting.YELLOW);

    private final String keySuffix;
    private final int color;
    private final ChatFormatting formatting;

    LockoutBingoTeam(String keySuffix, int color, ChatFormatting formatting) {
        this.keySuffix = keySuffix;
        this.color = color;
        this.formatting = formatting;
    }

    public int color() {
        return color;
    }

    public ChatFormatting formatting() {
        return formatting;
    }

    public String translationKey() {
        return "challengecraft.lockout.team." + keySuffix;
    }

    public Component displayName() {
        return Component.translatable(translationKey()).withStyle(formatting);
    }

    public static LockoutBingoTeam fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return null;
        }
        return values()[ordinal];
    }
}
