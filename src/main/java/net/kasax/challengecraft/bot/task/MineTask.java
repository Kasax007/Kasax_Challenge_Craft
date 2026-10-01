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
    private boolean walking, searching;
    private Direction heading;
    private Explorer explorer;
    private BlockPos lead, approaching;
    private int approachTicks;

    /** Mine until the bot holds {@code count} of the {@code items} together. */
    public MineTask(String what, Predicate<BlockState> blocks, Set<Item> items, int count) {
        this(what, blocks, items, count, null);
    }

    public MineTask(String what, Predicate<BlockState> blocks, Set<Item> items, int count, Integer depth) {
        this(what, blocks, items, count, depth, null);
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
        if (ObtainPlanner.countAny(bot.body(), items) >= count) return Result.DONE;

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

    /** Nothing in sight: walk out on the surface, or dig for it. */
    private Result search(Bot bot) {
        // Digging: time to get down there, plus five minutes of tunnel.
        if (budget == 0) budget = depth == null ? SEARCH_TICKS : 6000 + 50 * Math.max(0, bot.body().blockPosition().getY() - depth);
        if (++searchTicks > budget || legs > (depth == null ? MAX_LEGS : 600)) {
            bot.say("found no " + what);
            return Result.FAILED;
        }
        if (!searching) {
            searching = true;
            walking = false;
        }
        if (heading == null) heading = Direction.Plane.HORIZONTAL.getRandomDirection(bot.body().getRandom());
        BotNavigator nav = bot.navigator();
        if (depth != null) return dig(bot);
        // Where they are known to be: go there first.
        if (lead != null) {
            // Far off: over the surface in legs (a path search straight there would dig through
            // the hills in between, and go underground where it ran out of budget).
            if (!walking) {
                nav.goNear(Explorer.legToward(level(bot), nav.feet(), lead, 40), 4);
                walking = true;
            }
            BotNavigator.Status s = nav.tick();
            if (s != BotNavigator.Status.MOVING) {
                walking = false;
                scanCooldown = 0;
                // There (or no way on): look around there, then explore from there.
                if (s == BotNavigator.Status.FAILED || horizontal(nav.feet(), lead) < 8) lead = null;
            }
            return Result.RUNNING;
        }
        // On the surface (climbing up first if it is down in a mine), walking out.
        if (explorer == null) explorer = new Explorer(budget);
        Result r = explorer.tick(bot);
        if (r == Result.FAILED) bot.say("found no " + what);
        return r;
    }

    private static ServerLevel level(Bot bot) {
        return (ServerLevel) bot.body().level();
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
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
