package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Set;

/** Puts on armour from the inventory until {@code pieces} of the wanted items are worn. */
public final class EquipTask implements BotTask {
    private final Set<Item> items;
    private final int pieces;

    public EquipTask(Set<Item> items, int pieces) {
        this.items = Set.copyOf(items);
        this.pieces = pieces;
    }

    public static int worn(BotPlayer body, Set<Item> items) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (items.contains(body.getItemBySlot(slot).getItem())) n++;
        }
        return n;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        if (worn(body, items) >= pieces) return Result.DONE;
        var inv = body.getInventory().getNonEquipmentItems();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s.isEmpty() || !items.contains(s.getItem())) continue;
            Equippable eq = s.get(DataComponents.EQUIPPABLE);
            if (eq == null) continue;
            ItemStack old = body.getItemBySlot(eq.slot());
            if (items.contains(old.getItem())) continue; // already wearing a wanted piece there
            // One piece a tick, the way a player right-clicks them on one after another.
            body.setItemSlot(eq.slot(), s.split(1));
            if (!old.isEmpty()) CraftTask.give(body, old);
            body.getInventory().setChanged();
            return Result.RUNNING;
        }
        bot.say("nothing more to put on for " + ObtainPlanner.names(items));
        return Result.FAILED;
    }

    @Override
    public String describe() {
        return "put on " + ObtainPlanner.names(items);
    }
}
