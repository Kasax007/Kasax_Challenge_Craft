package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Eats or drinks one of the given items (for "eat X" goals), holding use until it is done. */
public final class ConsumeTask implements BotTask {
    private final Set<Item> items;
    private boolean started;
    private int ticks;

    public ConsumeTask(Set<Item> items) {
        this.items = Set.copyOf(items);
    }

    /** Whether the bot could eat/drink this now (food needs hunger unless it is always edible). */
    public static boolean canConsumeNow(BotPlayer body, ItemStack s) {
        FoodProperties food = s.get(DataComponents.FOOD);
        return food == null || body.canEat(food.canAlwaysEat());
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        if (!started) {
            int slot = -1;
            for (Item i : items) {
                slot = BotInventory.slotOf(body, i);
                if (slot >= 0) break;
            }
            if (slot < 0) return Result.FAILED;
            ItemStack s = body.getInventory().getNonEquipmentItems().get(slot);
            if (!canConsumeNow(body, s)) {
                // Not hungry: keep it and try again later.
                return ++ticks > 2400 ? Result.FAILED : Result.RUNNING;
            }
            bot.navigator().stop();
            bot.tools().select(slot);
            body.gameMode.useItem(body, body.level(), body.getMainHandItem(), InteractionHand.MAIN_HAND);
            started = true;
            ticks = 0;
            return Result.RUNNING;
        }
        if (++ticks > 100) {
            body.stopUsingItem();
            return Result.FAILED;
        }
        return body.isUsingItem() ? Result.RUNNING : Result.DONE;
    }

    @Override
    public String describe() {
        return "eat " + ObtainPlanner.names(items);
    }
}
