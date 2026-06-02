package net.kasax.challengecraft.client.screen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Transitional screen used while the integrated server is reopening the same save. */
public class RestartingScreen extends Screen {
    public RestartingScreen() {
        super(Component.translatable("challengecraft.restart.world.title"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(this.font, Component.translatable("challengecraft.restart.world.generating").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), this.width / 2, this.height / 2 - 10, 0xFFFFFF);
        context.centeredText(this.font, Component.translatable("challengecraft.restart.world.randomizing"), this.width / 2, this.height / 2 + 10, 0xAAAAAA);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
