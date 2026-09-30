package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Takes the crafting table it put down back into the pack before moving on, as players do: the
 * next thing to craft is somewhere else, and a new table costs a log.
 */
public final class PackTableTask implements BotTask {
    private final BlockPos table;
    private int ticks;
    private boolean walking;

    public PackTableTask(BlockPos table) {
        this.table = table;
    }

    /** Whether it is worth doing now: its own table close by, and none in the pack. */
    public static boolean worth(Bot bot) {
        BlockPos t = bot.ownTable;
        if (t == null) return false;
        if (t.distSqr(bot.body().blockPosition()) > 8 * 8 || !bot.body().level().getBlockState(t).is(Blocks.CRAFTING_TABLE)) {
            bot.ownTable = null; // left behind for good, or gone
            return false;
        }
        return bot.body().getInventory().countItem(Items.CRAFTING_TABLE) == 0;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (++ticks > 300) return Result.FAILED;
        if (level.getBlockState(table).is(Blocks.CRAFTING_TABLE)) {
            if (!bot.actions().inReach(table)) {
                if (!walking) {
                    bot.navigator().goNear(table, 3.5);
                    walking = true;
                }
                if (bot.navigator().tick() != BotNavigator.Status.MOVING) walking = false;
                return Result.RUNNING;
            }
            bot.navigator().stop();
            walking = false;
            bot.actions().breakTick(table);
            return Result.RUNNING;
        }
        var drops = BotWorld.drops(level, table, 5, Set.of(Items.CRAFTING_TABLE));
        if (drops.isEmpty() || bot.body().getInventory().countItem(Items.CRAFTING_TABLE) > 0) {
            bot.ownTable = null;
            return Result.DONE;
        }
        if (!walking || bot.navigator().status() != BotNavigator.Status.MOVING) {
            bot.navigator().goPickUp(drops.get(0));
            walking = true;
        }
        bot.navigator().tick();
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "take the crafting table along";
    }
}
