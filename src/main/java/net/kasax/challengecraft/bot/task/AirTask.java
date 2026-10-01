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
        if (!bot.body().isEyeInFluid(FluidTags.WATER)) return Result.DONE;
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
        if (!started) {
            bot.navigator().setGoal(p -> breathable(level, p), bot.body().blockPosition().above(8));
            started = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s == BotNavigator.Status.FAILED) {
            started = false;
            if (++fails > 3) return Result.FAILED;
        } else if (s == BotNavigator.Status.ARRIVED) {
            started = false;
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "up for air";
    }
}
