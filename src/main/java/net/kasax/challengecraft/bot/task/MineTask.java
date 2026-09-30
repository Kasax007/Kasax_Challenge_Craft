package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Gather {@code count} of an item by breaking blocks that drop it: find the nearest visible such
 * block, walk up to it, break it with the best tool, pick up what falls. When nothing is in sight
 * the bot explores outwards to find more.
 */
public final class MineTask implements BotTask {
    private final String what;
    private final Predicate<BlockState> blocks;
    private final Item item;
    private final int count;
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos target;
    private int collectTicks, explores, idle, debugTicks;
    private boolean walking;

    public MineTask(String what, Predicate<BlockState> blocks, Item item, int count) {
        this.what = what;
        this.blocks = blocks;
        this.item = item;
        this.count = count;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (BotInventory.count(bot.body(), item) >= count) return Result.DONE;
        if (++debugTicks % 100 == 0) {
            bot.say("mine: target=" + (target == null ? "-" : target.toShortString()) + " reach="
                    + (target != null && bot.actions().inReach(target)) + " nav=" + bot.navigator().status()
                    + " walking=" + walking + " collect=" + collectTicks + " skip=" + skip.size());
        }

        // Pick up what fell.
        if (collectTicks > 0) {
            collectTicks--;
            List<ItemEntity> drops = BotWorld.drops(level, bot.body().blockPosition(), 6, item);
            if (!drops.isEmpty()) {
                ItemEntity e = drops.get(0);
                if (!walking || bot.navigator().status() != BotNavigator.Status.MOVING) {
                    bot.navigator().goTo(e.blockPosition());
                    walking = true;
                }
                if (bot.navigator().tick() == BotNavigator.Status.FAILED) collectTicks = 0;
                return Result.RUNNING;
            }
            collectTicks = 0;
            walking = false;
        }

        if (target == null || !blocks.test(level.getBlockState(target))) {
            target = BotWorld.nearest(level, bot.body().blockPosition(), 28, 20, blocks, true, skip);
            walking = false;
            if (target == null) return explore(bot);
            if (!bot.tools().canHarvest(level.getBlockState(target))) {
                bot.say("need a better tool for " + what);
                return Result.FAILED;
            }
        }
        if (!bot.actions().inReach(target)) {
            if (!walking) {
                bot.navigator().goNear(target, 3.6);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED) {
                skip.add(target);
                target = null;
                walking = false;
            } else if (s == BotNavigator.Status.ARRIVED) {
                walking = false;
                if (!bot.actions().inReach(target)) {
                    skip.add(target);
                    target = null;
                }
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        walking = false;
        if (bot.actions().breakTick(target)) {
            if (blocks.test(level.getBlockState(target))) {
                skip.add(target); // refused (protected)
            }
            target = null;
            collectTicks = 60;
            idle = 0;
        } else if (++idle > 400) {
            skip.add(target);
            target = null;
            idle = 0;
        }
        return Result.RUNNING;
    }

    private Result explore(Bot bot) {
        if (explores > 6) return Result.FAILED;
        if (!walking) {
            explores++;
            double a = bot.body().getRandom().nextDouble() * Math.PI * 2;
            BlockPos p = bot.body().blockPosition().offset((int) (Math.cos(a) * 40), 0, (int) (Math.sin(a) * 40));
            BlockPos surface = bot.body().level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
            bot.navigator().goNear(surface, 6);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "mine " + count + " " + what;
    }
}
