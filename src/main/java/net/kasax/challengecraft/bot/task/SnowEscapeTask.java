package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Sunk into powder snow (slid in, fallen in from a ledge): the snow round the body dug away (it
 * goes at a touch) and out with a jump, before the cold gets to the hearts.
 */
public final class SnowEscapeTask implements BotTask {
    private int ticks;

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        var level = body.level();
        if (!body.isInPowderSnow && !level.getBlockState(body.blockPosition()).is(Blocks.POWDER_SNOW)) return Result.DONE;
        if (++ticks > 300) return Result.FAILED;
        bot.navigator().stop();
        body.stopInputs();
        BlockPos feet = body.blockPosition();
        // Above first (the way out), then where it stands.
        for (BlockPos p : new BlockPos[]{feet.above(2), feet.above(), feet}) {
            if (level.getBlockState(p).is(Blocks.POWDER_SNOW)) {
                bot.actions().breakTick(p);
                return Result.RUNNING;
            }
        }
        // Up with jumps (it lets the body rise slowly), towards the nearest firm ground about.
        BlockPos firm = null;
        double best = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(feet.offset(-4, -1, -4), feet.offset(4, 3, 4))) {
            var st = level.getBlockState(q);
            if (st.is(Blocks.POWDER_SNOW) || st.getCollisionShape(level, q).isEmpty()) continue;
            if (!level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty() || level.getBlockState(q.above()).is(Blocks.POWDER_SNOW)) continue;
            double d = q.distSqr(feet);
            if (d < best) {
                best = d;
                firm = q.immutable();
            }
        }
        if (firm != null) {
            body.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(firm.above()));
            body.setXRot(0);
            body.forward = 1f;
        }
        body.jump = true;
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "out of the powder snow";
    }
}
