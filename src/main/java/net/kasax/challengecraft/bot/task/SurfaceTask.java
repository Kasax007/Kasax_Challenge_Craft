package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Back up to the surface from a mine: straight up by jumping and putting a block under itself
 * (breaking what is above the head), or, without blocks to spare or with something liquid above,
 * up a staircase it digs. Stops as soon as it sees the sky.
 */
public final class SurfaceTask implements BotTask {
    private Direction heading;
    private BlockPos jumpedFrom, stepTo;
    private int ticks, stepTicks, bestY = Integer.MIN_VALUE, sinceBest, switches;
    private boolean stairs, navigating;
    private int navFails;
    private boolean failedPlace, triedEntry;

    /** Deep enough under the ground that walking about on the surface needs a climb first. */
    public static boolean underground(BotPlayer body) {
        return underground((ServerLevel) body.level(), body.blockPosition());
    }

    /** {@link #underground(BotPlayer)} for feet at {@code feet}. */
    public static boolean underground(ServerLevel level, BlockPos feet) {
        // Little sky light where the head is: a cave or a mine. (Under trees or next to a trunk
        // there is plenty; up an open shaft too, and that one needs no climbing either.)
        BlockPos head = feet.above();
        if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, head) < 6
                && head.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, head.getX(), head.getZ())) return true;
        // Down a shaft or a ravine (the sky shines straight in): the ground a few blocks around is
        // mostly well above the feet. (One side high is only a cliff or a wall.)
        int feetY = feet.getY(), high = 0, samples = 0;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            int x = feet.getX() + (int) Math.round(Math.cos(a) * 4);
            int z = feet.getZ() + (int) Math.round(Math.sin(a) * 4);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            samples++;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) > feetY + 1) high++;
        }
        // ... and it is hemmed in (a hole or a tunnel, not a valley floor).
        int walls = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = feet.above().relative(d);
            if (!level.getBlockState(n).getCollisionShape(level, n).isEmpty()) walls++;
        }
        return samples > 0 && high * 10 >= samples * 7 && walls >= 2;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = bot.navigator().feet();
        // (Only judged standing: mid-jump the body is a block higher than where it stands.)
        if ((body.onGround() || body.isInWater()) && jumpedFrom == null && !underground(body)) {
            body.stopInputs();
            return Result.DONE;
        }
        if (++ticks > 2400) return Result.FAILED;
        // First choice: let the path search find the way up (cave passages, a staircase dug
        // through whatever is cheapest, pillars where there are blocks). The hand-made climb
        // below is only for when it finds nothing.
        // Came in through a cave: out the way it came, if that is not far.
        if (bot.caveEntry != null && !triedEntry) {
            if (bot.caveEntry.distSqr(feet) > 96 * 96) bot.caveEntry = null;
            else {
                if (!navigating) {
                    bot.navigator().goStandNear(bot.caveEntry, 2);
                    navigating = true;
                }
                BotNavigator.Status s = bot.navigator().tick();
                if (s == BotNavigator.Status.MOVING) return Result.RUNNING;
                navigating = false;
                triedEntry = true;
                if (s == BotNavigator.Status.ARRIVED) bot.caveEntry = null;
                return Result.RUNNING;
            }
        }
        if (navFails < 4) {
            if (!navigating) {
                bot.navigator().setGoal(p -> !underground(level, p),
                        new BlockPos(feet.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()), feet.getZ()));
                navigating = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.MOVING) return Result.RUNNING;
            navigating = false;
            if (s == BotNavigator.Status.FAILED) navFails++;
            return Result.RUNNING; // arrived: judged again next tick
        }
        if (heading == null) heading = body.getDirection();
        // Not getting higher (gravel keeps falling in, water above, a ledge): change the way.
        if (feet.getY() > bestY) {
            bestY = feet.getY();
            sinceBest = 0;
        } else if (!bot.actions().isBreaking() && ++sinceBest > 300) {
            sinceBest = 0;
            stairs = !stairs;
            heading = heading.getClockWise();
            jumpedFrom = null;
            stepTo = null;
            if (++switches > 6) return Result.FAILED;
        }

        if (stepTo != null) return step(bot, feet);

        // Pillar: head room, then jump and set a block where the feet were.
        BlockPos head = feet.above(2);
        boolean headSafe = safe(level, head);
        if (!stairs && jumpedFrom == null && headSafe && bot.actions().hasThrowaway() && !failedPlace) {
            if (!clear(level, head)) {
                bot.actions().breakTick(head);
                return Result.RUNNING;
            }
            if (body.onGround()) {
                jumpedFrom = feet;
                body.jump = true;
            }
            return Result.RUNNING;
        }
        if (jumpedFrom != null) {
            body.jump = false;
            if (body.getY() > jumpedFrom.getY() + 1.05) {
                if (!bot.actions().placeThrowaway(jumpedFrom)) failedPlace = true; // stairs from now on
                jumpedFrom = null;
            } else if (body.onGround() && ++stepTicks > 20) {
                jumpedFrom = null; // did not get up (a low ceiling after all): try again
                stepTicks = 0;
            }
            return Result.RUNNING;
        }

        // Staircase up: head room here, then the two blocks ahead one higher.
        BlockPos ahead = feet.relative(heading);
        List<BlockPos> dig = List.of(feet.above(2), ahead.above(), ahead.above(2));
        boolean ok = !clear(level, ahead) && dig.stream().allMatch(b -> safe(level, b));
        if (!ok) {
            heading = heading.getClockWise();
            return Result.RUNNING;
        }
        for (BlockPos b : dig) {
            if (!clear(level, b)) {
                bot.actions().breakTick(b);
                return Result.RUNNING;
            }
        }
        stepTo = ahead.above();
        stepTicks = 0;
        return Result.RUNNING;
    }

    private Result step(Bot bot, BlockPos feet) {
        BotPlayer body = bot.body();
        if (feet.equals(stepTo)) {
            body.stopInputs();
            stepTo = null;
            return Result.RUNNING;
        }
        body.lookAt(Vec3.atBottomCenterOf(stepTo).add(0, body.getEyeHeight(), 0));
        body.forward = 1f;
        body.jump = true;
        if (++stepTicks > 40) {
            body.stopInputs();
            stepTo = null;
            heading = heading.getClockWise();
        }
        return Result.RUNNING;
    }

    private static boolean clear(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty();
    }

    /** Nothing liquid there or next to it (above all: nothing that would pour down on the bot). */
    private static boolean safe(ServerLevel level, BlockPos p) {
        if (!level.getFluidState(p).isEmpty() || level.getBlockState(p).getDestroySpeed(level, p) < 0) return false;
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (!level.getFluidState(p.relative(d)).isEmpty()) return false;
        }
        // Gravel or sand above would just fall in again; fine, it gets dug again, but not lava.
        return true;
    }

    @Override
    public String status() {
        return describe() + " [jumpedFrom " + jumpedFrom + ", stepTo " + stepTo + ", heading " + heading + ", ticks " + ticks + "]";
    }

    @Override
    public String describe() {
        return "climb to the surface";
    }
}
