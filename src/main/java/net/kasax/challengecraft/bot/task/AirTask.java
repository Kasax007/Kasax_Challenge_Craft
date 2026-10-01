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
    private int ticks, fails;

    /** The head would be out of the water standing (or swimming) here. */
    public static boolean breathable(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        return level.getFluidState(head).isEmpty() && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
    }

    @Override
    public Result tick(Bot bot) {
        if (!bot.body().isEyeInFluid(FluidTags.WATER)) return Result.DONE;
        if (++ticks > 400) return Result.FAILED;
        ServerLevel level = (ServerLevel) bot.body().level();
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
