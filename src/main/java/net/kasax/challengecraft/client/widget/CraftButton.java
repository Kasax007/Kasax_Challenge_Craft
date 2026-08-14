package net.kasax.challengecraft.client.widget;

import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared card-style button in the CraftUI language, with weighted styles and a hover glow. */
public class CraftButton extends Button {
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

    public CraftButton(int x, int y, int width, int height, Component message, Style style, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    // 26.2: AbstractButton.extractWidgetRenderState is final and only calls extractContents() plus
    // handleCursor(). extractContents is therefore the drop-in for the old renderWidget, and — as
    // before — skipping extractDefaultSprite() means the vanilla button texture is never drawn.
    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
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

        Font tr = Minecraft.getInstance().font;
        int textColor = enabled ? style.textColor : CraftUI.TEXT_MUTED;
        // Deliberately `this.message`, NOT getMessage(): 26.2 slots
        // AbstractWidget.WithInactiveMessage between Button and AbstractWidget, and its
        // getMessage() returns ComponentUtils.mergeStyles(message, Style.EMPTY.withColor(0xA0A0A0))
        // whenever active == false. An explicit Style colour beats the colour argument passed to
        // text(), so every disabled CraftButton (six sites toggle .active) would silently render
        // vanilla grey instead of CraftUI.TEXT_MUTED. `message` is the protected raw field and is
        // exactly what 1.21.5's getMessage() returned.
        context.centeredText(tr, this.message, x + w / 2, y + (h - tr.lineHeight) / 2 + 1, textColor);
    }
}
