package net.kasax.challengecraft.bot.lockout;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.BotSenses;
import net.kasax.challengecraft.bot.task.GoToBiomeTask;
import net.kasax.challengecraft.bot.task.MineTask;
import net.kasax.challengecraft.bot.task.UseOnMobTask;
import net.kasax.challengecraft.bot.task.VisitStructureTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
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

    /**
     * A way to do a goal: the estimated effort in seconds, how to make the task when chosen, and
     * what the bot will hold afterwards (the goal's items and everything made on the way: the
     * furnace, the pickaxe, spare iron), for planning which goal to do next.
     */
    public record Option(double cost, Supplier<BotTask> task, Supplier<Set<Item>> yields) {
        Option(double cost, Supplier<BotTask> task) {
            this(cost, task, Set::of);
        }
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
            case DIMENSION -> "minecraft:the_nether".equals(goal.primaryTarget()) ? nether(bot, planner) : null;
            case BIOME -> biome(bot, goal);
            case STRUCTURE -> structure(bot, goal);
            case LOCATION -> "reach_y_minus_50".equals(goal.id()) ? descend(bot, -50) : null;
            case ADVANCEMENT -> advancement(bot, planner, goal.id());
            default -> null;
        };
    }

    /**
     * Into the Nether: ten obsidian (made from a lava pool with a water bucket, or mined), flint and
     * steel, a few blocks for the frame's corners, then a portal built and walked through.
     */
    private static Option nether(Bot bot, ObtainPlanner planner) {
        if (!overworld(bot)) return null;
        Option built = builtPortal(bot, planner), cast = castPortal(bot, planner);
        if (built == null) return cast;
        return cast == null || built.cost() <= cast.cost() ? built : cast;
    }

    public static Option netherForTest(Bot bot, ObtainPlanner planner) {
        return nether(bot, planner);
    }

    /**
     * The speedrunners' way in, no diamond pickaxe needed: with a lava pool in sight, two buckets
     * (one of them full of water), flint and steel and ten spare blocks, the portal is cast right
     * there at the pool (see {@link net.kasax.challengecraft.bot.task.CastPortalTask}).
     */
    private static Option castPortal(Bot bot, ObtainPlanner planner) {
        Double lava = planner.seen(bot, Blocks.LAVA);
        if (lava == null) return null;
        Set<Item> blocks = Set.of(Items.DIRT, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE);
        boolean water = ObtainPlanner.countAny(bot.body(), Set.of(Items.WATER_BUCKET)) > 0;
        double cost = planner.estimate(bot, Set.of(Items.WATER_BUCKET), 1)
                // (the water bucket's estimate counts one bucket already, unless one is held)
                + planner.estimate(bot, Set.of(Items.BUCKET), !water && ObtainPlanner.countAny(bot.body(), Set.of(Items.BUCKET)) > 0 ? 2 : 1)
                + planner.estimate(bot, Set.of(Items.FLINT_AND_STEEL), 1)
                + planner.estimate(bot, blocks, 10);
        if (cost >= INF) return null;
        Set<Item> bucketKit = Set.of(Items.WATER_BUCKET, Items.BUCKET, Items.FLINT_AND_STEEL);
        return new Option(cost + lava + 75, () -> {
            // The pool in sight now: the gathering may lead away from it.
            BlockPos pool = bot.memory().nearest((ServerLevel) bot.body().level(), bot.body().blockPosition(),
                    st -> st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && st.getFluidState().isSource(), Set.of());
            return new SequenceTask("cast a portal to the Nether", List.of(
                () -> new ObtainTask(Set.of(Items.WATER_BUCKET), 1, planner).keeping(Set.of(Items.FLINT_AND_STEEL)),
                () -> new ObtainTask(Set.of(Items.BUCKET), 1, planner).keeping(Set.of(Items.WATER_BUCKET, Items.FLINT_AND_STEEL)),
                () -> new ObtainTask(Set.of(Items.FLINT_AND_STEEL), 1, planner).keeping(bucketKit),
                () -> new ObtainTask(blocks, 10, planner).keeping(bucketKit),
                () -> new net.kasax.challengecraft.bot.task.CastPortalTask(bot.body().level(), pool)));
        });
    }

    /** Ten obsidian mined (a diamond pickaxe), flint and steel, then the frame built block by block. */
    private static Option builtPortal(Bot bot, ObtainPlanner planner) {
        Set<Item> kit = Set.of(Items.OBSIDIAN, Items.FLINT_AND_STEEL, Items.COBBLESTONE);
        double obsidian = planner.estimate(bot, Set.of(Items.OBSIDIAN), 10);
        double flint = planner.estimate(bot, Set.of(Items.FLINT_AND_STEEL), 1);
        if (obsidian >= INF || flint >= INF) return null;
        return new Option(obsidian + flint + 40, () -> new SequenceTask("go to the Nether", List.of(
                () -> new ObtainTask(Set.of(Items.OBSIDIAN), 10, planner).keeping(kit),
                () -> new ObtainTask(Set.of(Items.FLINT_AND_STEEL), 1, planner).keeping(kit),
                () -> new ObtainTask(Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT), 4, planner).keeping(kit),
                () -> new net.kasax.challengecraft.bot.task.PortalTask(bot.body().level()))));
    }

    // ---- places -------------------------------------------------------------------------------

    private static boolean overworld(Bot bot) {
        return bot.body().level().dimension() == Level.OVERWORLD;
    }

    /** Biomes: a walk if one is in view; otherwise exploring, longer the rarer the biome. */
    private static Option biome(Bot bot, LockoutBingoGoal goal) {
        Identifier id = Identifier.parse(goal.primaryTarget());
        boolean nether = goal.category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER;
        if (nether != (bot.body().level().dimension() == Level.NETHER)) return null;
        BlockPos seen = bot.senses().biome(id);
        double cost = seen != null ? 3 + Math.sqrt(seen.distSqr(bot.body().blockPosition())) / 4.0 : unseenEffort(goal);
        return new Option(cost, () -> new GoToBiomeTask(id));
    }

    /** Structures: those it has seen are a walk away; surface ones are worth exploring for. */
    private static Option structure(Bot bot, LockoutBingoGoal goal) {
        String path = Identifier.parse(goal.primaryTarget()).getPath();
        boolean netherOrEnd = path.equals("fortress") || path.equals("bastion_remnant") || path.equals("end_city");
        if (netherOrEnd != !overworld(bot)) return null;
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        Set<Identifier> ids = VisitStructureTask.resolve(level, goal.primaryTarget());
        if (ids.isEmpty()) return null;
        BotSenses.SeenStructure seen = VisitStructureTask.nearest(bot, ids);
        double cost;
        if (seen != null) cost = 5 + Math.sqrt(seen.spot().distSqr(bot.body().blockPosition())) / 4.0;
        else if (SURFACE_STRUCTURES.contains(path)) cost = unseenEffort(goal);
        else return null; // underground (mineshaft, stronghold, ancient city): found by chance only
        return new Option(cost, () -> new VisitStructureTask(level, goal.primaryTarget()));
    }

    private static final Set<String> SURFACE_STRUCTURES = Set.of("village", "shipwreck", "ruined_portal", "desert_pyramid",
            "jungle_temple", "pillager_outpost", "abandoned_camp", "ocean_ruin");

    private static double unseenEffort(LockoutBingoGoal goal) {
        return switch (goal.difficulty()) {
            case EASY -> 240;
            case MEDIUM -> 480;
            default -> 1500;
        };
    }

    /** Down to a height: the digging, about a block every second or two. */
    private static Option descend(Bot bot, int y) {
        if (!overworld(bot)) return null;
        int down = bot.body().getBlockY() - y;
        if (down <= 0) return null; // already there: the game counts it by itself
        return new Option(20 + down * 0.8, () -> new BotTask() {
            // Digging for nothing in particular, down to y (and done the moment it is there).
            private final MineTask dig = new MineTask("the depth", s -> false, Set.of(), 1, y - 2);

            @Override
            public Result tick(Bot b) {
                return b.body().getBlockY() <= y ? Result.DONE : dig.tick(b);
            }

            @Override
            public String describe() {
                return "dig down to y " + y;
            }
        });
    }

    private static Option advancement(Bot bot, ObtainPlanner planner, String id) {
        return switch (id) {
            case "advancement_stone_age" -> obtain(bot, planner, Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.BLACKSTONE),
                    ObtainPlanner.countAny(bot.body(), Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.BLACKSTONE)) + 1);
            case "advancement_acquire_hardware" -> obtain(bot, planner, Set.of(Items.IRON_INGOT),
                    ObtainPlanner.countAny(bot.body(), Set.of(Items.IRON_INGOT)) + 1);
            case "advancement_diamonds" -> obtain(bot, planner, Set.of(Items.DIAMOND), ObtainPlanner.countAny(bot.body(), Set.of(Items.DIAMOND)) + 1);
            case "advancement_we_need_to_go_deeper" -> nether(bot, planner);
            case "advancement_suit_up" -> equip(bot, planner, Set.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
                    Items.IRON_BOOTS), 1);
            default -> null;
        };
    }

    // ---- the goal kinds -----------------------------------------------------------------------

    private static Option obtain(Bot bot, ObtainPlanner planner, Set<Item> items, int count) {
        if (items.isEmpty()) return null;
        double cost = planner.estimate(bot, items, count);
        if (cost >= INF) return null;
        return new Option(cost, () -> new ObtainTask(items, count, planner), () -> planner.yields(bot, items, count, false));
    }

    private static Option craft(Bot bot, ObtainPlanner planner, Set<Item> items) {
        if (items.isEmpty()) return null;
        double cost = planner.estimateCraft(bot, items);
        if (cost >= INF) return null;
        // Crafting counts, not having: one more than now, out of a crafting grid.
        return new Option(cost + 1, () -> new ObtainTask(items, ObtainPlanner.countAny(bot.body(), items) + 1, planner, true),
                () -> planner.yields(bot, items, ObtainPlanner.countAny(bot.body(), items) + 1, true));
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
        if (id.equals("milk_cow")) {
            return useOnMob(bot, planner, EntityTypes.COW, Items.BUCKET, e -> !e.isBaby());
        }
        if (id.equals("shear_sheep")) {
            return useOnMob(bot, planner, EntityTypes.SHEEP, Items.SHEARS,
                    e -> e instanceof net.minecraft.world.entity.animal.sheep.Sheep s && s.readyForShearing());
        }
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

    private static Option useOnMob(Bot bot, ObtainPlanner planner, EntityType<?> type, Item tool,
                                   java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> ok) {
        double toolCost = planner.estimate(bot, Set.of(tool), 1);
        double mob = planner.mobEffort(bot, type, 40);
        if (toolCost >= INF || mob >= INF) return null;
        return new Option(toolCost + mob + 2, () -> new SequenceTask("use " + ObtainPlanner.names(Set.of(tool)) + " on a "
                + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(), List.of(
                () -> new ObtainTask(Set.of(tool), 1, planner),
                () -> new UseOnMobTask(type, tool, ok))), () -> Set.of(tool));
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
