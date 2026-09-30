package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;

/** Sits still for a while (on a horse: gets off afterwards). */
public final class WaitTask implements BotTask {
    private int left;

    public WaitTask(int ticks) {
        this.left = ticks;
    }

    @Override
    public Result tick(Bot bot) {
        bot.body().stopInputs();
        if (--left > 0 && bot.body().isPassenger()) return Result.RUNNING;
        if (bot.body().isPassenger()) bot.body().stopRiding();
        return Result.DONE;
    }

    @Override
    public String describe() {
        return "wait";
    }
}
