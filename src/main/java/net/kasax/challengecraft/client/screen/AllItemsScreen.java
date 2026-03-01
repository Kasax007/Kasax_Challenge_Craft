package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.challenges.Chal_22_AllItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class AllItemsScreen extends Screen {
    private final List<ItemStack> items;
    private final int currentIndex;
    private ItemListWidget list;

    public AllItemsScreen(List<ItemStack> items, int currentIndex) {
        super(Text.translatable("challengecraft.all_items_list.title"));
        this.items = items;
        this.currentIndex = currentIndex;
    }

    @Override
    protected void init() {
        this.list = new ItemListWidget();
        this.addDrawableChild(this.list);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.list.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    class ItemListWidget extends AlwaysSelectedEntryListWidget<ItemListWidget.Entry> {
        public ItemListWidget() {
            super(AllItemsScreen.this.client, AllItemsScreen.this.width, AllItemsScreen.this.height - 60, 30, 20);
            for (int i = 0; i < items.size(); i++) {
                this.addEntry(new Entry(i, items.get(i)));
            }
            if (currentIndex >= 0 && currentIndex < this.getEntryCount()) {
                this.setSelected(this.getEntry(currentIndex));
                // Try to scroll to it
                // this.setScrollAmount(currentIndex * 20);
            }
        }

        class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final int index;
            private final ItemStack stack;

            public Entry(int index, ItemStack stack) {
                this.index = index;
                this.stack = stack;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                boolean collected = this.index < currentIndex;
                boolean current = this.index == currentIndex;
                
                Formatting color = collected ? Formatting.GREEN : (current ? Formatting.GOLD : Formatting.GRAY);
                Text name = Chal_22_AllItems.getFormattedItemName(stack).copy().formatted(color);
                
                if (current) name = Text.literal("> ").append(name);
                
                context.drawItem(stack, x + 5, y);
                context.drawTextWithShadow(client.textRenderer, name, x + 25, y + 5, 0xFFFFFF);
                
                if (collected) {
                    context.drawTextWithShadow(client.textRenderer, Text.literal("✓").formatted(Formatting.GREEN), x + entryWidth - 20, y + 5, 0xFFFFFF);
                }
            }

            @Override
            public Text getNarration() {
                return Text.literal(stack.getName().getString());
            }
        }
    }
}
