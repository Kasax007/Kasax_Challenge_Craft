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

    private static final int BUDGET = 5000;
    private static final int STUCK_TICKS = 50;
    private static final int MAX_REPLANS = 12;

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
        setGoal(p -> p.equals(feet), feet);
    }

    /** Go within {@code range} blocks of {@code pos} (to work on it or pick something up). */
    public void goNear(BlockPos pos, double range) {
        double r2 = range * range;
        // Measured from the eyes, which is what reach is about.
        setGoal(p -> Vec3.atCenterOf(p).add(0, 1.12, 0).distanceToSqr(Vec3.atCenterOf(pos)) <= r2, pos);
    }

    /** Stand within {@code range} blocks of {@code pos}, measured at the feet (being there, not reaching it). */
    public void goStandNear(BlockPos pos, double range) {
        double r2 = range * range, dy = Math.max(1, range);
        setGoal(p -> {
            double dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
            return dx * dx + dz * dz <= r2 && Math.abs(p.getY() - pos.getY()) <= dy;
        }, pos);
    }

    /** Close enough to an item lying about to pick it up (it may hover or lie on a slab). */
    public void goPickUp(net.minecraft.world.entity.Entity item) {
        double ix = item.getX(), iy = item.getY(), iz = item.getZ();
        setGoal(p -> {
            double dx = p.getX() + 0.5 - ix, dz = p.getZ() + 0.5 - iz;
            return dx * dx + dz * dz <= 1.1 * 1.1 && iy - p.getY() > -0.6 && iy - p.getY() < 1.6;
        }, item.blockPosition());
    }

    public void setGoal(Predicate<BlockPos> goal, BlockPos steer) {
        this.goal = goal;
        this.target = steer.immutable();
        this.path = null;
        this.replans = 0;
        this.avoid.clear();
        this.status = Status.MOVING;
    }

    private Predicate<BlockPos> savedGoal;
    private BlockPos savedTarget;
    private BotTask savedFor;

    /**
     * Stops for something that comes in between (a fight, food): the walk of {@code owner} is
     * kept and taken up again by {@link #resumeFor} once that task is back on top.
     */
    public void suspend(BotTask owner) {
        if (status == Status.MOVING && goal != null) {
            savedGoal = goal;
            savedTarget = target;
            savedFor = owner;
        }
        stop();
    }

    /** {@code task} is on top again: the walk it was on when interrupted goes on. */
    public void resumeFor(BotTask task) {
        if (task != null && task == savedFor && savedGoal != null && status != Status.MOVING) {
            setGoal(savedGoal, savedTarget);
        }
        savedGoal = null;
        savedFor = null;
    }

    public void stop() {
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
        if (path == null || index >= path.size()) {
            if (!replan(feet)) return status;
        }
        BotPathfinder.Step step = path.get(index);
        // A step taking far longer than it should (jumping at a wall it cannot get up, pushed back
        // by water again and again): give it up and look for another way, as Baritone does.
        // (Digging a step into the bank is work, not being stuck.)
        if (bankDig == null && ++stepTicks > stepLimit(step) + (bot.isInWater() ? 160 : 0)) {
            avoid.add(step.to().asLong());
            stepTicks = 0;
            path = null;
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
                    path = null; // out of blocks: find a way that needs none
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
            if (bot.getY() > step.place().getY() + 1.0 && open) {
                if (!actions.placeThrowaway(step.place())) {
                    path = null; // out of blocks: find a way that needs none
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
        bot.sprintNow = false;
        Vec3 aim = new Vec3(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        double dx = aim.x - bot.getX(), dz = aim.z - bot.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 0.3 && Math.abs(bot.getY() - to.getY()) < 0.7 || feet.equals(to) && flat < 0.45) {
            advance();
            return status;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setXRot(10f);
        bot.forward = flat > 0.15 ? 1f : 0f;
        // The next step breaks a pressure plate: creep up, so as not to slide onto it.
        if (index + 1 < path.size() && path.get(index + 1).breaks().stream()
                .anyMatch(b -> bot.level().getBlockState(b).is(net.minecraft.tags.BlockTags.PRESSURE_PLATES))) {
            bot.forward = flat > 0.1 ? 0.25f : 0f;
            bot.setSprinting(false);
            bot.sprintNow = false;
        }
        bot.strafe = 0;
        bot.setSprinting(false);
        boolean up = to.getY() > bot.getY() + 0.4;
        bot.jump = (up && flat < 1.6) || bot.isInWater() && (up || to.getY() >= bot.getY() - 0.2 || bot.horizontalCollision);
        if (bot.onGround() && bot.horizontalCollision && !up) bot.jump = true; // over a lip
        // Stuck: no progress for a while.
        double d = flat + Math.abs(bot.getY() - to.getY());
        if (d < lastDistance - 0.02) stuck = 0;
        else if (++stuck > STUCK_TICKS) {
            stuck = 0;
            path = null;
        }
        lastDistance = d;
        // Knocked off the path.
        if (feet.distManhattan(to) > 4 && !step.leap()) path = null;
        return status;
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
            path = null; // fell in
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

    private boolean replan(BlockPos feet) {
        feet = standing(feet);
        if (++replans > MAX_REPLANS) {
            fail("no way found");
            return false;
        }
        BotPathfinder finder = new BotPathfinder((ServerLevel) bot.level(),
                new BotPathfinder.Abilities(mayBreak, mayPillar && actions.hasThrowaway(), tools), avoid);
        path = finder.find(feet, goal, target, BUDGET);
        index = 0;
        stepTicks = 0;
        stuck = 0;
        lastDistance = Double.MAX_VALUE;
        if (path == null || path.isEmpty()) {
            fail("no way found from " + feet.toShortString() + " (" + bot.level().getBlockState(feet).getBlock().getName().getString()
                    + " on " + bot.level().getBlockState(feet.below()).getBlock().getName().getString() + ", "
                    + finder.debugMoves(feet) + ")");
            return false;
        }
        return true;
    }

    private Status fail(String why) {
        BotManager.debug(bot, "navigation failed: " + why);
        bot.stopInputs();
        actions.reset();
        path = null;
        status = Status.FAILED;
        return status;
    }
}
