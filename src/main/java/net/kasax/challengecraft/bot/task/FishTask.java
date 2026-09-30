package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.mixin.FishingHookAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Fishing: to open water (a source with air above, a few blocks out from the bank), cast, wait for
 * the bite and reel in at once, until something is caught.
 */
public final class FishTask implements BotTask {
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos water;
    private boolean walking;
    private int ticks, castTicks, caught = -1;

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 6000) return Result.FAILED;
        int rod = BotInventory.slotOf(body, Items.FISHING_ROD);
        if (rod < 0) return Result.FAILED;
        int fish = countFish(bot);
        if (caught < 0) caught = fish;
        if (fish > caught) {
            bot.navigator().stop();
            return Result.DONE;
        }
        if (water == null || !open(level, water)) {
            water = BotWorld.nearest(level, body.blockPosition(), 24, 8, s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource(), true, skip);
            if (water == null) water = bot.memory().nearest(level, body.blockPosition(), s -> s.is(net.minecraft.world.level.block.Blocks.WATER), skip);
            if (water == null) return Result.FAILED;
            if (!open(level, water)) {
                skip.add(water);
                water = null;
                return Result.RUNNING;
            }
            walking = false;
        }
        double dist = body.getEyePosition().distanceTo(Vec3.atCenterOf(water));
        if (dist > 8 || castSpot(level, body.blockPosition()) == null && dist > 4) {
            if (!walking) {
                bot.navigator().goNear(water, 5);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED) {
                skip.add(water);
                water = null;
            } else if (s != BotNavigator.Status.MOVING) walking = false;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        bot.tools().select(rod);
        var hook = body.fishing;
        if (hook == null) {
            // Cast out over the water: a spot a few blocks out, thrown with a slight lift (the
            // bobber flies in an arc; aimed down it drops short on the bank).
            BlockPos out = castSpot(level, body.blockPosition());
            if (out == null) {
                skip.add(water);
                water = null;
                return Result.RUNNING;
            }
            body.lookAt(Vec3.atCenterOf(out));
            body.setXRot(Math.min(body.getXRot(), -8f));
            body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
            castTicks = 0;
            return Result.RUNNING;
        }
        // A bite: reel in now. (No bite for a long while, or the hook landed on land: cast again.)
        if (((FishingHookAccessor) hook).challengecraft$nibble() > 0 || ++castTicks > 900 || hook.onGround()) {
            body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
        }
        return Result.RUNNING;
    }

    /** Open water five to nine blocks out from here, the nearest such. */
    private BlockPos castSpot(ServerLevel level, BlockPos from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-9, -3, -9), from.offset(9, 1, 9))) {
            double d = Math.sqrt(p.distSqr(from));
            if (d < 5 || d > 9 || d >= bestD || !open(level, p)) continue;
            best = p.immutable();
            bestD = d;
        }
        return best;
    }

    private static int countFish(Bot bot) {
        var inv = bot.body().getInventory();
        return inv.countItem(Items.COD) + inv.countItem(Items.SALMON) + inv.countItem(Items.TROPICAL_FISH) + inv.countItem(Items.PUFFERFISH);
    }

    /** Water with air above and water around (not a puddle in a corner). */
    private static boolean open(ServerLevel level, BlockPos p) {
        if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getBlockState(p.above()).isAir()) return false;
        int wet = 0;
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, 0, -1), p.offset(1, 0, 1))) if (level.getFluidState(q).is(FluidTags.WATER)) wet++;
        return wet >= 6;
    }

    @Override
    public String describe() {
        return "catch a fish";
    }
}
