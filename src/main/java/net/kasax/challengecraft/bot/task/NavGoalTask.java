package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BiPredicate;
import java.util.function.Function;

/** To any spot the goal accepts, steered towards a point (down to the bedrock, up to the sky). */
public final class NavGoalTask implements BotTask {
    private final String what;
    private final BiPredicate<ServerLevel, BlockPos> goal;
    private final Function<Bot, BlockPos> steer;
    private final int limit;
    private boolean started;
    private int ticks, fails;

    public NavGoalTask(String what, BiPredicate<ServerLevel, BlockPos> goal, Function<Bot, BlockPos> steer, int limitTicks) {
        this.what = what;
        this.goal = goal;
        this.steer = steer;
        this.limit = limitTicks;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (goal.test(level, bot.navigator().feet()) && bot.body().onGround()) return Result.DONE;
        if (++ticks > limit) return Result.FAILED;
        if (!started) {
            bot.navigator().setGoal(p -> goal.test(level, p), steer.apply(bot));
            started = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s == BotNavigator.Status.FAILED) {
            started = false;
            if (++fails > 5) return Result.FAILED;
        } else if (s == BotNavigator.Status.ARRIVED) {
            started = false;
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return what;
    }
}
