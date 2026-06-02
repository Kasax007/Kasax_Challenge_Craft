package net.kasax.challengecraft.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Confirmation screen shown before applying challenge changes that require a restart. */
public class ConfirmRestartScreen extends Screen {
    private final Screen parent;
    private final Runnable onConfirm;

    public ConfirmRestartScreen(Screen parent, Runnable onConfirm) {
        super(Component.translatable("challengecraft.restart.confirm.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int spacing = 20;
        int xStart = width / 2 - buttonWidth - spacing / 2;

        addRenderableWidget(Button.builder(Component.translatable("challengecraft.restart.confirm.accept").withStyle(ChatFormatting.RED), button -> {
            onConfirm.run();
            this.minecraft.setScreen(null);
        }).bounds(xStart, height / 2, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("challengecraft.restart.confirm.cancel"), button -> {
            this.minecraft.setScreen(parent);
        }).bounds(xStart + buttonWidth + spacing, height / 2, buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(font, Component.translatable("challengecraft.restart.confirm.prompt").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), width / 2, height / 2 - 40, 0xFFFFFF);
        context.centeredText(font, Component.translatable("challengecraft.restart.confirm.new_world"), width / 2, height / 2 - 25, 0xAAAAAA);
        context.centeredText(font, Component.translatable("challengecraft.restart.confirm.archive"), width / 2, height / 2 - 15, 0xAAAAAA);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
