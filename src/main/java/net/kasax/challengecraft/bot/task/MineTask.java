package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Gathers items by breaking the blocks that drop them: finds the nearest such block it can see,
 * walks up to it, breaks it with the best tool, picks up what falls.
 *
 * <p>When none is in sight it searches the way a player would. Things that grow on the surface
 * (trees, sand, flowers) it looks for by walking out in some direction. Ores it digs for: a
 * staircase down to the height where that ore is most common, then a straight tunnel, mining
 * every wanted block the tunnel uncovers (no X-ray: only what the tunnel lays open counts).
 */
public final class MineTask implements BotTask {
    private static final int SEARCH_TICKS = 3600, MAX_LEGS = 80;

    private final String what;
    private final Predicate<BlockState> blocks;
    private final Set<Item> items;
    private final int count;
    /** Where the blocks are most common, for digging (null: search on the surface). */
    private final Integer depth;
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos target;
    private int collectTicks, idle, searchTicks, legs, scanCooldown, budget;
    private boolean walking, searching, leadSaid;
    private Direction heading;
    private Explorer explorer;
    private BlockPos lead, approaching;
    /** The few in sight it is on its way to, any one of which will do (null: just the target). */
    private List<BlockPos> candidates;
    private int approachTicks, leadFails, sideStep;

    /** Mine until the bot holds {@code count} of the {@code items} together. */
    public MineTask(String what, Predicate<BlockState> blocks, Set<Item> items, int count) {
        this(what, blocks, items, count, null);
    }

    public MineTask(String what, Predicate<BlockState> blocks, Set<Item> items, int count, Integer depth) {
        this(what, blocks, items, count, depth, null);
    }

    /** What is needed now; {@code count} may be more (for later, taken while here). */
    private int needed = Integer.MAX_VALUE;

    /** Mine at least {@code needed}, and on up to the full count while more is close by. */
    public MineTask atLeast(int needed) {
        this.needed = Math.min(needed, count);
        return this;
    }

    private boolean nearbyMore(ServerLevel level, Bot bot) {
        BlockPos at = bot.body().blockPosition();
        BlockPos near = BotWorld.nearest(level, at, 12, 8, blocks, true, skip);
        if (near == null) near = bot.memory().nearest(level, at, blocks, skip);
        return near != null && near.distSqr(at) <= 12 * 12;
    }

