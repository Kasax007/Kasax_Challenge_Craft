package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.world.item.Item;

import java.util.Set;

/**
 * "Have {@code count} of these items": asks the {@link ObtainPlanner} for the next step, runs it,
 * asks again, until the items are in the inventory. A step that fails marks its way as failed, so
 * the next plan takes another (other trees, a different recipe, hunting instead of mining).
 */
public final class ObtainTask implements BotTask {
    private static final int MAX_FAILURES = 8;

    private final Set<Item> items;
    private final int count;
    private final ObtainPlanner planner;
    private BotTask step;
    private String stepKey;
    private int failures;

    public ObtainTask(Set<Item> items, int count) {
        this(items, count, new ObtainPlanner());
    }

    public ObtainTask(Set<Item> items, int count, ObtainPlanner planner) {
        this.items = Set.copyOf(items);
        this.count = count;
        this.planner = planner;
    }

    @Override
    public Result tick(Bot bot) {
        if (step == null) {
            ObtainPlanner.Plan p = planner.plan(bot, items, count);
            if (p instanceof ObtainPlanner.Have) return Result.DONE;
            if (p instanceof ObtainPlanner.Stuck s) {
                bot.say("can't get " + ObtainPlanner.names(items) + ": " + s.why());
                return Result.FAILED;
            }
            ObtainPlanner.Step st = (ObtainPlanner.Step) p;
            step = st.task();
            stepKey = st.key();
            bot.say("  " + step.describe());
        }
        Result r;
        try {
            r = step.tick(bot);
        } catch (RuntimeException e) {
            net.kasax.challengecraft.bot.BotManager.LOG.warn("[Bot] step {} crashed", step.describe(), e);
            r = Result.FAILED;
        }
        if (r == Result.RUNNING) return Result.RUNNING;
        bot.navigator().stop();
        bot.actions().reset();
        if (r == Result.FAILED) {
            bot.say("  x " + step.describe());
            planner.markFailed(stepKey, bot.body().level().getGameTime());
            if (++failures > MAX_FAILURES) return Result.FAILED;
        }
        step = null;
        return Result.RUNNING;
    }

    public BotTask step() {
        return step;
    }

    @Override
    public String describe() {
        return "get " + ObtainPlanner.names(items) + " x" + count;
    }
}
