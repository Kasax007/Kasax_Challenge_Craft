package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A crossbow loaded with a firework rocket (and kept loaded): the rocket in the off hand, the
 * crossbow drawn in the main hand until it is charged, as a player does it.
 */
public final class LoadCrossbowTask implements BotTask {
    private int ticks, drawTicks;

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (loaded(body)) {
            body.stopUsingItem();
            return Result.DONE;
        }
        if (++ticks > 400) return Result.FAILED;
        int bow = BotInventory.slotOf(body, Items.CROSSBOW);
        if (bow < 0) return Result.FAILED;
        // The rocket into the off hand (whatever was there goes back into the pack).
        if (!body.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            int rocket = BotInventory.slotOf(body, Items.FIREWORK_ROCKET);
            if (rocket < 0) return Result.FAILED;
            ItemStack r = body.getInventory().getItem(rocket);
            ItemStack old = body.getOffhandItem().copy();
            body.setItemSlot(EquipmentSlot.OFFHAND, r.copy());
            body.getInventory().setItem(rocket, old);
            return Result.RUNNING;
        }
        bot.tools().select(bow);
        // Loaded already with something else (an arrow): shot off into the air first.
        if (CrossbowItem.isCharged(body.getMainHandItem())) {
            body.setXRot(-60f);
            body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
            return Result.RUNNING;
        }
        body.stopInputs();
        if (!body.isUsingItem()) {
            drawTicks = 0;
            body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
        } else if (++drawTicks > CrossbowItem.getChargeDuration(body.getMainHandItem(), body) + 2) {
            body.releaseUsingItem();
        }
        return Result.RUNNING;
    }

    /** A crossbow it holds with a firework rocket in it. */
    public static boolean loaded(BotPlayer body) {
        for (ItemStack st : body.getInventory().getNonEquipmentItems()) {
            if (!st.is(Items.CROSSBOW)) continue;
            var charged = st.get(DataComponents.CHARGED_PROJECTILES);
            if (charged != null && charged.contains(Items.FIREWORK_ROCKET)) return true;
        }
        return false;
    }

    @Override
    public String describe() {
        return "load a crossbow with a firework rocket";
    }
}
