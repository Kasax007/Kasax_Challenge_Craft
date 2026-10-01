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
    private static final Set<EntityType<?>> BOSSES = Set.of(EntityTypes.ENDER_DRAGON, EntityTypes.WITHER, EntityTypes.WARDEN,
            EntityTypes.ELDER_GUARDIAN, EntityTypes.RAVAGER, EntityTypes.SHULKER, EntityTypes.BREEZE, EntityTypes.GUARDIAN, EntityTypes.PHANTOM);

    /** Goals estimated above this (half an hour) are out of reach for now. */
    private static final double MAX_COST = 1800;

    /** Whether the bot knows any way for this goal (whatever the cost; Nether goals count in any dimension). */
    public static boolean covers(Bot bot, ObtainPlanner planner, LockoutBingoGoal goal) {
        if (goal.category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER && overworld(bot)
                && goal.type() != net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.DIMENSION) {
            // From up here: whether it knows a way once down there (as if it stood in the Nether,
            // knowing nothing of it yet). Places there are found by exploring.
            var t = goal.type();
            if (t == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.BIOME
                    || t == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.STRUCTURE) return true;
            return planner.inNether(bot, () -> basic(bot, planner, goal)) != null;
        }
        return option(bot, planner, goal) != null;
    }

    public static Option plan(Bot bot, ObtainPlanner planner, LockoutBingoGoal goal) {
        Option o = option(bot, planner, goal);
        return o == null || o.cost() > MAX_COST ? null : o;
    }

    private static Option option(Bot bot, ObtainPlanner planner, LockoutBingoGoal goal) {
        // A Nether tile from up here: the way in first (a portal cast and walked through); once
        // there, the tile is planned for what it is. (Without this, no Nether tile ever looks doable
        // from the Overworld, and the bot never goes.)
        if (goal.category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER && overworld(bot)
                && goal.type() != net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.DIMENSION) {
            Option trip = nether(bot, planner);
            return trip == null ? null : new Option(trip.cost() + 90, trip.task(), trip.yields());
        }
        // An Overworld tile from down in the Nether: back through the portal first (the tile is
        // planned for real once up there). Nether tiles come first that way, then home.
        if (bot.body().level().dimension() == Level.NETHER
                && goal.category() != net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER) {
            if (goal.type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.DIMENSION) return null;
            Double back = wayBack(bot);
            return back == null ? null : new Option(back + 150, net.kasax.challengecraft.bot.task.ThroughPortalTask::new);
        }
        Option here = basic(bot, planner, goal);
        if (here != null || !overworld(bot)) return here;
        // Not to be had up here, but made from something the Nether has (quartz, blaze rods, soul
        // sand): the trip there, then the rest planned there.
        var t = goal.type();
        if (t != net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.ITEM && t != net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.ITEM_AMOUNT
                && t != net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.CRAFT) return null;
        Option there = planner.inNether(bot, () -> basic(bot, planner, goal));
        Option trip = nether(bot, planner);
        if (trip == null) return null;
        if (there != null) return new Option(trip.cost() + there.cost() + 60, trip.task(), trip.yields());
        return fetchFromNether(bot, planner, goal, trip);
    }

    /** What only the Nether has, that Overworld things are made from. */
    private static final List<Item> NETHER_RAWS = List.of(Items.QUARTZ, Items.BLAZE_ROD, Items.GLOWSTONE_DUST, Items.SOUL_SAND,
            Items.NETHER_WART, Items.GHAST_TEAR, Items.MAGMA_CREAM, Items.CRIMSON_STEM, Items.WARPED_STEM, Items.BLACKSTONE);

    /**
     * Made at home from something only the Nether has (a comparator: stone and redstone here, the
     * quartz there): over, just that fetched, back, and made up here as a player does it. Null
     * when that does not work out either.
     */
    private static Option fetchFromNether(Bot bot, ObtainPlanner planner, LockoutBingoGoal goal, Option trip) {
        Set<Item> all = new java.util.HashSet<>(NETHER_RAWS);
        Option home = planner.assuming(bot, all, () -> basic(bot, planner, goal));
        if (home == null) return null;
        // Which of them it really takes: those without which it is not to be had.
        List<Item> wanted = new ArrayList<>();
        for (Item raw : NETHER_RAWS) {
            if (ObtainPlanner.countAny(bot.body(), Set.of(raw)) > 0) continue;
            Set<Item> without = new java.util.HashSet<>(all);
            without.remove(raw);
            if (planner.assuming(bot, without, () -> basic(bot, planner, goal)) == null) wanted.add(raw);
        }
        if (wanted.isEmpty() || wanted.size() > 2) return null;
        double fetch = 0;
        for (Item raw : wanted) {
            double c = planner.inNether(bot, () -> planner.estimate(bot, Set.of(raw), 3));
            if (c >= INF) return null;
            fetch += c;
        }
        double cost = trip.cost() + fetch + 120 + home.cost();
        List<Supplier<BotTask>> steps = new ArrayList<>();
        steps.add(trip.task());
        for (Item raw : wanted) steps.add(() -> new ObtainTask(Set.of(raw), 3, planner));
        steps.add(net.kasax.challengecraft.bot.task.ThroughPortalTask::new);
        steps.add(() -> {
            Option now = basic(bot, planner, goal);
            return now == null ? null : now.task().get();
        });
        String names = wanted.stream().map(ObtainPlanner::name).collect(java.util.stream.Collectors.joining(" and "));
        return new Option(cost, () -> new SequenceTask(names + " from the Nether, then " + goal.id(), steps), trip.yields());
    }

    private static Option basic(Bot bot, ObtainPlanner planner, LockoutBingoGoal goal) {
        Option stunt = stunt(bot, planner, goal.id());
        if (stunt != null) return stunt;
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
            case STRUCTURE -> structure(bot, planner, goal);
            case LOCATION -> "reach_y_minus_50".equals(goal.id()) ? descend(bot, -50) : null;
            case ADVANCEMENT -> advancement(bot, planner, goal.id());
            case TRADE -> trade(bot, planner, goal.id());
            default -> null;
        };
    }

    /**
     * Into the Nether: ten obsidian (made from a lava pool with a water bucket, or mined), flint and
     * steel, a few blocks for the frame's corners, then a portal built and walked through.
     */
    /**
     * Seconds back to the Overworld from the Nether: the walk to the portal it came through (it
     * remembers it), or building one from obsidian it carries; null without a way (or when that
     * just failed: not again at once).
     */
    static Double wayBack(Bot bot) {
        if (bot.body().level().getGameTime() - bot.portalBackFailedAt < 2400) return null;
        BlockPos at = bot.body().blockPosition();
        BlockPos portal = net.kasax.challengecraft.bot.BotWorld.nearest((net.minecraft.server.level.ServerLevel) bot.body().level(), at, 32, 16,
                s -> s.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL), false, Set.of());
        if (portal == null) portal = bot.senses().knownPortal(at);
        // (A Nether walk is slower than its straight line: up and down, round the lava.)
        if (portal != null) return 10 + Math.sqrt(portal.distSqr(at)) / 4.3 * 1.6;
        boolean frame = ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.OBSIDIAN)) >= 10
                && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.FLINT_AND_STEEL, net.minecraft.world.item.Items.FIRE_CHARGE)) > 0;
        return frame ? 60.0 : null;
    }

    private static Option nether(Bot bot, ObtainPlanner planner) {
        if (!overworld(bot)) return null;
        // The speedrunners' way is the way in: cast a portal at a lava pool. Mining obsidian only
        // when a diamond pickaxe is already in hand (or there is no lava known at all).
        Option cast = castPortal(bot, planner);
        boolean diamondPick = ObtainPlanner.countAny(bot.body(), Set.of(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) > 0;
        if (cast != null && !diamondPick) return cast;
        Option built = builtPortal(bot, planner);
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
        // A pool under the open sky it knows of (casting down in a cave costs minutes a try).
        BlockPos open = openPool(bot);
        if (open == null) return null;
        double lava = 5 + Math.sqrt(open.distSqr(bot.body().blockPosition())) / 4.0;
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
            BlockPos pool = openPool(bot);
            return new SequenceTask("cast a portal to the Nether", List.of(
                () -> new ObtainTask(Set.of(Items.WATER_BUCKET), 1, planner).keeping(Set.of(Items.FLINT_AND_STEEL)),
                () -> new ObtainTask(Set.of(Items.BUCKET), 1, planner).keeping(Set.of(Items.WATER_BUCKET, Items.FLINT_AND_STEEL)),
                () -> new ObtainTask(Set.of(Items.FLINT_AND_STEEL), 1, planner).keeping(bucketKit),
                () -> new ObtainTask(blocks, 10, planner).keeping(bucketKit),
                () -> new net.kasax.challengecraft.bot.task.CastPortalTask(bot.body().level(), pool)));
        });
    }

    /**
     * The lava pool to cast at: one under the open sky if there is one about (room, light, no
     * climbing out of a cave with the buckets), else the nearest.
     */
    private static BlockPos openPool(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> lava =
                st -> st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && st.getFluidState().isSource();
        Set<BlockPos> skip = new java.util.HashSet<>();
        BlockPos first = null;
        for (int i = 0; i < 24; i++) {
            BlockPos p = bot.memory().nearest(level, bot.body().blockPosition(), lava, skip);
            if (p == null) break;
            // (Not one where casting already failed: no room, no way to it.)
            if (bot.badCastPools.stream().anyMatch(b -> b.distSqr(p) < 24 * 24)) {
                skip.add(p);
                continue;
            }
            if (first == null) first = p;
            if (p.distSqr(bot.body().blockPosition()) > 256 * 256) break;
            if (level.canSeeSky(p.above())) return p;
            skip.add(p);
        }
        // (Lava down in a cave is no place to cast: digging out the room and pouring in the dark
        // took five minutes a try. Then the other ways in.)
        return null;
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

    // ---- stunts: things to do rather than to have --------------------------------------------

    private static final Set<Item> BLOCKS = Set.of(Items.DIRT, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.NETHERRACK);
    private static final Set<Item> BEDS = beds();

    private static Set<Item> beds() {
        Set<Item> out = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) if (new ItemStack(item).is(net.minecraft.tags.ItemTags.BEDS)) out.add(item);
        return out;
    }

    private static Option stunt(Bot bot, ObtainPlanner planner, String id) {
        var body = bot.body();
        return switch (id) {
            case "have_10_hearts_missing" -> {
                // Ten hearts lost in all: a few safe falls at full health (about four hearts each),
                // healing up in between. (Fights on the way count too.)
                int taken = net.kasax.challengecraft.challenges.Chal_40_LockoutBingo.damageTakenSinceStart(bot.server(), body, "have_10_hearts_missing");
                int left = Math.max(0, 200 - taken);
                int falls = (left + 79) / 80;
                if (falls == 0) yield null;
                Option one = body.getHealth() >= 18 ? fall(bot, planner, 11, true) : null;
                yield one == null ? null : new Option(one.cost() + (falls - 1) * 45, one.task());
            }
            case "take_fall_damage" -> fall(bot, planner, 5, false);
            case "fall_20_blocks_and_survive" -> body.getHealth() >= 19 ? fall(bot, planner, 22, true) : null;
            case "stand_on_bedrock" -> {
                if (!overworld(bot)) yield null;
                int down = body.getBlockY() - (body.level().getMinY() + 1);
                double pick = planner.estimate(bot, Set.of(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE), 1);
                if (pick >= INF) yield null;
                yield new Option(pick + down * 2.5 + 20, () -> new SequenceTask("down to the bedrock", List.of(
                        () -> new ObtainTask(Set.of(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE), 1, planner),
                        () -> new net.kasax.challengecraft.bot.task.NavGoalTask("stand on the bedrock",
                                (level, p) -> level.getBlockState(p.below()).is(Blocks.BEDROCK),
                                b -> new BlockPos(b.body().getBlockX(), b.body().level().getMinY() + 1, b.body().getBlockZ()), 12000))));
            }
            case "place_tnt" -> placeThen(bot, planner, Items.TNT, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.NOTHING, null, 0);
            case "ignite_tnt" -> placeThen(bot, planner, Items.TNT, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.USE_ITEM, Items.FLINT_AND_STEEL, 8);
            case "survive_explosion" -> body.getHealth() >= 18
                    ? placeThen(bot, planner, Items.TNT, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.USE_ITEM, Items.FLINT_AND_STEEL, 3) : null;
            case "activate_pressure_plate" -> placeThen(bot, planner, Items.STONE_PRESSURE_PLATE, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.STEP_ON, null, 0);
            case "burn_and_survive" -> body.getHealth() >= 16
                    ? placeThen(bot, planner, Items.CAMPFIRE, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.STEP_ON, null, 0) : null;
            case "set_spawn" -> bed(bot, planner, false);
            case "sleep_in_bed" -> body.level().isDarkOutside() ? bed(bot, planner, true) : null;
            case "tame_wolf" -> tame(bot, planner, EntityTypes.WOLF, Items.BONE, 3);
            case "tame_cat" -> tame(bot, planner, EntityTypes.CAT, Items.COD, 3);
            case "breed_cows" -> breed(bot, planner, EntityTypes.COW, Items.WHEAT);
            case "breed_sheep" -> breed(bot, planner, EntityTypes.SHEEP, Items.WHEAT);
            case "breed_pigs" -> breed(bot, planner, EntityTypes.PIG, Items.CARROT);
            case "breed_chickens" -> breed(bot, planner, EntityTypes.CHICKEN, Items.WHEAT_SEEDS);
            case "breed_animals" -> {
                Option best = null;
                for (Option o : new Option[]{breed(bot, planner, EntityTypes.COW, Items.WHEAT), breed(bot, planner, EntityTypes.SHEEP, Items.WHEAT),
                        breed(bot, planner, EntityTypes.PIG, Items.CARROT), breed(bot, planner, EntityTypes.CHICKEN, Items.WHEAT_SEEDS)}) {
                    if (o != null && (best == null || o.cost() < best.cost())) best = o;
                }
                yield best;
            }
            case "advancement_take_aim" -> {
                // Anything hit with an arrow: a bow, a few arrows, the nearest mob.
                double bow = planner.estimate(bot, Set.of(Items.BOW), 1), arrows = planner.estimate(bot, Set.of(Items.ARROW), 4);
                yield bow + arrows >= INF ? null : new Option(bow + arrows + 25, () -> new SequenceTask("hit something with an arrow", List.of(
                        () -> new ObtainTask(Set.of(Items.BOW), 1, planner).keeping(Set.of(Items.ARROW)),
                        () -> new ObtainTask(Set.of(Items.ARROW), 4, planner).keeping(Set.of(Items.BOW)),
                        () -> new net.kasax.challengecraft.bot.task.ShootTask(Items.BOW, e -> e instanceof net.minecraft.world.entity.Mob, 4))));
            }
            case "obtain_firework_crossbow" -> {
                double xbow = planner.estimate(bot, Set.of(Items.CROSSBOW), 1), rocket = planner.estimate(bot, Set.of(Items.FIREWORK_ROCKET), 1);
                Set<Item> kit = Set.of(Items.CROSSBOW, Items.FIREWORK_ROCKET);
                yield xbow + rocket >= INF ? null : new Option(xbow + rocket + 6, () -> new SequenceTask("a crossbow loaded with a firework", List.of(
                        () -> new ObtainTask(Set.of(Items.CROSSBOW), 1, planner).keeping(kit),
                        () -> new ObtainTask(Set.of(Items.FIREWORK_ROCKET), 1, planner).keeping(kit),
                        net.kasax.challengecraft.bot.task.LoadCrossbowTask::new)));
            }
            case "shoot_crossbow" -> {
                double xbow = planner.estimate(bot, Set.of(Items.CROSSBOW), 1), arrows = planner.estimate(bot, Set.of(Items.ARROW), 1);
                yield xbow + arrows >= INF ? null : new Option(xbow + arrows + 8, () -> new SequenceTask("shoot a crossbow", List.of(
                        () -> new ObtainTask(Set.of(Items.CROSSBOW), 1, planner).keeping(Set.of(Items.ARROW)),
                        () -> new ObtainTask(Set.of(Items.ARROW), 1, planner).keeping(Set.of(Items.CROSSBOW)),
                        () -> new net.kasax.challengecraft.bot.task.ShootTask(Items.CROSSBOW,
                                ((net.minecraft.server.level.ServerLevel) bot.body().level()).getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                                        bot.body().blockPosition().relative(bot.body().getDirection(), 8)).below(), 1))));
            }
            case "hit_target_block" -> {
                // A target block put down a few steps off, and an arrow into it.
                double target = planner.estimate(bot, Set.of(Items.TARGET), 1), bow = planner.estimate(bot, Set.of(Items.BOW), 1),
                        arrows = planner.estimate(bot, Set.of(Items.ARROW), 2);
                yield target + bow + arrows >= INF ? null : new Option(target + bow + arrows + 15, () -> new SequenceTask("hit a target block", List.of(
                        () -> new ObtainTask(Set.of(Items.TARGET), 1, planner).keeping(Set.of(Items.BOW, Items.ARROW)),
                        () -> new ObtainTask(Set.of(Items.BOW), 1, planner).keeping(Set.of(Items.TARGET, Items.ARROW)),
                        () -> new ObtainTask(Set.of(Items.ARROW), 2, planner).keeping(Set.of(Items.TARGET, Items.BOW)),
                        () -> new net.kasax.challengecraft.bot.task.PlaceAndUseTask(Items.TARGET, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.NOTHING, null, 6),
                        () -> net.kasax.challengecraft.bot.task.ShootTask.atBlock(Items.BOW, st -> st.is(Blocks.TARGET), 2))));
            }
            case "obtain_honeycomb" -> {
                // Shears on a bee nest full of honey: three combs (the bees get angry; it runs off).
                var level = (net.minecraft.server.level.ServerLevel) body.level();
                java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> full = st -> (st.is(Blocks.BEE_NEST) || st.is(Blocks.BEEHIVE))
                        && st.getValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL) >= net.minecraft.world.level.block.BeehiveBlock.MAX_HONEY_LEVELS;
                BlockPos nest = bot.memory().nearest(level, body.blockPosition(), full, Set.of());
                double shears = planner.estimate(bot, Set.of(Items.SHEARS), 1);
                yield nest == null || shears >= INF ? null : new Option(shears + 6 + Math.sqrt(nest.distSqr(body.blockPosition())) / 4, () -> new SequenceTask("honeycomb", List.of(
                        () -> new ObtainTask(Set.of(Items.SHEARS), 1, planner),
                        () -> new net.kasax.challengecraft.bot.task.ClickBlockTask("shear a bee nest", full).with(Items.SHEARS, Set.of(Items.HONEYCOMB)))));
            }
            case "drink_potion" -> {
                // A water bottle is a potion too: a glass bottle filled at the water, and drunk.
                double bottle = planner.estimate(bot, Set.of(Items.GLASS_BOTTLE), 1);
                Double water = planner.seen(bot, Blocks.WATER);
                yield bottle >= INF || water == null ? null : new Option(bottle + water + 6, () -> new SequenceTask("drink a water bottle", List.of(
                        () -> new ObtainTask(Set.of(Items.GLASS_BOTTLE), 1, planner),
                        net.kasax.challengecraft.bot.task.FillBucketTask::bottle,
                        () -> new ConsumeTask(Set.of(Items.POTION)))));
            }
            case "get_shot_by_skeleton" -> body.getHealth() >= 16 ? new Option(body.level().isDarkOutside() ? 45 : 400,
                    net.kasax.challengecraft.bot.task.GetShotTask::new) : null;
            case "block_damage_with_shield" -> {
                double shield = planner.estimate(bot, Set.of(Items.SHIELD), 1);
                yield shield >= INF ? null : new Option(shield + (body.level().isDarkOutside() ? 40 : 200), () -> new SequenceTask("block with a shield", List.of(
                        () -> new ObtainTask(Set.of(Items.SHIELD), 1, planner),
                        net.kasax.challengecraft.bot.task.ShieldTask::new)));
            }
            case "advancement_monster_hunter" -> {
                // Any monster killed: whichever is nearest or quickest.
                Option best = null;
                for (String m : List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper",
                        "minecraft:drowned", "minecraft:husk", "minecraft:enderman", "minecraft:slime")) {
                    Option o = kill(bot, planner, m);
                    if (o != null && (best == null || o.cost() < best.cost())) best = o;
                }
                yield best;
            }
            case "advancement_best_friends_forever" -> {
                // Any animal tamed: whichever is quickest (a wolf with bones, a cat with fish, a horse).
                Option best = null;
                for (String other : List.of("tame_wolf", "tame_cat", "tame_horse")) {
                    Option o = stunt(bot, planner, other);
                    if (o != null && (best == null || o.cost() < best.cost())) best = o;
                }
                yield best;
            }
            case "ride_horse" -> mount(bot, planner, EntityTypes.HORSE, 1);
            case "tame_horse" -> mount(bot, planner, EntityTypes.HORSE, 12);
            case "get_poisoned" -> consume(bot, planner, Set.of(Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH));
            case "throw_ender_pearl" -> {
                double c = planner.estimate(bot, Set.of(Items.ENDER_PEARL), 1);
                yield c >= INF ? null : new Option(c + 2, () -> new SequenceTask("throw an ender pearl", List.of(
                        () -> new ObtainTask(Set.of(Items.ENDER_PEARL), 1, planner),
                        () -> new net.kasax.challengecraft.bot.task.UseItemTask(Items.ENDER_PEARL, -30f))));
            }
            case "craft_cake" -> withEgg(bot, planner, Items.CAKE, null);
            case "eat_cake_slice" -> withEgg(bot, planner, Items.CAKE, () -> new net.kasax.challengecraft.bot.task.PlaceAndUseTask(Items.CAKE,
                    net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.CLICK, null, 0));
            case "craft_pumpkin_pie" -> withEgg(bot, planner, Items.PUMPKIN_PIE, null);
            case "eat_pumpkin_pie" -> withEgg(bot, planner, Items.PUMPKIN_PIE, () -> new ConsumeTask(Set.of(Items.PUMPKIN_PIE)));
            case "advancement_tactical_fishing" -> {
                // A fish caught in a bucket of water: any fish (cod, salmon, ...) in a river or the
                // sea; one in sight is a short swim, else to the nearest river or sea known.
                double bucket = planner.estimate(bot, Set.of(Items.WATER_BUCKET), 1);
                if (bucket >= INF) yield null;
                double find = fishSearch(bot, FISH);
                yield find >= INF ? null : new Option(bucket + find, () -> new SequenceTask("catch a fish in a bucket", List.of(
                        () -> new ObtainTask(Set.of(Items.WATER_BUCKET), 1, planner),
                        () -> new UseOnMobTask(FISH, Items.WATER_BUCKET, e -> true))));
            }
            case "obtain_pufferfish" -> fishItem(bot, planner, Items.PUFFERFISH, EntityTypes.PUFFERFISH, 8);
            case "obtain_tropical_fish" -> fishItem(bot, planner, Items.TROPICAL_FISH, EntityTypes.TROPICAL_FISH, 50);
            case "catch_fish", "advancement_fishy_business" -> {
                double rod = planner.estimate(bot, Set.of(Items.FISHING_ROD), 1);
                Double water = planner.seen(bot, Blocks.WATER);
                yield rod >= INF || water == null ? null : new Option(rod + water + 40, () -> new SequenceTask("fish", List.of(
                        () -> new ObtainTask(Set.of(Items.FISHING_ROD), 1, planner),
                        () -> new net.kasax.challengecraft.bot.task.FishTask())));
            }
            case "enchant_item", "advancement_enchanter" -> enchant(bot, planner, Set.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
                    Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.BOOK));
            case "enchant_sword" -> enchant(bot, planner, Set.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD));
            case "enchant_pickaxe" -> enchant(bot, planner, Set.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE));
            case "ring_bell" -> click(bot, "ring a bell", Blocks.BELL);
            case "use_lectern" -> click(bot, "use a lectern", Blocks.LECTERN);
            case "sleep_in_village_bed" -> body.level().isDarkOutside() ? clickBed(bot) : null;
            default -> null;
        };
    }

    private static final Set<EntityType<?>> FISH = Set.of(EntityTypes.COD, EntityTypes.SALMON, EntityTypes.TROPICAL_FISH, EntityTypes.PUFFERFISH);

    /**
     * Seconds to find one of these fish: the swim to one in sight, else the walk to the nearest
     * river or sea known (where they live), and a look about there. INF: no such water known.
     */
    private static double fishSearch(Bot bot, Set<EntityType<?>> kinds) {
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        if (level.dimension() != Level.OVERWORLD) return INF;
        BlockPos at = bot.body().blockPosition();
        double best = INF;
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(at).inflate(48),
                e -> kinds.contains(e.getType()) && e.isAlive())) {
            best = Math.min(best, 8 + Math.sqrt(e.distanceToSqr(bot.body())) / 3);
        }
        if (best < INF) return best;
        for (EntityType<?> t : kinds) {
            var seen = bot.memory().lastSeen(level, t, at);
            if (seen != null) best = Math.min(best, 25 + Math.sqrt(seen.distSqr(at)) / 4);
            if (net.kasax.challengecraft.bot.plan.MobHabitats.inHabitat(bot, t)) best = Math.min(best, 45);
            BlockPos home = net.kasax.challengecraft.bot.plan.MobHabitats.nearestKnown(bot, t);
            if (home != null) best = Math.min(best, 50 + Math.sqrt(home.distSqr(at)) / 4);
        }
        return best;
    }

    /**
     * A fish item (a pufferfish, a tropical fish): the fish itself killed where it lives (it drops
     * itself), or caught with a rod (one cast in {@code oneIn} brings that kind). The quicker.
     */
    private static Option fishItem(Bot bot, ObtainPlanner planner, Item item, EntityType<?> kind, int oneIn) {
        Option plain = obtain(bot, planner, Set.of(item), 1);
        double find = fishSearch(bot, Set.of(kind));
        Option hunt = find >= INF ? null : new Option(find + 20, () -> new KillTask(Set.of(kind), Set.of(item), 1, 1));
        double rod = planner.estimate(bot, Set.of(Items.FISHING_ROD), 1);
        Double water = planner.seen(bot, Blocks.WATER);
        Option fishing = rod >= INF || water == null ? null : new Option(rod + water + oneIn * 25, () -> new SequenceTask("fish for " + ObtainPlanner.name(item), List.of(
                () -> new ObtainTask(Set.of(Items.FISHING_ROD), 1, planner),
                () -> new net.kasax.challengecraft.bot.task.FishTask(Set.of(item), 24000))));
        Option best = null;
        for (Option o : new Option[]{plain, hunt, fishing}) if (o != null && (best == null || o.cost() < best.cost())) best = o;
        return best;
    }

    /** An enchanting table (made or found), lapis, the item, a level or two of experience. */
    private static Option enchant(Bot bot, ObtainPlanner planner, Set<Item> items) {
        double table = nearby(bot, Blocks.ENCHANTING_TABLE) ? 5 : planner.estimate(bot, Set.of(Items.ENCHANTING_TABLE), 1);
        double lapis = planner.estimate(bot, Set.of(Items.LAPIS_LAZULI), 1);
        double item = planner.estimate(bot, items, 1);
        // Experience: a level comes with a little mining or smelting.
        double xp = bot.body().experienceLevel >= 1 ? 0 : 60;
        if (table >= INF || lapis >= INF || item >= INF) return null;
        return new Option(table + lapis + item + xp + 8, () -> new SequenceTask("enchant " + ObtainPlanner.names(items), List.of(
                () -> nearby(bot, Blocks.ENCHANTING_TABLE) ? null : new ObtainTask(Set.of(Items.ENCHANTING_TABLE), 1, planner),
                () -> new ObtainTask(Set.of(Items.LAPIS_LAZULI), 1, planner).keeping(Set.of(Items.ENCHANTING_TABLE)),
                () -> new ObtainTask(items, 1, planner).keeping(Set.of(Items.ENCHANTING_TABLE, Items.LAPIS_LAZULI)),
                () -> bot.body().experienceLevel >= 1 ? null
                        : new ObtainTask(Set.of(Items.COAL), 3, planner).keeping(Set.of(Items.ENCHANTING_TABLE, Items.LAPIS_LAZULI)),
                () -> new net.kasax.challengecraft.bot.task.EnchantTask(items))));
    }

    /** A block it knows of, clicked: the walk there is the cost. */
    /**
     * To the nearest village (bells, lecterns, beds, villagers to trade with): planned as the walk
     * there, the tile itself is planned again once there. Null outside the Overworld.
     */
    private static Option villageTrip(Bot bot) {
        if (!overworld(bot)) return null;
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        var village = VisitStructureTask.nearest(bot, VisitStructureTask.resolve(level, "village"));
        double dist = village == null ? 400 : Math.sqrt(village.spot().distSqr(bot.body().blockPosition()));
        return new Option(dist / 4 + 90 + (village == null ? 300 : 0), () -> new VisitStructureTask(level, "village"));
    }

    private static Option click(Bot bot, String what, Block block) {
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        BlockPos at = bot.memory().nearest(level, bot.body().blockPosition(), s -> s.is(block), Set.of());
        if (at == null) return block == Blocks.BELL || block == Blocks.LECTERN ? villageTrip(bot) : null;
        double cost = 5 + Math.sqrt(at.distSqr(bot.body().blockPosition())) / 3.5;
        return new Option(cost, () -> new net.kasax.challengecraft.bot.task.ClickBlockTask(what, s -> s.is(block)));
    }

    private static Option clickBed(Bot bot) {
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        BlockPos at = bot.memory().nearest(level, bot.body().blockPosition(), s -> s.is(net.minecraft.tags.BlockTags.BEDS), Set.of());
        if (at == null) return villageTrip(bot);
        return new Option(5 + Math.sqrt(at.distSqr(bot.body().blockPosition())) / 3.5,
                () -> new net.kasax.challengecraft.bot.task.ClickBlockTask("sleep in a village bed", s -> s.is(net.minecraft.tags.BlockTags.BEDS)));
    }

    /** Onto a horse (and again, until it stops bucking and is tame). */
    private static Option mount(Bot bot, ObtainPlanner planner, EntityType<?> type, int times) {
        double mob = planner.mobEffort(bot, type, 240);
        if (mob >= INF) return null;
        return new Option(mob + times * 4, () -> {
            List<Supplier<BotTask>> steps = new ArrayList<>();
            for (int i = 0; i < times; i++) {
                steps.add(() -> new UseOnMobTask(type, Items.AIR, e -> true));
                steps.add(() -> new net.kasax.challengecraft.bot.task.WaitTask(60));
            }
            return new SequenceTask("ride a " + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(), steps);
        });
    }

    private static Option fall(Bot bot, ObtainPlanner planner, int height, boolean hurt) {
        double blocks = planner.estimate(bot, BLOCKS, height + 2);
        if (blocks >= INF) return null;
        return new Option(blocks + height * 0.7 + 5, () -> new SequenceTask("fall " + height + " blocks", List.of(
                () -> new ObtainTask(BLOCKS, height + 2, planner),
                () -> new net.kasax.challengecraft.bot.task.FallTask(height, hurt))));
    }

    private static Option placeThen(Bot bot, ObtainPlanner planner, Item block, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then then, Item tool, int away) {
        double c = planner.estimate(bot, Set.of(block), 1) + (tool == null ? 0 : planner.estimate(bot, Set.of(tool), 1));
        if (c >= INF) return null;
        List<Supplier<BotTask>> steps = new ArrayList<>();
        steps.add(() -> new ObtainTask(Set.of(block), 1, planner));
        if (tool != null) steps.add(() -> new ObtainTask(Set.of(tool), 1, planner).keeping(Set.of(block)));
        steps.add(() -> new net.kasax.challengecraft.bot.task.PlaceAndUseTask(block, then, tool, away));
        return new Option(c + 6, () -> new SequenceTask("put down " + ObtainPlanner.name(block), steps));
    }

    private static Option bed(Bot bot, ObtainPlanner planner, boolean sleep) {
        double c = planner.estimate(bot, BEDS, 1);
        if (c >= INF) return null;
        return new Option(c + 6, () -> new SequenceTask(sleep ? "sleep in a bed" : "set the spawn at a bed", List.of(
                () -> new ObtainTask(BEDS, 1, planner),
                () -> {
                    Item bed = BEDS.stream().filter(b -> ObtainPlanner.countAny(bot.body(), Set.of(b)) > 0).findFirst().orElse(BEDS.iterator().next());
                    return new net.kasax.challengecraft.bot.task.PlaceAndUseTask(bed, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.CLICK, null, 0);
                })));
    }

    private static Option tame(Bot bot, ObtainPlanner planner, EntityType<?> type, Item food, int count) {
        double c = planner.estimate(bot, Set.of(food), count), mob = planner.mobEffort(bot, type, 180);
        if (c >= INF || mob >= INF) return null;
        return new Option(c + mob + 10, () -> new SequenceTask("tame a " + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(), List.of(
                () -> new ObtainTask(Set.of(food), count, planner),
                () -> new net.kasax.challengecraft.bot.task.TameTask(type, food))));
    }

    private static Option breed(Bot bot, ObtainPlanner planner, EntityType<?> type, Item food) {
        double c = planner.estimate(bot, Set.of(food), 2), mob = planner.mobEffort(bot, type, 60);
        if (c >= INF || mob >= INF) return null;
        java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> ready = e -> e instanceof net.minecraft.world.entity.animal.Animal a
                && !a.isBaby() && !a.isInLove() && a.canFallInLove();
        return new Option(c + mob * 1.5 + 6, () -> new SequenceTask("breed " + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + "s", List.of(
                () -> new ObtainTask(Set.of(food), 2, planner),
                () -> new UseOnMobTask(type, food, ready),
                () -> new UseOnMobTask(type, food, ready))));
    }

    // ---- trading --------------------------------------------------------------------------------

    /**
     * A trade with a villager nearby: of the offers that answer the goal (any, a profession, a
     * result), the one whose payment is quickest to get (32 sticks for the fletcher's emerald,
     * coal for the armorer's). Payment first, then the trade.
     */
    private static Option trade(Bot bot, ObtainPlanner planner, String id) {
        java.util.function.BiPredicate<net.minecraft.world.entity.npc.villager.AbstractVillager, net.minecraft.world.item.trading.MerchantOffer> wanted = switch (id) {
            case "trade_with_villager" -> (v, o) -> true;
            case "obtain_emerald_by_trade" -> (v, o) -> o.getResult().is(Items.EMERALD);
            case "buy_bread" -> (v, o) -> o.getResult().is(Items.BREAD);
            case "buy_arrows" -> (v, o) -> o.getResult().is(Items.ARROW);
            case "buy_lapis" -> (v, o) -> o.getResult().is(Items.LAPIS_LAZULI);
            default -> {
                if (!id.startsWith("trade_with_")) yield null;
                String profession = id.substring("trade_with_".length());
                yield (v, o) -> v instanceof net.minecraft.world.entity.npc.villager.Villager vi
                        && vi.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath().equals(profession)).orElse(false);
            }
        };
        if (wanted == null) return null;
        var body = bot.body();
        // No villager about: to the nearest village known, then the trade is planned there.
        if (body.level().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.AbstractVillager.class,
                new net.minecraft.world.phys.AABB(body.blockPosition()).inflate(96), v -> v.isAlive() && !v.isBaby()).isEmpty()) {
            return villageTrip(bot);
        }
        double best = INF;
        net.minecraft.world.item.trading.MerchantOffer bestOffer = null;
        net.minecraft.world.entity.npc.villager.AbstractVillager bestVillager = null;
        for (var v : body.level().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.AbstractVillager.class,
                new net.minecraft.world.phys.AABB(body.blockPosition()).inflate(96), v -> v.isAlive() && !v.isBaby())) {
            for (var o : v.getOffers()) {
                if (o.isOutOfStock() || !wanted.test(v, o)) continue;
                double c = planner.estimate(bot, Set.of(o.getCostA().getItem()), o.getCostA().getCount());
                if (!o.getCostB().isEmpty()) c += planner.estimate(bot, Set.of(o.getCostB().getItem()), o.getCostB().getCount());
                c += Math.sqrt(v.distanceToSqr(body)) / 3.5;
                if (c < best) {
                    best = c;
                    bestOffer = o;
                    bestVillager = v;
                }
            }
        }
        if (bestOffer == null || best >= INF) return null;
        final ItemStack a = bestOffer.getCostA().copy(), b = bestOffer.getCostB().copy();
        return new Option(best + 5, () -> {
            List<Supplier<BotTask>> steps = new ArrayList<>();
            steps.add(() -> new ObtainTask(Set.of(a.getItem()), a.getCount(), planner));
            if (!b.isEmpty()) steps.add(() -> new ObtainTask(Set.of(b.getItem()), b.getCount(), planner).keeping(Set.of(a.getItem())));
            steps.add(() -> new net.kasax.challengecraft.bot.task.TradeTask("trade (" + id + ")", wanted));
            return new SequenceTask("trade: " + id, steps);
        });
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
    private static Option structure(Bot bot, ObtainPlanner planner, LockoutBingoGoal goal) {
        String path = Identifier.parse(goal.primaryTarget()).getPath();
        boolean netherOrEnd = path.equals("fortress") || path.equals("bastion_remnant") || path.equals("end_city");
        boolean netherOnly = path.equals("fortress") || path.equals("bastion_remnant");
        // A fortress or a bastion from up here: the way into the Nether first.
        if (netherOnly && overworld(bot)) {
            Option trip = nether(bot, planner);
            return trip == null ? null : new Option(trip.cost() + 400, trip.task(), trip.yields());
        }
        if (netherOrEnd != !overworld(bot)) return null;
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        Set<Identifier> ids = VisitStructureTask.resolve(level, goal.primaryTarget());
        if (ids.isEmpty()) return null;
        BotSenses.SeenStructure seen = VisitStructureTask.nearest(bot, ids);
        double cost;
        if (seen != null) cost = 5 + Math.sqrt(seen.spot().distSqr(bot.body().blockPosition())) / 4.0;
        else if (SURFACE_STRUCTURES.contains(path)) cost = unseenEffort(goal);
        else if (path.equals("mineshaft")) cost = 420;
        else if (path.equals("trial_chambers")) cost = 700;
        // In the Nether: walked into while exploring (they are big, and close to the portal often).
        else if (path.equals("fortress") || path.equals("bastion_remnant")) cost = 400;
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
        if (cost >= INF) {
            // No drop worth knowing it by: still findable. Monsters come out at night and in caves;
            // bosses and the rare ones are not a quick tile.
            if (BOSSES.contains(type)) return null;
            if (type.getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) return null;
            cost = bot.body().level().isDarkOutside() ? 150 : 420;
        }
        return new Option(cost, () -> new KillTask(Set.of(type), Set.of(), 0, 1));
    }

    /**
     * Something with an egg in it (cake, pumpkin pie): the egg from a flock of chickens (they lay
     * every few minutes), the rest as usual; then {@code after} (eat it, put it down), if any.
     */
    private static Option withEgg(Bot bot, ObtainPlanner planner, Item item, Supplier<BotTask> after) {
        boolean haveEgg = ObtainPlanner.countAny(bot.body(), Set.of(Items.EGG)) > 0;
        double rest = haveEgg ? planner.estimate(bot, Set.of(item), 1) : planner.assuming(bot, Set.of(Items.EGG), () -> planner.estimate(bot, Set.of(item), 1));
        if (rest >= INF) return null;
        double egg = 0;
        if (!haveEgg && ObtainPlanner.countAny(bot.body(), Set.of(item)) == 0) {
            var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
            var chickens = bot.memory().lastSeen(level, EntityTypes.CHICKEN, bot.body().blockPosition());
            egg = (chickens == null ? 200 : Math.sqrt(chickens.distSqr(bot.body().blockPosition())) / 4) + 150;
        }
        List<Supplier<BotTask>> steps = new ArrayList<>();
        if (egg > 0) steps.add(net.kasax.challengecraft.bot.task.EggTask::new);
        steps.add(() -> new ObtainTask(Set.of(item), 1, planner));
        if (after != null) steps.add(after);
        return new Option(rest + egg + 3, () -> new SequenceTask("make " + ObtainPlanner.name(item), steps));
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
