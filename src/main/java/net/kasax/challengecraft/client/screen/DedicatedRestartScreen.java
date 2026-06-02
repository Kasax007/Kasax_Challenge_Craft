package net.kasax.challengecraft.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Idle screen shown to dedicated-server clients while a world restart is in progress. */
public class DedicatedRestartScreen extends Screen {
    public DedicatedRestartScreen() {
        super(Component.translatable("challengecraft.restart.server.title"));
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.toMenu"), button -> {
            if (this.minecraft != null) {
                // The connection is already gone, so there is no vanilla disconnect flow left to finish.
                this.minecraft.setScreen(null);
                this.minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            }
        }).bounds(this.width / 2 - 100, this.height / 4 + 120, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, Component.translatable("challengecraft.restart.server.started").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), this.width / 2, this.height / 4, 0xFFFFFF);
        context.centeredText(this.font, Component.translatable("challengecraft.restart.server.creating"), this.width / 2, this.height / 4 + 20, 0xFFFFFF);
        context.centeredText(this.font, Component.translatable("challengecraft.restart.server.reconnect"), this.width / 2, this.height / 4 + 40, 0xFFFFFF);
        context.centeredText(this.font, Component.translatable("challengecraft.restart.server.disconnected").withStyle(ChatFormatting.GRAY), this.width / 2, this.height / 4 + 80, 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
