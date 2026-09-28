package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoSounds;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** A casino-styled button: gold on black lacquer, lifts on hover, clicks like a chip. */
@Environment(EnvType.CLIENT)
public class CasinoButton extends AbstractWidget {
    private final Runnable onPress;
    private final BooleanSupplier enabled;
    private final int accent;
    private final Anim.Tween hover = new Anim.Tween(0f);

    public CasinoButton(int x, int y, int w, int h, Component label, int accent, BooleanSupplier enabled, Runnable onPress) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        this.enabled = enabled;
        this.accent = accent;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean on = enabled.getAsBoolean();
        this.active = on;
        float g = hover.approach(on && isHovered() ? 1f : 0f, 14f);
        int lift = Math.round(g);
        int y = getY() - lift;
        int fill = on ? CraftUI.mix(0xF0181018, 0xF02A1A12, g) : 0xB0141418;
        int border = on ? CraftUI.mix(CraftUI.darken(accent, 0.6f), accent, g) : 0xFF3A3A44;
        CraftUI.panel(ctx, getX(), y, getWidth(), getHeight(), fill, border, on ? accent : 0xFF4A4A55);
        int text = on ? CraftUI.mix(0xFFE9DCC2, 0xFFFFFFFF, g) : CraftUI.TEXT_MUTED;
        var font = Minecraft.getInstance().font;
        String label = CraftUI.trimToWidth(font, getMessage().getString(), getWidth() - 8);
        ctx.centeredText(font, Component.literal(label), getX() + getWidth() / 2,
                y + (getHeight() - font.lineHeight) / 2 + 1, text);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!enabled.getAsBoolean()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(CasinoSounds.CHIP, 1.0f, 0.7f));
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}
