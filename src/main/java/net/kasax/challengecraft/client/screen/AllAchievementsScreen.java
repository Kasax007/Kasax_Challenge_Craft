package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.network.AdvancementInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Scrollable list view for the ordered all-achievements run. */
public class AllAchievementsScreen extends Screen {
    private final List<AdvancementInfo> advancements;
    private final int currentIndex;
    private AdvancementListWidget list;

    public AllAchievementsScreen(List<AdvancementInfo> advancements, int currentIndex) {
        super(Component.translatable("challengecraft.all_achievements_list.title"));
        this.advancements = advancements;
        this.currentIndex = currentIndex;
    }

    @Override
    protected void init() {
        this.list = new AdvancementListWidget();
        this.addRenderableWidget(this.list);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.list.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        AdvancementListWidget.Entry hoveredEntry = this.list.getEntryAt(mouseX, mouseY);
        if (hoveredEntry != null) {
            hoveredEntry.renderTooltip(context, mouseX, mouseY);
        }
    }

    class AdvancementListWidget extends ObjectSelectionList<AdvancementListWidget.Entry> {
        public AdvancementListWidget() {
            super(AllAchievementsScreen.this.minecraft, AllAchievementsScreen.this.width, AllAchievementsScreen.this.height, 30, 25);
            for (int i = 0; i < advancements.size(); i++) {
                this.addEntry(new net.kasax.challengecraft.client.screen.AllAchievementsScreen.AdvancementListWidget.Entry(i, advancements.get(i)));
            }
            if (currentIndex >= 0 && currentIndex < this.getItemCount()) {
                this.setSelected(this.children().get(currentIndex));
            }
        }

        public net.kasax.challengecraft.client.screen.AllAchievementsScreen.AdvancementListWidget.Entry getEntryAt(double x, double y) {
            return this.getEntryAtPosition(x, y);
        }

        class Entry extends ObjectSelectionList.Entry<net.kasax.challengecraft.client.screen.AllAchievementsScreen.AdvancementListWidget.Entry> {
            private final int index;
            private final AdvancementInfo info;
            private Component nameCache = null;

            public Entry(int index, AdvancementInfo info) {
                this.index = index;
                this.info = info;
            }

            private void updateCache() {
                if (nameCache != null) return;
                
                ChatFormatting color = index < currentIndex ? ChatFormatting.GREEN : (index == currentIndex ? ChatFormatting.GOLD : ChatFormatting.GRAY);
                nameCache = info.title().copy().withStyle(color);
            }

            public void renderTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
                if (info.description() != null) {
                    context.setTooltipForNextFrame(minecraft.font, info.description(), mouseX, mouseY);
                }
            }

            @Override
            public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int x = this.getContentX();
                int y = this.getContentY();
                int entryWidth = this.getContentWidth();
                updateCache();
                
                boolean current = this.index == currentIndex;
                Component displayName = nameCache;
                if (current) displayName = Component.empty().append("> ").append(displayName);
                
                context.item(info.icon(), x + 5, y + 2);
                context.text(minecraft.font, displayName, x + 25, y + 6, 0xFFFFFF);
                
                if (this.index < currentIndex) {
                    context.text(minecraft.font, Component.empty().append("✓").withStyle(ChatFormatting.GREEN), x + entryWidth - 20, y + 6, 0xFFFFFF);
                }
            }

            @Override
            public Component getNarration() {
                return info.title();
            }
        }
    }
}
