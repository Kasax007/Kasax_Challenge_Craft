package net.kasax.challengecraft.client.screen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RestartingScreen extends Screen {
    public RestartingScreen() {
        super(Text.literal("Restarting World"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Generating fresh world...").formatted(Formatting.GOLD, Formatting.BOLD), this.width / 2, this.height / 2 - 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Please wait, terrain is being randomized."), this.width / 2, this.height / 2 + 10, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
