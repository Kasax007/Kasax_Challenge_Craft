package net.kasax.challengecraft.client.widget;

import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/**
 * Custom-drawn entry point for the progression screen on the title menu.
 *
 * <p>Uses the shared {@link CraftUI} panel language (navy fill, gold accent hairline) with a
 * level emblem and a subtle, delta-timed hover glow — replacing the old pulsing-text look.
 */
public class AnimatedLevelButton extends Button {
    private final Anim.Tween glow = new Anim.Tween(0f);

    public AnimatedLevelButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    private static int currentLevel() {
        Minecraft client = Minecraft.getInstance();
        long xp;
        if (client.getUser() != null) {
            UUID uuid = client.getUser().getProfileId();
            xp = uuid != null ? XpManager.getXp(uuid) : XpManager.getTotalXp();
        } else {
            xp = XpManager.getTotalXp();
        }
        return LevelManager.getLevelForXp(xp);
    }

    // 26.2: AbstractButton.extractWidgetRenderState is final; extractContents is the hook that
    // replaces the old renderWidget. Not calling extractDefaultSprite() keeps the vanilla button
    // texture out of the way, exactly as before.
    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
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

        Font tr = Minecraft.getInstance().font;
        int labelColor = CraftUI.mix(CraftUI.TEXT_PRIMARY, 0xFFFFF3C9, g);
        // `this.message`, not getMessage(): 26.2's AbstractWidget.WithInactiveMessage (new
        // supertype of Button) returns a grey-styled copy while active == false, and a Style
        // colour overrides the colour argument to text(). See CraftButton for the full note.
        context.text(tr, this.message, x + 28, y + (h - tr.lineHeight) / 2 + 1, labelColor, false);

        // Compact level readout on the right edge.
        Component levelText = Component.translatable("challengecraft.mainmenu.button_level", currentLevel());
        context.text(tr, levelText, x + w - tr.width(levelText) - 12,
                y + (h - tr.lineHeight) / 2 + 1, CraftUI.GOLD, false);
    }
}
