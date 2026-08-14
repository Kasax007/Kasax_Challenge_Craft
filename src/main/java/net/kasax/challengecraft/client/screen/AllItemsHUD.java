package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_22_AllItems;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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


    /**
     * Drops the progress of the world just left.
     *
     * <p>Without this the card keeps last world's numbers: {@code setActive(false)} only hides it,
     * and after a save-and-restart the new world re-enables the challenge before its first progress
     * packet arrives — so the old count is what the player sees.
     */
    public static void reset() {
        currentItem = ItemStack.EMPTY;
        currentIndex = 0;
        totalItems = 0;
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static HudCard buildCard() {
        // Visible as soon as the challenge is ACTIVE, not once progress data has arrived.
        //
        // Gating on a zero total meant the card stayed hidden for the whole window between the
        // challenge-sync packet and this challenge's progress packet — and on joining a world that
        // window is exactly when a player looks for it. It only appeared later, when some gameplay
        // action happened to trigger another progress sync. Until the data lands the card shows its
        // pending placeholder, which is honest and, unlike nothing at all, tells the player the
        // challenge is running.
        if (!active) {
            return null;
        }

        // total 0 means "not synced yet", which must not read as a finished run.
        boolean completed = totalItems > 0 && currentIndex >= totalItems;
        Component title;
        ItemStack icon;
        if (completed) {
            title = Component.translatable("challengecraft.completed");
            icon = new ItemStack(Items.NETHER_STAR);
        } else if (currentItem.isEmpty()) {
            // No progress packet yet — do not run an empty stack through the name formatter.
            title = Component.translatable("challengecraft.placeholder.pending");
            icon = ItemStack.EMPTY;
        } else {
            title = Chal_22_AllItems.getFormattedItemName(currentItem);
            icon = currentItem;
        }
        int accent = completed ? CraftUI.SUCCESS : CraftUI.GOLD;

        float progress = totalItems == 0 ? 0f : currentIndex / (float) totalItems;
        Component value = Component.nullToEmpty(currentIndex + " / " + totalItems);
        return new HudCard("all_items", icon, title, value, progress, accent, currentIndex);
    }
}
