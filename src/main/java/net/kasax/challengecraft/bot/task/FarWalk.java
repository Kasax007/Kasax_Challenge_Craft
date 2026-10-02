package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
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
    private long climbAgainAt;

    public enum Status { MOVING, ARRIVED, FAILED }

    /** One tick of walking to {@code target}, to within {@code range} blocks. */
    public Status tick(Bot bot, BlockPos target, double range) {
        if (!target.equals(this.target)) {
            this.target = target.immutable();
            this.range = range;
            walking = false;
            fails = 0;
            side = 0;
        }
        BotNavigator nav = bot.navigator();
        ServerLevel level = (ServerLevel) bot.body().level();
        BlockPos feet = nav.feet();
        double dx = target.getX() - feet.getX(), dz = target.getZ() - feet.getZ(), flat = Math.sqrt(dx * dx + dz * dz);
        boolean near = flat <= NEAR;
        // Down in a mine with the walk far over the land: up first.
        // (Once up, not again for a while: a climb that ends at once is not to be repeated each tick.)
        long now = level.getGameTime();
        if (!near && climb == null && now >= climbAgainAt && SurfaceTask.underground(bot.body())) climb = new SurfaceTask();
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
        // No way this time: aside, or (at the end) as near as it got.
        if (++fails > MAX_FAILS) return Status.FAILED;
        side = bot.body().getRandom().nextBoolean() ? 1 : -1;
        if (legDirect && flat <= range + 3) return Status.ARRIVED;
        return Status.MOVING;
    }

    public void stop(Bot bot) {
        if (walking) bot.navigator().stop();
        walking = false;
    }
}
