package net.kasax.challengecraft.client.widget;

import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Custom-drawn entry point for the progression screen on the title menu.
 *
 * <p>Uses the shared {@link CraftUI} panel language (navy fill, gold accent hairline) with a
 * level emblem and a subtle, delta-timed hover glow — replacing the old pulsing-text look.
 */
public class AnimatedLevelButton extends ButtonWidget {
    private final Anim.Tween glow = new Anim.Tween(0f);

    public AnimatedLevelButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    private static int currentLevel() {
        MinecraftClient client = MinecraftClient.getInstance();
        long xp;
        if (client.getSession() != null) {
            UUID uuid = client.getSession().getUuidOrNull();
            xp = uuid != null ? XpManager.getXp(uuid) : XpManager.getTotalXp();
        } else {
            xp = XpManager.getTotalXp();
        }
        return LevelManager.getLevelForXp(xp);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        float g = glow.approach(isHovered() ? 1f : 0f, 12f);

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // Soft outer glow on hover.
        if (g > 0.01f) {
            int glowColor = CraftUI.applyAlpha(CraftUI.GOLD, 0.28f * g);
            context.fill(x - 2, y - 2, x + w + 2, y + h + 2, glowColor);
        }

        int fill = CraftUI.mix(CraftUI.SURFACE_RAISED, 0xEE243449, g);
        int border = CraftUI.mix(CraftUI.BORDER, CraftUI.GOLD, g);
        int accent = CraftUI.mix(CraftUI.ACCENT_HAIRLINE, 0xFFF3D88A, g);
        CraftUI.panel(context, x, y, w, h, fill, border, accent);

        // Level emblem on the left.
        int emblemX = x + 14;
        int emblemY = y + h / 2;
        CraftUI.gem(context, emblemX, emblemY, CraftUI.mix(CraftUI.GOLD, 0xFFFFF0B8, g));

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int labelColor = CraftUI.mix(CraftUI.TEXT_PRIMARY, 0xFFFFF3C9, g);
        context.drawText(tr, getMessage(), x + 28, y + (h - tr.fontHeight) / 2 + 1, labelColor, false);

        // Compact level readout on the right edge.
        Text levelText = Text.translatable("challengecraft.mainmenu.button_level", currentLevel());
        context.drawText(tr, levelText, x + w - tr.getWidth(levelText) - 12,
                y + (h - tr.fontHeight) / 2 + 1, CraftUI.GOLD, false);
    }
}
