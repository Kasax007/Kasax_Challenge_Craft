package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;

/**
 * Lying in the bed just clicked until the night is over. When the others stay up (the night does
 * not pass), up again after a little while: the night is not slept away alone.
 */
public final class SleepTask implements BotTask {
    private int ticks;

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        ticks++;
        // Not lying down (monsters near, already day): nothing to wait for.
        if (!body.isSleeping()) return ticks < 20 ? Result.RUNNING : Result.DONE;
        if (ticks > 300) {
            body.stopSleepInBed(true, true);
            return Result.DONE;
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "sleep";
    }
}
