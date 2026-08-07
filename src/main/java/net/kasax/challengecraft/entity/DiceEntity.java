package net.kasax.challengecraft.entity;

import net.kasax.challengecraft.challenges.Chal_46_Dice;
import net.kasax.challengecraft.challenges.dice.DiePhysics;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A physically tumbling d6. Thrown by the dice item of challenge 46; whichever face is pointing up
 * when it comes to rest becomes the thrower's movement budget.
 *
 * <p><b>Fairness:</b> the outcome is NOT steered. The starting orientation is drawn uniformly from
 * the 24 cube rotations, which makes the settled face exactly uniform regardless of how biased the
 * tumble is — see {@link DiePhysics#randomStart}. Consequently <b>nothing in this tick loop may
 * branch on the orientation</b>; doing so would break that proof.
 *
 * <p><b>Sides:</b> physics runs server-only. The client receives positions from the normal entity
 * tracker and smooths them with a {@link PositionInterpolator} (a plain {@code Entity} returns
 * {@code null} from {@code getInterpolator()} and would hard-snap on every packet), and receives
 * the orientation through tracked data via {@link #onTrackedDataSet}.
 */
public class DiceEntity extends Entity {
    private static final TrackedData<Quaternionf> ORIENTATION =
            DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.QUATERNION_F);
    /** Stays 0 until the die begins settling, so the result cannot be read early. */
    private static final TrackedData<Byte> PIPS =
            DataTracker.registerData(DiceEntity.class, TrackedDataHandlerRegistry.BYTE);

    private static final int DISPLAY_TICKS_AFTER_REST = 60; // let the player see the result

    private final PositionInterpolator interpolator = new PositionInterpolator(this, 3);

    /** Server-authoritative orientation; on the client this mirrors the tracked value. */
    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf lastOrientation = new Quaternionf();
    /** Angular velocity in the WORLD frame, radians/tick (Quaternionf.integrate pre-multiplies). */
    private final Vector3f omega = new Vector3f();

    private Quaternionf settleFrom;
    private Quaternionf settleTarget;
    private int settleTicks = -1;
    private int restTicks;
    private int flightTicks;
    private int restedTicks = -1;
    private UUID throwerUuid;
    private boolean reported;

    public DiceEntity(EntityType<? extends DiceEntity> type, World world) {
        super(type, world);
    }

    @Override
    public PositionInterpolator getInterpolator() {
        return this.interpolator;
    }

    // ---- the four abstract members -----------------------------------------------------------

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(ORIENTATION, new Quaternionf());
        builder.add(PIPS, (byte) 0);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    // The type is built with disableSaving(), so these stay empty — but Entity still declares
    // them abstract, so they must exist.
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    // ---- client-side orientation intake -------------------------------------------------------

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (ORIENTATION.equals(data)) {
            // lastOrientation is snapshotted at the start of each client tick (see tick()), the
            // same way vanilla keeps lastX/lastY/lastZ — do NOT also roll it here, or a second
            // packet within one tick would collapse the interpolation window.
            this.orientation.set(getDataTracker().get(ORIENTATION));
        }
    }

    /** Live orientation. The renderer slerps between this and {@link #getLastOrientation()}. */
    public Quaternionf getOrientation() {
        return this.orientation;
    }

    /** Orientation as of the previous sync, for render interpolation. */
    public Quaternionf getLastOrientation() {
        return this.lastOrientation;
    }

    public int getPips() {
        return getDataTracker().get(PIPS);
    }

    @Override
    protected double getGravity() {
        return DiePhysics.GRAVITY;
    }

    /** Like ItemEntity: sample the block actually underneath a small entity. */
    @Override
    public BlockPos getVelocityAffectingPos() {
        return getPosWithYOffset(0.999999f);
    }

    // ---- throw ---------------------------------------------------------------------------------

    /** Server-side. Launches the die from the player's view with a random tumble. */
    public void throwFrom(ServerPlayerEntity player) {
        this.throwerUuid = player.getUuid();

        Vec3d look = player.getRotationVec(1.0f);
        setVelocity(look.multiply(0.55).add(0.0, 0.18, 0.0)
                .add(this.random.nextGaussian() * 0.02, 0.0, this.random.nextGaussian() * 0.02));

        this.omega.set((float) this.random.nextGaussian(),
                (float) this.random.nextGaussian(),
                (float) this.random.nextGaussian());
        if (this.omega.lengthSquared() < 1.0e-6f) {
            this.omega.set(1f, 0.3f, 0.2f);
        }
        this.omega.normalize().mul(0.9f + this.random.nextFloat() * 1.2f);

        // The entire fairness mechanism, in one line. See DiePhysics.randomStart.
        this.orientation.set(DiePhysics.randomStart(this.random));
        this.lastOrientation.set(this.orientation);
        pushOrientation();
    }

    private void pushOrientation() {
        getDataTracker().set(ORIENTATION, new Quaternionf(this.orientation));
    }

    // ---- tick ----------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (!(getWorld() instanceof ServerWorld serverWorld)) {
            // Snapshot the previous orientation ONCE per tick, mirroring vanilla's lastX/lastY/lastZ.
            // Without this, a resting die keeps slerping between two slightly different quaternions
            // as tickDelta cycles 0..1 every frame — which reads as a permanent micro-jitter.
            this.lastOrientation.set(this.orientation);
            this.interpolator.tick();
            return;
        }

        if (this.restedTicks >= 0) {
            if (++this.restedTicks > DISPLAY_TICKS_AFTER_REST) discard();
            return;
        }
        if (this.settleTicks >= 0) {
            tickSettle();
            return;
        }

        this.flightTicks++;

        // ItemEntity's buoyancy helpers are private, so rather than half-reimplementing them a die
        // that hits liquid simply resolves where it is.
        if (isTouchingWater() || isInLava()) {
            beginSettle(serverWorld);
            return;
        }

        applyGravity();

        Vec3d before = getVelocity();
        move(MovementType.SELF, before);
        Vec3d after = getVelocity();

        // Vanilla's stuck-in-a-block remedy.
        if (!getWorld().isSpaceEmpty(this, getBoundingBox().contract(1.0e-7))) {
            pushOutOfBlocks(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2.0, getZ());
        }

        double vx = after.x;
        double vy = after.y;
        double vz = after.z;
        boolean impact = false;

        if (this.verticalCollision) {
            impact = true;
            if (before.y < 0.0 && this.groundCollision) {
                vy = -before.y * DiePhysics.FLOOR_RESTITUTION;
                if (vy < DiePhysics.BOUNCE_CUTOFF) vy = 0.0;
            } else {
                vy = 0.0;
            }
        }
        if (this.horizontalCollision) {
            impact = true;
            if (Math.abs(after.x) < 1.0e-8 && Math.abs(before.x) > 1.0e-8) {
                vx = -before.x * DiePhysics.WALL_RESTITUTION;
            }
            if (Math.abs(after.z) < 1.0e-8 && Math.abs(before.z) > 1.0e-8) {
                vz = -before.z * DiePhysics.WALL_RESTITUTION;
            }
        }

        if (this.groundCollision) {
            vx *= DiePhysics.TANGENT_FRICTION;
            vz *= DiePhysics.TANGENT_FRICTION;
        }
        setVelocity(vx, vy, vz);

        // Angular integration. omega is world-frame because integrate() pre-multiplies.
        this.lastOrientation.set(this.orientation);
        this.orientation.integrate(1.0f, this.omega.x, this.omega.y, this.omega.z).normalize();

        if (impact) {
            this.omega.mul(DiePhysics.SPIN_LOSS_ON_IMPACT);
            if (this.groundCollision) {
                playSound(SoundEvents.BLOCK_BONE_BLOCK_HIT, 0.28f, 1.5f + this.random.nextFloat() * 0.3f);
            }
        }
        this.omega.mul(this.groundCollision ? DiePhysics.GROUND_ANGULAR_DAMP : DiePhysics.AIR_ANGULAR_DAMP);

        pushOrientation();

        boolean atRest = this.groundCollision
                && getVelocity().lengthSquared() < DiePhysics.LIN_EPS_SQ
                && this.omega.lengthSquared() < DiePhysics.ANG_EPS_SQ;
        this.restTicks = atRest ? this.restTicks + 1 : 0;

        if (this.restTicks >= DiePhysics.REST_TICKS_REQUIRED || this.flightTicks > DiePhysics.MAX_FLIGHT_TICKS) {
            beginSettle(serverWorld);
        }
    }

    /** Reads the top face and starts easing onto the matching axis-aligned orientation. */
    private void beginSettle(ServerWorld world) {
        Direction top = DiePhysics.topFace(this.orientation);
        int pips = DiePhysics.pipsForLocalFace(top);

        this.settleFrom = new Quaternionf(this.orientation);
        // MUST preserve the detected face — an unrestricted snap would show a different number
        // than the one awarded about 5% of the time.
        this.settleTarget = DiePhysics.snapPreservingTop(this.orientation, top);
        this.settleTicks = 0;
        this.omega.set(0f);
        setVelocity(Vec3d.ZERO);

        getDataTracker().set(PIPS, (byte) pips);
        playSound(SoundEvents.BLOCK_BONE_BLOCK_PLACE, 0.5f, 1.1f);
    }

    private void tickSettle() {
        this.settleTicks++;
        float t = Math.min(1f, this.settleTicks / (float) DiePhysics.SETTLE_EASE_TICKS);
        float eased = t * t * (3f - 2f * t);

        this.lastOrientation.set(this.orientation);
        this.settleFrom.slerp(this.settleTarget, eased, this.orientation);
        this.orientation.normalize();
        pushOrientation();

        if (t >= 1f) {
            this.settleTicks = -1;
            this.restedTicks = 0;
            report();
        }
    }

    private void report() {
        if (this.reported || this.throwerUuid == null) return;
        this.reported = true;

        // Release the throw lock UNCONDITIONALLY, before anything that can bail out. If the thrower
        // logged off mid-roll, skipping this would leave hasDieInFlight() true for their UUID
        // forever — on reconnect every throw is rejected and, since the die is the only source of
        // movement, that player is permanently frozen. onDiceSettled clears it again; harmless.
        Chal_46_Dice.clearInFlight(this.throwerUuid);

        if (getWorld().getServer() == null) return;
        ServerPlayerEntity player = getWorld().getServer().getPlayerManager().getPlayer(this.throwerUuid);
        if (player != null) {
            Chal_46_Dice.onDiceSettled(player, getPips());
        }
    }

    @Override
    public void onRemoved() {
        // A die that is unloaded or killed mid-flight must not leave the thrower stuck forever.
        if (!getWorld().isClient && this.throwerUuid != null && !this.reported) {
            Chal_46_Dice.clearInFlight(this.throwerUuid);
        }
        super.onRemoved();
    }
}
