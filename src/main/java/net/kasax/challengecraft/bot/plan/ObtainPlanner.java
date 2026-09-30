package net.kasax.challengecraft.bot.plan;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.bot.task.CraftTask;
import net.kasax.challengecraft.bot.task.KillTask;
import net.kasax.challengecraft.bot.task.MineTask;
import net.kasax.challengecraft.bot.task.SmeltTask;
import net.kasax.challengecraft.bot.task.StationTaskRange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns "have N of these items" into the one thing to do next, the way a player thinks it through:
 * I want a stone pickaxe → I need 3 cobblestone and 2 sticks and a crafting table → I have no
 * pickaxe for the cobblestone → first a wooden pickaxe → planks → logs → there is a birch tree over
 * there. Called again whenever a step is done, so it always plans from what the bot really has
 * (and adapts when a drop was lucky or a mob stole something).
 *
 * <p>Which way to get an item is picked by estimated effort: the game's recipes give the ways,
 * the costs come from what the bot holds (free), what it can see around it (cheap) and how rare
 * the raw material is otherwise. Ways that just failed are avoided for a while.
 */
public final class ObtainPlanner {
    public sealed interface Plan permits Have, Step, Stuck {
    }

    public record Have() implements Plan {
    }

    /**
     * Do {@code task} next; {@code key} names the way, so the caller can report it as failed, and
     * {@code sources} are the blocks or mobs it relies on finding.
     */
    public record Step(BotTask task, String key, Set<?> sources) implements Plan {
    }

    public record Stuck(String why) implements Plan {
    }

    private static final double INF = 1e9, MAX_WAY_COST = 1200;
    private static final int SCAN_RADIUS = 32;
    private static final int MAX_DEPTH = 12;

    private final Map<String, Long> failed = new HashMap<>();
    private final Map<Object, Long> missing = new HashMap<>();
    /** What the bot saw at the last look around, with the effort (seconds) to get to the nearest one. */
    private Map<Block, Double> visibleBlocks = Map.of();
    private Map<EntityType<?>, Double> visibleMobs = Map.of();
    private boolean dark;
    private long scannedAt = -10_000, costsAt = -1;
    private Map<Item, Double> costs = Map.of();
    private boolean craftOnly;
    private Set<Item> keep = Set.of();

    /**
     * A step failed: avoid its way for two minutes, and if it searched for its blocks or mobs and
     * found none, count those as not around here for ten (every estimate then goes around them).
     */
    public void markFailed(Step step, long gameTime) {
        failed.put(step.key(), gameTime + 2400);
        for (Object source : step.sources()) missing.put(source, gameTime + 12000);
        scannedAt = -10_000; // look again
    }

    private boolean isMissing(Object source) {
        Long until = missing.get(source);
        return until != null && until > costsAt;
    }

    private boolean isFailed(String key, long now) {
        Long until = failed.get(key);
        return until != null && until > now;
    }

    public Plan plan(Bot bot, Collection<Item> accept, int count) {
        return plan(bot, accept, count, false);
    }

    /**
     * Like {@link #plan(Bot, Collection, int)}; with {@code craftOnly} the last step has to be a
     * crafting recipe (a "craft X" goal counts crafting, not having).
     */
    public Plan plan(Bot bot, Collection<Item> accept, int count, boolean craftOnly) {
        return plan(bot, accept, count, craftOnly, Set.of());
    }

    /**
     * ... and {@code keep}: items the bot holds for something else (the red dye already collected
     * for "5 dye colours") are not to be used up on the way: the plan acts as if they were not there.
     */
    public Plan plan(Bot bot, Collection<Item> accept, int count, boolean craftOnly, Set<Item> keep) {
        refresh(bot);
        this.craftOnly = craftOnly;
        this.keep = keep;
        try {
            return plan(bot, Set.copyOf(accept), count, 0, new HashSet<>());
        } finally {
            this.craftOnly = false;
            this.keep = Set.of();
        }
    }

