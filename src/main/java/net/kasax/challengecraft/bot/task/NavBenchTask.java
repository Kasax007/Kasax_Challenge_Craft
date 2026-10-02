package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A navigation benchmark: walks to {@code count} spots on the surface {@code min}..{@code max}
 * blocks away, one after another, and logs for each whether it got there, how long it took, how
 * far it really walked (against the straight line), how long it stood waiting for a search, how
 * often it swung back (further off than its best by eight blocks and more) and why paths were
 * dropped ([NAVBENCH] in the log), then a summary. The spots come from the world seed: the same
 * world gives the same spots, so two versions can be compared.
 */
public final class NavBenchTask implements BotTask {
    /** Sprinting, blocks a second: the speed measured against. */
    private static final double SPRINT = 5.612;
    private final int count, min, max;
    private final FarWalk walk = new FarWalk();
    private BlockPos target, from;
    private int done, ok, ticks, limit, failsAtStart, waitAtStart, swings;
    private double sumDist, sumTicks, sumOkDist, walked, sumWalked, sumOkWalked, sumOkTicks, best, sumWait;
    private int sumSwings;
    private boolean swung;
    private net.minecraft.world.phys.Vec3 last;
    private java.util.Map<String, Integer> dropsAtStart;
    private java.util.Random random;
    private BlockPos centre;

    public NavBenchTask(int count, int min, int max) {
        this.count = count;
        this.min = min;
        this.max = max;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (random == null) random = new java.util.Random(level.getSeed() ^ 0x5DEECE66DL);
        if (target == null) {
            if (done >= count) {
                BotManager.LOG.info("[NAVBENCH] summary: {} of {} reached, {} blocks in {} s ({} blocks/s), detour {}, speed {}% of sprint, "
                                + "waiting {}% of the time, swings {}, path searches with no way: {}, dropped: {}",
                        ok, count, Math.round(sumOkDist), Math.round(sumTicks / 20), String.format("%.2f", sumOkDist / Math.max(1, sumTicks / 20)),
                        String.format("%.2f", sumOkWalked / Math.max(1, sumOkDist)),
                        Math.round(100 * sumOkWalked / Math.max(1, sumOkTicks / 20) / SPRINT),
                        Math.round(100 * sumWait / Math.max(1, sumTicks)), sumSwings, bot.navigator().failures, bot.navigator().dropReasons);
                return Result.DONE;
            }
            // Round the world's spawn, from the seed: the same spots whatever way it went before
            // (two versions walk the same ways). The ground is generated there if need be.
            if (centre == null) centre = level.getRespawnData().pos();
            double a = random.nextDouble() * Math.PI * 2;
            double d = min + random.nextDouble() * (max - min);
            BlockPos flat = centre.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
            level.getChunk(flat);
            target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flat);
            d = Math.sqrt(bot.body().blockPosition().distSqr(target));
            from = bot.body().blockPosition();
            ticks = 0;
            limit = (int) (d / 2.0 * 20) + 600; // two blocks a second, and some
            failsAtStart = bot.navigator().failures;
            waitAtStart = bot.navigator().waitTicks;
            dropsAtStart = new java.util.TreeMap<>(bot.navigator().dropReasons);
            walked = 0;
            swings = 0;
            swung = false;
            best = Double.MAX_VALUE;
            last = bot.body().position();
        }
        ticks++;
        var pos = bot.body().position();
        walked += Math.sqrt((pos.x - last.x) * (pos.x - last.x) + (pos.z - last.z) * (pos.z - last.z));
        last = pos;
        double left = Math.sqrt(Math.pow(pos.x - target.getX() - 0.5, 2) + Math.pow(pos.z - target.getZ() - 0.5, 2));
        if (left < best) {
            best = left;
            swung = false;
        } else if (!swung && left > best + 8) {
            swung = true;
            swings++;
        }
        if (ticks % 200 == 0) BotManager.LOG.info("[NAVBENCH]   at {}: {} [{}]", bot.body().blockPosition().toShortString(), bot.navigator().debug(), bot.status());
        FarWalk.Status s = walk.tick(bot, target, 3);
        boolean timeout = ticks > limit;
        if (s == FarWalk.Status.MOVING && !timeout) return Result.RUNNING;
        double dist = Math.sqrt(from.distSqr(target));
        boolean reached = s == FarWalk.Status.ARRIVED && bot.body().blockPosition().distSqr(target) < 8 * 8;
        int wait = bot.navigator().waitTicks - waitAtStart;
        java.util.Map<String, Integer> drops = new java.util.TreeMap<>();
        bot.navigator().dropReasons.forEach((k, v) -> {
            int n = v - dropsAtStart.getOrDefault(k, 0);
            if (n > 0) drops.put(k, n);
        });
        BotManager.LOG.info("[NAVBENCH] {} to {} ({} blocks, {}): {} in {} s, {} blocks/s, walked {} (detour {}), {}% of sprint, waited {} s, swings {}, dropped {}, searches with no way {}",
                done + 1, target.toShortString(), Math.round(dist), level.getBiome(target).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"),
                reached ? "reached" : timeout ? "TIMEOUT" : "FAILED", ticks / 20, String.format("%.2f", dist / (ticks / 20.0)),
                Math.round(walked), String.format("%.2f", walked / Math.max(1, dist)), Math.round(100 * walked / (ticks / 20.0) / SPRINT),
                wait / 20, swings, drops, bot.navigator().failures - failsAtStart);
        if (reached) {
            ok++;
            sumOkDist += dist;
            sumOkWalked += walked;
            sumOkTicks += ticks;
        }
        sumDist += dist;
        sumTicks += ticks;
        sumWalked += walked;
        sumWait += wait;
        sumSwings += swings;
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
