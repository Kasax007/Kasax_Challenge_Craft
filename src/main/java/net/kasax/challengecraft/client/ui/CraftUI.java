package net.kasax.challengecraft.client.ui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Shared house-style component kit for every Challenge Craft screen and HUD.
 *
 * <p>The palette and the layered {@code panel}/{@code frame}/{@code chip}/{@code gem} drawing
 * primitives are lifted from the polished leveling screen so the rest of the mod can share one
 * cohesive visual language: a dark navy surface, a border, and a bright accent hairline that
 * sells depth. Prefer these helpers (and the {@link S} spacing scale) over ad-hoc
 * {@code fill}/{@code drawBorder} calls so alignment and colour stay consistent.
 */
public final class CraftUI {
    private CraftUI() {
    }

    // ---- Colour tokens -------------------------------------------------------------------
    /** Screen / outer-frame base fill. */
    public static final int SURFACE_BG = 0xE0121620;
    /** Inner panel / card base fill. */
    public static final int SURFACE_RAISED = 0xD91D2431;
    /** Slightly translucent panel fill for panels floated over busy art. */
    public static final int SURFACE_FLOAT = 0xC8141B26;
    /** Panel / card border. */
    public static final int BORDER = 0xFF3A465A;
    /** Bright bevel hairline that creates the raised look. */
    public static final int ACCENT_HAIRLINE = 0xFF8BA6D8;

    public static final int TEXT_PRIMARY = 0xFFF4F8FF;
    public static final int TEXT_SECONDARY = 0xFF9FB0CA;
    public static final int TEXT_MUTED = 0xFF75839A;

    public static final int SUCCESS = 0xFF7BE0A4;
    public static final int WARNING = 0xFFE3B35A;
    public static final int INFO = 0xFFB3D5FF;
    public static final int DANGER = 0xFFF06A63;

    /** The single canonical gold; replaces the four historical golds scattered across the mod. */
    public static final int GOLD = 0xFFE3B35A;

    // ---- Spacing scale (strict 4px grid) -------------------------------------------------
    public static final class S {
        public static final int XS = 4;
        public static final int SM = 8;
        public static final int MD = 12;
        public static final int LG = 16;
        public static final int XL = 24;

        private S() {
        }
    }

    // ---- Card state presets --------------------------------------------------------------
    public enum CardState {
        IDLE(0xC8202839, BORDER, ACCENT_HAIRLINE),
        ACTIVE(0xCC173126, 0xFF4EA97E, SUCCESS),
        SELECTED(0xCC233244, GOLD, 0xFFF3D88A),
        LOCKED(0xC81A1E27, 0xFF39414F, 0xFF4C566B),
        DANGER_STATE(0xCC2B1616, 0xFFA5443F, DANGER);

        public final int fill;
        public final int border;
        public final int accent;

        CardState(int fill, int border, int accent) {
            this.fill = fill;
            this.border = border;
            this.accent = accent;
        }
    }

    // ---- Frames & panels -----------------------------------------------------------------

    /** Heavy outer frame for a whole screen (double border with corner accents). */
    public static void frame(GuiGraphicsExtractor context, int x, int y, int width, int height,
                             int fill, int border, int accent) {
        context.fill(x + 2, y, x + width - 2, y + height, border);
        context.fill(x, y + 2, x + width, y + height - 2, border);
        context.fill(x + 5, y + 5, x + width - 5, y + height - 5, fill);
        context.fill(x + 8, y + 2, x + width - 8, y + 4, accent);
        context.fill(x + 2, y + 8, x + 4, y + height - 8, accent);
        context.fill(x + width - 4, y + 8, x + width - 2, y + height - 8, accent);
        context.fill(x + 8, y + height - 4, x + width - 8, y + height - 2, accent);
    }

    public static void frame(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        frame(context, x, y, width, height, SURFACE_BG, BORDER, ACCENT_HAIRLINE);
    }

    /** Standard panel: fill, border, and a top + side accent hairline. */
    public static void panel(GuiGraphicsExtractor context, int x, int y, int width, int height,
                             int fill, int border, int accent) {
        context.fill(x + 1, y, x + width - 1, y + height, border);
        context.fill(x, y + 1, x + width, y + height - 1, border);
        context.fill(x + 3, y + 3, x + width - 3, y + height - 3, fill);
        context.fill(x + 5, y + 1, x + width - 5, y + 2, accent);
        context.fill(x + 1, y + 5, x + 2, y + height - 5, accent);
        context.fill(x + width - 2, y + 5, x + width - 1, y + height - 5, accent);
    }

    public static void panel(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        panel(context, x, y, width, height, SURFACE_RAISED, BORDER, ACCENT_HAIRLINE);
    }

    public static void panelFloat(GuiGraphicsExtractor context, int x, int y, int width, int height, int accent) {
        panel(context, x, y, width, height, SURFACE_FLOAT, BORDER, accent);
    }

    /** A small pill used for status badges. */
    public static void chip(GuiGraphicsExtractor context, int x, int y, int width, int height, int fill, int accent) {
        context.fill(x + 1, y, x + width - 1, y + height, accent);
        context.fill(x, y + 1, x + width, y + height - 1, accent);
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
    }

