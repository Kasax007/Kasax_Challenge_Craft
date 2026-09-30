package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
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
    private int ticks, stepTicks;

    /** Deep enough under the ground that walking about on the surface needs a climb first. */
    public static boolean underground(BotPlayer body) {
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = body.blockPosition();
        // The ground around is above the feet (more than a hole's depth); its own shaft does not count.
        int ground = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ground = Math.max(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX() + dx, feet.getZ() + dz));
            }
        }
        return feet.getY() + 1 < ground;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = bot.navigator().feet();
        // (Only judged standing: mid-jump the body is a block higher than where it stands.)
        if (body.onGround() && jumpedFrom == null && !underground(body)) {
            body.stopInputs();
            return Result.DONE;
        }
        if (++ticks > 4000) return Result.FAILED;
        if (heading == null) heading = body.getDirection();

        if (stepTo != null) return step(bot, feet);

        // Pillar: head room, then jump and set a block where the feet were.
        BlockPos head = feet.above(2);
        boolean headSafe = safe(level, head);
        if (jumpedFrom == null && headSafe && bot.actions().hasThrowaway()) {
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
                bot.actions().placeThrowaway(jumpedFrom);
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
    public String describe() {
        return "climb to the surface";
    }
}
