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
    private static final int BUDGET = 40000;
    private static final long SYNC_NANOS = 25_000_000L;
    /** A search on a worker thread (plain position goals): first try, and the longer one if that found nothing. */
    private static final int QUICK_NODES = 8000;
    private static final long QUICK_NANOS = 8_000_000L;
    private static final int ASYNC_NODES = 200_000, ASYNC_NODES_HARD = 600_000;
    private static final long ASYNC_NANOS = 300_000_000L, ASYNC_NANOS_HARD = 1_500_000_000L;
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

    /** Go within {@code range} blocks of {@code pos} (to work on it or pick something up). */
    public void goNear(BlockPos pos, double range) {
        double r2 = range * range;
        // Measured from the eyes, which is what reach is about.
        setGoal(p -> Vec3.atCenterOf(p).add(0, 1.12, 0).distanceToSqr(Vec3.atCenterOf(pos)) <= r2, pos, true);
    }

    /** Stand within {@code range} blocks of {@code pos}, measured at the feet (being there, not reaching it). */
    public void goStandNear(BlockPos pos, double range) {
        double r2 = range * range, dy = Math.max(1, range);
        setGoal(p -> {
            double dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
            return dx * dx + dz * dz <= r2 && Math.abs(p.getY() - pos.getY()) <= dy;
        }, pos, true);
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
        cancelSearch();
        generation++;
        this.status = Status.MOVING;
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
                + " hcol " + bot.horizontalCollision + " jump " + bot.jump + " stuck " + stuck + " bank " + bankDig + "/" + bankTicks + " replans " + replans;
    }

    /** Whether the step being walked leads down (into deeper water, say). */
    public boolean headingDown() {
        if (status != Status.MOVING || path == null || index >= path.size()) return false;
        return path.get(index).to().getY() < feet().getY();
    }

    public BlockPos feet() {
        return BlockPos.containing(bot.getX(), bot.getY() + 0.2, bot.getZ());
    }

    public Status tick() {
        if (status != Status.MOVING) return status;
        BlockPos feet = feet();
        if (goal.test(feet) && bot.onGround() || goal.test(feet) && bot.isInWater()) {
            bot.stopInputs();
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
            dropPath();
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
                    dropPath(); // out of blocks: find a way that needs none
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
                    dropPath();
                }
                return status;
            }
            if (bot.getY() > step.place().getY() + 1.0 && open) {
                if (!actions.placeThrowaway(step.place())) {
                    dropPath(); // out of blocks: find a way that needs none
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
        if (step.leap()) return leap(to, feet);
        // The world still as planned for this step? (A block put or broken since, water run in.)
        if (!stillValid(step, feet)) {
            dropPath();
            holdStill();
            return status;
        }
        Vec3 aim = new Vec3(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        double dx = aim.x - bot.getX(), dz = aim.z - bot.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        BotPathfinder.Step next = index + 1 < path.size() ? path.get(index + 1) : null;
        // Straight on after this one: no need to come to the middle of the block first.
        boolean straightOn = next != null && next.breaks().isEmpty() && next.place() == null && !next.leap()
                && next.to().getY() == to.getY() && to.getY() == feet.getY()
                && Integer.signum(next.to().getX() - to.getX()) == Integer.signum(to.getX() - feet.getX())
                && Integer.signum(next.to().getZ() - to.getZ()) == Integer.signum(to.getZ() - feet.getZ());
        if (flat < 0.3 && Math.abs(bot.getY() - to.getY()) < 0.7 || feet.equals(to) && (flat < 0.45 || straightOn && flat < 0.9)) {
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
        if (bot.isInWater()) {
            swim(to, feet, flat, up);
        } else {
            // Up a step: jump once lined up and close (as Baritone does), never into a ceiling.
            bot.jump = up && flat < 1.2 && headRoom(feet);
            // Walking into something on the flat: a lip (a slab, a path block), hopped over.
            if (bot.onGround() && bot.horizontalCollision && !up) bot.jump = true;
        }
        // Stuck: no progress for a while.
        double d = flat + Math.abs(bot.getY() - to.getY());
        if (d < lastDistance - 0.02) stuck = 0;
        else if (++stuck > STUCK_TICKS) {
            stuck = 0;
            avoid.add(to.asLong());
            dropPath();
        }
        lastDistance = d;
        // Knocked off the path.
        if (feet.distManhattan(to) > 4) dropPath();
        return status;
    }

    /**
     * In the water: deep water is crossed sprint-swimming (under the surface at first, then along
     * it, breathing), not bobbing; out onto a bank or up a step, swimming up.
     */
    private void swim(BlockPos to, BlockPos feet, double flat, boolean up) {
        var level = bot.level();
        boolean deep = level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                || bot.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        boolean outOnto = up || !level.getFluidState(to).is(net.minecraft.tags.FluidTags.WATER);
        if (deep && !outOnto && flat > 1.2 && bot.getAirSupply() > bot.getMaxAirSupply() / 3) {
            diving = true;
            bot.sprintNow = true;
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
            if (!st.getCollisionShape(level, c).isEmpty() || st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return false;
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
            dropPath(); // fell in
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

    /** Drops the path (the world turned out different, stuck): a new search from where it stands. */
    private void dropPath() {
        path = null;
        partial = false;
        cancelSearch();
    }

    /** Waiting for a search: standing still (in water keeping the head up). */
    private void holdStill() {
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
                tools.snapshot(), bot.getFoodData().getFoodLevel() > 6);
        java.util.Set<Long> favoured = new java.util.HashSet<>();
        if (!ahead && path != null) for (int i = index; i < path.size(); i++) favoured.add(path.get(i).to().asLong());
        BotPathfinder finder = new BotPathfinder(view, abilities, new java.util.HashSet<>(avoid)).favouring(favoured);
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
        pending = SEARCH.submit(() -> finder.search(from, g, t, hard ? ASYNC_NODES_HARD : ASYNC_NODES, hard ? ASYNC_NANOS_HARD : ASYNC_NANOS));
        return true;
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
