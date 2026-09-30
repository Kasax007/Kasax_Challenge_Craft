package net.kasax.challengecraft.bot;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the bot knows about its tools: how long a block takes to break with the best one it has,
 * and which slot holds that tool. Mirrors the vanilla digging speed (tool speed, the ×3.33 penalty
 * without the right tool); enchantments and effects only make the real thing faster.
 */
public final class BotTools {
    private final BotPlayer bot;

    public BotTools(BotPlayer bot) {
        this.bot = bot;
    }

    /** Ticks to break {@code state} with the best tool in the inventory (hardness 0: 1 tick). */
    public double breakTicks(BlockState state) {
        float hardness = state.getDestroySpeed(bot.level(), bot.blockPosition());
        if (hardness < 0) return Double.POSITIVE_INFINITY;
        if (hardness == 0) return 1;
        double best = ticksWith(ItemStack.EMPTY, state, hardness);
        Inventory inv = bot.getInventory();
        for (int i = 0; i < inv.getNonEquipmentItems().size(); i++) {
            ItemStack s = inv.getNonEquipmentItems().get(i);
            if (!s.isEmpty()) best = Math.min(best, ticksWith(s, state, hardness));
        }
        return best;
    }

    private static double ticksWith(ItemStack tool, BlockState state, float hardness) {
        float speed = tool.isEmpty() ? 1f : tool.getDestroySpeed(state);
        boolean harvest = !state.requiresCorrectToolForDrops() || (!tool.isEmpty() && tool.isCorrectToolForDrops(state));
        double perTick = speed / hardness / (harvest ? 30.0 : 100.0);
        return Math.ceil(1.0 / perTick);
    }

    /** Whether breaking {@code state} with the best tool would drop its item. */
    public boolean canHarvest(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return true;
        for (ItemStack s : bot.getInventory().getNonEquipmentItems()) {
            if (!s.isEmpty() && s.isCorrectToolForDrops(state)) return true;
        }
        return false;
    }

    /** Puts the best tool for {@code state} into the hand (a hotbar slot, swapping if needed). */
    public void equipFor(BlockState state) {
        Inventory inv = bot.getInventory();
        float hardness = state.getDestroySpeed(bot.level(), bot.blockPosition());
        int bestSlot = -1;
        double best = ticksWith(inv.getSelectedItem(), state, Math.max(hardness, 0.01f));
        for (int i = 0; i < inv.getNonEquipmentItems().size(); i++) {
            ItemStack s = inv.getNonEquipmentItems().get(i);
            if (s.isEmpty()) continue;
            double t = ticksWith(s, state, Math.max(hardness, 0.01f));
            // Prefer the tool that also harvests; among equals keep what is in hand.
            if (t < best) {
                best = t;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) select(bestSlot);
    }

    /** Makes inventory slot {@code slot} the one in hand. */
    public void select(int slot) {
        Inventory inv = bot.getInventory();
        if (Inventory.isHotbarSlot(slot)) {
            inv.setSelectedSlot(slot);
            return;
        }
        int hand = inv.getSelectedSlot();
        ItemStack a = inv.getNonEquipmentItems().get(slot);
        ItemStack b = inv.getNonEquipmentItems().get(hand);
        inv.getNonEquipmentItems().set(hand, a);
        inv.getNonEquipmentItems().set(slot, b);
        inv.setChanged();
    }
}
