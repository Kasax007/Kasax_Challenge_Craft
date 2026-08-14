package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared base for the ordered "collect every X" list screens (items / entities / advancements).
 * Renders a framed panel with a section header, a progress header + bar, a search field, and a
 * scrollable list of {@code card(state)} rows with Done / Current / Upcoming state chips.
 */
public class CollectionScreen extends Screen {
    /** One list entry. {@code tooltip} may be empty. */
    public record Row(net.minecraft.world.item.ItemStack icon, Component name, List<Component> tooltip) {
    }

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 3;
    private static final int PAD = 12;

    private final List<Row> rows;
    private final int currentIndex;

    private WidgetScrollPanel panel;
    private EditBox search;

    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    private List<Component> hoverTooltip;
    private int hoverMouseX;
    private int hoverMouseY;

    protected CollectionScreen(Component title, List<Row> rows, int currentIndex) {
        super(title);
        this.rows = rows;
        this.currentIndex = currentIndex;
    }

    @Override
    protected void init() {
        this.frameW = Math.min(this.width - 20, 360);
        this.frameX = (this.width - frameW) / 2;
        this.frameY = 20;
        this.frameH = this.height - 40;

        int headerH = 46;
        int searchY = frameY + headerH;
        this.search = new EditBox(this.font, frameX + PAD, searchY, frameW - PAD * 2, 14, Component.empty());
        this.search.setHint(Component.translatable("challengecraft.gui.search"));
        this.search.setResponder(q -> rebuildRows());
        addRenderableWidget(this.search);

        this.listX = frameX + PAD;
        this.listY = searchY + 20;
        this.listW = frameW - PAD * 2;
        this.listH = frameY + frameH - listY - PAD;

        this.panel = new WidgetScrollPanel(listX, listY, listW, listH, Component.empty());
        addRenderableWidget(this.panel);
        rebuildRows();
    }

    private void rebuildRows() {
        this.panel.clearChildren();
        String query = this.search != null ? this.search.getValue().toLowerCase(Locale.ROOT) : "";
        int y = listY + 2;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (!query.isEmpty() && !row.name().getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            this.panel.addChild(new RowWidget(listX + 2, y, listW - 14, i, row));
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Runs once (called by the screen render harness) after the vanilla blur/darkening, so
        // chrome drawn here stays crisp and sits behind the widgets from super.extractRenderState().
        super.extractBackground(context, mouseX, mouseY, delta);

        CraftUI.frame(context, frameX, frameY, frameW, frameH);
        CraftUI.sectionHeader(context, this.font, this.title, frameX + PAD, frameY + PAD, frameW - PAD * 2, CraftUI.GOLD);

        int total = rows.size();
        int done = Mth.clamp(currentIndex, 0, total);
        int pct = total == 0 ? 100 : Math.round(done * 100f / total);
        Component progress = Component.translatable("challengecraft.collection.progress", done, total, pct);
        int progressY = frameY + PAD + this.font.lineHeight + 6;
        context.text(this.font, progress, frameX + PAD, progressY, CraftUI.TEXT_SECONDARY, false);
        int barY = progressY + this.font.lineHeight + 2;
        CraftUI.progressBar(context, frameX + PAD, barY, frameW - PAD * 2, 5, total == 0 ? 1f : done / (float) total);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.hoverTooltip = null;
        super.extractRenderState(context, mouseX, mouseY, delta);

        if (this.panel != null && this.panel.isEmpty()) {
            context.centeredText(this.font, Component.translatable("challengecraft.collection.empty"),
                    frameX + frameW / 2, listY + listH / 2 - 4, CraftUI.TEXT_MUTED);
        }

        if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
            // 26.2 dropped renderComponentTooltip: tooltips are queued here and drawn by
            // GuiGraphicsExtractor.extractDeferredElements() after the screen, so still on top.
            context.setComponentTooltipForNextFrame(this.font, hoverTooltip, hoverMouseX, hoverMouseY);
        }
    }

    private final class RowWidget extends AbstractWidget {
        private final int rowIndex;
        private final Row row;
        private final Anim.Tween hover = new Anim.Tween(0f);

        private RowWidget(int x, int y, int width, int rowIndex, Row row) {
            super(x, y, width, ROW_HEIGHT, row.name());
            this.rowIndex = rowIndex;
            this.row = row;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            boolean done = rowIndex < currentIndex;
            boolean current = rowIndex == currentIndex;

            CraftUI.CardState state = done ? CraftUI.CardState.ACTIVE
                    : (current ? CraftUI.CardState.SELECTED : CraftUI.CardState.IDLE);
            float g = hover.approach(isHovered() ? 1f : 0f, 12f);
            int fill = CraftUI.mix(state.fill, 0xCC243449, g * 0.6f);
            int border = CraftUI.mix(state.border, state.accent, g);
            CraftUI.panel(context, getX(), getY(), getWidth(), getHeight(), fill, border, state.accent);

            CraftUI.iconTileItem(context, row.icon(), getX() + 4, getY() + (ROW_HEIGHT - 18) / 2, 18, state.accent);

            // State chip on the right.
            Component stateText;
            int chipAccent;
            if (done) {
                stateText = Component.translatable("challengecraft.collection.state.done");
                chipAccent = CraftUI.SUCCESS;
            } else if (current) {
                stateText = Component.translatable("challengecraft.collection.state.current");
                chipAccent = CraftUI.GOLD;
            } else {
                stateText = Component.translatable("challengecraft.collection.state.upcoming");
                chipAccent = CraftUI.TEXT_MUTED;
            }
            int chipW = CollectionScreen.this.font.width(stateText) + 10;
            int chipX = getX() + getWidth() - chipW - 6;
            CraftUI.labelChip(context, CollectionScreen.this.font, stateText, chipX, getY() + (ROW_HEIGHT - 12) / 2, chipAccent);

            int nameX = getX() + 28;
            String name = CraftUI.trimToWidth(CollectionScreen.this.font, row.name().getString(), chipX - nameX - 6);
            int nameColor = current ? CraftUI.TEXT_PRIMARY : (done ? CraftUI.SUCCESS : CraftUI.TEXT_SECONDARY);
            context.text(CollectionScreen.this.font, Component.nullToEmpty(name), nameX,
                    getY() + (ROW_HEIGHT - CollectionScreen.this.font.lineHeight) / 2, nameColor, false);

            if (isHovered() && row.tooltip() != null && !row.tooltip().isEmpty()) {
                hoverTooltip = row.tooltip();
                hoverMouseX = mouseX;
                hoverMouseY = mouseY;
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }
}