    /** Rough effort, in seconds of play, to hold {@code count} of these items (0 if it already does). */
    public double estimate(Bot bot, Collection<Item> items, int count) {
        refresh(bot);
        int have = countAny(bot.body(), Set.copyOf(items));
        if (have >= count) return 0;
        double best = INF;
        for (Item i : items) best = Math.min(best, cost(i));
        return best >= INF ? INF : best * (count - have);
    }

    /** Rough effort to craft one of these items (from scratch, whatever is held). */
    public double estimateCraft(Bot bot, Collection<Item> items) {
        refresh(bot);
        BotKnowledge k = BotKnowledge.get(bot.server());
        double best = INF;
        for (Item i : items) {
            for (BotKnowledge.Route r : k.routesTo(i)) {
                if (r instanceof BotKnowledge.CraftRoute) best = Math.min(best, routeCost(r, costs));
            }
        }
        return best;
    }

    /** Rough effort to find and kill one mob of this kind; INF for mobs the bot does not hunt. */
    public double mobEffort(Bot bot, EntityType<?> type, double unknown) {
        refresh(bot);
        return mobCost(type, BotKnowledge.get(bot.server()).mobEffort(type, unknown));
    }

    /** Seen: the way there. Not seen: the usual effort, and monsters mostly come out at night. */
    private double mobCost(EntityType<?> type, double effort) {
        Double seen = visibleMobs.get(type);
        if (seen == null && isMissing(type)) return INF;
        if (seen != null) return seen;
        boolean monster = type.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
        return monster && !dark ? effort * 4 : effort;
    }

    // ---- looking around -----------------------------------------------------------------------

