package net.kasax.challengecraft.bot.lockout;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.task.ConsumeTask;
import net.kasax.challengecraft.bot.task.EquipTask;
import net.kasax.challengecraft.bot.task.KillTask;
import net.kasax.challengecraft.bot.task.ObtainTask;
import net.kasax.challengecraft.bot.task.SequenceTask;
import net.kasax.challengecraft.bot.task.StationTaskRange;
import net.kasax.challengecraft.bot.task.UseStationTask;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a Lockout goal asks for, in the bot's terms: which items to get, what to kill, craft, put
 * on, eat or use, and roughly how long that takes from where the bot stands. Goals the bot does
 * not know how to do (yet) come back as null, so it leaves those to its opponent.
 */
public final class LockoutGoals {
    private LockoutGoals() {
    }

    /** A way to do a goal: the estimated effort in seconds and how to make the task when chosen. */
    public record Option(double cost, Supplier<BotTask> task) {
    }

    private static final double INF = 1e9;

    /** Goals estimated above this (half an hour) are out of reach for now. */
    private static final double MAX_COST = 1800;

    public static Option plan(Bot bot, ObtainPlanner planner, LockoutBingoGoal goal) {
        Option o = option(bot, planner, goal);
        return o == null || o.cost() > MAX_COST ? null : o;
    }

    private static Option option(Bot bot, ObtainPlanner planner, LockoutBingoGoal goal) {
        return switch (goal.type()) {
            case ITEM -> obtain(bot, planner, items(goal.targets()), 1);
            case ITEM_AMOUNT -> obtain(bot, planner, items(goal.targets()), goal.amount());
            case CRAFT -> craft(bot, planner, items(goal.targets()));
            case KILL -> kill(bot, planner, goal.primaryTarget());
            case CONSUME -> "eat_cake_slice".equals(goal.id()) ? null : consume(bot, planner, items(goal.targets()));
            case EQUIP -> goal.contextTarget().isBlank() || goal.contextTarget().equals(Level.OVERWORLD.identifier().toString())
                    ? equip(bot, planner, items(goal.targets()), goal.amount()) : null;
            case INVENTORY_SET -> collectDistinct(bot, planner, items(goal.targets()), goal.amount());
            case INTERACT -> interact(bot, planner, goal.id());
            default -> null;
        };
    }

    // ---- the goal kinds -----------------------------------------------------------------------

    private static Option obtain(Bot bot, ObtainPlanner planner, Set<Item> items, int count) {
        if (items.isEmpty()) return null;
        double cost = planner.estimate(bot, items, count);
        if (cost >= INF) return null;
        return new Option(cost, () -> new ObtainTask(items, count, planner));
    }

    private static Option craft(Bot bot, ObtainPlanner planner, Set<Item> items) {
        if (items.isEmpty()) return null;
        double cost = planner.estimateCraft(bot, items);
        if (cost >= INF) return null;
        // Crafting counts, not having: one more than now, out of a crafting grid.
        return new Option(cost + 1, () -> new ObtainTask(items, ObtainPlanner.countAny(bot.body(), items) + 1, planner, true));
    }

