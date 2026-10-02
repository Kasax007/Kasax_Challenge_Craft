package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Getting somewhere far off that it knows of (a village a thousand blocks back, the portal it
 * came through): out of the ground first, then over the surface in legs of forty blocks (a path
 * search straight there runs out of budget, and the land in between is not even loaded yet). A
 * leg with no way (a lake, a cliff) is tried again turned aside; only many failures in a row end
 * the walk. Near enough, the last bit is walked straight.
 */
public final class FarWalk {
    private static final int LEG = 40, NEAR = 48, MAX_FAILS = 8;
    private BlockPos target;
    private double range;
    private boolean walking, legDirect;
    private int fails, side;
    private SurfaceTask climb;
    private long climbAgainAt, climbedAt;
    private BlockPos climbedFrom;
    /** The nearest it has come (flat distance) and when: no headway for long is a stuck walk. */
    private double bestFlat;
    private long progressAt;
    private int stalls;

    public enum Status { MOVING, ARRIVED, FAILED }

    /** One tick of walking to {@code target}, to within {@code range} blocks. */
    public Status tick(Bot bot, BlockPos target, double range) {
        if (!target.equals(this.target)) {
            this.target = target.immutable();
            this.range = range;
            walking = false;
            fails = 0;
            side = 0;
            bestFlat = Double.MAX_VALUE;
            progressAt = bot.body().level().getGameTime();
            stalls = 0;
        }
        BotNavigator nav = bot.navigator();
        ServerLevel level = (ServerLevel) bot.body().level();
        BlockPos feet = nav.feet();
        double dx = target.getX() - feet.getX(), dz = target.getZ() - feet.getZ(), flat = Math.sqrt(dx * dx + dz * dz);
        boolean near = flat <= NEAR;
        // Headway: some blocks nearer every so often. None for three quarters of a minute (the
        // way swinging between two half-found paths, into a hill and back): another approach -
        // up to the surface if under it, else a leg off to the side; three of those, given up.
        long tick = level.getGameTime();
        if (flat < bestFlat - 6) {
            bestFlat = flat;
            progressAt = tick;
        } else if (tick - progressAt > 900 && climb == null) {
            progressAt = tick;
            bestFlat = flat;
            if (++stalls > 3) return Status.FAILED;
            nav.stop();
            walking = false;
            if (SurfaceTask.underground(bot.body())) climb = new SurfaceTask();
            else if (BotTerrain.usable(level)) {
                // The large view's way led into something it could not see (a cliff between two
                // cells, a ravine): there believed dearer, and the way worked out afresh.
                nav.terrain.penalize(feet, 400);
                nav.rethinkWay();
            } else side = stalls % 2 == 1 ? 1 : -1;
        }
        // Down in a mine with the walk far over the land: up first. (A valley or a ravine is
        // not a mine: the path search takes the way out of it.)
        // (Once up, not again for a while: a climb that ends at once is not to be repeated each tick.)
        long now = level.getGameTime();
        // (Not again where it climbed out a little while ago: an overhang the way leads under is
        // not a mine, and climbing out of it again and again is a walk back and forth.)
        boolean climbedHere = climbedFrom != null && climbedFrom.distSqr(feet) < 24 * 24 && now - climbedAt < 2400;
        if (!near && climb == null && now >= climbAgainAt && !climbedHere && SurfaceTask.covered(bot.body())) {
            climb = new SurfaceTask();
            climbedFrom = feet;
            climbedAt = now;
        }
        if (climb != null) {
            var r = climb.tick(bot);
            if (r == net.kasax.challengecraft.bot.BotTask.Result.RUNNING) return Status.MOVING;
            climb = null;
            climbAgainAt = now + 200;
            walking = false;
        }
        if (!walking) {
            // Straight for the target, however far: the navigator plans it leg by leg as it walks
            // (each next leg while on the one before). Legs of its own only to get round
            // something after a failure.
            legDirect = near || side == 0;
            if (legDirect) nav.goNear(target, range);
            else {
                BlockPos aim = target;
                if (side != 0) {
                    // (Turned sixty degrees off the line, for one leg.)
                    double a = Math.atan2(dz, dx) + side * Math.PI / 3;
                    aim = feet.offset((int) (Math.cos(a) * 30), 0, (int) (Math.sin(a) * 30));
                }
                nav.goNear(Explorer.legToward(level, feet, aim, side != 0 ? 30 : LEG), 4);
            }
            walking = true;
        }
        BotNavigator.Status s = nav.tick();
        if (s == BotNavigator.Status.MOVING) return Status.MOVING;
        walking = false;
        if (s == BotNavigator.Status.ARRIVED) {
            fails = 0;
            side = 0;
            if (legDirect) return Status.ARRIVED;
            return Status.MOVING;
        }
        // No way this time: aside, or (at the end) as near as it got. (With the large view: that
        // spot believed dearer, and its way round worked out again; aside only under a roof.)
        if (++fails > MAX_FAILS) return Status.FAILED;
        if (BotTerrain.usable(level)) {
            nav.terrain.penalize(feet, 300);
            nav.rethinkWay();
        } else side = bot.body().getRandom().nextBoolean() ? 1 : -1;
        if (legDirect && flat <= range + 3) return Status.ARRIVED;
        return Status.MOVING;
    }

    public void stop(Bot bot) {
        if (walking) bot.navigator().stop();
        walking = false;
    }
}
