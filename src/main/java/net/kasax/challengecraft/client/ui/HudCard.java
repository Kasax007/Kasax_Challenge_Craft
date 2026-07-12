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

    public int width(TextRenderer tr) {
        int titleW = tr.getWidth(title);
        int valueW = tr.getWidth(value);
        int content = Math.max(titleW, valueW + 44);
        return MathHelper.clamp(34 + content + 10, 132, 220);
    }

    /** {@code flash} 0..1 briefly brightens the accent when progress advances. */
    public void render(DrawContext context, TextRenderer tr, int x, int y, float flash) {
        int w = width(tr);
        int acc = CraftUI.mix(accent, 0xFFFFFFFF, 0.65f * flash);
        CraftUI.panelFloat(context, x, y, w, HEIGHT, acc);

        CraftUI.iconTileItem(context, icon, x + 7, y + 7, 20, acc);

        int textX = x + 34;
        String trimmedTitle = CraftUI.trimToWidth(tr, title.getString(), w - 34 - tr.getWidth(value) - 14);
        context.drawText(tr, Text.of(trimmedTitle), textX, y + 6, CraftUI.TEXT_PRIMARY, false);
        context.drawText(tr, value, x + w - tr.getWidth(value) - 8, y + 6, CraftUI.TEXT_SECONDARY, false);

        int barX = textX;
        int barY = y + 21;
        int barW = w - 34 - 10;
        CraftUI.progressBar(context, barX, barY, barW, 5, progress, accent);
    }
}
