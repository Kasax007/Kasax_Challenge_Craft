package net.kasax.challengecraft.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ConfirmRestartScreen extends Screen {
    private final Screen parent;
    private final Runnable onConfirm;

    public ConfirmRestartScreen(Screen parent, Runnable onConfirm) {
        super(Text.literal("Confirm Restart"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int spacing = 20;
        int xStart = width / 2 - buttonWidth - spacing / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Yes, Restart").formatted(Formatting.RED), button -> {
            onConfirm.run();
            this.client.setScreen(null);
        }).dimensions(xStart, height / 2, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            this.client.setScreen(parent);
        }).dimensions(xStart + buttonWidth + spacing, height / 2, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Are you sure you want to save and restart?").formatted(Formatting.YELLOW, Formatting.BOLD), width / 2, height / 2 - 40, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("A completely new world will be created."), width / 2, height / 2 - 25, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("The old world will be moved to 'old worlds' folder."), width / 2, height / 2 - 15, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
