package net.kasax.challengecraft.casino;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The croupier who runs the House at world spawn. A plain {@link Entity} rather than a mob on
 * purpose: he needs no AI, no attributes, no pathfinding and must never take damage, despawn or be
 * pushed — everything a {@code Mob} would bring along would have to be switched off again.
 *
 * <p>All his life is on the client: {@code CroupierRenderer} turns his head towards the nearest
 * player, lets him breathe and shuffle, and plays a gesture whenever {@link #GESTURE} ticks up
 * (paying out, taking a deposit, dealing a card). Right-click is handled by
 * {@code UseEntityCallback} in {@link CasinoGames}.
 */
public class CroupierEntity extends Entity {
    /** Increments on every gesture; the client plays the animation matching {@link #GESTURE_KIND}. */
    public static final EntityDataAccessor<Integer> GESTURE =
            SynchedEntityData.defineId(CroupierEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> GESTURE_KIND =
            SynchedEntityData.defineId(CroupierEntity.class, EntityDataSerializers.INT);

    public static final int GESTURE_WAVE = 0;
    public static final int GESTURE_TAKE = 1;
    public static final int GESTURE_PAY = 2;
    public static final int GESTURE_TIP_HAT = 3;

    public CroupierEntity(EntityType<? extends CroupierEntity> type, Level world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GESTURE, 0);
        builder.define(GESTURE_KIND, GESTURE_WAVE);
    }

    public void gesture(int kind) {
        this.entityData.set(GESTURE_KIND, kind);
        this.entityData.set(GESTURE, this.entityData.get(GESTURE) + 1);
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
