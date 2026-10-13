package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;

/**
 * Sits on a mount for a while and gets off afterwards (thrown off: done at once). On foot it is
 * done at once too - unless made with {@link #pause}, which waits on foot as well.
 */
public final class WaitTask implements BotTask {
    private int left;
    private boolean onFoot;

    public WaitTask(int ticks) {
        this.left = ticks;
    }

    /**
     * Stands still for that long, mounted or not (the hearts coming back before a fall, a landing
     * waited for): a plain wait was over at once on foot, and the heal before the fall was none.
     */
    public static WaitTask pause(int ticks) {
        WaitTask w = new WaitTask(ticks);
        w.onFoot = true;
        return w;
    }

    @Override
    public Result tick(Bot bot) {
        bot.body().stopInputs();
        if (--left > 0 && (onFoot || bot.body().isPassenger())) return Result.RUNNING;
        if (bot.body().isPassenger()) bot.body().stopRiding();
        return Result.DONE;
    }

    @Override
    public String describe() {
        return "wait";
    }
}
