package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.Modals;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Idle screen shown to dedicated-server clients while a world restart is in progress. */
public class DedicatedRestartScreen extends Screen {
    private static final int PANEL_HEIGHT = 150;

    private int panelX;
    private int panelY;

    public DedicatedRestartScreen() {
        super(Component.translatable("challengecraft.restart.server.title"));
    }

    @Override
    protected void init() {
        this.panelX = (this.width - Modals.WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        int buttonWidth = 160;
        int buttonY = panelY + PANEL_HEIGHT - 14 - 20;
        this.addRenderableWidget(new CraftButton(panelX + Modals.WIDTH / 2 - buttonWidth / 2, buttonY, buttonWidth, 20,
                Component.translatable("gui.toMenu"), CraftButton.Style.NEUTRAL, button -> {
            if (this.minecraft != null) {
                // The connection is already gone, so there is no vanilla disconnect flow left to finish.
                this.minecraft.setScreenAndShow(null);
                this.minecraft.setScreenAndShow(new TitleScreen());
            }
        }));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);

        Modals.header(context, this.font, this.title, Modals.Icon.SPINNER, panelX, panelY, PANEL_HEIGHT, CraftUI.INFO);
        int y = Modals.bodyTop(panelY, this.font);
        Modals.bodyLines(context, this.font, panelX, y,
                List.of(Component.translatable("challengecraft.restart.server.creating"),
                        Component.translatable("challengecraft.restart.server.reconnect")), CraftUI.TEXT_PRIMARY);
        Modals.bodyLines(context, this.font, panelX, y + 26,
                List.of(Component.translatable("challengecraft.restart.server.disconnected")), CraftUI.TEXT_SECONDARY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
