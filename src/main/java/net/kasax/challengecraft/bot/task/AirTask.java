package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

/**
 * Out of air under a roof of water (a tunnel dug into a flooded cave, a sea cave): to the nearest
 * spot where the head is in the air, before anything else. Swimming up is not enough there.
 */
public final class AirTask implements BotTask {
    private boolean started;
    /** Ticks with the head out, catching breath. */
    private int breathing;
    private int ticks, fails, stuck;
    private boolean digging;
    private net.minecraft.world.phys.Vec3 last;

    /** The head would be out of the water standing (or swimming) here. */
    public static boolean breathable(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        return level.getFluidState(head).isEmpty() && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
    }

    @Override
    public Result tick(Bot bot) {
        var b = bot.body();
        // Head out: there until the lungs are nearly full again - a breath and straight back
        // under (the way on leads through the water) and the next breath is shorter, until
        // there is none. Meanwhile still, afloat (head kept up) or standing.
        if (!b.isEyeInFluid(FluidTags.WATER)) {
            if (b.getAirSupply() >= b.getMaxAirSupply() * 9 / 10 || ++breathing > 200) return Result.DONE;
            bot.navigator().stop();
            b.stopInputs();
            if (b.isInWater()) b.jump = true;
            return Result.RUNNING;
        }
        if (breathing > 0 && b.getAirSupply() >= b.getMaxAirSupply() / 2) {
            // (Bobbing under for a moment while waiting at the surface: up again, no new search.)
            b.stopInputs();
            b.jump = true;
            return Result.RUNNING;
        }
        if (++ticks > (digging ? 1600 : 400)) return Result.FAILED;
        ServerLevel level = (ServerLevel) bot.body().level();
        // Not getting anywhere (a flooded gap one block high, a block on top): straight up,
        // digging out what is over the head, as a player would.
        var body = bot.body();
        if (ticks % 20 == 1) {
            // (Measured over a second: swimming is slow, but not this slow.)
            stuck = last != null && body.position().distanceToSqr(last) < 0.25 ? stuck + 1 : 0;
            last = body.position();
        }
        if (stuck >= 1 || fails > 1) {
            BlockPos head = BlockPos.containing(body.getX(), body.getEyeY(), body.getZ());
            for (BlockPos q : new BlockPos[]{head.above(), head.above(2)}) {
                if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getFluidState(q).isEmpty()) {
                    // Standing on the bottom while at it: afloat, digging is five times slower.
                    bot.navigator().stop();
                    body.stopInputs();
                    bot.sinkToDig = true;
                    digging = true;
                    bot.actions().breakTick(q);
                    return Result.RUNNING;
                }
            }
        }
        // No way found by the search, again and again: up by hand - straight up where the water
        // goes up to the air, else towards the nearest column of water that does (seed 88
        // drowned twice at the sea's surface with "no way found", the walk under it taken up
        // again after this gave up).
        if (fails > 1) {
            bot.navigator().stop();
            body.stopInputs();
            body.jump = true;
            BlockPos feet = body.blockPosition();
            BlockPos open = openColumn(level, feet);
            if (open != null && (open.getX() != feet.getX() || open.getZ() != feet.getZ())) {
                body.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(open.atY(feet.getY())));
                body.forward = 1f;
            }
            return Result.RUNNING;
        }
        if (!started) {
            bot.navigator().setGoal(p -> breathable(level, p), bot.body().blockPosition().above(8));
            started = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s == BotNavigator.Status.FAILED) {
            started = false;
            fails++;
        } else if (s == BotNavigator.Status.ARRIVED) {
            started = false;
        }
        return Result.RUNNING;
    }

    /** The nearest column (within six) where nothing but water stands between the feet's level and the air above. */
    private static BlockPos openColumn(ServerLevel level, BlockPos feet) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = dx * dx + dz * dz;
                if (d >= bestD) continue;
                BlockPos p = feet.offset(dx, 0, dz);
                for (int up = 0; up <= 10; up++) {
                    BlockPos q = p.above(up);
                    if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) break;
                    if (level.getFluidState(q).isEmpty()) {
                        best = p;
                        bestD = d;
                        break;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public String describe() {
        return "up for air";
    }
}