    /** {@code lead}: where to look first when none is in sight (a biome known for these blocks). */
    public MineTask(String what, Predicate<BlockState> blocks, Set<Item> items, int count, Integer depth, BlockPos lead) {
        this.what = what;
        this.blocks = blocks;
        this.items = Set.copyOf(items);
        this.count = count;
        this.depth = depth;
        this.lead = lead;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        int have = ObtainPlanner.countAny(bot.body(), items);
        // An ore vein is taken whole, whatever the count: the rest is right there now, and a
        // trip back for it later (the shield, the bucket) costs minutes.
        boolean veinLeft = collectTicks > 0 || veinNear(level, bot);
        if (have >= count && !veinLeft) return Result.DONE;
        // Enough for now, more only for later (other tiles want it too): while it is right here.
        if (have >= needed && collectTicks <= 0 && !veinLeft && (target == null ? !nearbyMore(level, bot) : target.distSqr(bot.body().blockPosition()) > 12 * 12)) return Result.DONE;

        // Pick up what fell.
        if (collectTicks > 0) {
            collectTicks--;
            List<ItemEntity> drops = BotWorld.drops(level, bot.body().blockPosition(), 6, items);
            if (!drops.isEmpty()) {
                ItemEntity e = drops.get(0);
                if (!walking || bot.navigator().status() != BotNavigator.Status.MOVING) {
                    bot.navigator().goPickUp(e);
                    walking = true;
                }
                if (bot.navigator().tick() == BotNavigator.Status.FAILED) collectTicks = 0;
                return Result.RUNNING;
            }
            collectTicks = 0;
            walking = false;
        }

        if (target == null || !blocks.test(level.getBlockState(target))) {
            target = null;
            // Looking around costs; while searching, look again once a second (and after each leg).
            if (!searching || --scanCooldown <= 0) {
                scanCooldown = 20;
                // (Not the ones found out of reach a little while ago, by this task or another.)
                skip.addAll(bot.unreachable());
                // The rest of the vein first: what was just broken showed its neighbours (a player
                // takes the whole cluster in front of him, not the one ten blocks off next).
                while (target == null && !vein.isEmpty()) {
                    BlockPos v = vein.poll();
                    if (!skip.contains(v) && blocks.test(level.getBlockState(v))) target = v;
                }
                if (target == null && rock(level)) {
                    // Rock and soil: from a wall at its own level (a hillside, a cave wall, the
                    // tunnel it digs), never from under its own feet - that is a shaft it then has
                    // to build its way out of with the very blocks it dug. No wall about: a
                    // staircase down (see dig), which is also the way back up.
                    // (Once on the staircase, only the staircase: a wall block beside it could be
                    // one of its steps, and the way back up would be gone.)
                    // (At body height, feet and head, as a tunnel is dug: not the ceiling, where
                    // it reaches up badly and the drops fall who knows where.)
                    if (!rockStairs) target = wallRock(level, bot);
                    if (target == null) {
                        if (!rockStairs) bot.say("no rock at hand for " + what + ": a staircase down");
                        rockStairs = true;
                        return search(bot);
                    }
                }
                candidates = null;
                if (target == null) {
                    // The nearest few in sight: which of them is really nearest (by the way there,
                    // not the straight line) the path search finds out (see goNearAny).
                    // (Things from the surface - logs, sand - seen deep down, a mineshaft's beams,
                    // are not worth digging fifty blocks for: those are left out, see worthIt.)
                    List<BlockPos> seen = new ArrayList<>();
                    for (BlockPos p : BotWorld.nearestN(level, bot.body().blockPosition(), searching ? 12 : 28, searching ? 8 : 20, blocks, true, skip, 12)) {
                        // (A trunk from the bottom: that one is in reach from the ground.)
                        for (int i = 0; i < 12 && blocks.test(level.getBlockState(p.below())) && !skip.contains(p.below()); i++) p = p.below();
                        if (!seen.contains(p) && worthIt(level, bot, p)) seen.add(p);
                    }
                    if (!seen.isEmpty()) {
                        target = seen.get(0);
                        if (seen.size() > 1) candidates = seen;
                    }
                }
                // Nothing right here: somewhere it has been past (or seen from afar).
                if (target == null) target = bot.memory().nearest(level, bot.body().blockPosition(), blocks, skip);
                for (int i = 0; i < 8 && target != null && !worthIt(level, bot, target); i++) {
                    skip.add(target);
                    target = bot.memory().nearest(level, bot.body().blockPosition(), blocks, skip);
                }
                if (target != null && !worthIt(level, bot, target)) target = null;
            }
            if (target == null) return search(bot);
            // A trunk (or any column of them) is taken from the bottom: that one is in reach from
            // the ground, the top ones only from a pillar.
            for (int i = 0; i < 12 && blocks.test(level.getBlockState(target.below())) && !skip.contains(target.below()); i++) target = target.below();
            if (searching) {
                bot.navigator().stop();
                if (explorer != null) explorer.pause(bot);
                searching = false;
            }
            walking = false;
            if (!bot.tools().canHarvest(level.getBlockState(target))) {
                // The pickaxe broke on the way down: a new one made right here (the stone is all
                // about, the sticks are in the pack), once - not given up for that.
                BlockState st = level.getBlockState(target);
                if (!retooled) {
                    retooled = true;
                    for (Item tool : List.of(net.minecraft.world.item.Items.STONE_PICKAXE, net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE)) {
                        if (!new net.minecraft.world.item.ItemStack(tool).isCorrectToolForDrops(st)) continue;
                        ObtainPlanner pl = new ObtainPlanner();
                        if (pl.estimate(bot, Set.of(tool), 1) > 40) break;
                        bot.say("the pickaxe is gone: a new " + ObtainPlanner.name(tool) + " right here");
                        target = null;
                        bot.interject(new ObtainTask(Set.of(tool), 1, pl));
                        return Result.RUNNING;
                    }
                }
                bot.say("need a better tool for " + what);
                return Result.FAILED;
            }
        }
        // In reach only mid-jump does not count: breaking in the air is five times slower.
        boolean footing = bot.body().onGround() || bot.body().isInWater() || bot.body().onClimbable();
        if (!bot.actions().inReach(target) || !footing && walking) {
            // Far above from down in a mine: up to the surface first (a straight climb beats
            // picking a way through the rock bit by bit).
            if (!walking && target.getY() > bot.body().getY() + 12 && SurfaceTask.underground(bot.body())) {
                bot.interject(new SurfaceTask());
                return Result.RUNNING;
            }
            if (!walking) {
                if (candidates != null) bot.navigator().goNearAny(candidates, 3.6);
                else bot.navigator().goNear(target, 3.6);
                walking = true;
            }
            // Much longer than the way there should take: this one is not to be had; another.
            if (!target.equals(approaching)) {
                approaching = target;
                approachTicks = 0;
            }
            if (++approachTicks > 300 + 25 * Math.sqrt(target.distSqr(bot.body().blockPosition()))) {
                giveUp(bot, target);
                target = null;
                walking = false;
                bot.navigator().stop();
                return Result.RUNNING;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED) {
                giveUp(bot, target);
                target = null;
                walking = false;
            } else if (s == BotNavigator.Status.ARRIVED) {
                walking = false;
                // (Of the few it set out for, the one the way led to.)
                if (candidates != null) {
                    BlockPos r = bot.navigator().reached();
                    if (r != null) target = r;
                    candidates = null;
                }
                if (!bot.actions().inReach(target)) {
                    giveUp(bot, target);
                    target = null;
                }
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        walking = false;
        net.minecraft.world.level.block.Block was = level.getBlockState(target).getBlock();
        if (bot.actions().breakTick(target)) {
            if (blocks.test(level.getBlockState(target))) {
                skip.add(target); // refused (protected)
            } else if (!BotWorld.COMMON.contains(was) && was != net.minecraft.world.level.block.Blocks.SAND && was != net.minecraft.world.level.block.Blocks.RED_SAND) {
                // (Not rock and soil: those are everywhere, and following them digs a pit.)
                // (Diamonds, gold, emeralds lie scattered with a block between: two out for those.)
                int r = was == net.minecraft.world.level.block.Blocks.DIAMOND_ORE || was == net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE
                        || was == net.minecraft.world.level.block.Blocks.GOLD_ORE || was == net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE
                        || was == net.minecraft.world.level.block.Blocks.EMERALD_ORE || was == net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE ? 2 : 1;
                for (BlockPos n : BlockPos.betweenClosed(target.offset(-r, -r, -r), target.offset(r, r, r))) {
                    if (blocks.test(level.getBlockState(n)) && !skip.contains(n)) vein.addFirst(n.immutable());
                }
            }
            target = null;
            collectTicks = 60;
            lastFind = searchTicks;
            idle = 0;
        } else if (++idle > 400) {
            skip.add(target);
            target = null;
            idle = 0;
        }
        return Result.RUNNING;
    }

    private Boolean rock;
    private boolean rockStairs, retooled;

    /** Gathering rock or soil (cobblestone, deepslate, netherrack, dirt): found everywhere. */
    private boolean rock(ServerLevel level) {
        if (rock == null) {
            rock = false;
            for (net.minecraft.world.level.block.Block b : BotWorld.COMMON) {
                // (Soul sand and soil, basalt: only in their own Nether valleys and deltas - found
                // by going there, not by digging anywhere.)
                if (b == net.minecraft.world.level.block.Blocks.SOUL_SAND || b == net.minecraft.world.level.block.Blocks.SOUL_SOIL
                        || b == net.minecraft.world.level.block.Blocks.BASALT) continue;
                if (b != net.minecraft.world.level.block.Blocks.BEDROCK && blocks.test(b.defaultBlockState())) rock = true;
            }
        }
        return rock;
    }

    /**
     * The nearest rock in a wall at body height (feet or head), dry: not one with water next to
     * it (digging in the wet is five times slower, and the water follows into the tunnel).
     */
    private BlockPos wallRock(ServerLevel level, Bot bot) {
        BlockPos feet = bot.body().blockPosition();
        boolean underground = SurfaceTask.underground(bot.body());
        Set<BlockPos> wet = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            Set<BlockPos> ignore = new HashSet<>(skip);
            ignore.addAll(wet);
            BlockPos low = BotWorld.nearest(level, feet, 8, 0, blocks, true, ignore);
            BlockPos high = BotWorld.nearest(level, feet.above(), 8, 0, blocks, true, ignore);
            BlockPos p = low == null ? high : high == null ? low : low.distSqr(feet) <= high.distSqr(feet) ? low : high;
            if (p == null) return null;
            boolean dry = true;
            for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) dry = false;
            // From up here, not one in the dark (a cave mouth: rock showing at body height is
            // mostly in one, and the monsters live there, at noon too). Down in a mine already,
            // the dark is all there is.
            boolean lit = underground;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (level.getBlockState(n).isAir() && (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, n) >= 8
                        || level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, n) >= 8)) lit = true;
            }
            if (dry && lit) return p;
            wet.add(p);
        }
        return null;
    }

    /** An ore of the vein being taken still in the wall a few steps away. */
    /** Ore of the vein being taken still close by (or just broken and lying about). */
    public boolean veinOpen(Bot bot) {
        return collectTicks > 0 || veinNear((ServerLevel) bot.body().level(), bot);
    }

    private boolean veinNear(ServerLevel level, Bot bot) {
        BlockPos feet = bot.body().blockPosition();
        for (BlockPos v : vein) {
            BlockState s = level.getBlockState(v);
            if (!skip.contains(v) && blocks.test(s) && ore(s) && v.distSqr(feet) <= 36) return true;
        }
        return false;
    }

    private static boolean ore(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.ORES) || s.is(net.minecraft.world.level.block.Blocks.NETHER_GOLD_ORE)
                || s.is(net.minecraft.world.level.block.Blocks.NETHER_QUARTZ_ORE) || s.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS);
    }

    private boolean knownOnly;
    /** Blocks of the vein (or tree) being taken, seen next to the ones broken: next in line. */
    private final java.util.ArrayDeque<BlockPos> vein = new java.util.ArrayDeque<>();

    /**
     * No way to it: it, and the rest of the same tree or ore vein (connected, a few dozen at most),
     * are left. Common rock is everywhere: only that one block.
     */
    private void giveUp(Bot bot, BlockPos p) {
        ServerLevel level = (ServerLevel) bot.body().level();
        skip.add(p.immutable());
        bot.markUnreachable(p);
        if (BotWorld.COMMON.contains(level.getBlockState(p).getBlock())) return;
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>(List.of(p.immutable()));
        Set<BlockPos> seen = new HashSet<>(open);
        while (!open.isEmpty() && seen.size() < 32) {
            BlockPos q = open.poll();
            for (BlockPos n : BlockPos.betweenClosed(q.offset(-1, -1, -1), q.offset(1, 1, 1))) {
                if (seen.size() >= 32 || seen.contains(n) || !blocks.test(level.getBlockState(n))) continue;
                BlockPos m = n.immutable();
                seen.add(m);
                open.add(m);
                skip.add(m);
                bot.markUnreachable(m);
            }
        }
    }

    /** The items it gathers. */
    public Set<Item> items() {
        return items;
    }

    /** None in sight: walking out to look (another kind of the same thing seen meanwhile may be better). */
    public boolean searching() {
        return searching && target == null;
    }

    private boolean digAtLead;

    /** Down where the lead is (a cave biome under it), not right here. */
    public MineTask digAtLead() {
        this.digAtLead = true;
        return this;
    }

    /** Only where it is seen or remembered: no searching for it. */
    public MineTask knownOnly() {
        this.knownOnly = true;
        return this;
    }

    /** Nothing in sight: walk out on the surface, or dig for it. */
    private Result search(Bot bot) {
        if (knownOnly && lead == null) {
            bot.say("no " + what + " in sight, and not worth a search");
            return Result.FAILED;
        }
        // Digging: time to get down there, plus five minutes of tunnel.
        if (budget == 0) budget = depth == null ? SEARCH_TICKS : 6000 + 50 * Math.max(0, bot.body().blockPosition().getY() - depth);
        // (Walking to where they are known to be is not searching: only the looking about counts.)
        if (lead == null) searchTicks++;
        if (searchTicks > budget || legs > (depth == null ? MAX_LEGS : 600)) {
            bot.say("found no " + what);
            return Result.FAILED;
        }
        if (!searching) {
            searching = true;
            walking = false;
        }
        if (heading == null) heading = Direction.Plane.HORIZONTAL.getRandomDirection(bot.body().getRandom());
        BotNavigator nav = bot.navigator();
        // Ores: a cave first (walk through it and see what its walls show), a tunnel only when
        // there is no cave about or the caves had nothing.
        if (depth != null && !cavesDone && !rock(level(bot)) && !(digAtLead && lead != null)) return explore(bot);
        if (depth == null && rock(level(bot))) return dig(bot);
        if (depth != null && !(digAtLead && lead != null)) return dig(bot);
        // Where they are known to be: go there first.
        if (lead != null) {
            // Far off: over the surface in legs (a path search straight there would dig through
            // the hills in between, and go underground where it ran out of budget).
            if (!leadSaid) {
                leadSaid = true;
                bot.say("none in sight: off to where " + what + " is found, " + lead.toShortString());
            }
            if (!walking) {
                BlockPos aim = lead;
                if (sideStep != 0) {
                    // (Turned 60 degrees off the straight line, for one leg.)
                    double dx = lead.getX() - nav.feet().getX(), dz = lead.getZ() - nav.feet().getZ();
                    double a = Math.atan2(dz, dx) + sideStep * Math.PI / 3;
                    aim = nav.feet().offset((int) (Math.cos(a) * 30), 0, (int) (Math.sin(a) * 30));
                }
                nav.goNear(Explorer.legToward(level(bot), nav.feet(), aim, sideStep != 0 ? 30 : 40), 4);
                walking = true;
            }
            BotNavigator.Status s = nav.tick();
            if (s != BotNavigator.Status.MOVING) {
                walking = false;
                scanCooldown = 0;
                // There: look around there, then explore from there. No way on: a leg a bit to
                // the side (a lake, a cliff in the way), and only after a few of those give up.
                if (s == BotNavigator.Status.FAILED && ++leadFails > 5 || horizontal(nav.feet(), lead) < 8) lead = null;
                if (s == BotNavigator.Status.FAILED) sideStep = bot.body().getRandom().nextBoolean() ? 1 : -1;
                else sideStep = 0;
            }
            return Result.RUNNING;
        }
        // On the surface (climbing up first if it is down in a mine), walking out.
        if (explorer == null) explorer = new Explorer(budget);
        Result r = explorer.tick(bot);
        if (r == Result.FAILED) bot.say("found no " + what);
        return r;
    }

    /** Not buried far below the ground when it is a surface thing (and the bot is not down there too). */
    private boolean worthIt(ServerLevel level, Bot bot, BlockPos p) {
        // Under water: diving and digging there (five times slower) is not worth it while there
        // are others on dry land.
        // (Not so for what lies under shallow water as a rule - clay on a river bed - or once a
        // while has gone by with nothing found on land: then it is dived for.)
        boolean diveFor = shallowWater(level, p) && (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.CLAY) || searchTicks > 600);
        if (!bot.actions().inReach(p) && !diveFor) {
            for (Direction d : Direction.values()) {
                if (d != Direction.DOWN && !level.getFluidState(p.relative(d)).isEmpty()) return false;
            }
        }
        // Below a river or a lake: the way down is through the water. Others first.
        if (!bot.actions().inReach(p) && !diveFor && p.getY() < bot.body().getBlockY()) {
            for (int y = p.getY() + 1; y <= Math.min(p.getY() + 24, bot.body().getBlockY() + 2); y++) {
                if (!level.getFluidState(new BlockPos(p.getX(), y, p.getZ())).isEmpty()) return false;
            }
        }
        if (depth != null) {
            // One remembered far off against digging down for it here: the walk (some 2.5 blocks
            // a second over and under ground) must beat the dig down plus a minute's search at
            // the depth (a cave ore remembered 500 blocks away is not worth the trip).
            BlockPos feet = bot.body().blockPosition();
            double dx = p.getX() - feet.getX(), dz = p.getZ() - feet.getZ();
            double walk = Math.sqrt(dx * dx + dz * dz) / 2.5 + Math.abs(p.getY() - feet.getY());
            double dig = Math.max(0, feet.getY() - depth) + 60;
            // Many still wanted (eleven iron for the pickaxe, the bucket and the shield): not one
            // walk to a lone ore after the other over the hills, but down to where they are
            // common and mined there in one go - unless the next one is a short walk.
            int left = count - ObtainPlanner.countAny(bot.body(), items);
            if (left >= 3) return walk <= Math.min(dig, 25);
            return walk <= dig;
        }
        if (Math.abs(p.getY() - bot.body().getBlockY()) <= 8) return true;
        // (Under a roof there is no surface to be near: the Nether's blocks are all "inside".)
        if (level.dimensionType().hasCeiling()) return Math.abs(p.getY() - bot.body().getBlockY()) <= 24;
        return p.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 6;
    }

    /** Water over it, at most four deep (a river bed, a pond: dived to and dug in a breath). */
    private static boolean shallowWater(ServerLevel level, BlockPos p) {
        int deep = 0;
        BlockPos q = p.above();
        while (level.getFluidState(q).is(net.minecraft.tags.FluidTags.WATER)) {
            if (++deep > 4) return false;
            q = q.above();
        }
        return deep > 0 && level.getBlockState(q).isAir();
    }

    private static ServerLevel level(Bot bot) {
        return (ServerLevel) bot.body().level();
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- caves --------------------------------------------------------------------------------

    /**
     * The memory has already "walked" every cave about (it knows each ore showing in a cave wall),
     * so a cave is only still worth it as the quick way down: walking down a cave beats digging a
     * staircase. Two spots, nearer the wanted height each, then the tunnel from there.
     */
    /** Spots of a cave walked inside the ore's band before the yield says: enough of this one. */
    private static final int MAX_BAND_SPOTS = 14;
    /** Searching this long in the band without finding any: the caves here are done. */
    private static final int DRY_TICKS = 1800;
    private final List<BlockPos> caveVisited = new java.util.ArrayList<>();
    private BlockPos caveSpot;
    private boolean cavesDone;
    private int caveTicks, bandSpots, lastFind;

    /** How far above and below the wanted height the ore is common (iron, coal and the like widely; diamonds narrowly). */
    private int band() {
        for (Item i : items) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath();
            if (id.contains("diamond") || id.contains("redstone") || id.contains("gold")) return 10;
        }
        return 16;
    }

    /**
     * Down a cave towards the wanted height (each spot reached also shows more of the world: the
     * target search looks again), then the tunnel from there. No cave about that goes lower: the
     * tunnel from here.
     */
    private Result explore(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BotNavigator nav = bot.navigator();
        if (caveSpot == null) {
            int band = band();
            boolean inBand = Math.abs(nav.feet().getY() - depth) <= band;
            // In the band, the cave walked on spot by spot (each shows its walls: what is in
            // them is mined as it comes into sight), until it stops paying.
            if (inBand && (bandSpots >= MAX_BAND_SPOTS || searchTicks - lastFind > DRY_TICKS)) return caveGiveUp(bot, "the caves here are done");
            caveSpot = bot.memory().cave(level, nav.feet(), depth, band, caveVisited.isEmpty() ? 128 : 64, caveVisited);
            if (caveSpot == null) return caveGiveUp(bot, inBand ? "no more cave here" : "no cave about");
            // Out of the band: only a cave that gets it nearer the height it wants (not one up a hill).
            if (!inBand && Math.abs(caveSpot.getY() - depth) > band && Math.abs(caveSpot.getY() - depth) + 6 >= Math.abs(nav.feet().getY() - depth)) {
                caveSpot = null;
                return caveGiveUp(bot, "no cave lower down");
            }
            if (inBand) bandSpots++;
            bot.say((inBand ? "on through the cave for " : "down through a cave for ") + what + " (to " + caveSpot.toShortString() + ")");
            // Remember the way in: the spot it went underground from, to get out the same way.
            if (bot.caveEntry == null && !SurfaceTask.underground(bot.body())) bot.caveEntry = nav.feet();
            nav.goStandNear(caveSpot, 2);
            caveTicks = 0;
        }
        BotNavigator.Status st = nav.tick();
        if (st != BotNavigator.Status.MOVING || ++caveTicks > 400 + 20 * (int) Math.sqrt(caveSpot.distSqr(nav.feet()))) {
            caveVisited.add(caveSpot);
            caveSpot = null;
            scanCooldown = 0; // look about: what does this part of the cave show?
            nav.stop();
        }
        return Result.RUNNING;
    }

    private Result caveGiveUp(Bot bot, String why) {
        bot.say(why + ": tunnelling for " + what);
        cavesDone = true;
        return Result.RUNNING;
    }

    // ---- digging ------------------------------------------------------------------------------

    private List<BlockPos> digBlocks;
    private BlockPos digTo;
    /** The height the staircase goes down to (the ore's, or a few levels for rock). */
    private Integer digDepth;
    private int stepTicks, turns, moves;

    /**
     * One step of a staircase down (three blocks ahead: head, feet, one below) or, at the right
     * height, of a two-high tunnel; then step in. Checks first that the step has a floor and that
     * no water or lava would come in, and turns away if it would.
     */
    private Result dig(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BotNavigator nav = bot.navigator();
        if (searchTicks % 600 == 0) bot.say("digging for " + what + ": at " + nav.feet().toShortString() + ", heading " + heading.getName() + ", y " + (digDepth != null ? digDepth : depth) + " wanted");
        if (digTo == null) {
            BlockPos feet = nav.feet();
            if (digDepth == null) digDepth = depth != null ? depth : feet.getY() - 8;
            boolean down = feet.getY() > digDepth;
            BlockPos ahead = feet.relative(heading);
            stepTicks = 0;
            // Down: a staircase (head, feet and one below ahead, then a step down into it), never
            // a shaft: it never drops into the unknown, it yields three blocks a level, and it is
            // the way back up afterwards - walked, not built with the blocks just dug.
            digTo = down ? ahead.below() : ahead;
            digBlocks = down ? List.of(ahead.above(), ahead, ahead.below()) : List.of(ahead, ahead.above());
            // The next step has no floor: a cave right below. A short drop into it is the way on
            // (and its walls show more than any tunnel); else on along the level for a step.
            if (down && !safeStep(level, digBlocks, digTo)) {
                if (shortDrop(level, digBlocks, digTo)) {
                    // (The landing is wherever it comes down: the step is taken from there.)
                    // Broken into a cave: that is walked first, as any cave found.
                    if (depth != null && cavesDone) {
                        cavesDone = false;
                        bandSpots = 0;
                        lastFind = searchTicks;
                    }
                } else {
                    List<BlockPos> along = List.of(ahead, ahead.above());
                    if (safeStep(level, along, ahead)) {
                        digTo = ahead;
                        digBlocks = along;
                    }
                }
            }
            if (!safeStep(level, digBlocks, digTo) && !(down && shortDrop(level, digBlocks, digTo))) {
                digTo = null;
                heading = heading.getClockWise();
                if (++turns >= 4) {
                    // No safe way on from here (water all around, a ledge): try a few blocks away.
                    turns = 0;
                    if (++moves > 4) {
                        bot.say("can't dig on here");
                        return Result.FAILED;
                    }
                    BlockPos away = feet.offset(bot.body().getRandom().nextInt(13) - 6, 0, bot.body().getRandom().nextInt(13) - 6);
                    // (Down here: a few blocks away down here, not up on the surface above them.)
                    bot.interject(new GoToTask(SurfaceTask.underground(bot.body()) ? away : Explorer.ground(level, away, feet.getY()), 2));
                }
                return Result.RUNNING;
            }
            legs++;
            scanCooldown = 0; // look around after each step: the tunnel may have laid something open
        }
        // In a shaft, stand in the middle first, or the bot hangs on the edge of the hole.
        if (digTo.getX() == nav.feet().getX() && digTo.getZ() == nav.feet().getZ()) {
            var body = bot.body();
            double dx = digTo.getX() + 0.5 - body.getX(), dz = digTo.getZ() + 0.5 - body.getZ();
            if (dx * dx + dz * dz > 0.02) {
                body.lookAt(new net.minecraft.world.phys.Vec3(digTo.getX() + 0.5, body.getEyeY(), digTo.getZ() + 0.5));
                body.forward = 0.25f;
                return Result.RUNNING;
            }
            body.forward = 0;
        }
        for (BlockPos b : digBlocks) {
            BlockState s = level.getBlockState(b);
            if (s.getCollisionShape(level, b).isEmpty() && s.getFluidState().isEmpty()) continue;
            if (!s.getFluidState().isEmpty()) {
                // Water or lava flowed in after all: back off this way.
                digTo = null;
                heading = heading.getOpposite();
                return Result.RUNNING;
            }
            bot.actions().breakTick(b);
            if (++stepTicks > 600) {
                digTo = null;
                heading = heading.getClockWise();
            }
            return Result.RUNNING;
        }
        // Step in: straight ahead (and down for a stair), no path needed.
        var body = bot.body();
        net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atBottomCenterOf(digTo);
        double dx = c.x - body.getX(), dz = c.z - body.getZ();
        boolean landed = nav.feet().getX() == digTo.getX() && nav.feet().getZ() == digTo.getZ() && nav.feet().getY() <= digTo.getY()
                && bot.body().onGround();
        if ((nav.feet().equals(digTo) || landed) && dx * dx + dz * dz < 0.1) {
            body.stopInputs();
            digTo = null;
            turns = 0;
            return Result.RUNNING;
        }
        body.lookAt(c.add(0, body.getEyeHeight(), 0));
        body.forward = dx * dx + dz * dz > 0.3 ? 1f : 0.4f;
        if (++stepTicks > 160) {
            body.stopInputs();
            digTo = null;
            heading = heading.getClockWise();
        }
        return Result.RUNNING;
    }

    /**
     * The step down opens onto a cave: no floor under it, but solid ground at most three blocks
     * lower (a drop that does not hurt), nothing liquid on the way down or round what gets dug.
     */
    private static boolean shortDrop(ServerLevel level, List<BlockPos> dig, BlockPos feet) {
        for (BlockPos b : dig) {
            if (!level.getBlockState(b).getFluidState().isEmpty() || level.getBlockState(b).getDestroySpeed(level, b) < 0) return false;
            for (Direction d : Direction.values()) {
                BlockPos n = b.relative(d);
                if (!dig.contains(n) && !level.getFluidState(n).isEmpty()) return false;
            }
        }
        BlockPos p = feet.below();
        for (int i = 0; i < 4; i++, p = p.below()) {
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) return false;
            if (!s.getCollisionShape(level, p).isEmpty()) return i > 0 && !s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
        }
        return false;
    }

    /** The new spot has a floor, nothing is above it that falls, and no liquid touches what gets dug out. */
    private static boolean safeStep(ServerLevel level, List<BlockPos> dig, BlockPos feet) {
        BlockPos floor = feet.below();
        BlockState f = level.getBlockState(floor);
        if (f.getCollisionShape(level, floor).isEmpty() || !f.getFluidState().isEmpty()) return false;
        if (level.getBlockState(floor).is(net.minecraft.world.level.block.Blocks.BEDROCK) && feet.getY() <= level.getMinY() + 1) return false;
        for (BlockPos b : dig) {
            if (!level.getBlockState(b).getFluidState().isEmpty()) return false;
            if (level.getBlockState(b).getDestroySpeed(level, b) < 0) return false;
            for (Direction d : Direction.values()) {
                BlockPos n = b.relative(d);
                if (dig.contains(n)) continue;
                if (!level.getBlockState(n).getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }

    @Override
    public String status() {
        return describe() + " [target " + (target == null ? "-" : target.toShortString()) + ", searching " + searching
                + ", dig to " + (digTo == null ? "-" : digTo.toShortString()) + ", collect " + collectTicks + ", idle " + idle
                + ", skip " + skip.size() + ", nav " + "]";
    }

    @Override
    public String describe() {
        return "mine " + what + " (" + count + ")" + (depth != null ? " at y " + depth : "");
    }
}
