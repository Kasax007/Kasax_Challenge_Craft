package net.kasax.challengecraft.client.screen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Transitional screen used while the integrated server is reopening the same save. */
public class RestartingScreen extends Screen {
    public RestartingScreen() {
        super(Text.translatable("challengecraft.restart.world.title"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.restart.world.generating").formatted(Formatting.GOLD, Formatting.BOLD), this.width / 2, this.height / 2 - 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.restart.world.randomizing"), this.width / 2, this.height / 2 + 10, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
