package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.Set;

/**
 * The common part of working at a crafting table or furnace: use one close by if there is one
 * (walking over to it), otherwise put down the one in the inventory. Subclasses do the work once
 * the station is in reach.
 */
abstract class StationTask implements BotTask {
    private final Block block;
    private final Item item;
    private final Set<BlockPos> unreachable = new HashSet<>();
    protected BlockPos station;
    private boolean walking;
    private int placeTries;

    StationTask(Block block, Item item) {
        this.block = block;
        this.item = item;
    }

    /** Whether this task needs the station at all (a 2×2 recipe does not). */
    protected boolean needsStation() {
        return true;
    }

    @Override
    public final Result tick(Bot bot) {
        if (!needsStation()) return work(bot);
        ServerLevel level = (ServerLevel) bot.body().level();
        if (station != null && !level.getBlockState(station).is(block)) station = null;
        if (station == null) {
            station = BotWorld.nearest(level, bot.body().blockPosition(), StationTaskRange.RANGE, 8, s -> s.is(block), false, unreachable);
            walking = false;
            if (station == null) {
                if (BotInventory.slotOf(bot.body(), item) < 0) {
                    bot.say("no " + block.getName().getString() + " to use");
                    return Result.FAILED;
                }
                station = bot.actions().placeNearby(item);
                if (station == null) {
                    // Nowhere to put it here: step aside and try again.
                    if (++placeTries > 4) return Result.FAILED;
                    BlockPos p = bot.body().blockPosition().offset(bot.body().getRandom().nextInt(7) - 3, 0,
                            bot.body().getRandom().nextInt(7) - 3);
                    bot.interject(new GoToTask(p, 1));
                    return Result.RUNNING;
                }
            }
        }
        if (!bot.actions().inReach(station)) {
            if (!walking) {
                bot.navigator().goNear(station, 3.5);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED || s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(station)) {
                unreachable.add(station);
                station = null;
            }
            return Result.RUNNING;
        }
        if (walking) {
            bot.navigator().stop();
            walking = false;
        }
        bot.body().lookAt(station);
        return work(bot);
    }

    /** Called every tick once the station is in reach. */
    protected abstract Result work(Bot bot);
}
