package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * Walks a bot along the paths {@link BotPathfinder} finds: faces the next node and holds
 * "forward", jumps up steps and out of water, stops to dig what is in the way, and jumps to put a
 * block under itself when pillaring. Long ways are travelled in legs (the search looks ahead a
 * limited distance); when the bot is stuck or knocked off the path it simply searches again.
 */
public final class BotNavigator {
    public enum Status { IDLE, MOVING, ARRIVED, FAILED }

    /** A search on the server thread (goals that look at the world): nodes, and at most this long. */
    // (The node counts are the limit, so the same world gives the same way however busy the
    // machine is; the times only stop a search that would hold up the server.)
    private static final int BUDGET = 40000;
    private static final long SYNC_NANOS = 60_000_000L;
    /** A search on a worker thread (plain position goals): first try, and the longer one if that found nothing. */
    private static final int QUICK_NODES = 8000;
    private static final long QUICK_NANOS = 30_000_000L;
    private static final int ASYNC_NODES = 200_000, ASYNC_NODES_HARD = 600_000;
    private static final long ASYNC_NANOS = 1_200_000_000L, ASYNC_NANOS_HARD = 6_000_000_000L;
    /** Goals further than this (flat) are walked by the large view's way (see {@link BotTerrain}). */
    private static final int GUIDE_FROM = 48;
    /** Chunks around the start a search may read (beyond is unknown: a leg's end, planned on from there). */
    private static final int VIEW_CHUNKS = 7;
    private static final int STUCK_TICKS = 40;
    private static final int MAX_REPLANS = 30;
    /** Searches one after another that found no step forward at all, before giving up. */
    private static final int MAX_NO_WAY = 3;

