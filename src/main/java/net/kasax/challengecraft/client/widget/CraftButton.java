package net.kasax.challengecraft.client.widget;

import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Shared card-style button in the CraftUI language, with weighted styles and a hover glow. */
public class CraftButton extends ButtonWidget {
    public enum Style {
        NEUTRAL(CraftUI.SURFACE_RAISED, CraftUI.BORDER, CraftUI.ACCENT_HAIRLINE, CraftUI.INFO, CraftUI.TEXT_PRIMARY),
        PRIMARY(0xCC153026, 0xFF4EA97E, CraftUI.SUCCESS, 0xFFBFF3D0, 0xFFF3FFF8),
        DANGER(0xCC2B1616, 0xFFA5443F, CraftUI.DANGER, 0xFFFFC7C2, 0xFFFFF0EE);

        final int fill;
        final int border;
        final int accent;
        final int hoverAccent;
        final int textColor;

        Style(int fill, int border, int accent, int hoverAccent, int textColor) {
            this.fill = fill;
            this.border = border;
            this.accent = accent;
            this.hoverAccent = hoverAccent;
            this.textColor = textColor;
        }
    }

    private final Style style;
    private final Anim.Tween glow = new Anim.Tween(0f);

    public CraftButton(int x, int y, int width, int height, Text message, Style style, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.style = style;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean enabled = this.active;
        float g = glow.approach(enabled && isHovered() ? 1f : 0f, 12f);

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        if (g > 0.01f) {
            context.fill(x - 2, y - 2, x + w + 2, y + h + 2, CraftUI.applyAlpha(style.accent, 0.25f * g));
        }

        int fill = enabled ? style.fill : CraftUI.applyAlpha(style.fill, 0.5f);
        int border = enabled ? CraftUI.mix(style.border, style.hoverAccent, g) : CraftUI.applyAlpha(style.border, 0.6f);
        int accent = enabled ? CraftUI.mix(style.accent, style.hoverAccent, g) : CraftUI.applyAlpha(style.accent, 0.5f);
        CraftUI.panel(context, x, y, w, h, fill, border, accent);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int textColor = enabled ? style.textColor : CraftUI.TEXT_MUTED;
        context.drawCenteredTextWithShadow(tr, getMessage(), x + w / 2, y + (h - tr.fontHeight) / 2 + 1, textColor);
    }
}
