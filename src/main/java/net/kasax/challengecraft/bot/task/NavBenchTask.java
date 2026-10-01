package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A navigation benchmark: walks to {@code count} random spots on the surface {@code min}..{@code max}
 * blocks away, one after another, and logs for each whether it got there, how long it took and
 * how often the path search found no way ([NAVBENCH] in the log), then a summary.
 */
public final class NavBenchTask implements BotTask {
    private final int count, min, max;
    private final FarWalk walk = new FarWalk();
    private BlockPos target, from;
    private int done, ok, ticks, limit, failsAtStart;
    private double sumDist, sumTicks, sumOkDist;

    public NavBenchTask(int count, int min, int max) {
        this.count = count;
        this.min = min;
        this.max = max;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (target == null) {
            if (done >= count) {
                BotManager.LOG.info("[NAVBENCH] summary: {} of {} reached, {} blocks in {} s ({} blocks/s), path searches with no way: {}",
                        ok, count, Math.round(sumOkDist), Math.round(sumTicks / 20), String.format("%.2f", sumOkDist / Math.max(1, sumTicks / 20)),
                        bot.navigator().failures);
                return Result.DONE;
            }
            var r = bot.body().getRandom();
            double a = 0, d = 0;
            BlockPos flat = null;
            // (Only where the world is loaded: the ground elsewhere reads as the bottom of the world.)
            for (int i = 0; i < 50 && (flat == null || !level.hasChunkAt(flat)); i++) {
                a = r.nextDouble() * Math.PI * 2;
                d = min + r.nextDouble() * (max - min);
                flat = bot.body().blockPosition().offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
            }
            target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flat);
            // (Not into the sea: the shore is what a player would walk to.)
            from = bot.body().blockPosition();
            ticks = 0;
            limit = (int) (d / 2.0 * 20) + 600; // two blocks a second, and some
            failsAtStart = bot.navigator().failures;
        }
        ticks++;
        FarWalk.Status s = walk.tick(bot, target, 3);
        boolean timeout = ticks > limit;
        if (s == FarWalk.Status.MOVING && !timeout) return Result.RUNNING;
        double dist = Math.sqrt(from.distSqr(target));
        boolean reached = s == FarWalk.Status.ARRIVED && bot.body().blockPosition().distSqr(target) < 8 * 8;
        BotManager.LOG.info("[NAVBENCH] {} to {} ({} blocks, {}): {} in {} s, {} blocks/s, searches with no way {}",
                done + 1, target.toShortString(), Math.round(dist), level.getBiome(target).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"),
                reached ? "reached" : timeout ? "TIMEOUT" : "FAILED", ticks / 20, String.format("%.2f", dist / (ticks / 20.0)),
                bot.navigator().failures - failsAtStart);
        if (reached) {
            ok++;
            sumOkDist += dist;
        }
        sumDist += dist;
        sumTicks += ticks;
        walk.stop(bot);
        done++;
        target = null;
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "navigation benchmark (" + done + " of " + count + ")";
    }
}
