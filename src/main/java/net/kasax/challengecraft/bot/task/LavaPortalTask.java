package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Nether portal cast at a lava pool, the way that cannot go wrong: a lava source turns to
 * obsidian the moment water touches it from above or from the side (and nothing else happens
 * when the water is taken back before it starts to run, five ticks later).
 *
 * <p>So: a standing spot by the pool with lava in reach; two blocks in front of it the portal's
 * plane (4 wide, 5 high) dug clear, with the space in front of it; a wall of blocks right behind
 * the plane, the mould every bucket is poured against. Then each of the ten frame blocks, from
 * the bottom up: water into the cell beside it (the inside of the portal, or the top corners for
 * the top row), lava into the frame cell - obsidian at once - and the water scooped back up. The
 * lava comes from the pool a bucket at a time. Lit, and through.
 */
public final class LavaPortalTask implements BotTask {
    private enum Stage { PLAN, GO, CLEAR, WALL, CAST, LIGHT, ENTER }

    /**
     * Frame cells (x across, y up) in casting order, each with the cell its water goes into: the
     * bottom (water above it, inside the portal), the top (water in the top corners, seen past
     * the still open sides), then the sides from the top down (water inside, beside them).
     */
    private static final int[][] FRAME = {
            {1, 0, 1, 1}, {2, 0, 2, 1},
            {1, 4, 0, 4}, {2, 4, 3, 4},
            {0, 3, 1, 3}, {3, 3, 2, 3},
            {0, 2, 1, 2}, {3, 2, 2, 2},
            {0, 1, 1, 1}, {3, 1, 2, 1}};

    private final ResourceKey<Level> from;
    private final BlockPos near;
    private final Set<Long> badSites = new HashSet<>();
    private Stage stage = Stage.PLAN;
    private BlockPos stand, origin;
    private Direction ahead, across;
    private int ticks, tries, cell, phase, walkFails, sites;
    private boolean walking;
    private BotTask sub;

    public LavaPortalTask(Level level, BlockPos near) {
        this.from = level.dimension();
        this.near = near;
    }

    /** The frame's bottom corners: not part of a portal, left as they are (something to aim at). */
    private static boolean corner(int x, int y, int dz) {
        return dz == 0 && y == 0 && (x == 0 || x == 3);
    }

    /** The cell (x across, y up) of the portal's plane; dz -1 the space in front, +1 the mould wall behind. */
    private BlockPos at(int x, int y, int dz) {
        return origin.relative(across, x).above(y).relative(ahead, dz);
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (level.dimension() != from) return Result.DONE;
        if (++ticks > 9600) return fail(bot, "took too long");
        if (sub != null) {
            Result r = sub.tick(bot);
            if (r == Result.RUNNING) return r;
            sub = null;
            if (r == Result.FAILED) return fail(bot, "no water to cast with");
            return Result.RUNNING;
        }
        return switch (stage) {
            case PLAN -> plan(bot, level);
            case GO -> go(bot);
            case CLEAR -> clear(bot, level);
            case WALL -> wall(bot, level);
            case CAST -> cast(bot, level);
            case LIGHT -> light(bot, level);
            case ENTER -> enter(bot, level);
        };
    }

    private Result fail(Bot bot, String why) {
        bot.say("casting failed: " + why + (stand == null ? "" : " (spot " + stand.toShortString() + ", frame cell " + cell + ")"));
        if (near != null) bot.badCastPools.add(near);
        return Result.FAILED;
    }

    /** Another site for the frame (this one did not work out), a few times. */
    private Result replan(Bot bot, String why) {
        bot.say("casting site " + stand.toShortString() + " no good (" + why + "): another");
        badSites.add(stand.asLong());
        stage = Stage.PLAN;
        bot.navigator().stop();
        walking = false;
        return ++sites > 4 ? fail(bot, "no site works") : Result.RUNNING;
    }

    // ---- choosing the site --------------------------------------------------------------------

    private static boolean lavaSource(ServerLevel level, BlockPos p) {
        return level.getFluidState(p).is(FluidTags.LAVA) && level.getFluidState(p).isSource();
    }