    /** Path searches off the server thread (as Baritone plans while the player walks). */
    private static final java.util.concurrent.ExecutorService SEARCH = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "bot-path-search");
        t.setDaemon(true);
        return t;
    });

    /** The large view's searches: a thread of their own (a path search may wait on one, never the other way). */
    private static final java.util.concurrent.ExecutorService TERRAIN = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bot-terrain");
        t.setDaemon(true);
        return t;
    });

    private final BotPlayer bot;
    private final BotTools tools;
    private final BotActions actions;

    private Predicate<BlockPos> goal;
    private BlockPos target;
    private List<BotPathfinder.Step> path;
    private BlockPos bankDig;
    private int bankTicks;
    private int index, stuck, replans;
    /** Whether the block being broken is one the navigator is digging through. */
    private boolean digging;
    private int stepTicks;
    private final java.util.Set<Long> avoid = new java.util.HashSet<>();
    private double lastDistance;
    private Status status = Status.IDLE;
    /** The goal only reads positions (no world): its search may run on the worker thread. */
    private boolean pureGoal;
    /** The path does not reach the goal (a leg): the next leg is planned before this one ends. */
    private boolean partial;
    private java.util.concurrent.Future<BotPathfinder.Result> pending;
    private boolean pendingAhead, pendingHard;
    private int generation, pendingGeneration, noWay;
    /** Under water on purpose (sprint-swimming just below the surface): the bot does not hold jump. */
    private boolean diving;
    /** Allow digging and building on the way. */
    public boolean mayBreak = true, mayPillar = true;
    /** The land in the large, for far goals and the planner's walking times. */
    public final BotTerrain terrain = new BotTerrain();
    /** The large view's way to the current far goal, being worked out or done (null: none). */
    private java.util.concurrent.Future<BotTerrain.Field> guideJob;
    private long guideAt;
    private BlockPos guideCenter;
    /** What the large view said the walk would take, and when that was (to learn how long walks really take). */
    private double walkEstimate;
    private int walkEstimateAt;
    /** How near is near enough for the goal (reach), for the search's estimate. */
    private double near;
    /** What was left of a path dropped (stuck, the world changed): the next search keeps to it where it can. */
    private java.util.Set<Long> rest;

    public BotNavigator(BotPlayer bot, BotTools tools, BotActions actions) {
        this.bot = bot;
        this.tools = tools;
        this.actions = actions;
    }

    public Status status() {
        return status;
    }

    /** Go to exactly this block (feet position). */
    public void goTo(BlockPos feet) {
        setGoal(p -> p.equals(feet), feet, true);
    }

    /** The large view's way to the far goal is to be worked out again (somewhere on it got stuck). */
    public void rethinkWay() {
        guideJob = null;
    }

    /** The large view's way from {@code from} to the current goal, as points every {@code every} cells (for the log). */
    public java.util.List<BlockPos> wayPoints(int every) {
        if (guideJob == null || !guideJob.isDone()) return java.util.List.of();
        try {
            return guideJob.get().route(feet(), every);
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /** Go within {@code range} blocks of {@code pos} (to work on it or pick something up). */
    public void goNear(BlockPos pos, double range) {
        double r2 = range * range;
        // Measured from the eyes, which is what reach is about.
        setGoal(p -> Vec3.atCenterOf(p).add(0, 1.12, 0).distanceToSqr(Vec3.atCenterOf(pos)) <= r2, pos, true);
        near = range;
    }

    /**
     * Within reach ({@code range}, from the eyes) of any one of {@code targets}: the search finds
     * the one cheapest to get to (over the river or up the cliff is not near, however close it
     * looks). Which one it was: {@link #reached()} once arrived.
     */
    public void goNearAny(List<BlockPos> targets, double range) {
        if (targets.size() == 1) {
            goNear(targets.get(0), range);
            return;
        }
        double r2 = range * range;
        List<Vec3> centres = targets.stream().map(Vec3::atCenterOf).toList();
        setGoal(p -> {
            Vec3 eye = Vec3.atCenterOf(p).add(0, 1.12, 0);
            for (Vec3 c : centres) if (eye.distanceToSqr(c) <= r2) return true;
            return false;
        }, targets.get(0), true);
        near = range;
        anyOf = List.copyOf(targets);
    }

    /** The places of {@link #goNearAny}, while that is the goal. */
    private List<BlockPos> anyOf;

    /** Of the places of {@link #goNearAny}, the one in reach from where it stands (null if none). */
    public BlockPos reached() {
        if (anyOf == null) return null;
        Vec3 eye = Vec3.atCenterOf(feet()).add(0, 1.12, 0);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos t : anyOf) {
            double d = eye.distanceToSqr(Vec3.atCenterOf(t));
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    /** Stand within {@code range} blocks of {@code pos}, measured at the feet (being there, not reaching it). */
    public void goStandNear(BlockPos pos, double range) {
        double r2 = range * range, dy = Math.max(1, range);
        setGoal(p -> {
            double dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
            return dx * dx + dz * dz <= r2 && Math.abs(p.getY() - pos.getY()) <= dy;
        }, pos, true);
        near = range;
    }

    /** Close enough to an item lying about to pick it up (it may hover or lie on a slab). */
    public void goPickUp(net.minecraft.world.entity.Entity item) {
        double ix = item.getX(), iy = item.getY(), iz = item.getZ();
        setGoal(p -> {
            double dx = p.getX() + 0.5 - ix, dz = p.getZ() + 0.5 - iz;
            return dx * dx + dz * dz <= 1.1 * 1.1 && iy - p.getY() > -0.6 && iy - p.getY() < 1.6;
        }, item.blockPosition(), true);
    }

    /** To any spot {@code goal} accepts (it may look at the world: searched on the server thread). */
    public void setGoal(Predicate<BlockPos> goal, BlockPos steer) {
        setGoal(goal, steer, false);
    }

    /** {@code pure}: the goal only does arithmetic on the position, so the search may run on a worker thread. */
    public void setGoal(Predicate<BlockPos> goal, BlockPos steer, boolean pure) {
        this.goal = goal;
        this.target = steer.immutable();
        this.pureGoal = pure;
        this.path = null;
        this.partial = false;
        this.replans = 0;
        this.noWay = 0;
        this.avoid.clear();
        this.near = 0;
        this.anyOf = null;
        this.rest = null;
        this.guideJob = null;
        this.walkEstimate = 0;
        cancelSearch();
        generation++;
        this.status = Status.MOVING;
        tickedAt = bot.tickCount; // (a walk just set is not a walk left off)
    }

    private void cancelSearch() {
        if (pending != null) pending.cancel(true);
        pending = null;
        pendingAhead = false;
    }

    /** Swimming on purpose with the head under (the bot's own "swim up" is held off meanwhile). */
    public boolean diving() {
        return diving && status == Status.MOVING;
    }

    private Predicate<BlockPos> savedGoal;
    private boolean savedPure;
    private BlockPos savedTarget;
    private BotTask savedFor;

    /**
     * Stops for something that comes in between (a fight, food): the walk of {@code owner} is
     * kept and taken up again by {@link #resumeFor} once that task is back on top.
     */
    public void suspend(BotTask owner) {
        if (status == Status.MOVING && goal != null) {
            savedPure = pureGoal;
            savedGoal = goal;
            savedTarget = target;
            savedFor = owner;
        }
        stop();
    }

    /** {@code task} is on top again: the walk it was on when interrupted goes on. */
    public void resumeFor(BotTask task) {
        if (task != null && task == savedFor && savedGoal != null && status != Status.MOVING) {
            setGoal(savedGoal, savedTarget, savedPure);
        }
        savedGoal = null;
        savedFor = null;
    }

    public void stop() {
        cancelSearch();
        diving = false;
        bankDig = null;
        // Only abandon a dig the navigator itself started; the caller may be mining on its own.
        if (digging) actions.reset();
        digging = false;
        goal = null;
        path = null;
        status = Status.IDLE;
        bot.stopInputs();
    }

    /** The current step, for the status command. */
    public String debug() {
        if (path == null) return "no path";
        if (index >= path.size()) return "path done";
        BotPathfinder.Step st = path.get(index);
        return "step " + index + "/" + path.size() + " to " + st.to().toShortString() + (st.breaks().isEmpty() ? "" : " breaking " + st.breaks())
                + (st.place() == null ? "" : " placing") + " y " + String.format("%.2f", bot.getY()) + " water " + bot.isInWater()
                + " hcol " + bot.horizontalCollision + " jump " + bot.jump + " stuck " + stuck + " bank " + bankDig + "/" + bankTicks + " replans " + replans + " drops " + drops + " (" + dropped + ")" + (guideJob != null ? " guided" : "");
    }

    /** Whether the step being walked leads down (into deeper water, say). */
    public boolean headingDown() {
        if (status != Status.MOVING || path == null || index >= path.size()) return false;
        return path.get(index).to().getY() < feet().getY();
    }

    public BlockPos feet() {
        return BlockPos.containing(bot.getX(), bot.getY() + 0.2, bot.getZ());
    }

    /** The body tick the walk was last carried on in (see {@link Bot}: inputs let go otherwise). */
    public int tickedAt = -1;

    public Status tick() {
        tickedAt = bot.tickCount;
        if (status != Status.MOVING) return status;
        BlockPos feet = feet();
        if (goal.test(feet) && bot.onGround() || goal.test(feet) && bot.isInWater()) {
            bot.stopInputs();
            if (walkEstimate > 0) terrain.walked(bot.tickCount - walkEstimateAt, walkEstimate);
            walkEstimate = 0;
            status = Status.ARRIVED;
            return status;
        }
        pollSearch();
        if (status != Status.MOVING) return status;
        if (path != null && index >= path.size()) {
            // The end of a leg with the next one not there yet (or a whole path walked and the goal
            // not quite met): a search from here.
            path = null;
        }
        if (path == null) {
            if (pending == null && !startSearch(standing(feet), false)) return status;
            holdStill();
            return status;
        }
        // The next leg planned while this one is walked (no stop at the end of each leg).
        if (pureGoal && partial && pending == null && path.size() - index <= 12 && plainEnd()) {
            startSearch(path.get(path.size() - 1).to(), true);
        }
        BotPathfinder.Step step = path.get(index);
        // A step taking far longer than it should (jumping at a wall it cannot get up, pushed back
        // by water again and again): give it up and look for another way, as Baritone does.
        // (Digging a step into the bank is work, not being stuck.)
        if (bankDig == null && ++stepTicks > stepLimit(step) + (bot.isInWater() ? 160 : 0)) {
            avoid.add(step.to().asLong());
            stepTicks = 0;
            dropPath("step too slow");
            bankDig = null;
            bankTicks = 0;
            return status;
        }
        // Dig first.
        for (BlockPos b : step.breaks()) {
            var bstate = bot.level().getBlockState(b);
            // (A pressure plate has no collision, but is very much in the way.)
            if (!bstate.getCollisionShape(bot.level(), b).isEmpty() || bstate.is(net.minecraft.tags.BlockTags.PRESSURE_PLATES)) {
                bot.stopInputs();
                if (!actions.inReach(b)) {
                    return fail("block out of reach");
                }
                // A trap (pressure plate): stand still in the middle of the block first; sliding
                // onto it while breaking it would set it off.
                var bs = bot.level().getBlockState(b);
                if (bs.is(net.minecraft.tags.BlockTags.PRESSURE_PLATES)) {
                    BlockPos here = feet();
                    double cx = here.getX() + 0.5 - bot.getX(), cz = here.getZ() + 0.5 - bot.getZ();
                    var v = bot.getDeltaMovement();
                    if (cx * cx + cz * cz > 0.01 || v.x * v.x + v.z * v.z > 1e-4) {
                        centreOn(here);
                        bot.forward = Math.min(bot.forward, 0.15f);
                        return status;
                    }
                }
                actions.breakTick(b);
                digging = true;
                stuck = 0;
                return status;
            }
        }
        // Bridge: sneak to the edge, set the block under the next spot against this one, walk on.
        if (step.place() != null && (step.place().getX() != feet.getX() || step.place().getZ() != feet.getZ())) {
            BlockPos place = step.place();
            if (bot.level().getBlockState(place).getCollisionShape(bot.level(), place).isEmpty()) {
                bot.sneak = bot.onGround(); // (sneaking in water would only sink it)
                Vec3 edge = new Vec3(place.getX() + 0.5, bot.getY(), place.getZ() + 0.5);
                double ex = edge.x - bot.getX(), ez = edge.z - bot.getZ();
                float yaw = (float) (Mth.atan2(ez, ex) * Mth.RAD_TO_DEG) - 90f;
                bot.setYRot(yaw);
                bot.forward = 0.6f; // sneaking stops at the edge by itself
                if (actions.inReach(place) && !actions.placeThrowaway(place) && ++stuck > STUCK_TICKS) {
                    stuck = 0;
                    dropPath("out of blocks"); // out of blocks: find a way that needs none
                    return status;
                }
                return status;
            }
            bot.sneak = false;
            path.set(index, new BotPathfinder.Step(step.to(), List.of(), null));
            return status;
        }
        // Pillar: jump and put a block where the feet were.
        if (step.place() != null) {
            bot.forward = 0;
            bot.strafe = 0;
            centreOn(step.place());
            bot.jump = true;
            // (Open means air, or water it stood in: a block goes in there as well.)
            boolean open = bot.level().getBlockState(step.place()).getCollisionShape(bot.level(), step.place()).isEmpty();
            // Afloat, swimming up stops with the eyes at the surface, too low to set a block under
            // the feet. Swimming against a wall pops the body out of the water, as it does at a
            // bank: that is high enough.
            if (open && bot.isInWater() && !bot.onGround()) {
                // (A wall anywhere along the body will do, up at the head as well.)
                wall:
                for (double h : new double[] {0.6, 1.2, 1.75}) {
                    BlockPos at = BlockPos.containing(bot.getX(), bot.getY() + h, bot.getZ());
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockPos w = at.relative(d);
                        if (bot.level().getBlockState(w).getCollisionShape(bot.level(), w).isEmpty()) continue;
                        bot.setYRot(d.toYRot());
                        bot.forward = 1f;
                        break wall;
                    }
                }
            }
            // Nothing for the block to go against (the middle of a flooded pit): one set beside
            // it first, against whatever is solid there.
            if (open && bot.isInWater() && !supported(step.place())) {
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos n = step.place().relative(d);
                    if (!bot.level().getBlockState(n).getCollisionShape(bot.level(), n).isEmpty() || !supported(n)) continue;
                    if (bot.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(n))) continue;
                    if (actions.inReach(n)) actions.placeThrowaway(n);
                    break;
                }
                if (!supported(step.place()) && ++stuck > STUCK_TICKS * 2) {
                    stuck = 0;
                    dropPath("pillar unsupported");
                }
                return status;
            }
            if (bot.getY() > step.place().getY() + 1.0 && open) {
                if (!actions.placeThrowaway(step.place())) {
                    dropPath("pillar: out of blocks");
                    return status;
                }
            }
            if (!open && bot.onGround()) advance();
            return status;
        }
        BlockPos to = step.to();
        // Out of the water onto a bank a full block above the water: vanilla's hop only clears it
        // with luck. After a few tries, dig a step into the bank at water height, as players do.
        // Bobbing at the bank: in the water or just above it, against the bank, below the step.
        boolean overWater = bot.isInWater() || !bot.onGround() && !bot.level().getFluidState(feet.below()).isEmpty();
        // (Only for a plain hop out; a step that digs its way already has the bank in hand.)
        if (overWater && bot.horizontalCollision && to.getY() > bot.getY() && step.breaks().isEmpty()) bankTicks++;
        if (bankDig == null && bankTicks > 40) {
            BlockPos bank = to.below();
            if (!bot.level().getBlockState(bank).getCollisionShape(bot.level(), bank).isEmpty()
                    && !bot.level().getBlockState(bank.below()).getCollisionShape(bot.level(), bank.below()).isEmpty()
                    && actions.inReach(bank) && bot.level().getBlockState(bank).getDestroySpeed(bot.level(), bank) >= 0) {
                bankDig = bank;
            }
        }
        if (bankDig != null) {
            bot.stopInputs();
            bot.jump = true; // stay up at the surface while digging
            digging = true;
            if (!actions.inReach(bankDig) || actions.breakTick(bankDig)) {
                path.set(index, new BotPathfinder.Step(bankDig, List.of(), null));
                bankDig = null;
                bankTicks = 0;
                stuck = 0;
            }
            return status;
        }
        // Sand or gravel still coming down where it is going (a column dug under): wait till it
        // has landed, as Baritone does - not into the gap under it.
        if (!bot.level().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(to).inflate(1, 0, 1).expandTowards(0, 4, 0)).isEmpty()) {
            bot.stopInputs();
            stuck = 0;
            return status;
        }
        // The next few steps still as planned (Baritone's look-ahead): lava run across the way,
        // a block fallen in - a new way now, not on walking up to it.
        if (bot.tickCount % 5 == 0) {
            for (int i = index + 1; i < Math.min(path.size(), index + 6); i++) {
                BotPathfinder.Step next = path.get(i);
                if (next.leap()) break;
                if (!stillValid(next, path.get(i - 1).to())) {
                    dropPath("ahead " + next.to().toShortString());
                    return status;
                }
            }
        }
        if (step.leap()) return leap(to, feet);
        // The world still as planned for this step? (A block put or broken since, water run in.)
        if (!stillValid(step, feet)) {
            dropPath("invalid " + step.to().toShortString());
            holdStill();
            return status;
        }
        // Running straight across: past a step it is level with, the steps up to there are done.
        int far = straightRun(feet);
        // (The run cuts corners: not every step's block is stood on. The step nearest to where
        // it is now is where it has got to.)
        {
            int nearest = index;
            double nd = Double.MAX_VALUE;
            // (Over the next few steps of plain walking on about its level: a jump may carry it
            // past the end of the run it set out on.)
            int upTo = far;
            for (int k = far + 1; k < Math.min(path.size(), index + 7); k++) {
                BotPathfinder.Step st = path.get(k);
                if (!st.breaks().isEmpty() || st.place() != null || st.leap() || Math.abs(st.to().getY() - feet.getY()) > 1) break;
                // (Not round a corner: only one it could walk straight to from here.)
                if (lineClear(bot.getX(), bot.getZ(), st.to().getX() + 0.5, st.to().getZ() + 0.5, st.to().getY()) == 0) break;
                upTo = k;
            }
            for (int k = index; k <= upTo; k++) {
                BlockPos p = path.get(k).to();
                double ex = p.getX() + 0.5 - bot.getX(), ez = p.getZ() + 0.5 - bot.getZ(), e = ex * ex + ez * ez;
                if (e < nd) {
                    nd = e;
                    nearest = k;
                }
            }
            if (nearest > index) {
                while (index < nearest) advance();
                return status;
            }
        }
        // Aimed at the furthest of the next steps it can run to in a straight line (no zigzag
        // from block middle to block middle on a slanting way).
        BlockPos aimAt = path.get(far).to();
        Vec3 aim = new Vec3(aimAt.getX() + 0.5, aimAt.getY(), aimAt.getZ() + 0.5);
        double dx = aim.x - bot.getX(), dz = aim.z - bot.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (far > index) {
            // (The current step itself, for arriving: where it stands is level with the run.)
            double sx = to.getX() + 0.5 - bot.getX(), sz = to.getZ() + 0.5 - bot.getZ();
            if (Math.sqrt(sx * sx + sz * sz) < 0.6) {
                advance();
                return status;
            }
        }
        BotPathfinder.Step next = index + 1 < path.size() ? path.get(index + 1) : null;
        // Straight on after this one: no need to come to the middle of the block first.
        boolean straightOn = next != null && next.breaks().isEmpty() && next.place() == null && !next.leap()
                && next.to().getY() == to.getY() && to.getY() == feet.getY()
                && Integer.signum(next.to().getX() - to.getX()) == Integer.signum(to.getX() - feet.getX())
                && Integer.signum(next.to().getZ() - to.getZ()) == Integer.signum(to.getZ() - feet.getZ());
        // (Swimming under the surface along a way laid on top of it: over the step is at the step.)
        boolean swumOver = bot.isInWater() && flat < 0.6 && bot.level().getFluidState(to).is(net.minecraft.tags.FluidTags.WATER) && bot.getY() <= to.getY() + 0.7;
        if (flat < 0.3 && Math.abs(bot.getY() - to.getY()) < 0.7 || swumOver || feet.equals(to) && (flat < 0.45 || straightOn && flat < 0.9)) {
            advance();
            return status;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setXRot(10f);
        bot.forward = flat > 0.15 ? 1f : 0f;
        bot.strafe = 0;
        boolean careful = false;
        // The next step breaks a pressure plate: creep up, so as not to slide onto it.
        if (next != null && next.breaks().stream()
                .anyMatch(b -> bot.level().getBlockState(b).is(net.minecraft.tags.BlockTags.PRESSURE_PLATES))) {
            bot.forward = flat > 0.1 ? 0.25f : 0f;
            careful = true;
        }
        boolean up = to.getY() > bot.getY() + 0.4;
        // Sprinting wherever a player would: on the flat, up steps and down drops; not into a
        // step that needs care (digging, building) right after.
        boolean nextCareful = next != null && (next.place() != null || !next.breaks().isEmpty());
        bot.sprintNow = !careful && !(nextCareful && flat < 1.2);
        diving = false;
        if (bot.isInLava()) {
            // Out of lava: up and on, as fast as it goes there.
            bot.jump = true;
            bot.sprintNow = false;
        } else if (bot.isInWater()) {
            swim(to, feet, flat, up);
        } else {
            // Up a step: jump once lined up and close (as Baritone does), never into a ceiling.
            bot.jump = up && flat < 1.2 && headRoom(feet);
            // Walking into something on the flat: a lip (a slab, a path block), hopped over.
            if (bot.onGround() && bot.horizontalCollision && !up) bot.jump = true;
            // A long straight run, open overhead: sprint-jumping, as players cover ground (a good
            // fifth faster than sprinting). Not with little food left, and lined up first.
            // (Never by a drop: a jump that comes down a little aside goes over the edge - the
            // Nether's ledges over the lava sea.)
            if (far >= index + 3 && flat > 3.5 && bot.sprintNow && bot.onGround() && !up && !careful && !byDrop(feet)
                    && bot.getFoodData().getFoodLevel() > 6 && runningAlong(dx, dz) && jumpRoom) bot.jump = true;
        }
        // Stuck: no progress for a while.
        double d = flat + Math.abs(bot.getY() - to.getY());
        if (d < lastDistance - 0.02) stuck = 0;
        else if (++stuck > STUCK_TICKS) {
            stuck = 0;
            avoid.add(to.asLong());
            dropPath("stuck");
        }
        lastDistance = d;
        // Knocked off the path.
        if (feet.distManhattan(to) > (bot.isInWater() ? 6 : 4)) dropPath("off path");
        return status;
    }

    /**
     * In the water: deep water is crossed sprint-swimming (under the surface at first, then along
     * it, breathing), not bobbing; out onto a bank or up a step, swimming up.
     */
    private void swim(BlockPos to, BlockPos feet, double flat, boolean up) {
        var level = bot.level();
        // (Sprinting with the head above the water, the body neither sinks nor swims: it glides
        // along the top as if walking on the water. Only under the surface is it a swim.)
        boolean under = bot.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        bot.sprintNow = false;
        bot.sinkInWater = false;
        boolean deep = level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                || bot.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        // (A step "up" to a water cell is only the path keeping to the top of the water: not a
        // bank to climb out onto.)
        boolean outOnto = !level.getFluidState(to).is(net.minecraft.tags.FluidTags.WATER);
        up = up && outOnto;
        // Down to where the way goes (a chest on the sea floor, the far side of a sunken ledge):
        // dived to, head first, sprint-swimming - not floating on top kicking.
        double drop = bot.getY() - to.getY();
        if (!outOnto && drop > 0.4 && bot.getAirSupply() > bot.getMaxAirSupply() / 4) {
            diving = true;
            bot.sprintNow = under;
            bot.sinkInWater = !under;
            bot.jump = false;
            bot.forward = 1f;
            float pitch = (float) Mth.clamp(Math.toDegrees(Math.atan2(drop, Math.max(flat, 0.3))), 20, 85);
            bot.setXRot(pitch);
            return;
        }
        // (The way going on through the water a few steps more: a crossing, swum - not a step
        // or two to the bank, bobbed.)
        int wet = 0;
        for (int k = index; k < Math.min(path.size(), index + 5); k++) {
            if (!level.getFluidState(path.get(k).to()).is(net.minecraft.tags.FluidTags.WATER)) break;
            wet++;
        }
        if (deep && !outOnto && (flat > 1.2 || wet >= 4) && bot.getAirSupply() > bot.getMaxAirSupply() / 3 && openAbove(feet) && openAbove(to)) {
            diving = true;
            bot.sprintNow = under;
            // (Head under first: pressed down, as a player ducks under to swim.)
            bot.sinkInWater = !under;
            bot.jump = false;
            if (bot.isSwimming()) {
                // Along the surface (the eyes come out, it breathes) or down to where the path goes.
                double dy = to.getY() + 0.2 - bot.getY();
                float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(dy, Math.max(flat, 0.5))), -30, 45);
                if (dy >= -0.3) pitch = Math.min(pitch, -4f);
                bot.setXRot(pitch);
            } else {
                // Not swimming yet: head under first (sprinting with the eyes in the water starts it).
                bot.setXRot(35f);
            }
            return;
        }
        bot.jump = up || to.getY() >= bot.getY() - 0.2 || bot.horizontalCollision;
    }

    /**
     * The water here is open to the air (no ice or rock over it within a few blocks up): safe to
     * swim under, it can come up for air anywhere.
     */
    private boolean openAbove(BlockPos p) {
        var level = bot.level();
        BlockPos q = p;
        for (int i = 0; i < 6 && level.getFluidState(q).is(net.minecraft.tags.FluidTags.WATER); i++) q = q.above();
        return level.getFluidState(q).isEmpty() && level.getBlockState(q).getCollisionShape(level, q).isEmpty();
    }

    /** Whether the straight run found by {@link #straightRun} has head room for jumping all along. */
    private boolean jumpRoom;

    /**
     * The furthest of the next few steps it can run to in a straight line from where it is: all
     * of them plain walking on the level it stands on, and the line itself (the body's width of
     * it) over ground, with room for the body, nothing harmful, no water. The current step if none.
     */
    private int straightRun(BlockPos feet) {
        // (In the air, mid-jump: the run found on the ground still holds - aiming back at the
        // step it has flown past would turn it round.)
        if (!bot.isInWater() && !bot.onGround() && runTo > index && runTo < path.size() && path.get(runTo).to().getY() == path.get(index).to().getY()) return runTo;
        jumpRoom = false;
        runTo = index;
        if (bot.isInWater() || !bot.onGround()) return index;
        int best = index;
        boolean room = false;
        for (int k = index; k < Math.min(path.size(), index + 7); k++) {
            BotPathfinder.Step st = path.get(k);
            if (!st.breaks().isEmpty() || st.place() != null || st.leap() || st.to().getY() != feet.getY()) break;
            if (k == index) continue;
            int r = lineClear(bot.getX(), bot.getZ(), st.to().getX() + 0.5, st.to().getZ() + 0.5, feet.getY());
            if (r == 0) break;
            best = k;
            room = r == 2;
        }
        jumpRoom = room;
        runTo = best;
        return best;
    }

    /** The end of the straight run last found on the ground. */
    private int runTo;

    /** 0: the line cannot be run; 1: it can; 2: it can, jumping too (room three blocks up). */
    private int lineClear(double x0, double z0, double x1, double z1, int y) {
        var level = bot.level();
        double dx = x1 - x0, dz = z1 - z0, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3) return 1;
        double ux = dx / len, uz = dz / len;
        boolean jump = true;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (double t = 0; t <= len; t += 0.25) {
            for (double side : new double[] {-0.31, 0, 0.31}) {
                double px = x0 + ux * t - uz * side, pz = z0 + uz * t + ux * side;
                int bx = Mth.floor(px), bz = Mth.floor(pz);
                for (int h = 0; h <= 2; h++) {
                    var st = level.getBlockState(m.set(bx, y + h, bz));
                    boolean open = st.getCollisionShape(level, m).isEmpty() && st.getFluidState().isEmpty()
                            && !st.is(net.minecraft.tags.BlockTags.FIRE) && !st.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                            && !st.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) && !st.is(net.minecraft.world.level.block.Blocks.COBWEB)
                            && !st.is(net.minecraft.tags.BlockTags.PRESSURE_PLATES);
                    if (!open) {
                        if (h < 2) return 0;
                        jump = false;
                    }
                }
                var ground = level.getBlockState(m.set(bx, y - 1, bz));
                if (ground.getCollisionShape(level, m).isEmpty() || ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        || ground.is(net.minecraft.world.level.block.Blocks.CACTUS)) return 0;
            }
        }
        return jump ? 2 : 1;
    }

    /** Already running along (dx, dz) at speed: lined up for a jump that lands on the line. */
    private boolean runningAlong(double dx, double dz) {
        var v = bot.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z), len = Math.sqrt(dx * dx + dz * dz);
        if (speed < 0.1 || len < 1e-3) return false; // (on the ground: what friction leaves of it)
        return (v.x * dx + v.z * dz) / (speed * len) > 0.97;
    }

    /** Room over the head to jump: nothing solid two blocks above the feet. */
    private boolean headRoom(BlockPos feet) {
        BlockPos h = feet.above(2);
        return bot.level().getBlockState(h).getCollisionShape(bot.level(), h).isEmpty();
    }

    /**
     * Whether a plain step can still be walked as planned: room for the body there, something to
     * stand on (or water), no lava; going up, room over the head to jump. A step that digs or
     * builds checks its own blocks as it goes.
     */
    private boolean stillValid(BotPathfinder.Step step, BlockPos feet) {
        if (!step.breaks().isEmpty() || step.place() != null) return true;
        var level = bot.level();
        BlockPos to = step.to();
        for (BlockPos c : new BlockPos[]{to, to.above()}) {
            var st = level.getBlockState(c);
            // (Lava is no place to step into - unless it is in it already and getting out.)
            if (!st.getCollisionShape(level, c).isEmpty() || st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && !bot.isInLava()) return false;
        }
        boolean floor = !level.getBlockState(to.below()).getCollisionShape(level, to.below()).isEmpty()
                || !level.getFluidState(to).isEmpty() || !level.getFluidState(to.below()).isEmpty();
        // (A drop lands further down: the floor is checked there, not right under the edge.)
        if (!floor && to.getY() >= feet.getY()) return false;
        if (to.getY() > feet.getY() && !bot.isInWater() && !headRoom(feet)) return false;
        return true;
    }

    /** Ticks a step may take: its digging plus a generous allowance for the movement itself. */
    private int stepLimit(BotPathfinder.Step step) {
        int limit = 80;
        for (BlockPos b : step.breaks()) limit += (int) Math.min(1200, tools.breakTicks(bot.level().getBlockState(b)) * 6) + 20;
        if (step.place() != null) limit += 60;
        return limit;
    }

    /**
     * A leap over a gap: run at it sprinting, jump at the very edge, keep pushing forward in the
     * air. Fallen short (below the landing): look for another way from down there.
     */
    private Status leap(BlockPos to, BlockPos feet) {
        double dx = to.getX() + 0.5 - bot.getX(), dz = to.getZ() + 0.5 - bot.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (bot.onGround() && feet.equals(to) && flat < 0.6 || bot.onGround() && flat < 0.4 && Math.abs(bot.getY() - to.getY()) < 0.6) {
            bot.sprintNow = false;
            advance();
            return status;
        }
        if (bot.getY() < to.getY() - 1.2) {
            bot.sprintNow = false;
            dropPath("fell short"); // fell in
            return status;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.forward = 1f;
        bot.strafe = 0;
        bot.sprintNow = true;
        // At the edge: the ground a little ahead is gone.
        double ux = dx / Math.max(flat, 1e-3), uz = dz / Math.max(flat, 1e-3);
        BlockPos ahead = BlockPos.containing(bot.getX() + ux * 0.45, bot.getY() - 0.5, bot.getZ() + uz * 0.45);
        bot.jump = bot.onGround() && bot.level().getBlockState(ahead).getCollisionShape(bot.level(), ahead).isEmpty();
        return status;
    }

    private void advance() {
        stepTicks = 0;
        index++;
        bankTicks = 0;
        stuck = 0;
        lastDistance = Double.MAX_VALUE;
    }

    /** Whether a block set at {@code p} has a solid face to go against. */
    private boolean supported(BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = p.relative(d);
            if (!bot.level().getBlockState(n).getCollisionShape(bot.level(), n).isEmpty()) return true;
        }
        return false;
    }

    private void centreOn(BlockPos p) {
        double dx = p.getX() + 0.5 - bot.getX(), dz = p.getZ() + 0.5 - bot.getZ();
        if (dx * dx + dz * dz > 0.04) {
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            bot.setYRot(yaw);
            bot.forward = 0.3f;
        }
    }

    /**
     * Where the bot really stands: on the edge of a block it is over air by its middle; then the
     * neighbouring spot whose ground holds it (else the path search finds no moves from "mid-air").
     */
    private BlockPos standing(BlockPos feet) {
        var level = bot.level();
        // Bobbing up out of water: it is in the water still, as far as moving on goes.
        if (!bot.onGround() && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.below()).is(net.minecraft.tags.FluidTags.WATER)) return feet.below();
        if (!bot.onGround() || !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) return feet;
        var box = bot.getBoundingBox();
        BlockPos best = feet;
        double bestD = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = feet.offset(dx, 0, dz);
                if (level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) continue;
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
                // (Only a block the body overlaps.)
                if (box.maxX <= p.getX() || box.minX >= p.getX() + 1 || box.maxZ <= p.getZ() || box.minZ >= p.getZ() + 1) continue;
                double d = Vec3.atBottomCenterOf(p).distanceToSqr(bot.position());
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    /** A drop of more than three (or lava) beside the feet, to any side. */
    private boolean byDrop(BlockPos feet) {
        var level = bot.level();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos q = feet.offset(dx, 0, dz);
                if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) continue;
                boolean ground = false;
                for (int i = 1; i <= 4 && !ground; i++) {
                    BlockPos r = q.below(i);
                    if (level.getFluidState(r).is(net.minecraft.tags.FluidTags.LAVA)) break;
                    if (!level.getBlockState(r).getCollisionShape(level, r).isEmpty() || !level.getFluidState(r).isEmpty()) ground = true;
                }
                if (!ground) return true;
            }
        }
        return false;
    }

    /** Why the last path was dropped, and how often paths were (for the status line). */
    private String dropped = "-";
    private int drops;
    /** Paths dropped, by reason, and ticks stood waiting for a search (for the benchmarks). */
    public final java.util.Map<String, Integer> dropReasons = new java.util.TreeMap<>();
    public int waitTicks;

    /**
     * The guard has kept the bot from the next steps for a second (they lead into lava, over a
     * drop that hurts): that step left out, and another way looked for.
     */
    public void guardStop() {
        if (path != null && index < path.size()) avoid.add(path.get(index).to().asLong());
        stuck = 0;
        dropPath("guard");
    }

    /**
     * The monsters about (Baritone's mob avoidance): ways near them cost more. Not the ones near
     * where it is going - that one it means to reach (a hunt).
     */
    private java.util.List<BlockPos> monstersAbout(ServerLevel level, BlockPos from) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        BlockPos dest = target;
        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(from).inflate(64, 24, 64),
                m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy)) {
            if (dest != null && m.blockPosition().distSqr(dest) < 12 * 12) continue;
            out.add(m.blockPosition());
            if (out.size() >= 32) break;
        }
        return out;
    }

    /** Mob spawners (dungeons, mineshafts, trial chambers) in the chunks about. */
    private static java.util.List<BlockPos> spawnersAbout(ServerLevel level, BlockPos from) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        int cx = from.getX() >> 4, cz = from.getZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                var chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values()) {
                    if (be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity
                            || be instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity) out.add(be.getBlockPos());
                }
            }
        }
        return out;
    }

    /** Drops the path (the world turned out different, stuck): a new search from where it stands. */
    private void dropPath(String why) {
        if (path != null && index < path.size()) {
            rest = new java.util.HashSet<>();
            for (int i = index; i < path.size(); i++) rest.add(path.get(i).to().asLong());
        }
        dropped = why;
        drops++;
        // (By reason, for the navigation benchmark: the coordinates cut off.)
        dropReasons.merge(why.split(" ")[0], 1, Integer::sum);
        path = null;
        partial = false;
        cancelSearch();
    }

    /** Waiting for a search: standing still (in water keeping the head up). */
    private void holdStill() {
        waitTicks++;
        bot.stopInputs();
        if (bot.isInWater()) bot.jump = true;
        diving = false;
    }

    /** The path's last steps need nothing placed or broken (a leg can be planned on from its end). */
    private boolean plainEnd() {
        for (int i = Math.max(index, path.size() - 3); i < path.size(); i++) {
            BotPathfinder.Step s = path.get(i);
            if (s.place() != null || !s.breaks().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Starts a search from {@code from} ({@code ahead}: from the end of the path being walked, for
     * the next leg). Position goals are searched on a worker thread; goals that look at the world
     * right here, within a time limit. False when it has given up.
     */
    private boolean startSearch(BlockPos from, boolean ahead) {
        if (!ahead && ++replans > MAX_REPLANS) {
            fail("no way found (" + replans + " tries)");
            return false;
        }
        ServerLevel level = (ServerLevel) bot.level();
        BotPathfinder.WorldView view = BotPathfinder.WorldView.capture(level, from, VIEW_CHUNKS);
        BotPathfinder.Abilities abilities = new BotPathfinder.Abilities(mayBreak, mayPillar && actions.hasThrowaway(),
                tools.snapshot(), bot.getFoodData().getFoodLevel() > 6, BotActions.buildingBlocks(bot),
                bot.level().dimension() == net.minecraft.world.level.Level.NETHER);
        // (Searching again from where it stands: the rest of the way it was on is kept to,
        // where it still goes - no swinging between two ways that cost about the same.)
        java.util.Set<Long> favoured = new java.util.HashSet<>();
        if (!ahead && path != null) for (int i = index; i < path.size(); i++) favoured.add(path.get(i).to().asLong());
        else if (!ahead && rest != null) favoured.addAll(rest);
        if (!ahead) for (long a : avoid) favoured.remove(a);
        BotPathfinder finder = new BotPathfinder(view, abilities, new java.util.HashSet<>(avoid)).favouring(favoured).near(near).toAnyOf(anyOf);
        finder.avoiding(monstersAbout(level, from), spawnersAbout(level, from));
        finder.fromUnderWater(bot.isEyeInFluid(net.minecraft.tags.FluidTags.WATER));
        var guideFuture = guide(level, from);
        if (guideFuture != null && guideFuture.isDone()) finder.guided(guideOf(guideFuture));
        Predicate<BlockPos> g = goal;
        BlockPos t = target;
        boolean hard = noWay > 0;
        if (!pureGoal) {
            apply(finder.search(from, g, t, BUDGET * (hard ? 3 : 1), SYNC_NANOS * (hard ? 3 : 1)), ahead, from, finder);
            return status == Status.MOVING;
        }
        // First a quick look right here (moving at once, as a player does not stand and think);
        // the long search plans on from the end of that, while it walks.
        if (!ahead && !hard) {
            BotPathfinder.Result quick = finder.search(from, g, t, QUICK_NODES, QUICK_NANOS);
            if (quick != null && (quick.complete() || quick.steps().get(quick.steps().size() - 1).to().distSqr(from) >= 9)) {
                apply(quick, false, from, finder);
                return true;
            }
        }
        pendingAhead = ahead;
        pendingGeneration = generation;
        pendingFrom = from;
        pendingFinder = finder;
        pending = SEARCH.submit(() -> {
            // (The large view first, if it is still being worked out: a few milliseconds.)
            if (guideFuture != null) finder.guided(guideOf(guideFuture));
            return finder.search(from, g, t, hard ? ASYNC_NODES_HARD : ASYNC_NODES, hard ? ASYNC_NANOS_HARD : ASYNC_NANOS);
        });
        return true;
    }

    /**
     * The large view's way to a far goal (on the surface, not under a roof), started on the
     * worker thread if not there yet or old (it has gone far since): null for near goals.
     */
    private java.util.concurrent.Future<BotTerrain.Field> guide(ServerLevel level, BlockPos from) {
        if (!pureGoal || !BotTerrain.usable(level)) return null;
        double dx = target.getX() - from.getX(), dz = target.getZ() - from.getZ();
        if (dx * dx + dz * dz < GUIDE_FROM * GUIDE_FROM) return guideJob;
        long now = level.getGameTime();
        if (guideJob == null || now - guideAt > 600 && guideCenter.distSqr(from) > 64 * 64) {
            BotTerrain.Grid grid = terrain.grid(level, from, target, BotWorld.viewChunks(level));
            BlockPos goalAt = target;
            guideJob = TERRAIN.submit(() -> grid.field(goalAt, true));
            guideAt = now;
            guideCenter = from;
        }
        if (walkEstimate == 0 && guideJob.isDone()) {
            BotTerrain.Field f = guideOf(guideJob);
            double t = f == null ? -1 : f.ticks(from.getX(), from.getZ());
            if (t > 0) {
                walkEstimate = t;
                walkEstimateAt = bot.tickCount;
            }
        }
        return guideJob;
    }

    private static BotTerrain.Field guideOf(java.util.concurrent.Future<BotTerrain.Field> f) {
        try {
            return f.get();
        } catch (Exception e) {
            return null;
        }
    }

    private BlockPos pendingFrom;
    private BotPathfinder pendingFinder;

    /** A finished search on the worker thread: taken up. */
    private void pollSearch() {
        if (pending == null || !pending.isDone()) return;
        BotPathfinder.Result r;
        try {
            r = pending.get();
        } catch (Exception e) {
            BotManager.LOG.warn("[Bot] path search failed", e);
            r = null;
        }
        boolean ahead = pendingAhead;
        pending = null;
        pendingAhead = false;
        if (pendingGeneration != generation) return; // (for a goal since replaced)
        apply(r, ahead, pendingFrom, pendingFinder);
    }

    private void apply(BotPathfinder.Result r, boolean ahead, BlockPos from, BotPathfinder finder) {
        if (r == null || r.steps().isEmpty()) {
            // The next leg not found from the end of this one: on to the end, then from there.
            if (ahead) {
                partial = false;
                return;
            }
            if (++noWay > MAX_NO_WAY) {
                fail("no way found from " + from.toShortString() + " (" + bot.level().getBlockState(from).getBlock().getName().getString()
                        + " on " + bot.level().getBlockState(from.below()).getBlock().getName().getString() + ", " + finder.debugMoves(from) + ")");
                return;
            }
            // (Another try: a longer search, and the spots that failed before allowed again.)
            if (noWay >= 2) avoid.clear();
            return;
        }
        noWay = 0;
        if (ahead && path != null && path.get(path.size() - 1).to().equals(from)) {
            path.addAll(r.steps());
        } else {
            path = new java.util.ArrayList<>(r.steps());
            index = 0;
            runTo = 0;
            stepTicks = 0;
            stuck = 0;
            lastDistance = Double.MAX_VALUE;
        }
        partial = !r.complete();
    }

    /** Searches that found no way at all, since the bot joined (for the benchmarks). */
    public int failures;

    private Status fail(String why) {
        failures++;
        // (In the log; the chat only hears of it from the task that gives up.)
        BotManager.LOG.info("[Bot] {}: navigation failed: {}", bot.getGameProfile().name(), why);
        cancelSearch();
        bot.stopInputs();
        actions.reset();
        path = null;
        status = Status.FAILED;
        return status;
    }
}
