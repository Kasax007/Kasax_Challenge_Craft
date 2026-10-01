package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;

/**
 * Up to the top of the world on a pillar of blocks (jump, a block where the feet were), and down
 * again the same way: the block under the feet broken, one at a time (the blocks come back).
 */
public final class BuildLimitTask implements BotTask {
    private int top, startY = Integer.MIN_VALUE, ticks, stuck;
    private BlockPos jumpedFrom;
    private boolean descending;

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        var level = body.level();
        if (startY == Integer.MIN_VALUE) {
            startY = body.getBlockY();
            top = level.getMaxY() - 4;
        }
        if (++ticks > 20 * 60 * 8) return Result.FAILED;
        bot.navigator().stop();
        if (!descending) {
            if (body.getBlockY() >= top) {
                descending = true;
                bot.say("at the top of the world (y " + body.getBlockY() + "): down again");
                return Result.RUNNING;
            }
            BlockPos head = body.blockPosition().above(2);
            if (jumpedFrom == null) {
                if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                    bot.actions().breakTick(head);
                    return Result.RUNNING;
                }
                if (!bot.actions().hasThrowaway()) {
                    bot.say("out of blocks at y " + body.getBlockY());
                    descending = true;
                    return Result.RUNNING;
                }
                if (body.onGround()) {
                    jumpedFrom = body.blockPosition();
                    body.jump = true;
                }
                return Result.RUNNING;
            }
            body.jump = false;
            if (body.getY() > jumpedFrom.getY() + 1.05) {
                bot.actions().placeThrowaway(jumpedFrom);
                jumpedFrom = null;
            } else if (body.onGround() && ++stuck > 40) {
                jumpedFrom = null;
                stuck = 0;
            }
            return Result.RUNNING;
        }
        // Down: the block underfoot broken, until back at the height it started from.
        if (body.getBlockY() <= startY || !body.onGround() && body.getBlockY() <= startY + 1) {
            body.stopInputs();
            return Result.DONE;
        }
        if (!body.onGround()) return Result.RUNNING;
        BlockPos under = body.blockPosition().below();
        if (level.getBlockState(under).getDestroySpeed(level, under) < 0) return Result.DONE;
        bot.actions().breakTick(under);
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "pillar up to the build limit" + (descending ? " (down again)" : "");
    }
}
