package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * The last way out when no path is found from where it stands (a pit on a hillside, an overhang
 * over a step): a two-high way dug straight out, with whatever is in hand, towards the most open
 * side - a few steps, then the paths again. (Seed 77: ten minutes "stuck" by the spawn, then the
 * night came.)
 */
public final class DigOutTask implements BotTask {
    private Direction dir;
    private int steps, ticks;
    private BlockPos startedAt;

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 1200 || steps >= 6) return Result.DONE;
        BlockPos feet = body.blockPosition();
        if (dir == null) {
            dir = mostOpen(level, feet);
            startedAt = feet;
        }
        BlockPos ahead = feet.relative(dir);
        // Up a step if the ground ahead rises: the block over the head too.
        boolean up = !level.getBlockState(ahead).getCollisionShape(level, ahead).isEmpty()
                && level.getBlockState(ahead.above(2)).getCollisionShape(level, ahead.above(2)).isEmpty() && ahead.getY() < 300;
        BlockPos[] cells = up ? new BlockPos[] {feet.above(2), ahead.above(2), ahead.above()} : new BlockPos[] {ahead, ahead.above()};
        body.stopInputs();
        for (BlockPos c : cells) {
            var s = level.getBlockState(c);
            if (s.getCollisionShape(level, c).isEmpty()) continue;
            if (s.getDestroySpeed(level, c) < 0 || !s.getFluidState().isEmpty()) {
                dir = dir.getClockWise(); // (bedrock, water: another side)
                return Result.RUNNING;
            }
            bot.tools().equipFor(s);
            bot.actions().breakTick(c);
            return Result.RUNNING;
        }
        // Open: a step on.
        body.setYRot(dir.toYRot());
        body.forward = 1f;
        body.jump = up;
        if (feet.distManhattan(startedAt) > steps) steps = feet.distManhattan(startedAt);
        return Result.RUNNING;
    }

    /** The side with the most room: open cells two high within six blocks. */
    private static Direction mostOpen(ServerLevel level, BlockPos feet) {
        Direction best = Direction.NORTH;
        int bestScore = -1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int score = 0;
            for (int i = 1; i <= 6; i++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = feet.relative(d, i).above(dy);
                    if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                            && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) score += 7 - i;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    @Override
    public String describe() {
        return "dig my way out";
    }
}
