package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;

import java.util.List;
import java.util.function.Supplier;

/**
 * Several tasks one after the other; each is made only when its turn comes, so it can look at
 * how things stand then (what the earlier ones brought in). Fails as soon as one fails.
 */
public final class SequenceTask implements BotTask {
    private final String what;
    private final List<Supplier<BotTask>> steps;
    private int index;
    private BotTask current;

    public SequenceTask(String what, List<Supplier<BotTask>> steps) {
        this.what = what;
        this.steps = List.copyOf(steps);
    }

    @Override
    public Result tick(Bot bot) {
        while (true) {
            if (current == null) {
                if (index >= steps.size()) return Result.DONE;
                current = steps.get(index++).get();
                if (current == null) continue; // nothing to do for this step
                bot.say("  " + current.describe());
            }
            Result r = current.tick(bot);
            if (r == Result.RUNNING) return Result.RUNNING;
            bot.navigator().stop();
            current = null;
            if (r == Result.FAILED) return Result.FAILED;
        }
    }

    @Override
    public String describe() {
        return what;
    }

    @Override
    public BotTask inner() {
        return current;
    }
}