    /**
     * Draws a labelled status chip sized to its text and returns its total width.
     * The fill is a dark translucent wash so every badge reads as the same family.
     */
    public static int labelChip(GuiGraphicsExtractor context, Font tr, Component label, int x, int y, int accent) {
        String text = label.getString();
        int w = tr.width(text) + 10;
        chip(context, x, y, w, 12, 0x66151C27, accent);
        context.text(tr, label, x + 5, y + 2, accent, false);
        return w;
    }

    // ---- Section header ------------------------------------------------------------------

    /** A left-aligned section title with a coloured underline hairline spanning the width. */
    public static void sectionHeader(GuiGraphicsExtractor context, Font tr, Component label,
                                     int x, int y, int width, int accent) {
        context.text(tr, label, x, y, TEXT_PRIMARY, false);
        int underlineY = y + tr.lineHeight + 2;
        context.fill(x, underlineY, x + width, underlineY + 1, applyAlpha(accent, 0.85f));
        context.fill(x, underlineY, x + Math.min(width, 28), underlineY + 1, accent);
    }

    // ---- Icon tile -----------------------------------------------------------------------

    /** A recessed slot-style tile. Draw an item into it with {@link #iconTileItem}. */
    public static void iconTile(GuiGraphicsExtractor context, int x, int y, int size, int accent) {
        context.fill(x, y, x + size, y + size, 0xFF10151F);
        context.outline(x, y, size, size, applyAlpha(accent, 0.55f));
    }

    public static void iconTileItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int size, int accent) {
        iconTile(context, x, y, size, accent);
        if (stack != null && !stack.isEmpty()) {
            int inset = (size - 16) / 2;
            context.item(stack, x + inset, y + inset);
        }
    }

    // ---- Progress bar --------------------------------------------------------------------

    /** Bevelled progress bar with a dark trough and a bright fill. */
    public static void progressBar(GuiGraphicsExtractor context, int x, int y, int width, int height,
                                   float progress, int fillColor) {
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        context.fill(x, y, x + width, y + height, 0xFF162231);
        int fill = Math.max(0, (int) ((width - 2) * Mth.clamp(progress, 0f, 1f)));
        if (fill > 0) {
            context.fill(x + 1, y + 1, x + 1 + fill, y + height - 1, fillColor);
            // subtle top gloss on the filled region
            context.fill(x + 1, y + 1, x + 1 + fill, y + 2, applyAlpha(0xFFFFFFFF, 0.18f));
        }
        context.outline(x - 1, y - 1, width + 2, height + 2, 0xFF6A7991);
    }

    public static void progressBar(GuiGraphicsExtractor context, int x, int y, int width, int height, float progress) {
        progressBar(context, x, y, width, height, progress, SUCCESS);
    }

    // ---- Gem (fallback reward marker) ----------------------------------------------------

    public static void gem(GuiGraphicsExtractor context, int centerX, int centerY, int color) {
        int shadow = darken(color, 0.45f);
        context.fill(centerX - 1, centerY - 4, centerX + 1, centerY - 2, shadow);
        context.fill(centerX - 3, centerY - 2, centerX + 3, centerY, color);
        context.fill(centerX - 2, centerY, centerX + 2, centerY + 3, color);
        context.fill(centerX - 1, centerY + 3, centerX + 1, centerY + 5, shadow);
    }

    // ---- Colour helpers ------------------------------------------------------------------

    public static int darken(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) * factor);
        int g = (int) (((color >>> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Multiplies the alpha channel of an ARGB colour by {@code alpha} (0..1). */
    public static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * Mth.clamp(alpha, 0f, 1f));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Linear blend between two ARGB colours. */
    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    // ---- Text helpers --------------------------------------------------------------------

    public static String trimToWidth(Font tr, String text, int width) {
        if (tr.width(text) <= width) {
            return text;
        }
        return tr.plainSubstrByWidth(text, Math.max(8, width - tr.width("..."))) + "...";
    }

    public static void drawWrapped(GuiGraphicsExtractor context, Font tr, Component text,
                                   int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = tr.split(text, width);
        int lineCount = Math.min(maxLines, lines.size());
        for (int i = 0; i < lineCount; i++) {
            context.text(tr, lines.get(i), x, y + i * tr.lineHeight, color, false);
        }
    }

    /** Draws text centred on ({@code cx}, {@code cy}) at an arbitrary scale. */
    public static void drawCenteredScaled(GuiGraphicsExtractor context, Font tr, Component text,
                                          int cx, int cy, float scale, int color) {
        // pose() is a 2D affine Matrix3x2fStack in 26.2 — scale/translate take two floats, not three.
        context.pose().pushMatrix();
        context.pose().translate(cx, cy);
        context.pose().scale(scale, scale);
        int w = tr.width(text);
        context.text(tr, text, -w / 2, -tr.lineHeight / 2, color, false);
        context.pose().popMatrix();
    }
}
