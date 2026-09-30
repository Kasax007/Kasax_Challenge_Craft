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

    public GoToTask(BlockPos target, double range) {
        this.target = target.immutable();
        this.range = range;
    }

    @Override
    public Result tick(Bot bot) {
        if (!started) {
            started = true;
            if (range <= 0) bot.navigator().goTo(target);
            else bot.navigator().goStandNear(target, range);
        }
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
