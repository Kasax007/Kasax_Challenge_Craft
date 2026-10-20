package net.kasax.challengecraft.film;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The mouse pointer of the menu shots. The operating system draws the real one, so it never lands
 * in a screenshot; the film draws its own on top of every screen while {@link #shown} is set: a
 * pixel arrow at the (virtual) cursor position and a gold ring that spreads from its tip when a
 * click lands.
 */
public final class FilmCursor {
    public static volatile boolean shown;
    /** Game ticks the film has run (the click ring ages by it). */
    public static volatile int ticks;
    private static volatile int clickedAt = Integer.MIN_VALUE / 2;

    private static final String[] ARROW = {
            "X",
            "XX",
            "XWX",
            "XWWX",
            "XWWWX",
            "XWWWWX",
            "XWWWWWX",
            "XWWWWWWX",
            "XWWWWWWWX",
            "XWWWWWWWWX",
            "XWWWWWXXXXX",
            "XWWXWWX",
            "XWX XWWX",
            "XX  XWWX",
            "X    XWWX",
            "     XWWX",
            "      XX",
    };

    private FilmCursor() {
    }

    public static void install() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                ScreenEvents.afterExtract(screen).register((sc, ctx, mouseX, mouseY, delta) -> {
                    if (shown) draw(ctx, mouseX, mouseY);
                }));
    }

    public static void click() {
        clickedAt = ticks;
    }

    private static void draw(GuiGraphicsExtractor ctx, int mx, int my) {
        int age = ticks - clickedAt;
        if (age >= 0 && age < 10) {
            int r = 3 + age * 2;
            int alpha = 0xFF - age * 22;
            ctx.outline(mx - r, my - r, 2 * r + 1, 2 * r + 1, (alpha << 24) | 0xFFD24A);
            ctx.outline(mx - r + 1, my - r + 1, 2 * r - 1, 2 * r - 1, (alpha << 24) | 0xFFD24A);
        }
        for (int row = 0; row < ARROW.length; row++) {
            String line = ARROW[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == ' ') continue;
                int color = c == 'X' ? 0xFF101010 : 0xFFFFFFFF;
                ctx.fill(mx + col, my + row, mx + col + 1, my + row + 1, color);
            }
        }
    }
}
