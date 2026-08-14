package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.Modals;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Transitional screen used while the integrated server is reopening the same save. */
public class RestartingScreen extends Screen {
    private static final int PANEL_HEIGHT = 110;
    private static final int FLAVOR_COUNT = 4;

    private int panelX;
    private int panelY;

    public RestartingScreen() {
        super(Component.translatable("challengecraft.restart.world.title"));
    }

    @Override
    protected void init() {
        this.panelX = (this.width - Modals.WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);

        Modals.header(context, this.font, this.title, Modals.Icon.SPINNER, panelX, panelY, PANEL_HEIGHT, CraftUI.INFO);

        // Rotating flavour text so the transition never looks frozen.
        int index = (int) ((System.currentTimeMillis() / 2200L) % FLAVOR_COUNT);
        Component flavor = Component.translatable("challengecraft.restart.flavor." + index);
        int y = Modals.bodyTop(panelY, this.font);
        Modals.bodyLines(context, this.font, panelX, y, List.of(flavor), CraftUI.TEXT_SECONDARY);
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
