package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;

/** Walk to a block (or near it). */
public final class GoToTask implements BotTask {
    private final BlockPos target;
    private final double range;
    private boolean started;
    private int ticks, limit = Integer.MAX_VALUE;
    private boolean sprint;

    /** Running, whatever the difficulty (getting away from something). */
    public GoToTask sprinting() {
        sprint = true;
        return this;
    }

    public GoToTask(BlockPos target, double range) {
        this.target = target.immutable();
        this.range = range;
    }

    @Override
    public Result tick(Bot bot) {
        // A walk that takes far longer than its distance says is not going to arrive.
        if (++ticks > limit) return Result.FAILED;
        if (!started) {
            started = true;
            limit = 300 + (int) (30 * Math.sqrt(target.distSqr(bot.body().blockPosition())));
            if (range <= 0) bot.navigator().goTo(target);
            else bot.navigator().goStandNear(target, range);
        }
        bot.body().hurry = sprint;
        return switch (bot.navigator().tick()) {
            case ARRIVED -> Result.DONE;
            case FAILED -> Result.FAILED;
            default -> Result.RUNNING;
        };
    }

    @Override
    public String describe() {
        return "go to " + target.toShortString();
    }

    /** The navigator status as a task result, for tasks that walk as part of their work. */
    public static Result walk(BotNavigator.Status s) {
        return s == BotNavigator.Status.FAILED ? Result.FAILED : Result.RUNNING;
    }
}
