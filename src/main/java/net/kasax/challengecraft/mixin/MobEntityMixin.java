package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_35_DoubleTrouble;
import net.kasax.challengecraft.util.EntityDoublingAccess;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.kasax.challengecraft.ChallengeCraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
/** Applies configured entity duplication after vanilla spawn setup. */
public abstract class MobEntityMixin implements EntityDoublingAccess {

    @Unique
    private boolean challengecraft$doubled = false;

    @Override
    public void challengecraft$setDoubled(boolean doubled) {
        this.challengecraft$doubled = doubled;
    }

    @Override
    public boolean challengecraft$isDoubled() {
        return challengecraft$doubled;
    }

    // 26.2 moved entity serialisation off raw CompoundTag onto ValueInput/ValueOutput. A mixin
    // handler's parameters must mirror the target's exactly, so these change with it.
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onWriteNbt(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("challengecraft_doubled", challengecraft$doubled);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadNbt(ValueInput input, CallbackInfo ci) {
        challengecraft$doubled = input.getBooleanOr("challengecraft_doubled", false);
    }

    @Inject(method = "baseTick()V", at = @At("HEAD"))
    private void onBaseTick(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (Chal_35_DoubleTrouble.isActive() && !challengecraft$doubled && !mob.level().isClientSide() && !(mob instanceof EnderDragon)) {
            // Only fresh spawns should duplicate; old entities may predate the saved marker.
            if (mob.tickCount < 20) {
                challengecraft$doubled = true;
                ServerLevel world = (ServerLevel) mob.level();
                int multiplier = Chal_35_DoubleTrouble.getMultiplier();
                if (multiplier > 1) {
                    ChallengeCraft.LOGGER.info("[DoubleTrouble] Doubling {} (mult={})", mob.getType().toString(), multiplier);
                    for (int i = 1; i < multiplier; i++) {
                        Mob copy = (Mob) mob.getType().create(world, EntitySpawnReason.EVENT);
                        if (copy != null) {
                            ((EntityDoublingAccess)copy).challengecraft$setDoubled(true);
                            copy.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
                            world.addFreshEntity(copy);
                        }
                    }
                }
            } else {
                challengecraft$doubled = true;
            }
        }
    }
}
