package net.kasax.challengecraft.client.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class AnimatedLevelButton extends ButtonWidget {
    public AnimatedLevelButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    public void drawMessage(DrawContext context, TextRenderer textRenderer, int color) {
        float time = (System.currentTimeMillis() % 2000) / 2000.0f;
        float sin = (float) Math.sin(time * 2 * Math.PI);
        
        // Pulse between gold (0xFFD700) and bright yellow (0xFFFF55)
        int r = 0xFF;
        int g = (int) (0xD7 + (0xFF - 0xD7) * (0.5 + 0.5 * sin));
        int b = (int) (0x00 + (0x55 - 0x00) * (0.5 + 0.5 * sin));
        
        int animatedColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
        super.drawMessage(context, textRenderer, animatedColor);
    }
}
