package net.kasax.challengecraft.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class DedicatedRestartScreen extends Screen {
    public DedicatedRestartScreen() {
        super(Text.literal("Server Restarting"));
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.toMenu"), button -> {
            if (this.client != null) {
                this.client.setScreen(null); // Close current screen
                // Returning to main menu is usually handled by disconnecting, 
                // but since we're already disconnected, we just go to TitleScreen
                this.client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
            }
        }).dimensions(this.width / 2 - 100, this.height / 4 + 120, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("World restart initiated!").formatted(Formatting.GOLD, Formatting.BOLD), this.width / 2, this.height / 4, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("The server is creating a fresh world."), this.width / 2, this.height / 4 + 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Please wait a moment and then reconnect."), this.width / 2, this.height / 4 + 40, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("You have been disconnected.").formatted(Formatting.GRAY), this.width / 2, this.height / 4 + 80, 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
