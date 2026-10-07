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
    private boolean dark, nether, underground;
    private net.kasax.challengecraft.bot.Bot habitatBot;
    /** Seconds until morning (villagers take no new job at night). */
    private double nightLeft;
    /** Blocks typical of a biome in view, with the walk there. */
    private Map<Block, Double> biomeHints = Map.of();
    private long scannedAt = -10_000, costsAt = -1;
    private Map<Item, Double> costs = Map.of();
    /**
     * Per item, beside its cost: the part of it that is searching (luck: a block not yet seen,
     * a mob not yet met), and where the work for it mostly is (null: anywhere, here). See
     * {@link #searchPart} and {@link #anchor}: what the brain makes its odds and its routes of.
     */
    private Map<Item, Double> searches = Map.of();
    private Map<Item, BlockPos> anchors = Map.of();
    /** Where the nearest seen one of each block and each mob is, where a biome's hint points, the village. */
    private Map<Block, BlockPos> visiblePos = Map.of();
    private Map<EntityType<?>, BlockPos> mobPos = Map.of();
    private Map<Block, BlockPos> hintPos = Map.of();
    private BlockPos villagePos, here;
    /** Seconds until the monsters come out on the surface (0 at night). */
    private double untilNight;

    /** A way to one item: its effort, the searching part of it, and where the work is. */
    private record Leaf(double cost, double search, BlockPos at) {
        static final Leaf NONE = new Leaf(1e9, 0, null);

        Leaf scaled(double f) {
            return new Leaf(cost * f, search * f, at);
        }
    }
    private boolean craftOnly;
    private int budget;
    private final Map<List<Object>, Integer> impossible = new HashMap<>();
    private Set<Item> assumeHeld = Set.of();
    private Set<Item> keep = Set.of();

    /**
     * A step failed: avoid its way for two minutes, and if it searched for its blocks or mobs and
     * found none, count those as not around here for ten (every estimate then goes around them).
     */
    public void markFailed(Step step, long gameTime) {
        // (Water: another source is never far; only a short pause before the next try.)
        failed.put(step.key(), gameTime + (step.key().startsWith("fill:water") ? 300 : 2400));
        // (Water is everywhere: one failed fill says nothing about water at large.)
        // (Ores and other things found deep down: not there where it looked; elsewhere, soon.)
        for (Object source : step.sources()) if (source != Blocks.WATER) missing.put(source, gameTime + (oreLike(source) ? 2400 : 12000));
        scannedAt = -10_000; // look again
    }

    private boolean isMissing(Object source) {
        Long until = missing.get(source);
        return until != null && until > costsAt;
    }

    private static boolean oreLike(Object source) {
        return source instanceof Block b && (BuiltInRegistries.BLOCK.getKey(b).getPath().endsWith("_ore") || b == Blocks.STONE || b == Blocks.DEEPSLATE);
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
        this.wanted = Set.copyOf(accept);
        try {
            return plan(bot, Set.copyOf(accept), count, 0, new HashSet<>());
        } finally {
            this.craftOnly = false;
            this.keep = Set.of();
        }
    }

    /**
     * What the bot would hold after getting these, that it does not hold now: the items and
     * everything made on the way (tools, a furnace, leftovers). Empty if it cannot plan it.
     */
    public Set<Item> yields(Bot bot, Collection<Item> items, int count, boolean craftOnly) {
        refresh(bot);
        this.craftOnly = craftOnly;
        try {
            Sim sim = Sim.of(bot.body());
            Map<Item, Integer> before = new IdentityHashMap<>(sim.inv);
            startSearch();
            if (expand(bot, sim, Set.copyOf(items), count, 0, new HashSet<>()) != null) return Set.of();
            Set<Item> out = new HashSet<>();
            sim.inv.forEach((i, n) -> {
                if (n > before.getOrDefault(i, 0)) out.add(i);
            });
            return out;
        } finally {
            this.craftOnly = false;
        }
    }

    /**
     * Runs {@code what} as if the bot stood in the Nether (with what it holds now, none of what is
     * about it here): what a trip there would make possible (quartz for a comparator, say).
     */
    /** Planning for the Nether now (being there, or as if: see {@link #inNether}). */
    public boolean planningNether() {
        return nether;
    }

    public <T> T inNether(Bot bot, java.util.function.Supplier<T> what) {
        refresh(bot);
        boolean savedNether = nether;
        Map<Block, Double> savedVisible = visibleBlocks, savedHints = biomeHints;
        Map<EntityType<?>, Double> savedMobs = visibleMobs;
        Map<Item, Double> saved = costs;
        nether = true;
        visibleBlocks = new IdentityHashMap<>();
        biomeHints = new IdentityHashMap<>();
        visibleMobs = new HashMap<>();
        costs = computeCosts(bot);
        try {
            return what.get();
        } finally {
            nether = savedNether;
            visibleBlocks = savedVisible;
            biomeHints = savedHints;
            visibleMobs = savedMobs;
            costs = saved;
        }
    }

    /**
     * Runs {@code what} as if the bot also held {@code held} (to weigh what one goal would do for
     * the next ones).
     */
    public <T> T assuming(Bot bot, Set<Item> held, java.util.function.Supplier<T> what) {
        refresh(bot);
        Map<Item, Double> saved = costs;
        Map<Item, Double> savedSearches = searches;
        Map<Item, BlockPos> savedAnchors = anchors;
        Set<Item> savedHeld = assumeHeld;
        assumeHeld = held;
        costs = computeCosts(bot);
        try {
            return what.get();
        } finally {
            assumeHeld = savedHeld;
            costs = saved;
            searches = savedSearches;
            anchors = savedAnchors;
        }
    }

    /**
     * Seconds to get {@code dy} blocks up or down to something and back: down means digging a
     * staircase and climbing it again (caves may be quicker, but that is luck); up is a climb.
     */
    private static double vertical(int dy, double digPerBlock) {
        return dy < 0 ? -dy * (digPerBlock + 0.6) : dy * 0.8;
    }

    /** Seconds to walk to the nearest block of this kind in sight around the bot, or null if none is. */
    public Double seen(Bot bot, Block block) {
        refresh(bot);
        return visibleBlocks.get(block);
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

    /**
     * Of {@link #estimate}: the part that is searching rather than known work - finding a block
     * not seen yet, meeting a mob not met yet. That part is luck (it may come at once or take
     * long); the rest (walks to things seen, crafting, smelting, digging to a known depth) is
     * about as planned. The odds of being done in some time follow from the two.
     */
    public double searchPart(Bot bot, Collection<Item> items, int count) {
        refresh(bot);
        int have = countAny(bot.body(), Set.copyOf(items));
        if (have >= count) return 0;
        Item best = cheapestOf(items);
        return best == null ? 0 : Math.min(cost(best), searches.getOrDefault(best, 0.0)) * (count - have);
    }

    /** Where the work for these items mostly is (a seen block, a village, a habitat, a depth), or null: here, anywhere. */
    public BlockPos anchor(Bot bot, Collection<Item> items) {
        refresh(bot);
        Item best = cheapestOf(items);
        return best == null ? null : anchors.get(best);
    }

    private Item cheapestOf(Collection<Item> items) {
        Item best = null;
        for (Item i : items) if (best == null || cost(i) < cost(best)) best = i;
        return best == null || cost(best) >= INF ? null : best;
    }

    /** {@link #searchPart} and {@link #anchor} for one mob of a kind. */
    public double mobSearch(Bot bot, EntityType<?> type, double unknown) {
        refresh(bot);
        return mobLeaf(type, BotKnowledge.get(bot.server()).mobEffort(type, unknown)).search();
    }

    public BlockPos mobAnchor(Bot bot, EntityType<?> type, double unknown) {
        refresh(bot);
        return mobLeaf(type, BotKnowledge.get(bot.server()).mobEffort(type, unknown)).at();
    }

    /** The nearest village it knows of (null: none). */
    public BlockPos village(Bot bot) {
        refresh(bot);
        return villagePos;
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
        return mobLeaf(type, effort).cost();
    }

    private Leaf mobLeaf(EntityType<?> type, double effort) {
        Leaf l = landMobLeaf(type, effort);
        // Fish and squid: in the water, fast, hard to corner. A player only hunts them when there is nothing else.
        var cat = type.getCategory();
        boolean water = cat == net.minecraft.world.entity.MobCategory.WATER_CREATURE || cat == net.minecraft.world.entity.MobCategory.WATER_AMBIENT
                || cat == net.minecraft.world.entity.MobCategory.UNDERGROUND_WATER_CREATURE;
        if (!water || l.cost() >= INF) return l;
        return new Leaf(l.cost() * 4 + 30, l.search() * 4 + 30, l.at());
    }

    private static final Set<EntityType<?>> RISKY_PREY = Set.of(net.minecraft.world.entity.EntityTypes.SKELETON, net.minecraft.world.entity.EntityTypes.STRAY, net.minecraft.world.entity.EntityTypes.BOGGED,
            net.minecraft.world.entity.EntityTypes.WITCH, net.minecraft.world.entity.EntityTypes.DROWNED, net.minecraft.world.entity.EntityTypes.PILLAGER, net.minecraft.world.entity.EntityTypes.BLAZE);

    /** A shield, or armour worth the name. */
    private static boolean geared(net.kasax.challengecraft.bot.BotPlayer body) {
        return body.getArmorValue() >= 6 || countAny(body, Set.of(net.minecraft.world.item.Items.SHIELD)) > 0;
    }

    private Leaf landMobLeaf(EntityType<?> type, double effort) {
        Double seen = visibleMobs.get(type);
        if (seen == null && isMissing(type)) return Leaf.NONE;
        // In sight: a walk there and the fight, no luck needed.
        if (seen != null) return new Leaf(seen, 0, mobPos.get(type));
        // Nether mobs only in the Nether, and the Overworld's not there.
        if (BotKnowledge.NETHER_MOBS.contains(type) != nether) return Leaf.NONE;
        // One that lives somewhere in particular: the walk to the nearest such place it knows,
        // then the search there; not knowing any is a long search (horses are not in the snow).
        double walk = 0;
        BlockPos at = null;
        if (habitatBot != null && !MobHabitats.inHabitat(habitatBot, type)) {
            BlockPos home = MobHabitats.nearestKnown(habitatBot, type);
            if (home != null) {
                walk = net.kasax.challengecraft.bot.BotTerrain.walkSeconds(habitatBot, habitatBot.body().blockPosition(), home);
                at = home;
            } else effort *= 2.5;
        }
        boolean monster = type.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
        // Monsters come out in the dark: on the surface by day that is a wait for the night (or
        // a trip down into a cave, about three searches' worth); in the Nether and down in a cave
        // it is dark at noon.
        double wait = monster && !dark && !nether && !underground ? Math.min(untilNight, effort * 3) : 0;
        // The ones that hit from afar (or with potions, or from the water) are not hunted bare:
        // without a shield or armour that is how the early deaths went (a skeleton hunted for
        // one arrow at minute four on seed 22, a witch, a drowned). Other ways first.
        if (RISKY_PREY.contains(type) && habitatBot != null && !geared(habitatBot.body())) wait += 300;
        return new Leaf(walk + wait + effort, effort, at);
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
        Map<Block, BlockPos> positions = new IdentityHashMap<>();
        BlockPos c = bot.body().blockPosition();
        here = c;
        // Seconds to dig one level of a staircase (two blocks of stone) with what it holds.
        double digPerBlock = Math.min(15, 2 * bot.tools().breakTicks(Blocks.STONE.defaultBlockState()) / 20.0) + 0.4;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        net.minecraft.world.phys.Vec3 eye = bot.body().getEyePosition();
        // (Not what it just found no way to: a tree up on a mesa is not wood at hand.)
        Set<BlockPos> unreachable = bot.unreachable();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                if (level.getChunkSource().getChunkNow((c.getX() + x) >> 4, (c.getZ() + z) >> 4) == null) continue;
                for (int y = -16; y <= 16; y++) {
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir()) continue;
                    // Walking ~4 blocks a second; up and down (climbing, digging) is slower.
                    double reach = 2 + Math.sqrt(x * x + z * z) / 4.0 + vertical(y, digPerBlock);
                    Double known = blocks.get(s.getBlock());
                    if (known != null && known <= reach) continue;
                    if (!BotWorld.exposed(level, m)) continue;
                    if (!unreachable.isEmpty() && unreachable.contains(m)) continue;
                    // Only what it could have seen (common rock anywhere it digs is seen enough).
                    if (!BotWorld.COMMON.contains(s.getBlock()) && !BotWorld.seen(level, m, eye)) continue;
                    blocks.put(s.getBlock(), reach);
                    positions.put(s.getBlock(), m.immutable());
                }
            }
        }
        // And what it remembers from further away (read from the chunks it has been near).
        for (Map.Entry<Block, BlockPos> e : bot.memory().nearestOfEach(level.dimension(), c, unreachable).entrySet()) {
            BlockPos p = e.getValue();
            // (By the way there over the land, not the straight line: across the bay is not near.)
            double reach = 4 + 1.15 * net.kasax.challengecraft.bot.BotTerrain.walkSeconds(bot, c, p) + vertical(p.getY() - c.getY(), digPerBlock);
            Double known = blocks.get(e.getKey());
            if (known == null || reach < known) {
                blocks.put(e.getKey(), reach);
                positions.put(e.getKey(), p);
            }
        }
        visibleBlocks = blocks;
        visiblePos = positions;
        Map<EntityType<?>, Double> mobs = new HashMap<>();
        Map<EntityType<?>, BlockPos> where = new HashMap<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(BotWorld.MOB_SIGHT),
                e -> e.isAlive() && !(e instanceof Player) && BotWorld.seesMob(bot.body(), e))) {
            double reach = 5 + e.distanceTo(bot.body()) / 3.0;
            Double known = mobs.get(e.getType());
            if (known == null || reach < known) {
                mobs.put(e.getType(), reach);
                where.put(e.getType(), e.blockPosition());
            }
        }
        visibleMobs = mobs;
        mobPos = where;
        dark = level.isDarkOutside();
        underground = net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body());
        habitatBot = bot;
        long clock = level.getOverworldClockTime() % 24000;
        nightLeft = dark && clock >= 12000 ? (24000 - clock) / 20.0 : 0;
        // (The first monsters of the night come a little after dusk.)
        untilNight = dark ? 0 : clock < 13000 ? (13000 - clock) / 20.0 : (24000 - clock + 13000) / 20.0;
        nether = level.dimension() == net.minecraft.world.level.Level.NETHER;
        // Villagers: how far the nearest village is, and which trades are there at which level.
        villageWalk = INF;
        villagePos = null;
        villagerLevels.clear();
        if (!nether && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            var seenVillager = bot.memory().lastSeen(level, net.minecraft.world.entity.EntityTypes.VILLAGER, c);
            if (seenVillager != null) {
                villageWalk = 10 + net.kasax.challengecraft.bot.BotTerrain.walkSeconds(bot, c, seenVillager);
                villagePos = seenVillager;
            }
            var village = net.kasax.challengecraft.bot.task.VisitStructureTask.nearest(bot, net.kasax.challengecraft.bot.task.VisitStructureTask.resolve(level, "village"));
            double viaMap = village == null ? Double.MAX_VALUE : 10 + net.kasax.challengecraft.bot.BotTerrain.walkSeconds(bot, c, village.spot());
            if (village != null && viaMap < villageWalk) {
                villageWalk = viaMap;
                villagePos = village.spot();
            }
            for (var v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, new AABB(c).inflate(96), e -> e.isAlive() && !e.isBaby())) {
                String prof = v.getVillagerData().profession().unwrapKey().map(k2 -> k2.identifier().getPath()).orElse("none");
                villagerLevels.merge(prof, v.getVillagerData().level(), Math::max);
            }
        }
        // Biomes in view: what they are known for is a walk away.
        Map<Block, Double> hints = new IdentityHashMap<>();
        Map<Block, BlockPos> hinted = new IdentityHashMap<>();
        for (Map.Entry<net.minecraft.resources.Identifier, BlockPos> e : bot.senses().biomes().entrySet()) {
            double walk = 6 + net.kasax.challengecraft.bot.BotTerrain.walkSeconds(bot, c, e.getValue());
            for (String id : BotKnowledge.typicalOf(e.getKey().getPath())) {
                Block b = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));
                if (b == net.minecraft.world.level.block.Blocks.AIR) continue;
                Double known = hints.get(b);
                if (known == null || walk < known) {
                    hints.put(b, walk);
                    hinted.put(b, e.getValue());
                }
            }
        }
        biomeHints = hints;
        hintPos = hinted;
        costs = computeCosts(bot);
    }

    // ---- effort estimates ---------------------------------------------------------------------

    private Map<Item, Double> computeCosts(Bot bot) {
        BotKnowledge k = BotKnowledge.get(bot.server());
        Map<Item, Double> cost = new IdentityHashMap<>();
        Map<Item, Double> search = new IdentityHashMap<>();
        Map<Item, BlockPos> at = new IdentityHashMap<>();
        searches = search;
        anchors = at;
        Set<Item> all = new HashSet<>(k.craftableItems());
        for (Item item : BuiltInRegistries.ITEM) {
            if (!k.blocksDropping(item).isEmpty() || !k.mobsDropping(item).isEmpty()) all.add(item);
        }
        // Made neither by a recipe nor dropped (see specialCost).
        all.addAll(List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.OBSIDIAN));
        for (TradeKnowledge.Trade t : TradeKnowledge.get(bot.server()).all()) all.add(t.gives());
        for (Item item : all) put(item, rawLeaf(bot, k, item, cost), cost, search, at);
        // Relax over the recipes until nothing gets cheaper (a few rounds: recipe chains are short).
        for (int round = 0; round < 10; round++) {
            boolean changed = false;
            for (Item item : all) {
                double now = cost.get(item);
                if (now == 0) continue;
                Leaf best = null;
                Leaf raw = rawLeaf(bot, k, item, cost);
                if (raw.cost() < now - 1e-6) {
                    best = raw;
                    now = raw.cost();
                }
                for (BotKnowledge.Route r : k.routesTo(item)) {
                    Leaf via = routeLeaf(r, cost, search, at);
                    if (via.cost() < now - 1e-6) {
                        best = via;
                        now = via.cost();
                    }
                }
                if (best != null) {
                    put(item, best, cost, search, at);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return cost;
    }

    private static void put(Item item, Leaf l, Map<Item, Double> cost, Map<Item, Double> search, Map<Item, BlockPos> at) {
        cost.put(item, l.cost());
        search.put(item, Math.min(l.cost(), l.search()));
        if (l.at() != null) at.put(item, l.at());
        else at.remove(item);
    }

    private double rawCost(Bot bot, BotKnowledge k, Item item, Map<Item, Double> cost) {
        return rawLeaf(bot, k, item, cost).cost();
    }

    private Leaf rawLeaf(Bot bot, BotKnowledge k, Item item, Map<Item, Double> cost) {
        if (assumeHeld.contains(item)) return new Leaf(0, 0, null);
        int held = net.kasax.challengecraft.bot.BotInventory.count(bot.body(), item);
        // A tool (anything that does not stack) held is free for good; a material only as far as
        // it goes (one raw iron does not make a block of nine).
        if (held > 0 && (item.getDefaultMaxStackSize() == 1 || item == Items.CRAFTING_TABLE || item == Items.FURNACE
                || item == Items.SMOKER || item == Items.BLAST_FURNACE)) return new Leaf(0, 0, null);
        double share = held <= 0 ? 1 : Math.max(0, 1 - held / 9.0);
        if (share == 0) return new Leaf(0, 0, null);
        return acquireLeaf(bot, k, item, cost).scaled(share);
    }

    private Leaf acquireLeaf(Bot bot, BotKnowledge k, Item item, Map<Item, Double> cost) {
        Leaf best = Leaf.NONE;
        for (BotKnowledge.Drop d : k.blocksDropping(item)) {
            Leaf l = blockLeaf(bot, k, d.block(), cost).scaled(1 / Math.max(0.05, d.count()));
            if (l.cost() < best.cost()) best = l;
        }
        for (BotKnowledge.MobDrop d : k.mobsDropping(item)) {
            Leaf l = mobLeaf(d.type(), d.effort()).scaled(1 / d.count());
            if (l.cost() < best.cost()) best = l;
        }
        for (Leaf l : new Leaf[]{tradeLeaf(bot, item, cost), barterLeaf(bot, item, cost), specialLeaf(bot, k, item, cost)}) {
            if (l.cost() < best.cost()) best = l;
        }
        return best;
    }

    /**
     * Gold ingots a piglin takes, on average, per item of these (from the bartering table: an
     * ender pearl in about every fifteen trades, obsidian in twelve, ...).
     */
    public static final Map<Item, Double> BARTER_GOLD = Map.ofEntries(Map.entry(Items.ENDER_PEARL, 15.3), Map.entry(Items.OBSIDIAN, 11.5),
            Map.entry(Items.CRYING_OBSIDIAN, 5.7), Map.entry(Items.FIRE_CHARGE, 11.5), Map.entry(Items.LEATHER, 3.8),
            Map.entry(Items.STRING, 3.8), Map.entry(Items.QUARTZ, 2.7), Map.entry(Items.SOUL_SAND, 2.3), Map.entry(Items.NETHER_BRICK, 2.3),
            Map.entry(Items.SPECTRAL_ARROW, 1.3), Map.entry(Items.IRON_NUGGET, 2.0));

    /**
     * Seconds per item bartered from piglins (in the Nether only): the gold (about six seconds of
     * admiring an ingot each), a gold piece to wear if it has none, finding a piglin.
     */
    private double barterCost(Bot bot, Item item, Map<Item, Double> cost) {
        return barterLeaf(bot, item, cost).cost();
    }

    /** (What a piglin gives is luck: half of it counted as such.) */
    private Leaf barterLeaf(Bot bot, Item item, Map<Item, Double> cost) {
        double c = barterSeconds(bot, item, cost);
        return c >= INF ? Leaf.NONE : new Leaf(c, c / 2, mobPos.get(net.minecraft.world.entity.EntityTypes.PIGLIN));
    }

    private double barterSeconds(Bot bot, Item item, Map<Item, Double> cost) {
        Double per = BARTER_GOLD.get(item);
        if (per == null || !nether) return INF;
        double gold = cost.getOrDefault(Items.GOLD_INGOT, INF);
        if (gold >= INF) return INF;
        boolean armour = net.kasax.challengecraft.bot.BotArmor.wearsGold(bot.body())
                || countAny(bot.body(), Set.of(Items.GOLDEN_HELMET, Items.GOLDEN_BOOTS, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS)) > 0;
        double wear = armour ? 0 : Math.min(cost.getOrDefault(Items.GOLDEN_BOOTS, INF), cost.getOrDefault(Items.GOLDEN_HELMET, INF));
        if (wear >= INF) return INF;
        Double piglin = visibleMobs.get(net.minecraft.world.entity.EntityTypes.PIGLIN);
        double find = piglin != null ? piglin : 60;
        return per * (gold + 6.5) + (wear + find + 20) / Math.max(1, 4 / per);
    }

    /** Seconds to the nearest known village (INF: none known), and the best level per profession there. */
    private double villageWalk = INF;
    private final Map<String, Integer> villagerLevels = new HashMap<>();

    /**
     * Seconds per item bought from a villager: the walk there, a villager of the trade's profession
     * (one given a job site if none has it), levelled up to the trade's level by selling it cheap
     * things, and the price.
     */
    private double tradeCost(Bot bot, Item item, Map<Item, Double> cost) {
        return tradeLeaf(bot, item, cost).cost();
    }

    /** (Known work, but for a jobless villager taking the job site put down: luck, counted half.) */
    private Leaf tradeLeaf(Bot bot, Item item, Map<Item, Double> cost) {
        double c = tradeSeconds(bot, item, cost);
        if (c >= INF) return Leaf.NONE;
        return new Leaf(c, Math.min(c, 15 + nightLeft / 2), villagePos);
    }

    private double tradeSeconds(Bot bot, Item item, Map<Item, Double> cost) {
        if (villageWalk >= INF) return INF;
        double best = INF;
        for (TradeKnowledge.Trade t : TradeKnowledge.get(bot.server()).selling(item)) {
            if (t.wants() == item) continue;
            best = Math.min(best, tradeCostOf(bot, t, cost));
        }
        return best;
    }

    /** {@link #tradeCost} for one particular trade. */
    private double tradeCostOf(Bot bot, TradeKnowledge.Trade t, Map<Item, Double> cost) {
        if (villageWalk >= INF) return INF;
        double pay = t.wantsCount() * cost.getOrDefault(t.wants(), INF) + (t.wantsB() == null ? 0 : t.wantsBCount() * cost.getOrDefault(t.wantsB(), INF));
        if (pay >= INF) return INF;
        int has = villagerLevels.getOrDefault(t.profession(), 0);
        double job = has > 0 ? 0 : villagerLevels.containsKey("none") ? 40 + nightLeft + cost.getOrDefault(TradeKnowledge.JOB_SITES.get(t.profession()).asItem(), INF) : INF;
        if (job >= INF) return INF;
        // Each level is so much experience from selling it things: the cheapest way per point,
        // with what Bob holds (sticks in the pack make a fletcher's levels a matter of seconds).
        double levels = 0;
        for (int l = Math.max(1, has); l < t.level(); l++) {
            double perXp = INF;
            for (TradeKnowledge.Trade e : TradeKnowledge.get(bot.server()).earning(t.profession(), l)) {
                double c = e.wantsCount() * cost.getOrDefault(e.wants(), INF) + 3;
                if (e.xp() > 0 && c < INF) perXp = Math.min(perXp, c / e.xp());
            }
            levels += perXp * (TradeKnowledge.LEVEL_XP[l + 1] - TradeKnowledge.LEVEL_XP[l]);
        }
        if (levels >= INF) return INF;
        return (villageWalk + 15 + job + levels + pay) / Math.max(1, t.givesCount());
    }


    /** Seconds to reach a biome that is nowhere in view (beyond some 400 blocks, and looked for). */
    private static final double FAR_BIOME = 600;

    /** Seconds to find some water when none is known (in the Overworld it is never far). */
    private static final double WATER_SEARCH = 90;

    /** Things not made by a recipe nor dropped: filled buckets, obsidian from a lava pool. */
    private double specialCost(Bot bot, BotKnowledge k, Item item, Map<Item, Double> cost) {
        return specialLeaf(bot, k, item, cost).cost();
    }

    private Leaf specialLeaf(Bot bot, BotKnowledge k, Item item, Map<Item, Double> cost) {
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            Block fluid = item == Items.WATER_BUCKET ? Blocks.WATER : Blocks.LAVA;
            Double seen = visibleBlocks.get(fluid);
            double search = 0;
            // Water in the Overworld is never far, even when none is known: a short search.
            if (seen == null && item == Items.WATER_BUCKET && !nether) {
                seen = WATER_SEARCH;
                search = WATER_SEARCH;
            }
            if (seen == null) return Leaf.NONE;
            double bucket = cost.getOrDefault(Items.BUCKET, INF);
            return new Leaf(seen + 3 + bucket, search + searches.getOrDefault(Items.BUCKET, 0.0), visiblePos.get(fluid));
        }
        if (item == Items.OBSIDIAN) {
            Double lava = visibleBlocks.get(Blocks.LAVA);
            if (lava == null) return Leaf.NONE;
            double tool = toolCost(bot, k, Blocks.OBSIDIAN, cost);
            double water = cost.getOrDefault(Items.WATER_BUCKET, INF);
            double c = lava + 14 + Math.min(tool, INF) / 10 + water / 10; // tools once for all ten
            return new Leaf(c, searches.getOrDefault(Items.WATER_BUCKET, 0.0) / 10, visiblePos.get(Blocks.LAVA));
        }
        return Leaf.NONE;
    }

    private double blockCost(Bot bot, BotKnowledge k, Block block, Map<Item, Double> cost) {
        return blockLeaf(bot, k, block, cost).cost();
    }

    /**
     * Seen (or remembered): a walk there, no luck needed. A biome known for it in view: a walk
     * there and a little looking about. Neither: a search, all luck - for an ore, down where it
     * is common (the place to work is then right below), for the rest anywhere about.
     */
    private Leaf blockLeaf(Bot bot, BotKnowledge k, Block block, Map<Item, Double> cost) {
        BlockState s = block.defaultBlockState();
        Double seen = visibleBlocks.get(block);
        // (Given up on as missing, but in sight now: it is there after all.)
        if (seen == null && isMissing(block)) return Leaf.NONE;
        double find = BotKnowledge.rarity(block, nether);
        double search = find;
        BlockPos at = null;
        if (seen != null && seen <= find) {
            find = seen;
            search = 0;
            at = visiblePos.get(block);
        }
        Double hint = biomeHints.get(block);
        if (hint != null && hint + 10 < find) {
            find = hint + 10; // + finding it there
            search = 10;
            at = hintPos.get(block);
        }
        // Only far off, where it grows (snow beyond the desert): the walk there.
        if (seen == null && hint == null) {
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            String dim = bot.body().level().dimension().identifier().toString();
            double nearest = Double.MAX_VALUE;
            for (var e : bot.farLeads.entrySet()) {
                if (e.getKey().dimension().equals(dim) && e.getValue().contains(id)) nearest = Math.min(nearest, Math.sqrt(e.getKey().pos().distSqr(bot.body().blockPosition())));
            }
            if (nearest < Double.MAX_VALUE) {
                find = Math.max(find, nearest / 5 + 10);
                // (The walk there is known; finding it there is the luck.)
                search = Math.min(find, 30 + 0.3 * find);
            }
            // Only found in certain biomes, and none of them anywhere in view (24 chunks): far off,
            // a long walk into the unknown. (Snow from the plains is not fifteen seconds away.)
            else if (!nether && BotKnowledge.biomeBound(block)) {
                find = Math.max(find, FAR_BIOME);
                search = 0.8 * find;
            }
        }
        // A cave biome's block not seen yet: after the walk to the biome, the way down into it
        // and the looking about there (minutes, and luck).
        if (seen == null && BotKnowledge.caveBiome(block)) {
            find += 90;
            search = Math.max(search, 0.7 * find);
        }
        // Not seen, dug for: the work is right below, at the depth where it is common.
        if (at == null && here != null && search > 0) {
            Integer depth = BotKnowledge.depth(block, here.getY());
            if (depth != null && depth < here.getY() - 4) {
                at = new BlockPos(here.getX(), depth, here.getZ());
                // A cave known to reach down to that height: the way down is walked, and its
                // walls show ore all along - much less looking about than a tunnel.
                BlockPos cave = bot.memory().cave((net.minecraft.server.level.ServerLevel) bot.body().level(), here, depth, 12, 128, List.of());
                if (cave != null && Math.abs(cave.getY() - depth) <= 12) {
                    double cut = 0.4 * search;
                    search -= cut;
                    find -= cut;
                    at = cave;
                }
            }
        }
        double breakSeconds = Math.min(60, bot.tools().breakTicks(s) / 20.0);
        Leaf tool = toolLeaf(bot, k, block, cost);
        return new Leaf(find + breakSeconds + tool.cost(), search + tool.search(), at != null ? at : tool.at());
    }

    private double toolCost(Bot bot, BotKnowledge k, Block block, Map<Item, Double> cost) {
        return toolLeaf(bot, k, block, cost).cost();
    }

    private Leaf toolLeaf(Bot bot, BotKnowledge k, Block block, Map<Item, Double> cost) {
        BlockState s = block.defaultBlockState();
        if (bot.tools().canHarvest(s)) return new Leaf(0, 0, null);
        Item best = null;
        for (Item t : k.harvestTools(block)) if (best == null || cost.getOrDefault(t, INF) < cost.getOrDefault(best, INF)) best = t;
        if (best == null || cost.getOrDefault(best, INF) >= INF) return Leaf.NONE;
        return new Leaf(cost.get(best), searches.getOrDefault(best, 0.0), anchors.get(best));
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

    /**
     * {@link #routeCost} with the searching parts of the ingredients added up, and the place of
     * the dearest ingredient that has one (the iron for the bucket: where the iron is).
     */
    private static Leaf routeLeaf(BotKnowledge.Route r, Map<Item, Double> cost, Map<Item, Double> search, Map<Item, BlockPos> at) {
        List<Ingredient> ings = r instanceof BotKnowledge.CraftRoute c ? c.ingredients() : List.of(((BotKnowledge.SmeltRoute) r).input());
        double sum = r instanceof BotKnowledge.CraftRoute ? 1 : 6, luck = 0, dearest = -1;
        BlockPos where = null;
        for (Ingredient i : ings) {
            Item pick = null;
            for (Item item : BotKnowledge.items(i)) if (pick == null || cost.getOrDefault(item, INF) < cost.getOrDefault(pick, INF)) pick = item;
            double c = pick == null ? INF : cost.getOrDefault(pick, INF);
            if (c >= INF) return Leaf.NONE;
            sum += c;
            luck += search.getOrDefault(pick, 0.0);
            BlockPos a = at.get(pick);
            if (a != null && c > dearest) {
                dearest = c;
                where = a;
            }
        }
        int yield = r instanceof BotKnowledge.CraftRoute c ? c.yield() : 1;
        return new Leaf(sum / yield, luck / yield, where);
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

    private enum Kind { MINE, KILL, ROUTE, CRAFT, SMELT, FILL, CAST, TRADE, BARTER }

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
        startSearch();
        long t0 = System.nanoTime();
        String why = expand(bot, sim, accept, count, depth, visiting);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (ms > 50) net.kasax.challengecraft.bot.BotManager.LOG.warn("[Bot] planning {} took {} ms ({} steps left of budget)", names(accept), ms, budget);
        if (why != null) return new Stuck(why);
        if (sim.steps.isEmpty()) return new Have();
        Pending first = sim.steps.get(0);
        if (Boolean.getBoolean("bob.debugPlan")) net.kasax.challengecraft.bot.BotManager.LOG.info("[Bot] plan for {} x{}: {}", names(accept), count,
                sim.steps.stream().map(st -> st.kind() + " " + st.key() + " x" + st.amount()).toList());
        // Everything from the surface first, then down: a digger that comes back up for wood or
        // wool loses minutes. (Gathering needs no other step first, only tools it already has.)
        if (digsDown(bot, first)) {
            for (Pending p : sim.steps) {
                if (p.kind() == Kind.MINE || p.kind() == Kind.KILL) {
                    if (!digsDown(bot, p) && usableNow(bot, p)) {
                        first = p;
                        break;
                    }
                }
            }
        }
        int total = 0;
        for (Pending p : sim.steps) if (p.key().equals(first.key())) total += p.amount();
        Set<?> sources = first.kind() == Kind.MINE || first.kind() == Kind.KILL ? (Set<?>) first.data() : Set.of();
        return new Step(toTask(bot, first, total), first.key(), sources);
    }

    @SuppressWarnings("unchecked")
    private boolean digsDown(Bot bot, Pending p) {
        if (p.kind() != Kind.MINE) return false;
        Set<Block> blocks = (Set<Block>) p.data();
        for (Block b : blocks) if (visibleBlocks.containsKey(b)) return false;
        Block common = blocks.stream().min(Comparator.comparingDouble(BotKnowledge::rarity)).orElse(null);
        Integer depth = common == null ? null : BotKnowledge.depth(common, bot.body().blockPosition().getY());
        return depth != null && depth < bot.body().blockPosition().getY() - 8;
    }

    /** A gathering step the bot can do with what it holds now (the right tool, if any is needed). */
    @SuppressWarnings("unchecked")
    private boolean usableNow(Bot bot, Pending p) {
        if (p.kind() == Kind.KILL) return true;
        for (Block b : (Set<Block>) p.data()) if (bot.tools().canHarvest(b.defaultBlockState())) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private BotTask toTask(Bot bot, Pending p, int total) {
        return switch (p.kind()) {
            case MINE -> {
                Set<Block> blocks = (Set<Block>) p.data();
                // If it has to dig for them: to the height of the most common of these blocks.
                Block common = blocks.stream().min(Comparator.comparingDouble(BotKnowledge::rarity)).orElseThrow();
                Integer depth = BotKnowledge.depth(common, bot.body().blockPosition().getY());
                int have = countAny(bot.body(), p.accept());
                // More of it wanted by other tiles on the board (iron for the bucket, the shears and
                // the compass): taken in the same trip while the vein is right there.
                int extra = 0;
                for (Item i : p.accept()) extra = Math.max(extra, boardDemand.getOrDefault(i, 0) - have - total);
                MineTask mine = new MineTask(names(p.accept()), s -> blocks.contains(s.getBlock()), p.accept(),
                        have + total + Math.max(0, Math.min(extra, 16)), depth, lead(bot, blocks)).atLeast(have + total);
                // Not lying about anywhere (a furnace, bricks): taken where seen, never searched
                // for (that walk can be a thousand blocks long); otherwise made.
                boolean nether = bot.body().level().dimension() == net.minecraft.world.level.Level.NETHER;
                if (BotKnowledge.rarity(common, nether) >= 1000) mine.knownOnly();
                // A cave biome's block: to the biome first, then down (not dug for right here).
                if (BotKnowledge.caveBiome(common) || blocks.contains(Blocks.AMETHYST_CLUSTER)) mine.digAtLead();
                // Nether wart grows in fortresses only: to the fortress first (exploring for one).
                if (nether && blocks.contains(Blocks.NETHER_WART) && !visibleBlocks.containsKey(Blocks.NETHER_WART)) {
                    var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
                    yield new net.kasax.challengecraft.bot.task.SequenceTask("nether wart from a fortress", List.of(
                            () -> new net.kasax.challengecraft.bot.task.VisitStructureTask(level, "minecraft:fortress"), () -> mine));
                }
                yield mine;
            }
            case KILL -> new KillTask((Set<EntityType<?>>) p.data(), p.accept(), countAny(bot.body(), p.accept()) + total, 0);
            case CRAFT -> new CraftTask((BotKnowledge.CraftRoute) p.data(), total);
            // Everything the board will want of it in one go (the ingots for the bucket, the
            // pickaxe and the shears): one furnace, one wait, instead of three trips to it.
            case SMELT -> {
                BotKnowledge.SmeltRoute r = (BotKnowledge.SmeltRoute) p.data();
                int later = demandFor(r.result()) - countAny(bot.body(), Set.of(r.result()));
                yield new SmeltTask(r, Math.max(total, Math.min(total + 16, later)));
            }
            case FILL -> new net.kasax.challengecraft.bot.task.FillBucketTask(p.data() == Items.LAVA_BUCKET
                    ? net.minecraft.tags.FluidTags.LAVA : net.minecraft.tags.FluidTags.WATER);
            case CAST -> new net.kasax.challengecraft.bot.task.MakeObsidianTask(countAny(bot.body(), Set.of(Items.OBSIDIAN)) + total);
            case TRADE -> new net.kasax.challengecraft.bot.task.VillagerTradeTask((TradeKnowledge.Trade) p.data(), countAny(bot.body(), p.accept()) + total);
            // (Luck decides: up to half as many trades again as the average wants.)
            case BARTER -> new net.kasax.challengecraft.bot.task.BarterTask(p.accept(), countAny(bot.body(), p.accept()) + total,
                    (int) Math.ceil(BARTER_GOLD.get(p.accept().iterator().next()) * total * 1.5) + 2);
            default -> throw new IllegalStateException();
        };
    }

    /** What is wanted in the end (wood itself, or something made from any wood). */
    private Set<Item> wanted = Set.of();

    /** The nearest biome in view known for one of these blocks, if none of them is in sight. */
    private BlockPos lead(Bot bot, Set<Block> blocks) {
        // Amethyst grows inside geodes: their shell (calcite round smooth basalt) shows in cave
        // walls and on hillsides from afar. One remembered: there, and in.
        if (blocks.contains(Blocks.AMETHYST_CLUSTER) && !visibleBlocks.containsKey(Blocks.AMETHYST_CLUSTER)) {
            BlockPos shell = bot.memory().nearest((net.minecraft.server.level.ServerLevel) bot.body().level(), bot.body().blockPosition(),
                    st -> st.is(Blocks.BUDDING_AMETHYST) || st.is(Blocks.AMETHYST_BLOCK) || st.is(Blocks.CALCITE) || st.is(Blocks.SMOOTH_BASALT), Set.of());
            if (shell != null) return shell;
        }
        // Seen ones may turn out out of reach (deep down, under water): the far lead stands by.
        for (Block b : blocks) if (visibleBlocks.containsKey(b)) return bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                && BotKnowledge.depth(b, bot.body().getBlockY()) == null ? farLeadStandBy(bot, blocks) : null;
        // Wood of any kind does (planks are planks): to the nearest forest, whichever trees grow there.
        // (Unless it is that wood that is wanted: a spruce log for its own sake.)
        if (blocks.stream().anyMatch(b -> b.defaultBlockState().is(net.minecraft.tags.BlockTags.OVERWORLD_NATURAL_LOGS))
                && wanted.stream().noneMatch(i -> i.getDefaultInstance().is(net.minecraft.tags.ItemTags.LOGS))) {
            Set<Block> logs = new HashSet<>();
            for (Block b : BuiltInRegistries.BLOCK) if (b.defaultBlockState().is(net.minecraft.tags.BlockTags.OVERWORLD_NATURAL_LOGS)) logs.add(b);
            blocks = logs;
        }
        BlockPos from = bot.body().blockPosition(), best = null;
        for (Map.Entry<net.minecraft.resources.Identifier, BlockPos> e : bot.senses().biomes().entrySet()) {
            boolean known = false;
            for (String id : BotKnowledge.typicalOf(e.getKey().getPath())) {
                Block b = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));
                if (blocks.contains(b)) known = true;
            }
            if (known && (best == null || e.getValue().distSqr(from) < best.distSqr(from))) best = e.getValue();
        }
        if (best == null) best = farLead(bot, blocks);
        return best;
    }

    /** Raw materials the open tiles want in all (set by the game plan): mined in bulk when in a vein. */
    public final Map<Item, Integer> boardDemand = new HashMap<>();

    /** The raw ore an ingot is smelted from (the board's demand is kept in raw ore). */
    private static final Map<Item, Item> INGOT_RAW = Map.of(Items.IRON_INGOT, Items.RAW_IRON, Items.GOLD_INGOT, Items.RAW_GOLD,
            Items.COPPER_INGOT, Items.RAW_COPPER);

    /** How many of {@code item} the board will want in all: an ingot counts what is wanted of its raw ore too. */
    public int demandFor(Item item) {
        Item raw = INGOT_RAW.get(item);
        return boardDemand.getOrDefault(item, 0) + (raw == null ? 0 : boardDemand.getOrDefault(raw, 0));
    }

    /** A far lead only if one was found before (no new biome search for blocks that are in sight). */
    private static BlockPos farLeadStandBy(Bot bot, Set<Block> blocks) {
        BlockPos from = bot.body().blockPosition(), known = null;
        String dim = bot.body().level().dimension().identifier().toString();
        for (var e : bot.farLeads.entrySet()) {
            if (!e.getKey().dimension().equals(dim)) continue;
            boolean fits = e.getValue().stream().anyMatch(id -> blocks.contains(BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id))));
            if (fits && (known == null || e.getKey().pos().distSqr(from) < known.distSqr(from))) known = e.getKey().pos();
        }
        return known != null && known.distSqr(from) > 24 * 24 ? known : null;
    }

    /**
     * None in view either: the nearest biome known for them further out (a forest on the horizon),
     * from the world's biome layout. Only for blocks some biome is known for.
     */
    private static BlockPos farLead(Bot bot, Set<Block> blocks) {
        if (!(bot.body().level() instanceof net.minecraft.server.level.ServerLevel level)) return null;
        java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> typical = h -> h.unwrapKey()
                .map(k -> BotKnowledge.typicalOf(k.identifier().getPath()).stream()
                        .anyMatch(id -> blocks.contains(BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id)))))
                .orElse(false);
        BlockPos from = bot.body().blockPosition();
        // Where it set out to last time (the same forest), so a fresh start does not turn round.
        BlockPos known = null;
        for (var e : bot.farLeads.entrySet()) {
            if (!e.getKey().dimension().equals(level.dimension().identifier().toString())) continue;
            boolean fits = e.getValue().stream().anyMatch(id -> blocks.contains(BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id))));
            BlockPos p = e.getKey().pos();
            if (fits && (known == null || p.distSqr(from) < known.distSqr(from))) known = p;
        }
        if (known != null && known.distSqr(from) > 24 * 24) return known;
        var found = level.findClosestBiome3d(typical, new BlockPos(from.getX(), Math.max(from.getY(), level.getSeaLevel()), from.getZ()), 1200, 32, 64);
        if (found == null) return null;
        BlockPos at = found.getFirst();
        // (Not generated yet the ground there reads as the bottom of the world: sea level instead.)
        BlockPos lead = level.hasChunkAt(at) ? net.kasax.challengecraft.bot.task.Explorer.ground(level, at, Math.max(from.getY(), level.getSeaLevel()))
                : new BlockPos(at.getX(), level.getSeaLevel(), at.getZ());
        String biome = found.getSecond().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        bot.farLeads.put(new Bot.FarLead(level.dimension().identifier().toString(), lead), BotKnowledge.typicalOf(biome));
        return lead;
    }

    /** Plays getting {@code count} of {@code accept} through on {@code sim}; null if it works out, else why not. */
    private String expand(Bot bot, Sim sim, Set<Item> accept, int count, int depth, Set<Item> visiting) {
        int need = count - sim.count(accept);
        if (need <= 0) return null;
        if (depth > MAX_DEPTH) return "too deep";
        // Long recipe chains with failed ways branch out fast: a budget per plan, and what could not
        // be had once is not tried again in the same plan.
        if (--budget < 0) return "too complicated to plan";
        // (What is excluded on the way — the thing being made — changes what is possible.)
        List<Object> key = List.of(accept, Set.copyOf(visiting));
        Integer failedAt = impossible.get(key);
        if (failedAt != null && need >= failedAt) return "no way to get " + names(accept);
        String why = expandWays(bot, sim, accept, count, depth, visiting);
        if (why != null) impossible.merge(key, need, Math::min);
        return why;
    }

    private void startSearch() {
        budget = 4000;
        impossible.clear();
    }

    private String expandWays(Bot bot, Sim sim, Set<Item> accept, int count, int depth, Set<Item> visiting) {
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
        if (!onlyCraft) {
            for (Item item : accept) {
                Block source = item == Items.WATER_BUCKET ? Blocks.WATER : item == Items.LAVA_BUCKET ? Blocks.LAVA : null;
                boolean known = source != null && (visibleBlocks.containsKey(source) || source == Blocks.WATER && !nether);
                if (known && !visiting.contains(Items.BUCKET) && !isFailed("fill:" + name(item), now)) {
                    ways.add(new Way("fill:" + name(item), visibleBlocks.getOrDefault(source, WATER_SEARCH) + 3 + cost(Items.BUCKET), Kind.FILL, item));
                }
            }
            // Bought from a villager.
            if (villageWalk < INF) {
                for (Item item : accept) {
                    for (TradeKnowledge.Trade t : TradeKnowledge.get(bot.server()).selling(item)) {
                        String key = "trade:" + t.profession() + ":" + name(item);
                        if (t.wants() == item || isFailed(key, now) || visiting.contains(t.wants())) continue;
                        double c = tradeCostOf(bot, t, costs);
                        if (c < INF) ways.add(new Way(key + ":" + name(t.wants()), c, Kind.TRADE, t));
                    }
                }
            }
            // Bartered from piglins (in the Nether).
            if (nether) {
                for (Item item : accept) {
                    String key = "barter:" + name(item);
                    if (!BARTER_GOLD.containsKey(item) || isFailed(key, now) || visiting.contains(Items.GOLD_INGOT)) continue;
                    double c = barterCost(bot, item, costs);
                    if (c < INF) ways.add(new Way(key, c, Kind.BARTER, item));
                }
            }
            if (accept.contains(Items.OBSIDIAN) && visibleBlocks.containsKey(Blocks.LAVA) && !isFailed("cast:obsidian", now)) {
                ways.add(new Way("cast:obsidian", specialCost(bot, k, Items.OBSIDIAN, costs), Kind.CAST, null));
            }
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
            if (w.cost() > MAX_WAY_COST || tried++ >= 6) break;
            Sim before = sim.copy();
            String why = switch (w.kind()) {
                case MINE -> mine(bot, k, sim, accept, count, w, depth, visiting);
                case KILL -> kill(sim, accept, count, w);
                case FILL -> fill(bot, sim, (Item) w.data(), count - sim.count(accept), depth, visiting, w.key());
                case CAST -> cast(bot, k, sim, count - sim.count(accept), depth, visiting, w.key());
                case TRADE -> trade(bot, sim, (TradeKnowledge.Trade) w.data(), count - sim.count(accept), depth, visiting, w.key());
                case BARTER -> barter(bot, sim, (Item) w.data(), count - sim.count(accept), depth, visiting, w.key());
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
        // (One seen close by and only a few wanted: walked to, no digging. Many wanted, or the
        // one seen far off: the staircase down - the mine does that whatever was seen.)
        Double seenAt = cheapest == null ? null : visibleBlocks.get(cheapest);
        int wanted = count - sim.count(accept);
        Integer digTo = cheapest == null || seenAt != null && seenAt < 20 && wanted < 3 ? null
                : BotKnowledge.depth(cheapest, bot.body().blockPosition().getY());
        // (Not for rock itself - the stone is what it is after - nor for a few levels down, nor
        // once down there: those are made up here, before the staircase, from the wood at hand.)
        int levels = bot.body().blockPosition().getY() - (digTo == null ? 0 : digTo);
        if (digTo != null && !BotWorld.COMMON.contains(cheapest) && levels > 20
                && !net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body())
                && !accept.contains(Items.STONE_PICKAXE) && !visiting.contains(Items.STONE_PICKAXE)) {
            // A staircase takes three blocks a level, then a tunnel down there: a stone pickaxe
            // lasts some 130 blocks.
            int spare = Math.min(4, 1 + (levels * 3 + 60) / 130);
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

    private String fill(Bot bot, Sim sim, Item filled, int need, int depth, Set<Item> visiting, String key) {
        Set<Item> v = new HashSet<>(visiting);
        v.add(filled);
        for (int i = 0; i < need; i++) {
            if (sim.count(Set.of(Items.BUCKET)) < 1) {
                String why = expand(bot, sim, Set.of(Items.BUCKET), 1, depth + 1, v);
                if (why != null) return why;
            }
            sim.take(Set.of(Items.BUCKET), 1);
            sim.add(filled, 1);
            sim.steps.add(new Pending(Kind.FILL, key, Set.of(filled), 1, filled));
        }
        return null;
    }

    private String trade(Bot bot, Sim sim, TradeKnowledge.Trade t, int need, int depth, Set<Item> visiting, String key) {
        Set<Item> v = new HashSet<>(visiting);
        v.add(t.gives());
        int rounds = (need + t.givesCount() - 1) / t.givesCount();
        String why = expand(bot, sim, Set.of(t.wants()), t.wantsCount() * rounds, depth + 1, v);
        if (why != null) return why;
        if (t.wantsB() != null) {
            why = expand(bot, sim, Set.of(t.wantsB()), t.wantsBCount() * rounds, depth + 1, v);
            if (why != null) return why;
        }
        sim.take(Set.of(t.wants()), t.wantsCount() * rounds);
        if (t.wantsB() != null) sim.take(Set.of(t.wantsB()), t.wantsBCount() * rounds);
        sim.add(t.gives(), t.givesCount() * rounds);
        sim.steps.add(new Pending(Kind.TRADE, key, Set.of(t.gives()), t.givesCount() * rounds, t));
        return null;
    }

    private String barter(Bot bot, Sim sim, Item item, int need, int depth, Set<Item> visiting, String key) {
        Set<Item> v = new HashSet<>(visiting);
        v.add(item);
        int gold = (int) Math.ceil(BARTER_GOLD.get(item) * need);
        String why = expand(bot, sim, Set.of(Items.GOLD_INGOT), gold, depth + 1, v);
        if (why != null) return why;
        sim.take(Set.of(Items.GOLD_INGOT), gold);
        sim.add(item, need);
        sim.steps.add(new Pending(Kind.BARTER, key, Set.of(item), need, gold));
        return null;
    }

    private String cast(Bot bot, BotKnowledge k, Sim sim, int need, int depth, Set<Item> visiting, String key) {
        Set<Item> v = new HashSet<>(visiting);
        v.add(Items.OBSIDIAN);
        if (sim.count(Set.of(Items.WATER_BUCKET)) == 0) {
            String why = expand(bot, sim, Set.of(Items.WATER_BUCKET), 1, depth + 1, v);
            if (why != null) return why;
        }
        if (!sim.canHarvest(Blocks.OBSIDIAN.defaultBlockState())) {
            List<Item> tools = k.harvestTools(Blocks.OBSIDIAN).stream().filter(t -> !v.contains(t)).toList();
            if (tools.isEmpty()) return "nothing to mine obsidian with";
            Item tool = tools.stream().min(Comparator.comparingDouble(this::cost)).orElseThrow();
            String why = expand(bot, sim, Set.of(tool), 1, depth + 1, v);
            if (why != null) return why;
        }
        sim.add(Items.OBSIDIAN, need);
        sim.steps.add(new Pending(Kind.CAST, key, Set.of(Items.OBSIDIAN), need, null));
        return null;
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
                bot.body().blockPosition(), StationTaskRange.RANGE, 8, s -> s.is(block), false, bot.unreachableStations) != null;
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
