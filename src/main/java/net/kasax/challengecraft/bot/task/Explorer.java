package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Walking out to see more of the world, for tasks that look for something not in sight: up to the
 * surface first if underground, then in legs of about 40 blocks in one direction (a player keeps
 * a direction rather than circling), turning when blocked. Each leg makes the bot look around again.
 */
final class Explorer {
    private final int budget;
    private Direction heading;
    private boolean walking, climbed;
    private int ticks, legs;

    Explorer(int budgetTicks) {
        this.budget = budgetTicks;
    }

    /** One tick of exploring; FAILED once the time is up. */
    BotTask.Result tick(Bot bot) {
        if (++ticks > budget || legs > 60) return BotTask.Result.FAILED;
        if (!climbed) {
            climbed = true;
            if (SurfaceTask.underground(bot.body())) {
                bot.interject(new SurfaceTask());
                return BotTask.Result.RUNNING;
            }
        }
        if (heading == null) heading = bot.exploreHeading != null ? bot.exploreHeading : Direction.Plane.HORIZONTAL.getRandomDirection(bot.body().getRandom());
        bot.exploreHeading = heading;
        BotNavigator nav = bot.navigator();
        if (!walking) {
            legs++;
            BlockPos p = nav.feet().relative(heading, 40).relative(heading.getClockWise(), bot.body().getRandom().nextInt(21) - 10);
            nav.goNear(bot.body().level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p), 6);
            walking = true;
        }
        BotNavigator.Status s = nav.tick();
        if (s != BotNavigator.Status.MOVING) {
            walking = false;
            climbed = false; // (a leg that went into a cave: back up before the next one)
            bot.senses().refresh();
            if (s == BotNavigator.Status.FAILED) heading = bot.body().getRandom().nextBoolean() ? heading.getClockWise() : heading.getCounterClockWise();
        }
        return BotTask.Result.RUNNING;
    }

    /** The next leg toward {@code target}: at most {@code leg} blocks on, on the surface there. */
    static BlockPos legToward(net.minecraft.server.level.ServerLevel level, BlockPos from, BlockPos target, int leg) {
        double dx = target.getX() - from.getX(), dz = target.getZ() - from.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d <= leg) return target;
        BlockPos p = BlockPos.containing(from.getX() + dx / d * leg, from.getY(), from.getZ() + dz / d * leg);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
    }

    void pause(Bot bot) {
        if (walking) bot.navigator().stop();
        walking = false;
    }
}
