package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
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
            station = BotWorld.nearest(level, bot.body().blockPosition(), StationTaskRange.RANGE, 8, s -> s.is(block), false, bot.unreachableStations);
            walking = false;
            if (station == null) {
                if (BotInventory.slotOf(bot.body(), item) < 0) {
                    bot.say("no " + block.getName().getString() + " to use");
                    return Result.FAILED;
                }
                // Carving a niche for it (see below): on with that first.
                if (niche != null) {
                    if (!bot.actions().breakTick(niche)) return Result.RUNNING;
                    niche = null;
                }
                station = bot.actions().placeNearby(item);
                placedHere = station;
                if (block == Blocks.CRAFTING_TABLE && station != null) bot.ownTable = station;
                if (station == null && bot.body().isInWater()) {
                    // Swimming: ashore first (nothing stands on water).
                    if (++placeTries > 4) return Result.FAILED;
                    bot.interject(new NavGoalTask("ashore to put down a " + block.getName().getString(),
                            (lv, p) -> lv.getFluidState(p).isEmpty() && lv.getFluidState(p.below()).isEmpty()
                                    && !lv.getBlockState(p.below()).getCollisionShape(lv, p.below()).isEmpty(),
                            b -> b.body().blockPosition(), 1200));
                    return Result.RUNNING;
                }
                if (station == null) {
                    // Nowhere to put it here (down a narrow shaft, say): a block out of the wall
                    // beside the feet, as a player makes room; else a step aside and again.
                    if (!carved) {
                        carved = true;
                        niche = niche(bot);
                        if (niche != null) return Result.RUNNING;
                    }
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
                bot.unreachableStations.add(station);
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

    private BlockPos placedHere, niche;
    private boolean carved;

    /** A block beside the feet to take out for room: breakable, a floor under it, nothing liquid by it. */
    private static BlockPos niche(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BlockPos feet = bot.body().blockPosition();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos p = feet.relative(d);
            var st = level.getBlockState(p);
            if (st.isAir() || !st.getFluidState().isEmpty() || st.getDestroySpeed(level, p) < 0 || st.getDestroySpeed(level, p) > 5) continue;
            if (level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) continue;
            boolean wet = false;
            for (net.minecraft.core.Direction n : net.minecraft.core.Direction.values()) if (!level.getFluidState(p.relative(n)).isEmpty()) wet = true;
            if (wet) continue;
            return p;
        }
        return null;
    }

    /** Called every tick once the station is in reach. */
    protected abstract Result work(Bot bot);
}
