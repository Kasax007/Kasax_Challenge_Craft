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

    /**
     * Puts the right tool for {@code state} into the hand (a hotbar slot, swapping if needed): the
     * cheapest one that does the job, as a player keeps the diamond pickaxe for what needs it and
     * digs stone with a stone one. A tool about to break is only used when nothing else works.
     */
    public void equipFor(BlockState state) {
        Inventory inv = bot.getInventory();
        float hardness = Math.max(state.getDestroySpeed(bot.level(), bot.blockPosition()), 0.01f);
        boolean needsTool = state.requiresCorrectToolForDrops();
        int bestSlot = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < inv.getNonEquipmentItems().size(); i++) {
            ItemStack s = inv.getNonEquipmentItems().get(i);
            double score = score(s, state, hardness, needsTool);
            if (score < bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        // The empty hand, if nothing beats it (keeps tools from wearing on dirt and leaves).
        if (bestSlot >= 0 && bestScore < score(ItemStack.EMPTY, state, hardness, needsTool)) select(bestSlot);
        else if (!inv.getSelectedItem().isEmpty() && inv.getSelectedItem().isDamageableItem()) selectEmptyHand();
    }

    /** Lower is better: ticks to break, then how valuable the tool is; wrong or worn-out tools last. */
    private static double score(ItemStack s, BlockState state, float hardness, boolean needsTool) {
        boolean harvests = !needsTool || !s.isEmpty() && s.isCorrectToolForDrops(state);
        double ticks = ticksWith(s, state, hardness);
        double score = ticks;
        if (!harvests) score += 10_000;
        if (!s.isEmpty() && s.isDamageableItem()) {
            // A faster tool is worth it only when it saves real time: value tools by durability.
            score += s.getMaxDamage() / 25.0;
            // Of two alike the more worn one first: used up one after the other (and gone), not
            // all worn down together to a pack of nearly broken ones.
            score += (s.getMaxDamage() - s.getDamageValue()) / (double) Math.max(1, s.getMaxDamage()) * 0.5;
        }
        return score;
    }

    /** An empty hand (for clicking a bed, a bell, a lever). */
    public void selectEmptyHandPublic() {
        selectEmptyHand();
    }

    private void selectEmptyHand() {
        Inventory inv = bot.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getNonEquipmentItems().get(i).isEmpty()) {
                inv.setSelectedSlot(i);
                return;
            }
        }
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
