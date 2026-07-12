package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

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
    public record Row(net.minecraft.item.ItemStack icon, Text name, List<Text> tooltip) {
    }

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 3;
    private static final int PAD = 12;

    private final List<Row> rows;
    private final int currentIndex;

    private WidgetScrollPanel panel;
    private TextFieldWidget search;

    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    private List<Text> hoverTooltip;
    private int hoverMouseX;
    private int hoverMouseY;

    protected CollectionScreen(Text title, List<Row> rows, int currentIndex) {
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
        this.search = new TextFieldWidget(this.textRenderer, frameX + PAD, searchY, frameW - PAD * 2, 14, Text.empty());
        this.search.setPlaceholder(Text.translatable("challengecraft.gui.search"));
        this.search.setChangedListener(q -> rebuildRows());
        addDrawableChild(this.search);

        this.listX = frameX + PAD;
        this.listY = searchY + 20;
        this.listW = frameW - PAD * 2;
        this.listH = frameY + frameH - listY - PAD;

        this.panel = new WidgetScrollPanel(listX, listY, listW, listH, Text.empty());
        addDrawableChild(this.panel);
        rebuildRows();
    }

    private void rebuildRows() {
        this.panel.clearChildren();
        String query = this.search != null ? this.search.getText().toLowerCase(Locale.ROOT) : "";
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Runs once (called by Screen.render) after the vanilla blur/darkening, so chrome drawn
        // here stays crisp and sits behind the widgets rendered by super.render().
        super.renderBackground(context, mouseX, mouseY, delta);

        CraftUI.frame(context, frameX, frameY, frameW, frameH);
        CraftUI.sectionHeader(context, this.textRenderer, this.title, frameX + PAD, frameY + PAD, frameW - PAD * 2, CraftUI.GOLD);

        int total = rows.size();
        int done = MathHelper.clamp(currentIndex, 0, total);
        int pct = total == 0 ? 100 : Math.round(done * 100f / total);
        Text progress = Text.translatable("challengecraft.collection.progress", done, total, pct);
        int progressY = frameY + PAD + this.textRenderer.fontHeight + 6;
        context.drawText(this.textRenderer, progress, frameX + PAD, progressY, CraftUI.TEXT_SECONDARY, false);
        int barY = progressY + this.textRenderer.fontHeight + 2;
        CraftUI.progressBar(context, frameX + PAD, barY, frameW - PAD * 2, 5, total == 0 ? 1f : done / (float) total);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.hoverTooltip = null;
        super.render(context, mouseX, mouseY, delta);

        if (this.panel != null && this.panel.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.collection.empty"),
                    frameX + frameW / 2, listY + listH / 2 - 4, CraftUI.TEXT_MUTED);
        }

        if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
            context.drawTooltip(this.textRenderer, hoverTooltip, hoverMouseX, hoverMouseY);
        }
    }

    private final class RowWidget extends ClickableWidget {
        private final int rowIndex;
        private final Row row;
        private final Anim.Tween hover = new Anim.Tween(0f);

        private RowWidget(int x, int y, int width, int rowIndex, Row row) {
            super(x, y, width, ROW_HEIGHT, row.name());
            this.rowIndex = rowIndex;
            this.row = row;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
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
            Text stateText;
            int chipAccent;
            if (done) {
                stateText = Text.translatable("challengecraft.collection.state.done");
                chipAccent = CraftUI.SUCCESS;
            } else if (current) {
                stateText = Text.translatable("challengecraft.collection.state.current");
                chipAccent = CraftUI.GOLD;
            } else {
                stateText = Text.translatable("challengecraft.collection.state.upcoming");
                chipAccent = CraftUI.TEXT_MUTED;
            }
            int chipW = CollectionScreen.this.textRenderer.getWidth(stateText) + 10;
            int chipX = getX() + getWidth() - chipW - 6;
            CraftUI.labelChip(context, CollectionScreen.this.textRenderer, stateText, chipX, getY() + (ROW_HEIGHT - 12) / 2, chipAccent);

            int nameX = getX() + 28;
            String name = CraftUI.trimToWidth(CollectionScreen.this.textRenderer, row.name().getString(), chipX - nameX - 6);
            int nameColor = current ? CraftUI.TEXT_PRIMARY : (done ? CraftUI.SUCCESS : CraftUI.TEXT_SECONDARY);
            context.drawText(CollectionScreen.this.textRenderer, Text.of(name), nameX,
                    getY() + (ROW_HEIGHT - CollectionScreen.this.textRenderer.fontHeight) / 2, nameColor, false);

            if (isHovered() && row.tooltip() != null && !row.tooltip().isEmpty()) {
                hoverTooltip = row.tooltip();
                hoverMouseX = mouseX;
                hoverMouseY = mouseY;
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
