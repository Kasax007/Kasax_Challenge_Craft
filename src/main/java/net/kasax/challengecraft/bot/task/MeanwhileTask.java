package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;

import java.util.function.BooleanSupplier;

/**
 * Work done while something else takes its time - a furnace cooking, say: {@code work} until it
 * ends, the time is up or {@code over} says the waiting is done (then its walk and its digging
 * stop where they are). It always ends as done: work in between that does not come off is no
 * failure of what was waited for.
 */
public final class MeanwhileTask implements BotTask {
    private final BotTask work;
    private final BooleanSupplier over;
    private int left;

    public MeanwhileTask(BotTask work, int ticks, BooleanSupplier over) {
        this.work = work;
        this.left = ticks;
        this.over = over;
    }

    @Override
    public Result tick(Bot bot) {
        if (--left < 0 || over.getAsBoolean()) {
            bot.navigator().stop();
            bot.actions().reset();
            return Result.DONE;
        }
        return work.tick(bot) == Result.RUNNING ? Result.RUNNING : Result.DONE;
    }

    @Override
    public String describe() {
        return "meanwhile: " + work.describe();
    }

    @Override
    public BotTask inner() {
        return work;
    }
}
