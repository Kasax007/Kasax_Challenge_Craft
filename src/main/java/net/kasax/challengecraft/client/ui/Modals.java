package net.kasax.challengecraft.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Shared modal-dialog chrome so the confirm and status screens all read as one family:
 * a framed panel with a top-centred icon tile, a centred title, and centred body text.
 */
public final class Modals {
    private Modals() {
    }

    public static final int WIDTH = 300;

    private static final int TOP_PAD = 12;
    private static final int ICON_SIZE = 24;
    private static final int ICON_TITLE_GAP = 8;

    public enum Icon {WARNING, INFO, SPINNER}

    /** Y coordinate where centred body text should begin, given the panel top. */
    public static int bodyTop(int panelY, TextRenderer tr) {
        return panelY + TOP_PAD + ICON_SIZE + ICON_TITLE_GAP + tr.fontHeight + 10;
    }

    /** Draws the framed panel plus its icon and centred title. */
    public static void header(DrawContext context, TextRenderer tr, Text title, Icon icon,
                              int panelX, int panelY, int panelHeight, int accent) {
        CraftUI.frame(context, panelX, panelY, WIDTH, panelHeight);

        int tileX = panelX + WIDTH / 2 - ICON_SIZE / 2;
        int tileY = panelY + TOP_PAD;
        int cx = tileX + ICON_SIZE / 2;
        int cy = tileY + ICON_SIZE / 2;
        CraftUI.iconTile(context, tileX, tileY, ICON_SIZE, accent);
        switch (icon) {
            case WARNING -> CraftUI.drawCenteredScaled(context, tr, Text.of("!"), cx, cy + 1, 1.7f, CraftUI.DANGER);
            case INFO -> CraftUI.gem(context, cx, cy, accent);
            case SPINNER -> Anim.spinner(context, cx, cy, 7, accent);
        }

        int titleY = tileY + ICON_SIZE + ICON_TITLE_GAP;
        context.drawCenteredTextWithShadow(tr, title, panelX + WIDTH / 2, titleY, CraftUI.TEXT_PRIMARY);
    }

    /** Draws a stack of centred body lines and returns the Y past the last line. */
    public static int bodyLines(DrawContext context, TextRenderer tr, int panelX, int y, List<Text> lines, int color) {
        for (Text line : lines) {
            for (OrderedText wrapped : tr.wrapLines(line, WIDTH - 40)) {
                context.drawCenteredTextWithShadow(tr, wrapped, panelX + WIDTH / 2, y, color);
                y += tr.fontHeight + 2;
            }
        }
        return y;
    }
}
