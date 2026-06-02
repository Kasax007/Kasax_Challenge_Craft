package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.challenges.Chal_22_AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Scrollable list view for the ordered all-items run. */
public class AllItemsScreen extends Screen {
    private final List<ItemStack> items;
    private final int currentIndex;
    private ItemListWidget list;

    public AllItemsScreen(List<ItemStack> items, int currentIndex) {
        super(Component.translatable("challengecraft.all_items_list.title"));
        this.items = items;
        this.currentIndex = currentIndex;
    }

    @Override
    protected void init() {
        this.list = new ItemListWidget();
        this.addRenderableWidget(this.list);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.list.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    class ItemListWidget extends ObjectSelectionList<ItemListWidget.Entry> {
        public ItemListWidget() {
            super(AllItemsScreen.this.minecraft, AllItemsScreen.this.width, AllItemsScreen.this.height - 60, 30, 20);
            for (int i = 0; i < items.size(); i++) {
                this.addEntry(new net.kasax.challengecraft.client.screen.AllItemsScreen.ItemListWidget.Entry(i, items.get(i)));
            }
            if (currentIndex >= 0 && currentIndex < this.getItemCount()) {
                this.setSelected(this.children().get(currentIndex));
            }
        }

        class Entry extends ObjectSelectionList.Entry<net.kasax.challengecraft.client.screen.AllItemsScreen.ItemListWidget.Entry> {
            private final int index;
            private final ItemStack stack;

            public Entry(int index, ItemStack stack) {
                this.index = index;
                this.stack = stack;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int x = this.getContentX();
                int y = this.getContentY();
                int entryWidth = this.getContentWidth();
                boolean collected = this.index < currentIndex;
                boolean current = this.index == currentIndex;
                
                ChatFormatting color = collected ? ChatFormatting.GREEN : (current ? ChatFormatting.GOLD : ChatFormatting.GRAY);
                Component name = Chal_22_AllItems.getFormattedItemName(stack).copy().withStyle(color);
                
                if (current) name = Component.empty().append("> ").append(name);
                
                context.item(stack, x + 5, y);
                context.text(minecraft.font, name, x + 25, y + 5, 0xFFFFFF);
                
                if (collected) {
                    context.text(minecraft.font, Component.empty().append("✓").withStyle(ChatFormatting.GREEN), x + entryWidth - 20, y + 5, 0xFFFFFF);
                }
            }

            @Override
            public Component getNarration() {
                return stack.getHoverName().copy();
            }
        }
    }
}
