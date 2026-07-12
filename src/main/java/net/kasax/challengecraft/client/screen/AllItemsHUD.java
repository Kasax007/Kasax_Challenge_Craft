package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_22_AllItems;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
/** Compact HUD card for the current all-items target. */
public class AllItemsHUD {
    private static ItemStack currentItem = ItemStack.EMPTY;
    private static int currentIndex = 0;
    private static int totalItems = 0;
    private static boolean active = false;

    public static void update(ItemStack stack, int index, int total) {
        currentItem = stack;
        currentIndex = index;
        totalItems = total;
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static HudCard buildCard() {
        if (!active || totalItems == 0) {
            return null;
        }

        boolean completed = currentIndex >= totalItems;
        Text title = completed
                ? Text.translatable("challengecraft.completed")
                : Chal_22_AllItems.getFormattedItemName(currentItem);
        ItemStack icon = completed ? new ItemStack(Items.NETHER_STAR) : currentItem;
        int accent = completed ? CraftUI.SUCCESS : CraftUI.GOLD;

        float progress = totalItems == 0 ? 0f : currentIndex / (float) totalItems;
        Text value = Text.of(currentIndex + " / " + totalItems);
        return new HudCard("all_items", icon, title, value, progress, accent, currentIndex);
    }
}
