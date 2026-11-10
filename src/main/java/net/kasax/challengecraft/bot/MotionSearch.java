package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Movement by the game's own physics (knob {@code motionsearch}, round 3). While Bob walks a plain
 * stretch of his path (no digging, building, leaps or water), each tick a short horizon of inputs
 * is played out on a copy of his state with the player's movement rules - the game's constants
 * (ground friction 0.6, air 0.91, gravity 0.08, jump 0.42, the sprint-jump's push 0.2, the
 * sprint's 1.3) and the level's own collision shapes ({@link Entity#collideBoundingBox}, the
 * 0.6 step) - and the input whose outcome gets furthest along the path's corridor, safely, is
 * taken (model-predictive: planned again next tick from the real state).
 *
 * <p>The inputs tried: four headings (toward points one and a half and three blocks ahead on the
 * path, and twelve degrees either side of the middle), each with no jump, a jump now, or jumping
 * whenever on the ground (sprint-jumping); sprinting whenever the body may. Unsafe outcomes are
 * dropped: off the corridor by more than a block and a half, below the path, a fall that hurts,
 * into lava or fire, into water the path does not cross. Bounded work: {@link #ROLLOUTS} rollouts
 * of {@link #HORIZON} ticks, no randomness, nothing read but the blocks - the same state gives the
 * same choice at any game speed.
 */
final class MotionSearch {
    static final int HORIZON = 10;
    static final double GRAVITY = 0.08, DRAG = 0.98, AIR = 0.91, JUMP = 0.42, SPRINT_JUMP = 0.2, WALK = 0.1, SPRINT = 1.3,
            FLY = 0.02, FLY_SPRINT = 0.026, STEP = 0.6;
    private static final double[] AHEAD = {1.5, 3.0};
    private static final float[] SIDE = {-12, 12};
    private static final int[] JUMPS = {-1, 0, 99};
    static final int ROLLOUTS = (AHEAD.length + SIDE.length) * JUMPS.length;

    /** The chosen input for this tick. */
    record Choice(float yaw, boolean jump, boolean sprint, double progress) {
    }

    /** The path ahead as a line through the middles of its steps (feet heights). */
    private final double[] px = new double[16], py = new double[16], pz = new double[16], arc = new double[16];
    private int n;
    private boolean[] wet = new boolean[16];

    /**
     * The best input for now, or null when no rollout is safe (the old way then steps in).
     * {@code steps}: the path's next steps (feet positions), at least two.
     */
    Choice choose(BotPlayer body, List<BlockPos> steps, boolean maySprint) {
        Level level = body.level();
        n = 0;
        px[0] = body.getX();
        py[0] = body.getY();
        pz[0] = body.getZ();
        arc[0] = 0;
        n = 1;
        for (BlockPos p : steps) {
            if (n >= px.length) break;
            px[n] = p.getX() + 0.5;
            py[n] = p.getY();
            pz[n] = p.getZ() + 0.5;
            wet[n] = !level.getFluidState(p).isEmpty();
            arc[n] = arc[n - 1] + Math.hypot(px[n] - px[n - 1], pz[n] - pz[n - 1]);
            n++;
        }
        if (n < 3) return null;
        Vec3 v0 = body.getDeltaMovement();
        boolean ground = body.onGround();
        Choice best = null;
        for (int h = 0; h < AHEAD.length + SIDE.length; h++) {
            float yaw;
            if (h < AHEAD.length) yaw = yawTo(body, at(AHEAD[h]));
            else yaw = yawTo(body, at(2.2)) + SIDE[h - AHEAD.length];
            for (int j : JUMPS) {
                double p = rollout(body, level, v0, ground, yaw, j, maySprint);
                if (p > -1e8 && (best == null || p > best.progress() + 1e-6)) {
                    boolean jumpNow = ground && (j == 0 || j == 99);
                    best = new Choice(yaw, jumpNow, maySprint, p);
                }
            }
        }
        return best;
    }

    /** The point {@code d} blocks along the path from where the body is. */
    private double[] at(double d) {
        for (int i = 1; i < n; i++) {
            if (arc[i] >= d || i == n - 1) {
                double seg = arc[i] - arc[i - 1], f = seg <= 1e-9 ? 1 : Math.min(1, (d - arc[i - 1]) / seg);
                return new double[]{px[i - 1] + (px[i] - px[i - 1]) * f, pz[i - 1] + (pz[i] - pz[i - 1]) * f};
            }
        }
        return new double[]{px[n - 1], pz[n - 1]};
    }

    private static float yawTo(BotPlayer body, double[] p) {
        return (float) (Mth.atan2(p[1] - body.getZ(), p[0] - body.getX()) * Mth.RAD_TO_DEG) - 90f;
    }

    /**
     * Plays out {@link #HORIZON} ticks of forward at {@code yaw} (sprinting if allowed), jumping as
     * {@code jump} says (-1 never, k at the first chance from tick k, 99 whenever on the ground).
     * Returns the progress along the path at the end, or -1e9 for an unsafe outcome.
     */
    private double rollout(BotPlayer body, Level level, Vec3 v0, boolean ground0, float yaw, int jump, boolean sprint) {
        AABB box = body.getBoundingBox();
        double vx = v0.x, vy = v0.y, vz = v0.z, fall = Math.max(0, body.fallDistance);
        boolean ground = ground0;
        double sin = Mth.sin(yaw * Mth.DEG_TO_RAD), cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double worstDev = 0;
        int noJump = 0;
        for (int t = 0; t < HORIZON; t++) {
            if (noJump > 0) noJump--;
            boolean press = jump == 99 || jump >= 0 && t >= jump;
            if (press && ground && noJump == 0) {
                vy = JUMP;
                if (sprint) {
                    vx += -sin * SPRINT_JUMP;
                    vz += cos * SPRINT_JUMP;
                }
                noJump = 10;
                if (jump != 99) jump = -1;
            }
            BlockPos below = BlockPos.containing(box.getCenter().x, box.minY - 0.5000001, box.getCenter().z);
            double friction = ground ? level.getBlockState(below).getBlock().getFriction() : 1.0;
            double speed = ground ? WALK * (sprint ? SPRINT : 1) * (0.21600002 / (friction * friction * friction)) : sprint ? FLY_SPRINT : FLY;
            vx += -sin * 0.98 * speed;
            vz += cos * 0.98 * speed;
            Vec3 want = new Vec3(vx, vy, vz);
            Vec3 got = collide(body, level, box, want, ground);
            box = box.move(got);
            boolean hitX = Math.abs(got.x - want.x) > 1e-7, hitZ = Math.abs(got.z - want.z) > 1e-7, hitY = Math.abs(got.y - want.y) > 1e-7;
            boolean landed = hitY && want.y < 0;
            if (landed) {
                if (fall > 3.2) return -1e9;
                fall = 0;
            } else if (got.y < 0) fall -= got.y;
            ground = landed;
            if (hitX) vx = 0;
            if (hitZ) vz = 0;
            if (hitY) vy = 0;
            double f1 = friction * AIR;
            vx *= f1;
            vz *= f1;
            vy = (vy - GRAVITY) * DRAG;
            // Safe: in the corridor, not below the path, out of lava and fire and unplanned water.
            double x = (box.minX + box.maxX) / 2, y = box.minY, z = (box.minZ + box.maxZ) / 2;
            double[] proj = project(x, z);
            if (proj[1] > 1.5) return -1e9;
            worstDev = Math.max(worstDev, proj[1]);
            if (y < proj[2] - 1.2) return -1e9;
            BlockPos feet = BlockPos.containing(x, y + 0.1, z);
            var fs = level.getFluidState(feet);
            if (fs.is(FluidTags.LAVA) || level.getBlockState(feet).is(net.minecraft.tags.BlockTags.FIRE)) return -1e9;
            if (fs.is(FluidTags.WATER) && !wet[(int) proj[3]]) return -1e9;
        }
        double[] end = project((box.minX + box.maxX) / 2, (box.minZ + box.maxZ) / 2);
        // (A little for keeping to the middle: of two as far, the one less likely to clip a corner.)
        return end[0] - 0.15 * worstDev;
    }

    /** The game's own collision with the level's shapes, with the step up a block's lip (0.6) on the ground. */
    private static Vec3 collide(Entity body, Level level, AABB box, Vec3 want, boolean ground) {
        Vec3 got = Entity.collideBoundingBox(body, want, box, level, List.of());
        boolean blocked = Math.abs(got.x - want.x) > 1e-7 || Math.abs(got.z - want.z) > 1e-7;
        boolean landing = got.y != want.y && want.y < 0;
        if (!blocked || !(ground || landing)) return got;
        // Up the step, across, down again; kept if it gets further across.
        AABB start = landing ? box.move(0, got.y, 0) : box;
        Vec3 up = Entity.collideBoundingBox(body, new Vec3(0, STEP, 0), start, level, List.of());
        AABB raised = start.move(up);
        Vec3 across = Entity.collideBoundingBox(body, new Vec3(want.x, 0, want.z), raised, level, List.of());
        AABB moved = raised.move(across);
        Vec3 down = Entity.collideBoundingBox(body, new Vec3(0, -up.y + (landing ? 0 : want.y), 0), moved, level, List.of());
        Vec3 stepped = new Vec3(across.x, (landing ? got.y : 0) + up.y + down.y, across.z);
        return stepped.horizontalDistanceSqr() > got.horizontalDistanceSqr() + 1e-7 ? stepped : got;
    }

    /** Where {@code (x, z)} lies along the path: {progress, distance off it, the path's height there, the step's index}. */
    private double[] project(double x, double z) {
        double best = Double.MAX_VALUE, prog = 0, hy = py[0];
        int idx = 0;
        for (int i = 1; i < n; i++) {
            double ax = px[i - 1], az = pz[i - 1], bx = px[i], bz = pz[i];
            double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
            double f = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len2));
            double qx = ax + dx * f, qz = az + dz * f;
            double d = Math.hypot(x - qx, z - qz);
            if (d < best - 1e-9) {
                best = d;
                prog = arc[i - 1] + (arc[i] - arc[i - 1]) * f;
                hy = Math.min(py[i - 1], py[i]);
                idx = f > 0.5 ? i : i - 1;
            }
        }
        return new double[]{prog, best, hy, idx};
    }
}
