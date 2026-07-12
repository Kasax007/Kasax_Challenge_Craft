package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.Modals;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Confirmation screen shown before applying challenge changes that require a restart. */
public class ConfirmRestartScreen extends Screen {
    private static final int PANEL_HEIGHT = 150;

    private final Screen parent;
    private final Runnable onConfirm;

    private int panelX;
    private int panelY;

    public ConfirmRestartScreen(Screen parent, Runnable onConfirm) {
        super(Text.translatable("challengecraft.restart.confirm.title"));
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

        addDrawableChild(new CraftButton(buttonsX, buttonsY, buttonWidth, 20,
                Text.translatable("challengecraft.restart.confirm.accept"), CraftButton.Style.DANGER, button -> {
            onConfirm.run();
            this.client.setScreen(null);
        }));
        addDrawableChild(new CraftButton(buttonsX + buttonWidth + gap, buttonsY, buttonWidth, 20,
                Text.translatable("challengecraft.restart.confirm.cancel"), CraftButton.Style.NEUTRAL, button -> {
            this.client.setScreen(parent);
        }));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        Modals.header(context, this.textRenderer, this.title, Modals.Icon.WARNING, panelX, panelY, PANEL_HEIGHT, CraftUI.DANGER);
        int y = Modals.bodyTop(panelY, this.textRenderer);
        y = Modals.bodyLines(context, this.textRenderer, panelX, y,
                List.of(Text.translatable("challengecraft.restart.confirm.prompt")), CraftUI.TEXT_PRIMARY);
        Modals.bodyLines(context, this.textRenderer, panelX, y + 4,
                List.of(Text.translatable("challengecraft.restart.confirm.new_world"),
                        Text.translatable("challengecraft.restart.confirm.archive")), CraftUI.TEXT_SECONDARY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }
}
