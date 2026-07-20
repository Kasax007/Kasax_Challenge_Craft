package net.kasax.challengecraft.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * A single compact HUD panel: an icon tile, a title, a right-aligned value, and a mini progress
 * bar. Rendered uniformly for every objective HUD so they all share one look. Layout is fed by
 * {@link HudStack}; sources build one of these per frame.
 */
public final class HudCard {
    public static final int HEIGHT = 34;

    public final String id;
    public final ItemStack icon;
    public final Text title;
    public final Text value;
    public final float progress;
    public final int accent;
    /** Integer that changes when progress advances; drives the tick flash. */
    public final int progressKey;

    public HudCard(String id, ItemStack icon, Text title, Text value, float progress, int accent, int progressKey) {
        this.id = id;
        this.icon = icon;
        this.title = title;
        this.value = value;
        this.progress = progress;
        this.accent = accent;
        this.progressKey = progressKey;
    }

    // Layout constants: icon column (x+34 text start), and the top line holds the title on the
    // left and the value right-aligned, so the card must be wide enough for BOTH side by side.
    private static final int ICON_COL = 34;
    private static final int TITLE_VALUE_GAP = 8;
    private static final int RIGHT_PAD = 8;
    private static final int MIN_WIDTH = 132;

    /**
     * Natural card width for the given max, sized to fit the full title AND value on one line
     * (not {@code max(title, value)} — they render together). Grows with content up to
     * {@code maxWidth} (a screen-relative cap so the card never overflows).
     */
    public int width(TextRenderer tr, int maxWidth) {
        int titleW = tr.getWidth(title);
        int valueW = tr.getWidth(value);
        int natural = ICON_COL + titleW + TITLE_VALUE_GAP + valueW + RIGHT_PAD;
        return MathHelper.clamp(natural, MIN_WIDTH, Math.max(MIN_WIDTH, maxWidth));
    }

    /** {@code flash} 0..1 briefly brightens the accent when progress advances. {@code w} is the
     *  width HudStack already laid out this card at (so layout and render never disagree). */
    public void render(DrawContext context, TextRenderer tr, int x, int y, float flash, int w) {
        int acc = CraftUI.mix(accent, 0xFFFFFFFF, 0.65f * flash);
        CraftUI.panelFloat(context, x, y, w, HEIGHT, acc);

        CraftUI.iconTileItem(context, icon, x + 7, y + 7, 20, acc);

        int textX = x + ICON_COL;
        int valueW = tr.getWidth(value);
        // Title trims only if it still can't fit (i.e. the card hit the screen-relative cap).
        int titleRoom = w - ICON_COL - TITLE_VALUE_GAP - valueW - RIGHT_PAD;
        String trimmedTitle = CraftUI.trimToWidth(tr, title.getString(), titleRoom);
        context.drawText(tr, Text.of(trimmedTitle), textX, y + 6, CraftUI.TEXT_PRIMARY, false);
        context.drawText(tr, value, x + w - valueW - RIGHT_PAD, y + 6, CraftUI.TEXT_SECONDARY, false);

        int barX = textX;
        int barY = y + 21;
        int barW = w - ICON_COL - 10;
        CraftUI.progressBar(context, barX, barY, barW, 5, progress, accent);
    }
}
