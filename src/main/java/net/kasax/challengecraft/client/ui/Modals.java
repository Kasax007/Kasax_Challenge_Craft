package net.kasax.challengecraft.client.ui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
    public static int bodyTop(int panelY, Font tr) {
        return panelY + TOP_PAD + ICON_SIZE + ICON_TITLE_GAP + tr.lineHeight + 10;
    }

    /** Draws the framed panel plus its icon and centred title. */
    public static void header(GuiGraphicsExtractor context, Font tr, Component title, Icon icon,
                              int panelX, int panelY, int panelHeight, int accent) {
        CraftUI.frame(context, panelX, panelY, WIDTH, panelHeight);

        int tileX = panelX + WIDTH / 2 - ICON_SIZE / 2;
        int tileY = panelY + TOP_PAD;
        int cx = tileX + ICON_SIZE / 2;
        int cy = tileY + ICON_SIZE / 2;
        CraftUI.iconTile(context, tileX, tileY, ICON_SIZE, accent);
        switch (icon) {
            case WARNING -> CraftUI.drawCenteredScaled(context, tr, Component.nullToEmpty("!"), cx, cy + 1, 1.7f, CraftUI.DANGER);
            case INFO -> CraftUI.gem(context, cx, cy, accent);
            case SPINNER -> Anim.spinner(context, cx, cy, 7, accent);
        }

        int titleY = tileY + ICON_SIZE + ICON_TITLE_GAP;
        context.centeredText(tr, title, panelX + WIDTH / 2, titleY, CraftUI.TEXT_PRIMARY);
    }

    /** Draws a stack of centred body lines and returns the Y past the last line. */
    public static int bodyLines(GuiGraphicsExtractor context, Font tr, int panelX, int y, List<Component> lines, int color) {
        for (Component line : lines) {
            for (FormattedCharSequence wrapped : tr.split(line, WIDTH - 40)) {
                context.centeredText(tr, wrapped, panelX + WIDTH / 2, y, color);
                y += tr.lineHeight + 2;
            }
        }
        return y;
    }
}
