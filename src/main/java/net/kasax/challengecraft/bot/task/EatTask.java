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

    /** Harms more than it feeds (poison, a teleport, who knows what): only when starving. */
    static boolean harmful(ItemStack s) {
        return s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO) || s.is(Items.PUFFERFISH)
                || s.is(Items.SUSPICIOUS_STEW) || s.is(Items.CHORUS_FRUIT);
    }

    /**
     * Feeds, with a chance of a short hunger after (raw chicken, rotten flesh): eaten once hurt -
     * the hunger costs well under a point on the average, and on hard no heart comes back below
     * eighteen (on seed 66 it sat dug in at three hearts with two raw chickens in the pack).
     */
    static boolean poor(ItemStack s) {
        return s.is(Items.ROTTEN_FLESH) || s.is(Items.CHICKEN);
    }

    /** Kept for what it is worth (golden apples): eaten only to save its life. */
    static boolean precious(ItemStack s) {
        return s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    /**
     * The inventory slot of the food worth eating now, or -1: the best plain food; raw chicken
     * and rotten flesh once hurt; a golden apple at six hearts and less; the harmful ones only
     * starving.
     */
    public static int bestFood(BotPlayer body) {
        var inv = body.getInventory().getNonEquipmentItems();
        int best = -1;
        double bestScore = 0;
        boolean starving = body.getFoodData().getFoodLevel() <= 6;
        boolean hurt = body.getHealth() < 14;
        boolean dying = body.getHealth() <= 6;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            FoodProperties food = s.get(DataComponents.FOOD);
            if (food == null) continue;
            boolean ok = harmful(s) ? starving : poor(s) ? hurt || starving : !precious(s) || dying || starving;
            if (!ok) continue;
            double score = food.nutrition() + food.saturation() - (harmful(s) ? 20 : poor(s) ? 10 : 0) + (precious(s) && dying ? 30 : 0);
            if (best < 0 || score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * The hunger points of what it carries that it eats without a second thought (not the poor,
     * harmful or precious kinds): what a stock is counted in.
     */
    public static int foodPoints(BotPlayer body) {
        int points = 0;
        for (ItemStack s : body.getInventory().getNonEquipmentItems()) {
            FoodProperties food = s.get(DataComponents.FOOD);
            if (food != null && !harmful(s) && !poor(s) && !precious(s)) points += food.nutrition() * s.getCount();
        }
        return points;
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
