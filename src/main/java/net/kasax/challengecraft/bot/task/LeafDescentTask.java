package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;

/**
 * Down out of a tree's crown (the world spawn can be up there): the leaves under the feet broken
 * one after the other - a block's fall each, not the jump off the top the fall guard forbids.
 */
public final class LeafDescentTask implements BotTask {
    private int ticks;

    /** Whether it stands on leaves with the ground more than a safe fall below. */
    public static boolean upATree(Bot bot) {
        var level = bot.body().level();
        BlockPos feet = bot.body().blockPosition();
        if (!level.getBlockState(feet.below()).is(BlockTags.LEAVES)) return false;
        var ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet);
        return ground.getY() < feet.getY() - 3;
    }

    @Override
    public Result tick(Bot bot) {
        if (++ticks > 600) return Result.FAILED;
        var body = bot.body();
        body.stopInputs();
        if (!body.onGround()) return Result.RUNNING;
        BlockPos below = body.blockPosition().below();
        var state = body.level().getBlockState(below);
        // (Leaves, and the vines or the odd log in a crown: whatever, it is only a block's fall.)
        if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.is(net.minecraft.world.level.block.Blocks.VINE)) return Result.DONE;
        bot.actions().breakTick(below);
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "down out of the tree";
    }
}
