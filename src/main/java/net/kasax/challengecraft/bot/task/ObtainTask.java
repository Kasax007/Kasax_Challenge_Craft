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
    private static final int MAX_FAILURES = 4;

    private final Set<Item> items;
    private final int count;
    private final ObtainPlanner planner;
    private final boolean craftOnly;
    private Set<Item> keep = Set.of();
    /** How many of each of {@link #keep} it held when it began: those stay, the rest may be used. */
    private java.util.Map<Item, Integer> kept;
    private BotTask step;
    private ObtainPlanner.Step stepPlan;
    private int failures, recheck;
    /** The pack as it was when the step began, and the same step done for nothing how often in a row. */
    private long packBefore;
    private String idleKey;
    private int idleRuns;

    public ObtainTask(Set<Item> items, int count) {
        this(items, count, new ObtainPlanner());
    }

    public ObtainTask(Set<Item> items, int count, ObtainPlanner planner) {
        this(items, count, planner, false);
    }

    /** With {@code craftOnly}, the items have to come out of a crafting grid (for "craft X" goals). */
    public ObtainTask(Set<Item> items, int count, ObtainPlanner planner, boolean craftOnly) {
        this.items = Set.copyOf(items);
        this.count = count;
        this.planner = planner;
        this.craftOnly = craftOnly;
    }

    /** What it gets, and how many in all. */
    public Set<Item> items() {
        return items;
    }

    public int count() {
        return count;
    }

    /** Items not to use up while getting these, as many as it holds when it begins (see {@link ObtainPlanner#plan(Bot, java.util.Collection, int, boolean, java.util.Map)}). */
    public ObtainTask keeping(Set<Item> keep) {
        this.keep = Set.copyOf(keep);
        return this;
    }

    @Override
    public Result tick(Bot bot) {
        // (What it held of them at the start, not all it will ever hold: the red dye made for the
        // orange one is there to be used - kept along with the first, it was made again and
        // again, thirty-six times in a self-play game, and never an orange one.)
        if (kept == null) {
            kept = new java.util.HashMap<>();
            for (Item k : keep) kept.put(k, ObtainPlanner.countAny(bot.body(), Set.of(k)));
        }
        if (step == null) {
            ObtainPlanner.Plan p = planner.plan(bot, items, count, craftOnly, kept);
            if (p instanceof ObtainPlanner.Have) return Result.DONE;
            if (p instanceof ObtainPlanner.Stuck s) {
                bot.say("can't get " + ObtainPlanner.names(items) + ": " + s.why());
                return Result.FAILED;
            }
            ObtainPlanner.Step st = (ObtainPlanner.Step) p;
            // Off to something else than crafting: take the table along first.
            // (A furnace is no table: smelting means a wait and a walk, the table along first.)
            if (!(st.task() instanceof StationTask station && station.usesTable()) && PackTableTask.worth(bot)) {
                step = new PackTableTask(bot.ownTable);
                stepPlan = null;
                return Result.RUNNING;
            }
            step = st.task();
            stepPlan = st;
            packBefore = pack(bot);
            bot.say("  " + step.describe());
        }
        // Out searching for one kind (spruce) while another that does as well (oak) comes into
        // sight: the plan again, every few seconds, and the other way if it now comes out first.
        if (step instanceof MineTask m && m.searching() && stepPlan != null && ++recheck % 100 == 0) {
            ObtainPlanner.Plan p = planner.plan(bot, items, count, craftOnly, kept);
            // (Only another source of the same thing: not off to something else the plan now
            // puts first - halfway down a staircase for iron, back up for wood.)
            if (p instanceof ObtainPlanner.Step st && !st.key().equals(stepPlan.key())
                    && st.task() instanceof MineTask other && other.items().stream().anyMatch(m.items()::contains)
                    && st.sources().stream().anyMatch(b -> b instanceof net.minecraft.world.level.block.Block block && planner.seen(bot, block) != null)) {
                bot.navigator().stop();
                bot.say("  other way now in sight: " + st.task().describe());
                step = st.task();
                stepPlan = st;
            }
        }
        Result r;
        try {
            r = step.tick(bot);
        } catch (RuntimeException e) {
            net.kasax.challengecraft.bot.BotManager.LOG.warn("[Bot] step {} crashed", step.describe(), e);
            r = Result.FAILED;
        }
        if (r == Result.RUNNING) return Result.RUNNING;
        if (stepPlan == null) { // the table packed (or not): on with the plan
            step = null;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        bot.actions().reset();
        // Done, and the pack just as it was - the third time running for the same step: it does
        // nothing, as a failure (a "buy flint" that was done at once without a trade, planned
        // again and again, 173 times in half a minute of a self-play game).
        if (r == Result.DONE) {
            if (pack(bot) == packBefore && stepPlan.key().equals(idleKey)) {
                if (++idleRuns >= 3) {
                    r = Result.FAILED;
                    idleRuns = 0;
                }
            } else {
                idleKey = pack(bot) == packBefore ? stepPlan.key() : null;
                idleRuns = idleKey == null ? 0 : 1;
            }
        }
        if (r == Result.FAILED) {
            bot.say("  x " + step.describe());
            if (stepPlan != null && !(step instanceof CraftTask c && c.missingIngredients())) planner.markFailed(stepPlan, bot.body().level().getGameTime());
            if (++failures > MAX_FAILURES) return Result.FAILED;
        }
        step = null;
        return Result.RUNNING;
    }

    /** What is in the pack, as one number (the same pack, the same number). */
    private static long pack(Bot bot) {
        long h = 17;
        for (var st : bot.body().getInventory().getNonEquipmentItems()) {
            h = h * 31 + (st.isEmpty() ? 0 : net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(st.getItem()) * 128L + st.getCount());
        }
        return h;
    }

    @Override
    public String status() {
        return describe() + (step == null ? "" : " > " + step.status());
    }

    public BotTask step() {
        return step;
    }

    @Override
    public String describe() {
        return (craftOnly ? "craft " : "get ") + ObtainPlanner.names(items) + " x" + count;
    }

    @Override
    public BotTask inner() {
        return step;
    }
}
