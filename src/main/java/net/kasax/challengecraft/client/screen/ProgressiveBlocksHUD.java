package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
/** Compact HUD card for the current progressive-block-drops target. */
public class ProgressiveBlocksHUD {
    private static ItemStack currentIcon = ItemStack.EMPTY;
    private static Text currentName = Text.empty();
    private static int currentIndex = 0;
    private static int totalBlocks = 0;
    private static boolean active = false;

    public static void update(String targetBlockId, int index, int total) {
        currentIndex = index;
        totalBlocks = total;
        if (targetBlockId == null || targetBlockId.isEmpty()) {
            currentIcon = ItemStack.EMPTY;
            currentName = Text.empty();
        } else {
            Block block = Registries.BLOCK.get(Identifier.of(targetBlockId));
            currentIcon = new ItemStack(block.asItem());
            currentName = block.getName();
        }
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static HudCard buildCard() {
        if (!active || totalBlocks == 0) {
            return null;
        }

        boolean completed = currentIndex >= totalBlocks;
        Text title = completed ? Text.translatable("challengecraft.completed") : currentName;
        ItemStack icon = completed ? new ItemStack(Items.NETHER_STAR) : currentIcon;
        int accent = completed ? CraftUI.SUCCESS : CraftUI.GOLD;

        // Show only how many blocks the run has unlocked so far — no "/total" fraction (the
        // pool is effectively "every block", so a denominator is meaningless noise).
        float progress = totalBlocks == 0 ? 0f : currentIndex / (float) totalBlocks;
        Text value = Text.translatable("challengecraft.progressive_blocks.unlocked_count", currentIndex);
        return new HudCard("progressive_blocks", icon, title, value, progress, accent, currentIndex);
    }
}
