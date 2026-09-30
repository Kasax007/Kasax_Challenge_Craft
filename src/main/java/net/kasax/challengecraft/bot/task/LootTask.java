package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/**
 * Goes to a loot chest (shipwreck, ruined portal, temple, ...) and takes what is in it, as a
 * player would on the way: the chest's loot is rolled for the bot when it opens it.
 */
public final class LootTask implements BotTask {
    private final BlockPos pos;
    private boolean walking;
    private int ticks;

    public LootTask(BlockPos pos) {
        this.pos = pos.immutable();
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)) {
            bot.senses().markLooted(pos);
            return Result.FAILED;
        }
        if (++ticks > 1800) {
            bot.senses().markLooted(pos);
            return Result.FAILED;
        }
        if (!bot.actions().inReach(pos)) {
            if (!walking) {
                bot.navigator().goNear(pos, 3.5);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED || s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(pos)) {
                bot.senses().markLooted(pos); // can't get at it: forget it
                return Result.FAILED;
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.lookAt(pos);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        chest.unpackLootTable(body);
        int took = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.isEmpty()) continue;
            ItemStack moving = s.copy();
            body.getInventory().add(moving);
            took += s.getCount() - moving.getCount();
            chest.setItem(i, moving.isEmpty() ? ItemStack.EMPTY : moving);
        }
        chest.setChanged();
        body.getInventory().setChanged();
        bot.senses().markLooted(pos);
        bot.say("looted " + took + " items from a chest at " + pos.toShortString());
        return Result.DONE;
    }

    @Override
    public String describe() {
        return "loot chest at " + pos.toShortString();
    }
}
