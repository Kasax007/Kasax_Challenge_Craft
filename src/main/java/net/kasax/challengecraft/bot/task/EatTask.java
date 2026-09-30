package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Eats the best food it carries (the way a player does: holding use until it is eaten). */
public final class EatTask implements BotTask {
    private boolean started;
    private int ticks;

    /** The inventory slot of the food worth eating now, or -1. Raw chicken and rotten flesh only in need. */
    public static int bestFood(BotPlayer body) {
        var inv = body.getInventory().getNonEquipmentItems();
        int best = -1;
        double bestScore = 0;
        boolean starving = body.getFoodData().getFoodLevel() <= 6;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            FoodProperties food = s.get(DataComponents.FOOD);
            if (food == null) continue;
            boolean bad = s.is(Items.ROTTEN_FLESH) || s.is(Items.CHICKEN) || s.is(Items.SPIDER_EYE)
                    || s.is(Items.POISONOUS_POTATO) || s.is(Items.PUFFERFISH) || s.is(Items.GOLDEN_APPLE)
                    || s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.SUSPICIOUS_STEW) || s.is(Items.CHORUS_FRUIT);
            if (bad && !starving) continue;
            double score = food.nutrition() + food.saturation() - (bad ? 10 : 0);
            if (best < 0 || score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        if (!started) {
            int slot = bestFood(body);
            if (slot < 0 || !body.canEat(false)) return Result.DONE;
            bot.navigator().stop();
            bot.tools().select(slot);
            body.gameMode.useItem(body, body.level(), body.getMainHandItem(), InteractionHand.MAIN_HAND);
            started = true;
            return Result.RUNNING;
        }
        if (++ticks > 80) {
            body.stopUsingItem();
            return Result.DONE;
        }
        return body.isUsingItem() ? Result.RUNNING : Result.DONE;
    }

    @Override
    public String describe() {
        return "eat";
    }
}
