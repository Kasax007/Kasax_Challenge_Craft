package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.Modals;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Confirmation screen shown before applying challenge changes that require a restart. */
public class ConfirmRestartScreen extends Screen {
    private static final int PANEL_HEIGHT = 150;

    private final Screen parent;
    private final Runnable onConfirm;

    private int panelX;
    private int panelY;

    public ConfirmRestartScreen(Screen parent, Runnable onConfirm) {
        super(Component.translatable("challengecraft.restart.confirm.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        this.panelX = (this.width - Modals.WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        int buttonWidth = 120;
        int gap = 12;
        int buttonsY = panelY + PANEL_HEIGHT - 14 - 20;
        int buttonsX = panelX + Modals.WIDTH / 2 - buttonWidth - gap / 2;

        addRenderableWidget(new CraftButton(buttonsX, buttonsY, buttonWidth, 20,
                Component.translatable("challengecraft.restart.confirm.accept"), CraftButton.Style.DANGER, button -> {
            onConfirm.run();
            this.minecraft.setScreenAndShow(null);
        }));
        addRenderableWidget(new CraftButton(buttonsX + buttonWidth + gap, buttonsY, buttonWidth, 20,
                Component.translatable("challengecraft.restart.confirm.cancel"), CraftButton.Style.NEUTRAL, button -> {
            this.minecraft.setScreenAndShow(parent);
        }));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);

        Modals.header(context, this.font, this.title, Modals.Icon.WARNING, panelX, panelY, PANEL_HEIGHT, CraftUI.DANGER);
        int y = Modals.bodyTop(panelY, this.font);
        y = Modals.bodyLines(context, this.font, panelX, y,
                List.of(Component.translatable("challengecraft.restart.confirm.prompt")), CraftUI.TEXT_PRIMARY);
        Modals.bodyLines(context, this.font, panelX, y + 4,
                List.of(Component.translatable("challengecraft.restart.confirm.new_world"),
                        Component.translatable("challengecraft.restart.confirm.archive")), CraftUI.TEXT_SECONDARY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
