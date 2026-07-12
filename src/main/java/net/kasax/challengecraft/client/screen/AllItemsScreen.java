package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.challenges.Chal_22_AllItems;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Scrollable list view for the ordered all-items run. */
public class AllItemsScreen extends CollectionScreen {
    public AllItemsScreen(List<ItemStack> items, int currentIndex) {
        super(Text.translatable("challengecraft.all_items_list.title"), toRows(items), currentIndex);
    }

    private static List<Row> toRows(List<ItemStack> items) {
        List<Row> rows = new ArrayList<>();
        for (ItemStack stack : items) {
            rows.add(new Row(stack, Chal_22_AllItems.getFormattedItemName(stack), List.of()));
        }
        return rows;
    }
}
