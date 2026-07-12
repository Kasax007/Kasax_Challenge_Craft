package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.Modals;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

import java.util.List;

/** Idle screen shown to dedicated-server clients while a world restart is in progress. */
public class DedicatedRestartScreen extends Screen {
    private static final int PANEL_HEIGHT = 150;

    private int panelX;
    private int panelY;

    public DedicatedRestartScreen() {
        super(Text.translatable("challengecraft.restart.server.title"));
    }

    @Override
    protected void init() {
        this.panelX = (this.width - Modals.WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        int buttonWidth = 160;
        int buttonY = panelY + PANEL_HEIGHT - 14 - 20;
        this.addDrawableChild(new CraftButton(panelX + Modals.WIDTH / 2 - buttonWidth / 2, buttonY, buttonWidth, 20,
                Text.translatable("gui.toMenu"), CraftButton.Style.NEUTRAL, button -> {
            if (this.client != null) {
                // The connection is already gone, so there is no vanilla disconnect flow left to finish.
                this.client.setScreen(null);
                this.client.setScreen(new TitleScreen());
            }
        }));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        Modals.header(context, this.textRenderer, this.title, Modals.Icon.SPINNER, panelX, panelY, PANEL_HEIGHT, CraftUI.INFO);
        int y = Modals.bodyTop(panelY, this.textRenderer);
        Modals.bodyLines(context, this.textRenderer, panelX, y,
                List.of(Text.translatable("challengecraft.restart.server.creating"),
                        Text.translatable("challengecraft.restart.server.reconnect")), CraftUI.TEXT_PRIMARY);
        Modals.bodyLines(context, this.textRenderer, panelX, y + 26,
                List.of(Text.translatable("challengecraft.restart.server.disconnected")), CraftUI.TEXT_SECONDARY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
