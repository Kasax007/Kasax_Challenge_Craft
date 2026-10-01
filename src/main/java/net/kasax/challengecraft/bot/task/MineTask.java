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
        if (have >= count) return Result.DONE;
        // Enough for now, more only for later (other tiles want it too): while it is right here.
        if (have >= needed && collectTicks <= 0 && (target == null ? !nearbyMore(level, bot) : target.distSqr(bot.body().blockPosition()) > 12 * 12)) return Result.DONE;

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
                target = BotWorld.nearest(level, bot.body().blockPosition(), searching ? 12 : 28, searching ? 8 : 20, blocks, true, skip);
                // Nothing right here: somewhere it has been past (or seen from afar).
                if (target == null) target = bot.memory().nearest(level, bot.body().blockPosition(), blocks, skip);
                // Things from the surface (logs, sand) seen deep down (a mineshaft's beams) are not
                // worth digging fifty blocks for: those are left for ones up here.
                for (int i = 0; i < 8 && target != null && !worthIt(level, bot, target); i++) {
                    skip.add(target);
                    target = BotWorld.nearest(level, bot.body().blockPosition(), searching ? 12 : 28, searching ? 8 : 20, blocks, true, skip);
                    if (target == null) target = bot.memory().nearest(level, bot.body().blockPosition(), blocks, skip);
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
                bot.navigator().goNear(target, 3.6);
                walking = true;
            }
            // Much longer than the way there should take: this one is not to be had; another.
            if (!target.equals(approaching)) {
                approaching = target;
                approachTicks = 0;
            }
            if (++approachTicks > 300 + 25 * Math.sqrt(target.distSqr(bot.body().blockPosition()))) {
                skip.add(target);
                target = null;
                walking = false;
                bot.navigator().stop();
                return Result.RUNNING;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED) {
                skip.add(target);
                target = null;
                walking = false;
            } else if (s == BotNavigator.Status.ARRIVED) {
                walking = false;
                if (!bot.actions().inReach(target)) {
                    skip.add(target);
                    target = null;
                }
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        walking = false;
        if (bot.actions().breakTick(target)) {
            if (blocks.test(level.getBlockState(target))) {
                skip.add(target); // refused (protected)
            }
            target = null;
            collectTicks = 60;
            idle = 0;
        } else if (++idle > 400) {
            skip.add(target);
            target = null;
            idle = 0;
        }
        return Result.RUNNING;
    }

    private boolean knownOnly;

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
        if (depth != null && !cavesDone) return explore(bot);
        if (depth != null) return dig(bot);
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
        if (!bot.actions().inReach(p)) {
            for (Direction d : Direction.values()) {
                if (d != Direction.DOWN && !level.getFluidState(p.relative(d)).isEmpty()) return false;
            }
        }
        // Below a river or a lake: the way down is through the water. Others first.
        if (!bot.actions().inReach(p) && p.getY() < bot.body().getBlockY()) {
            for (int y = p.getY() + 1; y <= Math.min(p.getY() + 24, bot.body().getBlockY() + 2); y++) {
                if (!level.getFluidState(new BlockPos(p.getX(), y, p.getZ())).isEmpty()) return false;
            }
        }
        if (depth != null) return true;
        if (Math.abs(p.getY() - bot.body().getBlockY()) <= 8) return true;
        return p.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 6;
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
    private static final int MAX_CAVE_SPOTS = 2;
    private final List<BlockPos> caveVisited = new java.util.ArrayList<>();
    private BlockPos caveSpot;
    private boolean cavesDone;
    private int caveTicks;

    /**
     * Down a cave towards the wanted height (each spot reached also shows more of the world: the
     * target search looks again), then the tunnel from there. No cave about that goes lower: the
     * tunnel from here.
     */
    private Result explore(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BotNavigator nav = bot.navigator();
        if (caveSpot == null) {
            if (caveVisited.size() >= MAX_CAVE_SPOTS) return caveGiveUp(bot, "deep enough by cave");
            caveSpot = bot.memory().cave(level, nav.feet(), depth, caveVisited.isEmpty() ? 96 : 48, caveVisited);
            if (caveSpot == null) return caveGiveUp(bot, "no cave about");
            // Only a cave that gets it nearer the height it wants (not one up a hill).
            if (Math.abs(caveSpot.getY() - depth) + 6 >= Math.abs(nav.feet().getY() - depth)) {
                caveSpot = null;
                return caveGiveUp(bot, "no cave lower down");
            }
            bot.say("down through a cave for " + what + " (to " + caveSpot.toShortString() + ")");
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
    private int stepTicks, turns, moves;

    /**
     * One step of a staircase down (three blocks ahead: head, feet, one below) or, at the right
     * height, of a two-high tunnel; then step in. Checks first that the step has a floor and that
     * no water or lava would come in, and turns away if it would.
     */
    private Result dig(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BotNavigator nav = bot.navigator();
        if (searchTicks % 600 == 0) bot.say("digging for " + what + ": at " + nav.feet().toShortString() + ", heading " + heading.getName() + ", y " + depth + " wanted");
        if (digTo == null) {
            BlockPos feet = nav.feet();
            boolean down = feet.getY() > depth;
            BlockPos ahead = feet.relative(heading);
            stepTicks = 0;
            // Down: a shaft (one block per level) where the block under it is solid ground and
            // nothing liquid is near; otherwise a staircase, which never drops into the unknown.
            if (down && safeStep(level, List.of(feet.below()), feet.below())) {
                digTo = feet.below();
                // Also whatever low thing the bot stands in (snow, a slab), or it never drops.
                digBlocks = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        ? List.of(feet.below()) : List.of(feet, feet.below());
            } else {
                digTo = down ? ahead.below() : ahead;
                digBlocks = down ? List.of(ahead.above(), ahead, ahead.below()) : List.of(ahead, ahead.above());
            }
            if (!safeStep(level, digBlocks, digTo)) {
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
                    bot.interject(new GoToTask(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, away), 2));
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
        if (nav.feet().equals(digTo) && dx * dx + dz * dz < 0.1) {
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
