package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Enchants one of these items at an enchanting table (its own, put down if need be): the item and
 * lapis in, the dearest option its levels pay for, the item back out.
 */
public final class EnchantTask extends StationTask {
    private final Set<Item> items;
    private int tries;

    public EnchantTask(Set<Item> items) {
        super(Blocks.ENCHANTING_TABLE, Items.ENCHANTING_TABLE);
        this.items = Set.copyOf(items);
    }

    @Override
    protected Result work(Bot bot) {
        var body = bot.body();
        if (++tries > 20) return Result.FAILED;
        var inv = body.getInventory().getNonEquipmentItems();
        int itemSlot = -1, lapisSlot = -1;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (itemSlot < 0 && items.contains(s.getItem()) && !s.isEnchanted()) itemSlot = i;
            if (lapisSlot < 0 && s.is(Items.LAPIS_LAZULI)) lapisSlot = i;
        }
        if (itemSlot < 0 || lapisSlot < 0 || body.experienceLevel < 1) return Result.FAILED;
        BlockPos table = station;
        EnchantmentMenu menu = new EnchantmentMenu(0, body.getInventory(), ContainerLevelAccess.create((ServerLevel) body.level(), table));
        menu.getSlot(0).set(inv.get(itemSlot).split(1));
        menu.getSlot(1).set(inv.get(lapisSlot).split(Math.min(3, inv.get(lapisSlot).getCount())));
        menu.slotsChanged(menu.getSlot(0).container);
        int pick = -1;
        for (int i = 2; i >= 0; i--) {
            if (menu.costs[i] > 0 && body.experienceLevel >= menu.costs[i] && menu.getSlot(1).getItem().getCount() >= i + 1) {
                pick = i;
                break;
            }
        }
        boolean done = pick >= 0 && menu.clickMenuButton(body, pick);
        menu.removed(body); // item and lapis back into the pack
        body.getInventory().setChanged();
        return done ? Result.DONE : Result.RUNNING;
    }

    @Override
    public String describe() {
        return "enchant " + net.kasax.challengecraft.bot.plan.ObtainPlanner.names(items);
    }
}
