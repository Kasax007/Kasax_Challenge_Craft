package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
/** Compact HUD card for the current progressive-block-drops target. */
public class ProgressiveBlocksHUD {
    private static ItemStack currentIcon = ItemStack.EMPTY;
    private static Component currentName = Component.empty();
    private static int currentIndex = 0;
    private static int totalBlocks = 0;
    private static boolean active = false;

    public static void update(String targetBlockId, int index, int total) {
        currentIndex = index;
        totalBlocks = total;
        if (targetBlockId == null || targetBlockId.isEmpty()) {
            currentIcon = ItemStack.EMPTY;
            currentName = Component.empty();
        } else {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(targetBlockId));
            currentIcon = new ItemStack(block.asItem());
            currentName = block.getName();
        }
    }


    /**
     * Drops the progress of the world just left.
     *
     * <p>Without this the card keeps last world's numbers: {@code setActive(false)} only hides it,
     * and after a save-and-restart the new world re-enables the challenge before its first progress
     * packet arrives — so the old count is what the player sees.
     */
    public static void reset() {
        currentIcon = ItemStack.EMPTY;
        currentName = Component.empty();
        currentIndex = 0;
        totalBlocks = 0;
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
        boolean completed = totalBlocks > 0 && currentIndex >= totalBlocks;
        Component title;
        ItemStack icon;
        if (completed) {
            title = Component.translatable("challengecraft.completed");
            icon = new ItemStack(Items.NETHER_STAR);
        } else if (currentIcon.isEmpty()) {
            title = Component.translatable("challengecraft.placeholder.pending");
            icon = ItemStack.EMPTY;
        } else {
            title = currentName;
            icon = currentIcon;
        }
        int accent = completed ? CraftUI.SUCCESS : CraftUI.GOLD;

        // Show only how many blocks the run has unlocked so far — no "/total" fraction (the
        // pool is effectively "every block", so a denominator is meaningless noise).
        float progress = totalBlocks == 0 ? 0f : currentIndex / (float) totalBlocks;
        Component value = Component.translatable("challengecraft.progressive_blocks.unlocked_count", currentIndex);
        return new HudCard("progressive_blocks", icon, title, value, progress, accent, currentIndex);
    }
}
