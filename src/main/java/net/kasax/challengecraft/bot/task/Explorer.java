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
        BlockPos p = d <= leg ? target : BlockPos.containing(from.getX() + dx / d * leg, from.getY(), from.getZ() + dz / d * leg);
        if (!level.hasChunkAt(p)) return new BlockPos(p.getX(), Math.max(level.getSeaLevel(), from.getY()), p.getZ());
        // Under a roof (the Nether): no surface to aim at; a spot to stand on near the height on
        // the line from here to there, else that height itself (the path search digs its way).
        if (level.dimensionType().hasCeiling()) {
            int y = d <= leg ? target.getY() : (int) Math.round(from.getY() + (target.getY() - from.getY()) * leg / d);
            for (int i = 0; i <= 24; i++) {
                for (int sign : new int[] {1, -1}) {
                    BlockPos q = new BlockPos(p.getX(), y + i * sign, p.getZ());
                    if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
                            && level.getBlockState(q.below()).isSolidRender() && level.getFluidState(q.below()).isEmpty()) return q;
                }
            }
            return new BlockPos(p.getX(), y, p.getZ());
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
    }

    void pause(Bot bot) {
        if (walking) bot.navigator().stop();
        walking = false;
    }
}
