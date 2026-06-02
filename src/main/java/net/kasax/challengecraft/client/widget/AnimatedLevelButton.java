package net.kasax.challengecraft.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Small animated entry point for the progression screen. */
public class AnimatedLevelButton extends Button {
    public AnimatedLevelButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float time = (System.currentTimeMillis() % 2000) / 2000.0f;
        float sin = (float) Math.sin(time * 2 * Math.PI);
        
        int r = 0xFF;
        int g = (int) (0xD7 + (0xFF - 0xD7) * (0.5 + 0.5 * sin));
        int b = (int) (0x00 + (0x55 - 0x00) * (0.5 + 0.5 * sin));
        
        int animatedColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
        this.extractDefaultSprite(context);
        context.centeredText(net.minecraft.client.Minecraft.getInstance().font, this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, animatedColor);
    }
}
