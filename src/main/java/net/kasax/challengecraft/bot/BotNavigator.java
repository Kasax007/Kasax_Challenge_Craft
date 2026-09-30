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
    private int index, stuck, replans;
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

    public void setGoal(Predicate<BlockPos> goal, BlockPos steer) {
        this.goal = goal;
        this.target = steer.immutable();
        this.path = null;
        this.replans = 0;
        this.status = Status.MOVING;
    }

    public void stop() {
        // Only abandon a dig the navigator itself started; the caller may be mining on its own.
        if (status == Status.MOVING) actions.reset();
        goal = null;
        path = null;
        status = Status.IDLE;
        bot.stopInputs();
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
        // Dig first.
        for (BlockPos b : step.breaks()) {
            if (!bot.level().getBlockState(b).getCollisionShape(bot.level(), b).isEmpty()) {
                bot.stopInputs();
                if (!actions.inReach(b)) {
                    return fail("block out of reach");
                }
                actions.breakTick(b);
                stuck = 0;
                return status;
            }
        }
        // Pillar: jump and put a block where the feet were.
        if (step.place() != null) {
            bot.forward = 0;
            bot.strafe = 0;
            centreOn(step.place());
            bot.jump = true;
            if (bot.getY() > step.place().getY() + 1.0 && bot.level().getBlockState(step.place()).isAir()) {
                if (!actions.placeThrowaway(step.place())) return fail("no block to pillar with");
            }
            if (!bot.level().getBlockState(step.place()).isAir() && bot.onGround()) advance();
            return status;
        }
        BlockPos to = step.to();
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
        if (feet.distManhattan(to) > 4) path = null;
        return status;
    }

    private void advance() {
        index++;
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

    private boolean replan(BlockPos feet) {
        if (++replans > MAX_REPLANS) {
            fail("no way found");
            return false;
        }
        BotPathfinder finder = new BotPathfinder((ServerLevel) bot.level(),
                new BotPathfinder.Abilities(mayBreak, mayPillar && actions.hasThrowaway(), tools));
        path = finder.find(feet, goal, target, BUDGET);
        index = 0;
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
