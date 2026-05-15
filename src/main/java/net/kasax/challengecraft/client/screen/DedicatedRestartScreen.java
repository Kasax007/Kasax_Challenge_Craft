package net.kasax.challengecraft.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Idle screen shown to dedicated-server clients while a world restart is in progress. */
public class DedicatedRestartScreen extends Screen {
    public DedicatedRestartScreen() {
        super(Text.translatable("challengecraft.restart.server.title"));
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.toMenu"), button -> {
            if (this.client != null) {
                // The connection is already gone, so there is no vanilla disconnect flow left to finish.
                this.client.setScreen(null);
                this.client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
            }
        }).dimensions(this.width / 2 - 100, this.height / 4 + 120, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.restart.server.started").formatted(Formatting.GOLD, Formatting.BOLD), this.width / 2, this.height / 4, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.restart.server.creating"), this.width / 2, this.height / 4 + 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.restart.server.reconnect"), this.width / 2, this.height / 4 + 40, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.restart.server.disconnected").formatted(Formatting.GRAY), this.width / 2, this.height / 4 + 80, 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