    private void refresh(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        long now = level.getGameTime();
        if (now - scannedAt < 200) {
            // Once a tick is enough (the brain asks about every tile in one go).
            if (now != costsAt) costs = computeCosts(bot);
            costsAt = now;
            return;
        }
        costsAt = now;
        scannedAt = now;
        Map<Block, Double> blocks = new IdentityHashMap<>();
        BlockPos c = bot.body().blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                if (level.getChunkSource().getChunkNow((c.getX() + x) >> 4, (c.getZ() + z) >> 4) == null) continue;
                for (int y = -16; y <= 16; y++) {
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir()) continue;
                    // Walking ~4 blocks a second; up and down (climbing, digging) is slower.
                    double reach = 2 + Math.sqrt(x * x + z * z) / 4.0 + Math.abs(y) * 0.8;
                    Double known = blocks.get(s.getBlock());
                    if (known != null && known <= reach) continue;
                    if (BotWorld.exposed(level, m)) blocks.put(s.getBlock(), reach);
                }
            }
        }
        visibleBlocks = blocks;
        Map<EntityType<?>, Double> mobs = new HashMap<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(48),
                e -> e.isAlive() && !(e instanceof Player))) {
            mobs.merge(e.getType(), 5 + e.distanceTo(bot.body()) / 3.0, Math::min);
        }
        visibleMobs = mobs;
        dark = level.isDarkOutside();
        costs = computeCosts(bot);
    }

    // ---- effort estimates ---------------------------------------------------------------------

    private Map<Item, Double> computeCosts(Bot bot) {
        BotKnowledge k = BotKnowledge.get(bot.server());
        Map<Item, Double> cost = new IdentityHashMap<>();
        Set<Item> all = new HashSet<>(k.craftableItems());
        for (Item item : BuiltInRegistries.ITEM) {
            if (!k.blocksDropping(item).isEmpty() || !k.mobsDropping(item).isEmpty()) all.add(item);
        }
        for (Item item : all) cost.put(item, rawCost(bot, k, item, cost));
        // Relax over the recipes until nothing gets cheaper (a few rounds: recipe chains are short).
        for (int round = 0; round < 10; round++) {
            boolean changed = false;
            for (Item item : all) {
                double best = cost.get(item);
                if (best == 0) continue;
                best = Math.min(best, rawCost(bot, k, item, cost));
                for (BotKnowledge.Route r : k.routesTo(item)) best = Math.min(best, routeCost(r, cost));
                if (best < cost.get(item) - 1e-6) {
                    cost.put(item, best);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return cost;
    }

    private double rawCost(Bot bot, BotKnowledge k, Item item, Map<Item, Double> cost) {
        if (net.kasax.challengecraft.bot.BotInventory.count(bot.body(), item) > 0) return 0;
        double best = INF;
        for (BotKnowledge.Drop d : k.blocksDropping(item)) {
            double c = blockCost(bot, k, d.block(), cost) / Math.max(0.05, d.count());
            best = Math.min(best, c);
        }
        for (BotKnowledge.MobDrop d : k.mobsDropping(item)) {
            best = Math.min(best, mobCost(d.type(), d.effort()) / d.count());
        }
        return best;
    }

    private double blockCost(Bot bot, BotKnowledge k, Block block, Map<Item, Double> cost) {
        BlockState s = block.defaultBlockState();
        if (isMissing(block)) return INF;
        Double seen = visibleBlocks.get(block);
        double find = seen != null ? Math.min(seen, BotKnowledge.rarity(block)) : BotKnowledge.rarity(block);
        double breakSeconds = Math.min(60, bot.tools().breakTicks(s) / 20.0);
        return find + breakSeconds + toolCost(bot, k, block, cost);
    }

    private double toolCost(Bot bot, BotKnowledge k, Block block, Map<Item, Double> cost) {
        BlockState s = block.defaultBlockState();
        if (bot.tools().canHarvest(s)) return 0;
        double best = INF;
        for (Item t : k.harvestTools(block)) best = Math.min(best, cost.getOrDefault(t, INF));
        return best;
    }

    private static double routeCost(BotKnowledge.Route r, Map<Item, Double> cost) {
        if (r instanceof BotKnowledge.CraftRoute c) {
            double sum = 1;
            for (Ingredient i : c.ingredients()) sum += cheapest(i, cost);
            return sum / c.yield();
        }
        BotKnowledge.SmeltRoute s = (BotKnowledge.SmeltRoute) r;
        return cheapest(s.input(), cost) + 6;
    }

    private static double cheapest(Ingredient i, Map<Item, Double> cost) {
        double best = INF;
        for (Item item : BotKnowledge.items(i)) best = Math.min(best, cost.getOrDefault(item, INF));
        return best;
    }

    public double cost(Item item) {
        return costs.getOrDefault(item, INF);
    }

    // ---- planning -----------------------------------------------------------------------------
    //
    // The planner plays the whole thing through on a copy of the inventory ("simulation"): every
    // step it would take, in order, with the items each one adds and uses up. Then it hands out
    // only the first step, but sized for the whole plan (all the logs the pickaxe, the sticks and
    // the table will need, in one go instead of one trip per log).

    private enum Kind { MINE, KILL, ROUTE, CRAFT, SMELT }

    private record Way(String key, double cost, Kind kind, Object data) {
    }

    private record Pending(Kind kind, String key, Set<Item> accept, int amount, Object data) {
    }

    private static final class Sim {
        final Map<Item, Integer> inv;
        final List<Pending> steps;

        Sim(Map<Item, Integer> inv, List<Pending> steps) {
            this.inv = inv;
            this.steps = steps;
        }

        static Sim of(Player p) {
            Map<Item, Integer> inv = new IdentityHashMap<>();
            for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (!s.isEmpty()) inv.merge(s.getItem(), s.getCount(), Integer::sum);
            return new Sim(inv, new ArrayList<>());
        }

        Sim copy() {
            return new Sim(new IdentityHashMap<>(inv), new ArrayList<>(steps));
        }

        void restore(Sim o) {
            inv.clear();
            inv.putAll(o.inv);
            steps.clear();
            steps.addAll(o.steps);
        }

        int count(Set<Item> items) {
            int n = 0;
            for (Item i : items) n += inv.getOrDefault(i, 0);
            return n;
        }

        void add(Item item, int n) {
            inv.merge(item, n, Integer::sum);
        }

        void take(Set<Item> items, int n) {
            for (Item i : items) {
                if (n <= 0) return;
                int have = inv.getOrDefault(i, 0);
                int t = Math.min(have, n);
                if (t > 0) inv.put(i, have - t);
                n -= t;
            }
        }

        boolean canHarvest(BlockState s) {
            if (!s.requiresCorrectToolForDrops()) return true;
            for (Map.Entry<Item, Integer> e : inv.entrySet()) {
                if (e.getValue() > 0 && new ItemStack(e.getKey()).isCorrectToolForDrops(s)) return true;
            }
            return false;
        }

        double fuel(Set<Item> except) {
            double f = 0;
            for (Map.Entry<Item, Integer> e : inv.entrySet()) {
                if (e.getValue() > 0 && !except.contains(e.getKey())) f += BotKnowledge.fuelValue(new ItemStack(e.getKey())) * e.getValue();
            }
            return f;
        }

        void burn(double items, Set<Item> except) {
            for (Map.Entry<Item, Integer> e : inv.entrySet()) {
                if (items <= 0) return;
                double v = BotKnowledge.fuelValue(new ItemStack(e.getKey()));
                if (v <= 0 || e.getValue() <= 0 || except.contains(e.getKey())) continue;
                int n = (int) Math.min(e.getValue(), Math.ceil(items / v));
                e.setValue(e.getValue() - n);
                items -= n * v;
            }
        }
    }

    private Plan plan(Bot bot, Set<Item> accept, int count, int depth, Set<Item> visiting) {
        Sim sim = Sim.of(bot.body());
        for (Item k : keep) if (!accept.contains(k)) sim.inv.remove(k);
        if (sim.count(accept) >= count) return new Have();
        String why = expand(bot, sim, accept, count, depth, visiting);
        if (why != null) return new Stuck(why);
        if (sim.steps.isEmpty()) return new Have();
        Pending first = sim.steps.get(0);
        int total = 0;
        for (Pending p : sim.steps) if (p.key().equals(first.key())) total += p.amount();
        Set<?> sources = first.kind() == Kind.MINE || first.kind() == Kind.KILL ? (Set<?>) first.data() : Set.of();
        return new Step(toTask(bot, first, total), first.key(), sources);
    }

    @SuppressWarnings("unchecked")
    private BotTask toTask(Bot bot, Pending p, int total) {
        return switch (p.kind()) {
            case MINE -> {
                Set<Block> blocks = (Set<Block>) p.data();
                // If it has to dig for them: to the height of the most common of these blocks.
                Block common = blocks.stream().min(Comparator.comparingDouble(BotKnowledge::rarity)).orElseThrow();
                Integer depth = BotKnowledge.depth(common, bot.body().blockPosition().getY());
                yield new MineTask(names(p.accept()), s -> blocks.contains(s.getBlock()), p.accept(),
                        countAny(bot.body(), p.accept()) + total, depth);
            }
            case KILL -> new KillTask((Set<EntityType<?>>) p.data(), p.accept(), countAny(bot.body(), p.accept()) + total, 0);
            case CRAFT -> new CraftTask((BotKnowledge.CraftRoute) p.data(), total);
            case SMELT -> new SmeltTask((BotKnowledge.SmeltRoute) p.data(), total);
            default -> throw new IllegalStateException();
        };
    }

    /** Plays getting {@code count} of {@code accept} through on {@code sim}; null if it works out, else why not. */
    private String expand(Bot bot, Sim sim, Set<Item> accept, int count, int depth, Set<Item> visiting) {
        if (sim.count(accept) >= count) return null;
        if (depth > MAX_DEPTH) return "too deep";
        BotKnowledge k = BotKnowledge.get(bot.server());
        long now = bot.body().level().getGameTime();

        List<Way> ways = new ArrayList<>();
        boolean onlyCraft = craftOnly && depth == 0;
        // Mining: every block that drops any of the wanted items, in one sweep.
        Map<Block, Double> mineBlocks = new LinkedHashMap<>();
        for (Item item : onlyCraft ? Set.<Item>of() : accept) {
            for (BotKnowledge.Drop d : k.blocksDropping(item)) {
                double c = blockCost(bot, k, d.block(), costs) / Math.max(0.05, d.count());
                mineBlocks.merge(d.block(), c, Math::min);
            }
        }
        if (!mineBlocks.isEmpty()) {
            String key = "mine:" + names(accept);
            double c = mineBlocks.values().stream().min(Double::compare).orElse(INF);
            if (!isFailed(key, now)) ways.add(new Way(key, c, Kind.MINE, mineBlocks));
        }
        Map<EntityType<?>, Double> mobs = new LinkedHashMap<>();
        for (Item item : onlyCraft ? Set.<Item>of() : accept) {
            for (BotKnowledge.MobDrop d : k.mobsDropping(item)) {
                mobs.merge(d.type(), mobCost(d.type(), d.effort()) / d.count(), Math::min);
            }
        }
        if (!mobs.isEmpty()) {
            String key = "kill:" + names(accept);
            double c = mobs.values().stream().min(Double::compare).orElse(INF);
            if (!isFailed(key, now)) ways.add(new Way(key, c, Kind.KILL, mobs.keySet()));
        }
        for (Item item : accept) {
            if (visiting.contains(item)) continue;
            for (BotKnowledge.Route r : k.routesTo(item)) {
                if (isFailed(r.key(), now)) continue;
                if (onlyCraft && !(r instanceof BotKnowledge.CraftRoute)) continue;
                // A recipe that eats one of the wanted items to make another (logs → wood) goes in circles.
                if (usesAny(r, accept)) continue;
                ways.add(new Way(r.key(), routeCost(r, costs), Kind.ROUTE, r));
            }
        }
        ways.sort(Comparator.comparingDouble(Way::cost));

        String firstWhy = "no known way to get " + names(accept);
        int tried = 0;
        for (Way w : ways) {
            // Ways that need things the bot won't find (storage blocks, the Nether) are no ways.
            if (w.cost() >= MAX_WAY_COST || tried++ >= 6) break;
            Sim before = sim.copy();
            String why = switch (w.kind()) {
                case MINE -> mine(bot, k, sim, accept, count, w, depth, visiting);
                case KILL -> kill(sim, accept, count, w);
                default -> route(bot, k, sim, (BotKnowledge.Route) w.data(), accept, count, depth, visiting);
            };
            if (why == null) return null;
            sim.restore(before);
            if (tried == 1) firstWhy = why;
        }
        return firstWhy;
    }

    @SuppressWarnings("unchecked")
    private String mine(Bot bot, BotKnowledge k, Sim sim, Set<Item> accept, int count, Way w, int depth, Set<Item> visiting) {
        Map<Block, Double> blocks = (Map<Block, Double>) w.data();
        // If the cheap blocks need a better tool, get the tool first (the cheapest one that does).
        Block cheapest = blocks.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (cheapest != null && !sim.canHarvest(cheapest.defaultBlockState())) {
            // Not the tool being made right now (cobblestone for a stone pickaxe needs a wooden one).
            List<Item> tools = k.harvestTools(cheapest).stream()
                    .filter(t -> !visiting.contains(t) && !accept.contains(t)).toList();
            if (tools.isEmpty()) return "no tool for " + name(cheapest.asItem());
            Set<Item> v = new HashSet<>(visiting);
            v.addAll(accept);
            Item tool = tools.stream().min(Comparator.comparingDouble(this::cost)).orElseThrow();
            String why = expand(bot, sim, Set.of(tool), sim.count(Set.of(tool)) + 1, depth + 1, v);
            if (why != null) return why;
        }
        // Digging down for it: take cheap pickaxes along for the stone on the way, so the good one
        // (needed for the ore itself) does not wear out on it.
        Integer digTo = cheapest == null || visibleBlocks.containsKey(cheapest) ? null
                : BotKnowledge.depth(cheapest, bot.body().blockPosition().getY());
        if (digTo != null && !accept.contains(Items.STONE_PICKAXE) && !visiting.contains(Items.STONE_PICKAXE)) {
            int spare = digTo < 30 ? 2 : 1;
            if (sim.count(Set.of(Items.STONE_PICKAXE)) < spare) {
                Set<Item> v = new HashSet<>(visiting);
                v.addAll(accept);
                String why = expand(bot, sim, Set.of(Items.STONE_PICKAXE), spare, depth + 1, v);
                if (why != null) return why;
            }
        }
        Set<Block> harvestable = blocks.keySet().stream()
                .filter(b -> sim.canHarvest(b.defaultBlockState()))
                .collect(Collectors.toCollection(() -> java.util.Collections.newSetFromMap(new IdentityHashMap<>())));
        if (harvestable.isEmpty()) return "no tool for " + names(accept);
        int need = count - sim.count(accept);
        sim.steps.add(new Pending(Kind.MINE, w.key(), accept, need, harvestable));
        sim.add(representative(k, accept, harvestable), need);
        return null;
    }

    private static Item representative(BotKnowledge k, Set<Item> accept, Set<Block> blocks) {
        for (Item i : accept) for (BotKnowledge.Drop d : k.blocksDropping(i)) if (blocks.contains(d.block())) return i;
        return accept.iterator().next();
    }

    @SuppressWarnings("unchecked")
    private String kill(Sim sim, Set<Item> accept, int count, Way w) {
        int need = count - sim.count(accept);
        Set<EntityType<?>> types = Set.copyOf((Collection<EntityType<?>>) w.data());
        sim.steps.add(new Pending(Kind.KILL, w.key(), accept, need, types));
        sim.add(accept.iterator().next(), need);
        return null;
    }

    private String route(Bot bot, BotKnowledge k, Sim sim, BotKnowledge.Route r, Set<Item> accept, int count, int depth, Set<Item> visiting) {
        int need = count - sim.count(accept);
        Set<Item> v = new HashSet<>(visiting);
        v.add(r.result());
        if (r instanceof BotKnowledge.CraftRoute c) {
            int crafts = (need + c.yield() - 1) / c.yield();
            if (c.table() && !hasOrSees(bot, sim, Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE)) {
                String why = expand(bot, sim, Set.of(Items.CRAFTING_TABLE), 1, depth + 1, v);
                if (why != null) return why;
            }
            // Same ingredient in several slots counts together (4 planks for a table).
            Map<Set<Item>, Integer> groups = new LinkedHashMap<>();
            for (Ingredient i : c.ingredients()) groups.merge(Set.copyOf(BotKnowledge.items(i)), crafts, Integer::sum);
            String why = gather(bot, sim, groups, depth, v);
            if (why != null) return why;
            groups.forEach(sim::take);
            sim.add(c.result(), crafts * c.yield());
            sim.steps.add(new Pending(Kind.CRAFT, c.key(), Set.of(c.result()), crafts, c));
            return null;
        }
        BotKnowledge.SmeltRoute s = (BotKnowledge.SmeltRoute) r;
        Set<Item> input = Set.copyOf(BotKnowledge.items(s.input()));
        // What goes in and what comes out is never burnt (charcoal would feed its own fire).
        Set<Item> notFuel = new HashSet<>(input);
        notFuel.add(s.result());
        if (!hasOrSees(bot, sim, Items.FURNACE, Blocks.FURNACE)) {
            String why = expand(bot, sim, Set.of(Items.FURNACE), 1, depth + 1, v);
            if (why != null) return why;
        }
        for (int round = 0; round < 3; round++) {
            double fuel = sim.fuel(notFuel);
            if (fuel < need) {
                // Coal if it is handy, else wood (always around, and 1.5 items per plank).
                boolean coal = s.result() != Items.CHARCOAL
                        && (cost(Items.COAL) < 15 || sim.count(Set.of(Items.COAL, Items.CHARCOAL)) > 0);
                Set<Item> fuelSet = coal ? Set.of(Items.COAL, Items.CHARCOAL) : planks();
                int units = coal ? (int) Math.ceil((need - fuel) / 8.0) : (int) Math.ceil((need - fuel) / 1.5);
                String why = expand(bot, sim, fuelSet, sim.count(fuelSet) + units, depth + 1, v);
                if (why != null) return why;
            }
            String why = gather(bot, sim, Map.of(input, need), depth, v);
            if (why != null) return why;
            if (sim.fuel(notFuel) >= need) break;
        }
        sim.burn(need, notFuel);
        sim.take(input, need);
        sim.add(s.result(), need);
        sim.steps.add(new Pending(Kind.SMELT, s.key(), Set.of(s.result()), need, s));
        return null;
    }

    /**
     * Gets every group of ingredients to its amount at the same time: making one (sticks) can use
     * up another (the planks set aside for the pickaxe head), so it goes round until all are there.
     */
    private String gather(Bot bot, Sim sim, Map<Set<Item>, Integer> groups, int depth, Set<Item> visiting) {
        for (int round = 0; round < 4; round++) {
            boolean short_ = false;
            for (Map.Entry<Set<Item>, Integer> g : groups.entrySet()) {
                if (sim.count(g.getKey()) >= g.getValue()) continue;
                short_ = true;
                String why = expand(bot, sim, g.getKey(), g.getValue(), depth + 1, visiting);
                if (why != null) return why;
            }
            if (!short_) return null;
        }
        for (Map.Entry<Set<Item>, Integer> g : groups.entrySet()) {
            if (sim.count(g.getKey()) < g.getValue()) return "could not gather " + names(g.getKey());
        }
        return null;
    }

    // ---- helpers ------------------------------------------------------------------------------

    private boolean hasOrSees(Bot bot, Sim sim, Item item, Block block) {
        // Looked up fresh, not from the scan: the bot may have just put one down.
        return sim.count(Set.of(item)) > 0 || BotWorld.nearest((ServerLevel) bot.body().level(),
                bot.body().blockPosition(), StationTaskRange.RANGE, 8, s -> s.is(block), false, Set.of()) != null;
    }

    private static Set<Item> planks() {
        Set<Item> out = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) if (new ItemStack(item).is(ItemTags.PLANKS)) out.add(item);
        return out;
    }

    private static boolean usesAny(BotKnowledge.Route r, Set<Item> accept) {
        if (r instanceof BotKnowledge.CraftRoute c) {
            for (Ingredient i : c.ingredients()) for (Item item : BotKnowledge.items(i)) if (accept.contains(item)) return true;
            return false;
        }
        for (Item item : BotKnowledge.items(((BotKnowledge.SmeltRoute) r).input())) if (accept.contains(item)) return true;
        return false;
    }

    public static int countAny(Player p, Set<Item> items) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (!s.isEmpty() && items.contains(s.getItem())) n += s.getCount();
        return n;
    }

    public static String name(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    public static String names(Set<Item> items) {
        if (items.size() == 1) return name(items.iterator().next());
        List<String> n = items.stream().map(ObtainPlanner::name).sorted().toList();
        return n.size() <= 3 ? String.join("|", n) : n.get(0) + "|… (" + n.size() + ")";
    }
}
