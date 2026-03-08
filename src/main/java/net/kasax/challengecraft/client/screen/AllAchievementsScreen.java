package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.network.AdvancementInfo;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public class AllAchievementsScreen extends Screen {
    private final List<AdvancementInfo> advancements;
    private final int currentIndex;
    private AdvancementListWidget list;

    public AllAchievementsScreen(List<AdvancementInfo> advancements, int currentIndex) {
        super(Text.translatable("challengecraft.all_achievements_list.title"));
        this.advancements = advancements;
        this.currentIndex = currentIndex;
    }

    @Override
    protected void init() {
        this.list = new AdvancementListWidget();
        this.addDrawableChild(this.list);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.list.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        AdvancementListWidget.Entry hoveredEntry = this.list.getEntryAt(mouseX, mouseY);
        if (hoveredEntry != null) {
            hoveredEntry.renderTooltip(context, mouseX, mouseY);
        }
    }

    class AdvancementListWidget extends AlwaysSelectedEntryListWidget<AdvancementListWidget.Entry> {
        public AdvancementListWidget() {
            super(AllAchievementsScreen.this.client, AllAchievementsScreen.this.width, AllAchievementsScreen.this.height, 30, 25);
            for (int i = 0; i < advancements.size(); i++) {
                this.addEntry(new Entry(i, advancements.get(i)));
            }
            if (currentIndex >= 0 && currentIndex < this.getEntryCount()) {
                this.setSelected(this.getEntry(currentIndex));
            }
        }

        public Entry getEntryAt(double x, double y) {
            return this.getEntryAtPosition(x, y);
        }

        @Override
        public int getRowWidth() {
            return 280;
        }

        class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final int index;
            private final AdvancementInfo info;
            private Text nameCache = null;

            public Entry(int index, AdvancementInfo info) {
                this.index = index;
                this.info = info;
            }

            private void updateCache() {
                if (nameCache != null) return;
                
                Formatting color = index < currentIndex ? Formatting.GREEN : (index == currentIndex ? Formatting.GOLD : Formatting.GRAY);
                nameCache = info.title().copy().formatted(color);
            }

            public void renderTooltip(DrawContext context, int mouseX, int mouseY) {
                if (info.description() != null) {
                    context.drawTooltip(client.textRenderer, info.description(), mouseX, mouseY);
                }
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                updateCache();
                
                boolean current = this.index == currentIndex;
                Text displayName = nameCache;
                if (current) displayName = Text.literal("> ").append(displayName);
                
                context.drawItem(info.icon(), x + 5, y + 2);
                context.drawTextWithShadow(client.textRenderer, displayName, x + 25, y + 6, 0xFFFFFF);
                
                if (this.index < currentIndex) {
                    context.drawTextWithShadow(client.textRenderer, Text.literal("✓").formatted(Formatting.GREEN), x + entryWidth - 20, y + 6, 0xFFFFFF);
                }
            }

            @Override
            public Text getNarration() {
                return info.title();
            }
        }
    }
}