    private static Option kill(Bot bot, ObtainPlanner planner, String entityId) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entityId)).orElse(null);
        if (type == null) return null;
        double cost = planner.mobEffort(bot, type, INF);
        if (cost >= INF) return null;
        return new Option(cost, () -> new KillTask(Set.of(type), Set.of(), 0, 1));
    }

    private static Option consume(Bot bot, ObtainPlanner planner, Set<Item> items) {
        if (items.isEmpty()) return null;
        double cost = planner.estimate(bot, items, 1);
        if (cost >= INF) return null;
        // Food only goes down when hungry; a full bot would have to wait for that.
        boolean ready = items.stream().anyMatch(i -> ConsumeTask.canConsumeNow(bot.body(), new ItemStack(i)));
        if (!ready) cost += 240;
        return new Option(cost + 3, () -> new SequenceTask("eat " + ObtainPlanner.names(items), List.of(
                () -> new ObtainTask(items, 1, planner),
                () -> new ConsumeTask(items))));
    }

    private static Option equip(Bot bot, ObtainPlanner planner, Set<Item> items, int pieces) {
        List<Item> chosen = cheapest(bot, planner, items, pieces);
        if (chosen == null) return null;
        double cost = 0;
        for (Item i : chosen) cost += planner.estimate(bot, Set.of(i), 1);
        List<Supplier<BotTask>> steps = new ArrayList<>();
        for (Item i : chosen) steps.add(() -> new ObtainTask(Set.of(i), 1, planner).keeping(Set.copyOf(chosen)));
        steps.add(() -> new EquipTask(items, pieces));
        return new Option(cost + 2, () -> new SequenceTask("wear " + pieces + " of " + ObtainPlanner.names(items), steps));
    }

    private static Option collectDistinct(Bot bot, ObtainPlanner planner, Set<Item> items, int kinds) {
        List<Item> chosen = cheapest(bot, planner, items, kinds);
        if (chosen == null) return null;
        double cost = 0;
        for (Item i : chosen) cost += planner.estimate(bot, Set.of(i), 1);
        List<Supplier<BotTask>> steps = new ArrayList<>();
        // Each one without using up the ones before (orange dye must not eat the red and yellow).
        for (Item i : chosen) steps.add(() -> new ObtainTask(Set.of(i), 1, planner).keeping(Set.copyOf(chosen)));
        return new Option(cost, () -> new SequenceTask("collect " + kinds + " kinds of " + ObtainPlanner.names(items), steps));
    }

    private static Option interact(Bot bot, ObtainPlanner planner, String id) {
        Block block = switch (id) {
            case "use_stonecutter" -> Blocks.STONECUTTER;
            case "use_grindstone" -> Blocks.GRINDSTONE;
            case "use_loom" -> Blocks.LOOM;
            case "use_smithing_table" -> Blocks.SMITHING_TABLE;
            case "use_cartography_table" -> Blocks.CARTOGRAPHY_TABLE;
            default -> null;
        };
        if (block == null) return null;
        Item item = block.asItem();
        boolean near = nearby(bot, block);
        double cost = near ? 5 : planner.estimate(bot, Set.of(item), 1);
        if (cost >= INF) return null;
        return new Option(cost + 3, () -> new SequenceTask("use " + block.getName().getString(), List.of(
                () -> nearby(bot, block) ? null : new ObtainTask(Set.of(item), 1, planner),
                () -> new UseStationTask(block, item))));
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** The {@code n} distinct items that are quickest to get (held ones first), or null if too few can be got. */
    private static List<Item> cheapest(Bot bot, ObtainPlanner planner, Set<Item> items, int n) {
        List<Item> sorted = items.stream()
                .filter(i -> planner.estimate(bot, Set.of(i), 1) < INF)
                .sorted(Comparator.comparingDouble(i -> planner.estimate(bot, Set.of(i), 1)))
                .toList();
        return sorted.size() < n ? null : sorted.subList(0, n);
    }

    private static boolean nearby(Bot bot, Block block) {
        return BotWorld.nearest((ServerLevel) bot.body().level(), bot.body().blockPosition(), StationTaskRange.RANGE, 8,
                s -> s.is(block), false, Set.of()) != null;
    }

    /** The items a goal's target list names: item ids, and item tags written as "#namespace:path". */
    public static Set<Item> items(List<String> targets) {
        Set<Item> out = new LinkedHashSet<>();
        for (String t : targets) {
            if (t.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(t.substring(1)));
                for (Item item : BuiltInRegistries.ITEM) if (new ItemStack(item).is(tag)) out.add(item);
            } else {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(t));
                if (item != Items.AIR) out.add(item);
            }
        }
        return out;
    }
}
