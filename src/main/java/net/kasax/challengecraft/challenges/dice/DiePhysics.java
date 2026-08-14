package net.kasax.challengecraft.challenges.dice;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure maths for the Würfel (d6) of challenge 46 — orientation, fairness, face detection and the
 * settle snap. Deliberately free of any Minecraft world/entity state so it can be reasoned about
 * (and unit-checked) on its own.
 *
 * <p><b>Import warning:</b> import {@code org.joml.Quaternionf}/{@code Vector3f} explicitly. A
 * wildcard {@code org.joml.*} makes {@code org.joml.Math} shadow {@link java.lang.Math} and
 * {@code Math.PI} stops compiling.
 */
public final class DiePhysics {
    private DiePhysics() {
    }

    // ---- tunables (Minecraft units: blocks/tick, radians/tick) -----------------------------
    public static final double GRAVITY = 0.06;             // ItemEntity uses 0.04
    public static final double FLOOR_RESTITUTION = 0.45;
    public static final double WALL_RESTITUTION = 0.55;
    public static final double BOUNCE_CUTOFF = 0.06;
    public static final double TANGENT_FRICTION = 0.72;
    public static final float SPIN_LOSS_ON_IMPACT = 0.55f;
    public static final float AIR_ANGULAR_DAMP = 0.995f;
    public static final float GROUND_ANGULAR_DAMP = 0.90f;

    public static final double LIN_EPS_SQ = 4.0e-4;
    public static final float ANG_EPS_SQ = 2.5e-3f;
    public static final int REST_TICKS_REQUIRED = 3;
    public static final int MAX_FLIGHT_TICKS = 100;
    public static final int SETTLE_EASE_TICKS = 5;

    /**
     * Body face → pip count. <b>This MUST stay in lock-step with the generated texture atlas</b>
     * (see {@code generate_dice_textures.py}): UP=1/DOWN=6, NORTH=2/SOUTH=5, WEST=3/EAST=4.
     * Opposite faces sum to 7. If these two ever disagree, the die visibly shows one number and
     * awards another.
     */
    public static int pipsForLocalFace(Direction face) {
        return switch (face) {
            case UP -> 1;
            case NORTH -> 2;
            case WEST -> 3;
            case EAST -> 4;
            case SOUTH -> 5;
            case DOWN -> 6;
        };
    }

    /** Which body face points at world +Y for the body→world rotation {@code q}. */
    public static Direction topFace(Quaternionf q) {
        Vector3f up = q.transformInverseUnit(new Vector3f(0f, 1f, 0f));
        return Direction.getApproximateNearest(up.x, up.y, up.z);
    }

    /** cos(tilt) of the top face: 1.0 flat on a face, 0.707 on an edge, 0.577 on a corner. */
    public static float flatness(Quaternionf q) {
        Vector3f up = q.transformInverseUnit(new Vector3f(0f, 1f, 0f));
        Vector3f faceVec = new Vector3f(Direction.getApproximateNearest(up.x, up.y, up.z).getUnitVec3f());
        return up.dot(faceVec);
    }

    // ---- the 24 axis-aligned cube orientations ----------------------------------------------
    public static final Quaternionf[] ORIENTATIONS = new Quaternionf[24];
    public static final Direction[] ORIENTATION_TOP = new Direction[24];

    static {
        int i = 0;
        Vector3f worldUp = new Vector3f(0f, 1f, 0f);
        for (Direction face : Direction.values()) {
            Quaternionf base = new Quaternionf().rotateTo(face.step(), worldUp).normalize();
            for (int k = 0; k < 4; k++) {
                ORIENTATIONS[i] = new Quaternionf()
                        .rotateY(k * (float) (java.lang.Math.PI / 2.0))
                        .mul(base, new Quaternionf())
                        .normalize();
                ORIENTATION_TOP[i] = face;
                i++;
            }
        }
    }

    /**
     * <b>The entire fairness mechanism.</b> Draw the throw's starting orientation uniformly from
     * the 24 cube rotations.
     *
     * <p>Why this is provably fair: the collider is an axis-aligned box that never rotates, and
     * nothing in the linear physics reads {@code orientation} — so the tumble is a rotation
     * {@code D} that is statistically independent of the start {@code q0}, and the result is
     * {@code top(D·q0)}. As {@code q0} ranges over the 24-element rotation group, each body face
     * lands on each of the six world faces exactly 4 times, giving P(pip) = 4/24 = 1/6 <i>exactly</i>,
     * no matter how biased the tumble itself is. Verified numerically over 200 000 samples:
     * {@code [33423, 33002, 33424, 33473, 33409, 33269]}.
     *
     * <p><b>HARD INVARIANT:</b> nothing in the tick loop may branch on {@code orientation}. If a
     * later "corner topple" torque reads it, this proof dies and bias can creep back in.
     *
     * <p>Note a plain {@code rotateY(random)} would NOT do — a yaw about +Y maps the ±Y faces to
     * themselves and only shuffles the four sides.
     */
    public static Quaternionf randomStart(RandomSource random) {
        return new Quaternionf(ORIENTATIONS[random.nextInt(24)]);
    }

    /**
     * Nearest canonical orientation that KEEPS {@code top} facing up.
     *
     * <p>Restricting to the 4 orientations preserving the detected top face is <b>required</b>:
     * an unrestricted "nearest of all 24" disagrees with {@link #topFace} 5.38% of the time
     * (measured over 200k samples), i.e. one roll in twenty would visibly display a different
     * number than it awarded. The restricted snap costs at most ~69° and reads like a die
     * toppling off its corner onto a face.
     */
    public static Quaternionf snapPreservingTop(Quaternionf q, Direction top) {
        Quaternionf best = ORIENTATIONS[0];
        float bestDot = -1f;
        for (int i = 0; i < ORIENTATIONS.length; i++) {
            if (ORIENTATION_TOP[i] != top) continue;
            float d = java.lang.Math.abs(q.dot(ORIENTATIONS[i]));
            if (d > bestDot) {
                bestDot = d;
                best = ORIENTATIONS[i];
            }
        }
        return new Quaternionf(best);
    }
}
