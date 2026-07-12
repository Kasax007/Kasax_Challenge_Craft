package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.Modals;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Transitional screen used while the integrated server is reopening the same save. */
public class RestartingScreen extends Screen {
    private static final int PANEL_HEIGHT = 110;
    private static final int FLAVOR_COUNT = 4;

    private int panelX;
    private int panelY;

    public RestartingScreen() {
        super(Text.translatable("challengecraft.restart.world.title"));
    }

    @Override
    protected void init() {
        this.panelX = (this.width - Modals.WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        Modals.header(context, this.textRenderer, this.title, Modals.Icon.SPINNER, panelX, panelY, PANEL_HEIGHT, CraftUI.INFO);

        // Rotating flavour text so the transition never looks frozen.
        int index = (int) ((System.currentTimeMillis() / 2200L) % FLAVOR_COUNT);
        Text flavor = Text.translatable("challengecraft.restart.flavor." + index);
        int y = Modals.bodyTop(panelY, this.textRenderer);
        Modals.bodyLines(context, this.textRenderer, panelX, y, List.of(flavor), CraftUI.TEXT_SECONDARY);
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