    private Result plan(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        if (BotInventory.slotOf(body, Items.WATER_BUCKET) < 0) {
            if (BotInventory.slotOf(body, Items.BUCKET) < 0) return fail(bot, "no bucket");
            sub = new FillBucketTask(FluidTags.WATER);
            return Result.RUNNING;
        }
        BlockPos pool = near != null && lavaSource(level, near) ? near
                : net.kasax.challengecraft.bot.BotWorld.nearest(level, body.blockPosition(), 24, 12, s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), false, Set.of());
        if (pool == null) return fail(bot, "no lava pool");
        // A frame begun here before (cut short, the bot called away): finished, not cast anew.
        if (resume(bot, level, pool)) return Result.RUNNING;
        List<BlockPos> sources = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(pool.offset(-10, -3, -10), pool.offset(10, 3, 10))) if (lavaSource(level, q)) sources.add(q.immutable());
        if (sources.size() < 10) return fail(bot, "only " + sources.size() + " lava sources here");
        double best = Double.MAX_VALUE;
        BlockPos bestStand = null, bestOrigin = null;
        Direction bestAhead = null;
        for (BlockPos p : BlockPos.betweenClosed(pool.offset(-9, -3, -9), pool.offset(9, 3, 9))) {
            if (badSites.contains(p.asLong())) continue;
            if (!standable(level, p)) continue;
            // Lava in reach from there: no walking for each bucket.
            int inReach = 0;
            Vec3 eye = new Vec3(p.getX() + 0.5, p.getY() + 1.62, p.getZ() + 0.5);
            for (BlockPos s : sources) if (canScoop(level, body, eye, s, 4.2)) inReach++;
            if (inReach == 0) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Direction a = d.getClockWise();
                // (Sunk one block: the bottom of the frame in the ground, the inside level with him.)
                BlockPos o = p.relative(d, 2).relative(a.getOpposite()).below();
                double cost = siteCost(level, o, d, a);
                if (cost >= 1e9) continue;
                cost += Math.max(0, 10 - inReach) * 6 + p.distManhattan(body.blockPosition()) * 0.3;
                if (cost < best) {
                    best = cost;
                    bestStand = p.immutable();
                    bestOrigin = o;
                    bestAhead = d;
                }
            }
        }
        if (bestStand == null) return fail(bot, "no room for a frame by the pool");
        stand = bestStand;
        origin = bestOrigin;
        ahead = bestAhead;
        across = ahead.getClockWise();
        bot.say("casting a portal at " + origin.toShortString() + " (standing at " + stand.toShortString() + ", facing " + ahead.getName() + ")");
        stage = Stage.GO;
        walking = false;
        return Result.RUNNING;
    }

    /**
     * Looks for a frame of its own begun near the pool (four obsidian blocks at least where a
     * frame's cells are, the rest still open): if there is one, it is taken up from where it stopped.
     */
    private boolean resume(Bot bot, ServerLevel level, BlockPos pool) {
        int best = 3 * 20 + 20;
        BlockPos bestOrigin = null;
        Direction bestAhead = null;
        for (BlockPos q : BlockPos.betweenClosed(pool.offset(-12, -4, -12), pool.offset(12, 4, 12))) {
            if (!level.getBlockState(q).is(Blocks.OBSIDIAN) || !level.getBlockState(q.above()).isAir() && !level.getBlockState(q.above()).is(Blocks.OBSIDIAN)) continue;
            // (Taken as the frame's bottom left cell, x 1 y 0, in every facing.)
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Direction a = d.getClockWise();
                BlockPos o = q.relative(a.getOpposite());
                int set = 0;
                boolean fits = true;
                for (int[] c : FRAME) {
                    BlockPos f = o.relative(a, c[0]).above(c[1]);
                    var st = level.getBlockState(f);
                    if (st.is(Blocks.OBSIDIAN)) set++;
                    else if (!st.isAir() && !st.getFluidState().is(FluidTags.WATER)) fits = false;
                }
                // (The same frame seen from behind reads the same: the side with the mould wall
                // - the blocks every bucket was poured against - is the back.)
                int wall = 0;
                for (int[] c : FRAME) {
                    BlockPos back = o.relative(a, c[0]).above(c[1]).relative(d, 1);
                    if (!level.getBlockState(back).getCollisionShape(level, back).isEmpty()) wall++;
                }
                set = set * 20 + wall;
                if (!fits || set <= best) continue;
                // Its inside open (or water), the standing spot in front reachable ground.
                BlockPos stand = o.relative(a).above().relative(d, -2);
                if (!standable(level, stand) || badSites.contains(stand.asLong())) continue;
                best = set;
                bestOrigin = o.immutable();
                bestAhead = d;
            }
        }
        if (bestOrigin == null || best / 20 >= FRAME.length) return false;
        origin = bestOrigin;
        ahead = bestAhead;
        across = ahead.getClockWise();
        stand = origin.relative(across).above().relative(ahead, -2);
        bot.say("the frame begun at " + origin.toShortString() + " (" + best / 20 + " of " + FRAME.length + " set): finishing it");
        stage = Stage.GO;
        walking = false;
        return true;
    }

    /** Running water beside the spot or its head: it pushes, and the pool is right there. */
    private static boolean flowingBeside(ServerLevel level, BlockPos p) {
        for (BlockPos q : new BlockPos[]{p, p.above()}) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                var fs = level.getFluidState(q.relative(d));
                if (fs.is(FluidTags.WATER) && !fs.isSource()) return true;
            }
        }
        return false;
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()) return false;
        if (flowingBeside(level, p)) return false;
        if (level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty() || !level.getFluidState(p.below()).isEmpty()) return false;
        return clearable(level, p) && clearable(level, p.above());
    }

    /** Air, or a block that breaks (no fluid in it). */
    private static boolean clearable(ServerLevel level, BlockPos p) {
        var st = level.getBlockState(p);
        if (!level.getFluidState(p).isEmpty()) return false;
        if (st.isAir() || st.getCollisionShape(level, p).isEmpty()) return true;
        float h = st.getDestroySpeed(level, p);
        return h >= 0 && h < 5;
    }

    /**
     * What it takes to cast here (blocks to dig, blocks for the wall), or 1e9 if it cannot: the
     * plane, the space in front and the standing spot clear of any fluid, and nothing liquid next
     * to the cells that get dug (or the lava would run in).
     */
    private double siteCost(ServerLevel level, BlockPos o, Direction d, Direction a) {
        double cost = 0;
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                for (int dz = -1; dz <= 0; dz++) {
                    // (In front of the frame only above the ground: that row is the floor.)
                    if (dz == -1 && y == 0 || corner(x, y, dz)) continue;
                    BlockPos q = o.relative(a, x).above(y).relative(d, dz);
                    if (!clearable(level, q)) return 1e9;
                    if (!level.getBlockState(q).isAir()) cost += 1.5;
                    // (No fluid next to anything dug: it would run in. The pool stays out of it.)
                    for (Direction n : Direction.values()) {
                        BlockPos m = q.relative(n);
                        if (!level.getFluidState(m).isEmpty()) return 1e9;
                    }
                    // Sand or gravel overhead comes down into the dug space, again and again.
                    if (y == 4 && level.getBlockState(q.above()).getBlock() instanceof net.minecraft.world.level.block.FallingBlock) cost += 25;
                }
                BlockPos back = o.relative(a, x).above(y).relative(d, 1);
                if (level.getBlockState(back).getCollisionShape(level, back).isEmpty()) cost += 1;
            }
        }
        // Ground in front of the frame to walk in on.
        for (int x = 1; x <= 2; x++) {
            BlockPos f = o.relative(a, x).relative(d, -1);
            if (level.getBlockState(f).getCollisionShape(level, f).isEmpty()) cost += 2;
        }
        return cost;
    }

    // ---- getting the site ready ---------------------------------------------------------------

    private Result go(Bot bot) {
        BotPlayer body = bot.body();
        if (bot.navigator().feet().equals(stand)) {
            bot.navigator().stop();
            body.stopInputs();
            stage = Stage.CLEAR;
            tries = 0;
            return Result.RUNNING;
        }
        if (!walking) {
            bot.navigator().goTo(stand);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        if (s == BotNavigator.Status.FAILED && ++walkFails > 6) {
            walkFails = 0;
            return replan(bot, "can't get there");
        }
        return Result.RUNNING;
    }

    /** Back to the standing spot if it got moved off it (a knock, a step to scoop). */
    private boolean atStand(Bot bot) {
        if (bot.navigator().feet().equals(stand)) {
            if (walking) bot.navigator().stop();
            walking = false;
            return true;
        }
        if (!walking) {
            bot.navigator().goTo(stand);
            walking = true;
        }
        if (bot.navigator().tick() != BotNavigator.Status.MOVING) walking = false;
        return false;
    }

    private Result clear(Bot bot, ServerLevel level) {
        if (!atStand(bot)) return Result.RUNNING;
        // The plane and the space in front of it, from the top down (nothing falls into what is
        // dug); the ground in front stays (it is the way in).
        for (int y = 4; y >= 0; y--) {
            for (int dz = -1; dz <= 0; dz++) {
                if (dz == -1 && y == 0) continue;
                for (int x = 0; x < 4; x++) {
                    if (corner(x, y, dz)) continue;
                    BlockPos q = at(x, y, dz);
                    // (Water left standing in it from a cast cut short: back into the bucket.)
                    if (level.getFluidState(q).is(FluidTags.WATER) && level.getFluidState(q).isSource()
                            && BotInventory.slotOf(bot.body(), Items.BUCKET) >= 0 && ++tries < 60) {
                        use(bot, level, Items.BUCKET, Vec3.atCenterOf(q));
                        return Result.RUNNING;
                    }
                    if (!level.getFluidState(q).isEmpty()) return replan(bot, "a fluid ran into the frame");
                    if (level.getBlockState(q).getCollisionShape(level, q).isEmpty()) continue;
                    // (The frame's own obsidian, cast before: stays.)
                    if (dz == 0 && frameCell(x, y) && level.getBlockState(q).is(Blocks.OBSIDIAN)) continue;
                    if (++tries > 600) return replan(bot, "can't dig " + q.toShortString());
                    bot.actions().breakTick(q);
                    return Result.RUNNING;
                }
            }
        }
        for (int x = 1; x <= 2; x++) {
            BlockPos f = at(x, 0, -1);
            if (!level.getBlockState(f).getCollisionShape(level, f).isEmpty()) continue;
            if (!bot.actions().hasThrowaway()) return fail(bot, "no blocks for the way in");
            if (++tries > 600) return replan(bot, "can't floor " + f.toShortString());
            bot.actions().placeThrowaway(f);
            return Result.RUNNING;
        }
        stage = Stage.WALL;
        tries = 0;
        return Result.RUNNING;
    }

    /** The mould: a block right behind every cell of the plane (the plane dug out first: in sight). */
    private Result wall(Bot bot, ServerLevel level) {
        if (!atStand(bot)) return Result.RUNNING;
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 4; x++) {
                if (corner(x, y, 0)) continue;
                BlockPos q = at(x, y, 1);
                if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) continue;
                // A flower, a sapling, a torch there: away first (a block does not go in over it).
                if (!level.getBlockState(q).isAir() && !level.getBlockState(q).canBeReplaced() && level.getFluidState(q).isEmpty()) {
                    if (++tries > 200) return replan(bot, "can't clear " + q.toShortString());
                    bot.actions().breakTick(q);
                    return Result.RUNNING;
                }
                if (!bot.actions().hasThrowaway()) return fail(bot, "no blocks for the mould");
                if (++tries > 80) return replan(bot, "can't build the mould at " + q.toShortString());
                // (Nothing to set it against: one below it first.)
                if (!hasSupport(level, q)) {
                    bot.actions().placeThrowaway(q.below());
                    return Result.RUNNING;
                }
                bot.actions().placeThrowaway(q);
                return Result.RUNNING;
            }
        }
        stage = Stage.CAST;
        cell = 0;
        phase = 0;
        tries = 0;
        return Result.RUNNING;
    }

    private static boolean hasSupport(ServerLevel level, BlockPos q) {
        for (Direction d : Direction.values()) {
            BlockPos n = q.relative(d);
            if (!level.getBlockState(n).getCollisionShape(level, n).isEmpty()) return true;
        }
        return false;
    }

    // ---- casting ------------------------------------------------------------------------------

    private Result cast(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        if (cell >= FRAME.length) {
            stage = Stage.LIGHT;
            tries = 0;
            return Result.RUNNING;
        }
        int[] c = FRAME[cell];
        BlockPos f = at(c[0], c[1], 0), w = at(c[2], c[3], 0);
        if (++tries > 400) return replan(bot, "frame cell " + cell + " would not set");
        // Done already (a retry, or the pool's own lava set there).
        if (level.getBlockState(f).is(Blocks.OBSIDIAN) && phase == 0) {
            cell++;
            tries = 0;
            return Result.RUNNING;
        }
        // A bucket of lava first (from where it stands if it can: else a few steps and back).
        if (phase == 0 && BotInventory.slotOf(body, Items.LAVA_BUCKET) < 0) return scoopLava(bot, level);
        if (!atStand(bot)) return Result.RUNNING;
        body.stopInputs();
        if (phase == 0) {
            // Something in the way of either bucket: out of it first.
            for (BlockPos q : new BlockPos[]{w, f}) {
                if (!level.getBlockState(q).isAir() && level.getFluidState(q).isEmpty() && !level.getBlockState(q).is(Blocks.OBSIDIAN)) {
                    bot.actions().breakTick(q);
                    return Result.RUNNING;
                }
            }
            // Both pours in sight before the water goes in, and the water to be had back past the
            // obsidian about to be there (else the water would stand and run).
            scoopAt = scoopPoint(bot, level, w, f);
            if (aim(bot, level, w) == null || aim(bot, level, f) == null || scoopAt == null) return replan(bot, "frame cell " + cell + " out of sight");
            if (!level.getFluidState(w).isSource()) {
                if (BotInventory.slotOf(body, Items.WATER_BUCKET) < 0) return refillWater(bot, level);
                pour(bot, level, Items.WATER_BUCKET, w);
                if (!level.getFluidState(w).is(FluidTags.WATER) || !level.getFluidState(w).isSource()) return Result.RUNNING;
            }
            phase = 1;
            return Result.RUNNING;
        }
        if (phase == 1) {
            // The next tick, long before the water would run (five): lava into the frame cell,
            // obsidian the moment it is there. If it did not go in: the water back at once.
            if (!level.getBlockState(f).is(Blocks.OBSIDIAN) && level.getFluidState(w).is(FluidTags.WATER)) pour(bot, level, Items.LAVA_BUCKET, f);
            phase = 2;
        }
        // The water back into the bucket before it runs (where it was seen to be had from; else
        // a block into it, which ends the water just as well).
        if (level.getFluidState(w).is(FluidTags.WATER) && level.getFluidState(w).isSource()) {
            if (BotInventory.slotOf(body, Items.BUCKET) < 0) return fail(bot, "no empty bucket to take the water back");
            use(bot, level, Items.BUCKET, scoopAt != null ? scoopAt : Vec3.atCenterOf(w));
            if (level.getFluidState(w).isSource()) {
                Vec3 again = scoopPoint(bot, level, w, null);
                if (again != null) use(bot, level, Items.BUCKET, again);
            }
            if (level.getFluidState(w).isSource()) {
                bot.say("the water would not come back up: a block into it");
                bot.actions().placeThrowaway(w);
                return Result.RUNNING;
            }
        }
        // (A block put into the water that would not come back: out again.)
        if (!level.getBlockState(w).isAir() && level.getFluidState(w).isEmpty() && !level.getBlockState(w).is(Blocks.OBSIDIAN)) {
            bot.actions().breakTick(w);
            return Result.RUNNING;
        }
        // Lava left where it did not set (should not be): back into the bucket.
        if (lavaSource(level, f)) {
            if (BotInventory.slotOf(body, Items.BUCKET) < 0) return fail(bot, "lava in the frame and no bucket");
            use(bot, level, Items.BUCKET, Vec3.atCenterOf(f));
            return Result.RUNNING;
        }
        if (level.getBlockState(f).is(Blocks.OBSIDIAN)) {
            cell++;
            tries = 0;
        }
        phase = 0;
        return Result.RUNNING;
    }

    private BlockPos lavaTarget;

    /** A bucket of lava from the pool: one in reach, else the nearest, from a spot that reaches it. */
    private Result scoopLava(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        if (BotInventory.slotOf(body, Items.BUCKET) < 0) return fail(bot, "no empty bucket for lava");
        Vec3 eye = body.getEyePosition();
        if (lavaTarget == null || !lavaSource(level, lavaTarget)) {
            lavaTarget = null;
            double best = Double.MAX_VALUE;
            for (BlockPos q : BlockPos.betweenClosed(stand.offset(-12, -4, -12), stand.offset(12, 4, 12))) {
                if (!lavaSource(level, q) || inFrame(q)) continue;
                double d = q.distSqr(body.blockPosition()) + (canScoop(level, body, eye, q, 4.2) ? 0 : 400);
                if (d < best) {
                    best = d;
                    lavaTarget = q.immutable();
                }
            }
            if (lavaTarget == null) return fail(bot, "no lava left in the pool");
            walking = false;
        }
        if (!canScoop(level, body, eye, lavaTarget, 4.2)) {
            if (!walking) {
                BlockPos target = lavaTarget;
                bot.navigator().setGoal(p -> level.getFluidState(p).isEmpty() && !flowingBeside(level, p) && canScoop(level, body,
                        new Vec3(p.getX() + 0.5, p.getY() + 1.62, p.getZ() + 0.5), target, 4.0), target);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s != BotNavigator.Status.MOVING) walking = false;
            if (s == BotNavigator.Status.FAILED && ++walkFails > 4) {
                walkFails = 0;
                lavaTarget = null;
            }
            return Result.RUNNING;
        }
        if (walking) bot.navigator().stop();
        walking = false;
        use(bot, level, Items.BUCKET, Vec3.atCenterOf(lavaTarget).add(0, 0.3, 0));
        if (BotInventory.slotOf(body, Items.LAVA_BUCKET) < 0 && ++walkFails > 6) {
            walkFails = 0;
            lavaTarget = null;
        }
        return Result.RUNNING;
    }

    private Result refillWater(Bot bot, ServerLevel level) {
        sub = new FillBucketTask(FluidTags.WATER);
        return Result.RUNNING;
    }

    private static boolean frameCell(int x, int y) {
        for (int[] c : FRAME) if (c[0] == x && c[1] == y) return true;
        return false;
    }

    private boolean inFrame(BlockPos q) {
        for (int x = 0; x < 4; x++) for (int y = 0; y < 5; y++) if (at(x, y, 0).equals(q)) return true;
        return false;
    }

    // ---- lighting and going through -----------------------------------------------------------

    private Result light(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        BlockPos inside = at(1, 1, 0);
        if (level.getBlockState(inside).is(Blocks.NETHER_PORTAL)) {
            stage = Stage.ENTER;
            walking = false;
            return Result.RUNNING;
        }
        if (!atStand(bot)) return Result.RUNNING;
        // Anything left inside (a scrap of water) first.
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                BlockPos q = at(x, y, 0);
                if (level.getFluidState(q).is(FluidTags.WATER)) {
                    if (level.getFluidState(q).isSource()) use(bot, level, Items.BUCKET, Vec3.atCenterOf(q));
                    return ++tries > 200 ? fail(bot, "water left in the frame") : Result.RUNNING;
                }
                if (!level.getBlockState(q).isAir()) {
                    bot.actions().breakTick(q);
                    return Result.RUNNING;
                }
            }
        }
        int slot = BotInventory.slotOf(body, Items.FLINT_AND_STEEL);
        if (slot < 0) slot = BotInventory.slotOf(body, Items.FIRE_CHARGE);
        if (slot < 0) return fail(bot, "nothing to light it with");
        bot.tools().select(slot);
        BlockPos base = at(1, 0, 0);
        Vec3 hit = Vec3.atCenterOf(base).add(0, 0.5, 0);
        body.lookAt(hit);
        body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, base, false));
        if (++tries > 40) return fail(bot, "the frame does not light (" + frameState(level) + ")");
        return Result.RUNNING;
    }

    private String frameState(ServerLevel level) {
        StringBuilder sb = new StringBuilder();
        for (int[] c : FRAME) sb.append(level.getBlockState(at(c[0], c[1], 0)).is(Blocks.OBSIDIAN) ? 'O' : '.');
        return sb.toString();
    }

    private Result enter(Bot bot, ServerLevel level) {
        BlockPos inside = at(1, 1, 0);
        if (!level.getBlockState(inside).is(Blocks.NETHER_PORTAL)) {
            stage = Stage.LIGHT;
            return Result.RUNNING;
        }
        if (bot.body().blockPosition().equals(inside)) {
            bot.navigator().stop();
            bot.body().stopInputs();
            return Result.RUNNING;
        }
        if (!walking) {
            bot.navigator().goTo(inside);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        // (The path search does not walk into portals: the last step by hand.)
        if (s == BotNavigator.Status.FAILED || bot.body().distanceToSqr(Vec3.atBottomCenterOf(inside)) < 4) {
            bot.body().lookAt(Vec3.atCenterOf(inside));
            bot.body().forward = 0.6f;
        }
        return Result.RUNNING;
    }

    // ---- buckets ------------------------------------------------------------------------------

    static boolean canScoop(ServerLevel level, BotPlayer body, Vec3 eye, BlockPos source, double reach) {
        Vec3 at = Vec3.atCenterOf(source);
        if (eye.distanceTo(at) > reach) return false;
        BlockHitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, body));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(source);
    }

    private static void use(Bot bot, ServerLevel level, Item item, Vec3 at) {
        int slot = BotInventory.slotOf(bot.body(), item);
        if (slot < 0) return;
        bot.tools().select(slot);
        bot.body().lookAt(at);
        bot.body().gameMode.useItem(bot.body(), level, bot.body().getMainHandItem(), InteractionHand.MAIN_HAND);
        bot.body().swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }

    /**
     * Where to look to pour a bucket into {@code spot}: a point on the face of a solid block next
     * to it (the mould behind, a frame block below) that is really in sight; null if none is.
     */
    private static Vec3 aim(Bot bot, ServerLevel level, BlockPos spot) {
        BotPlayer body = bot.body();
        Vec3 eye = body.getEyePosition();
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos n = spot.relative(d);
            if (level.getBlockState(n).getCollisionShape(level, n).isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3 c = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            // (A few points on the face: the middle may be hidden behind a frame block.)
            for (Vec3 off : new Vec3[]{Vec3.ZERO, new Vec3(0.3, 0.3, 0.3), new Vec3(-0.3, 0.3, -0.3), new Vec3(0.3, -0.3, 0.3), new Vec3(-0.3, -0.3, -0.3)}) {
                Vec3 at = c.add(face.getStepX() == 0 ? off.x : 0, face.getStepY() == 0 ? off.y : 0, face.getStepZ() == 0 ? off.z : 0);
                Vec3 to = eye.subtract(at);
                if (to.length() > 4.4) continue;
                if (to.x * face.getStepX() + to.y * face.getStepY() + to.z * face.getStepZ() <= 0.02 * to.length()) continue;
                Vec3 past = at.add(to.normalize().scale(-0.05));
                BlockHitResult hit = level.clip(new ClipContext(eye, past, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
                if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(n) || hit.getDirection() != face) continue;
                return at;
            }
        }
        return null;
    }

    private Vec3 scoopAt;

    /**
     * A point in {@code cell} the empty bucket can be aimed at to take its water back: nothing
     * solid on the way there, not even the frame block {@code soon} about to be set (null: none).
     */
    private static Vec3 scoopPoint(Bot bot, ServerLevel level, BlockPos cell, BlockPos soon) {
        BotPlayer body = bot.body();
        Vec3 eye = body.getEyePosition();
        net.minecraft.world.phys.AABB block = soon == null ? null : new net.minecraft.world.phys.AABB(soon);
        double[] offs = {0, -0.3, 0.3};
        for (double ox : offs) for (double oy : offs) for (double oz : offs) {
            Vec3 at = Vec3.atCenterOf(cell).add(ox, oy, oz);
            if (eye.distanceTo(at) > 4.4) continue;
            BlockHitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
            if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(cell)) continue;
            if (block != null && block.clip(eye, at).isPresent()) continue;
            // (Water already there: the bucket must find this source first.)
            if (!level.getFluidState(cell).isEmpty()) {
                BlockHitResult fluid = level.clip(new ClipContext(eye, at.add(at.subtract(eye).normalize().scale(0.2)), ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, body));
                if (fluid.getType() != HitResult.Type.BLOCK || !fluid.getBlockPos().equals(cell)) continue;
            }
            return at;
        }
        return null;
    }

    /** Pours a bucket into {@code spot} (see {@link #aim}). */
    private static void pour(Bot bot, ServerLevel level, Item bucket, BlockPos spot) {
        int slot = BotInventory.slotOf(bot.body(), bucket);
        Vec3 at = aim(bot, level, spot);
        if (slot < 0 || at == null) return;
        bot.tools().select(slot);
        bot.body().lookAt(at);
        bot.body().gameMode.useItem(bot.body(), level, bot.body().getMainHandItem(), InteractionHand.MAIN_HAND);
        // (The arm moves with it, as anyone watching would expect: the bucket really poured.)
        bot.body().swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }

    /** How many sites it gave up on before the one it cast at. */
    public int sitesGivenUp() {
        return sites;
    }

    @Override
    public String status() {
        return describe() + " [" + stage + (stage == Stage.CAST ? " cell " + cell + " phase " + phase : "") + ", tries " + tries + "]";
    }

    @Override
    public String describe() {
        return "cast a portal at a lava pool and go through";
    }
}
