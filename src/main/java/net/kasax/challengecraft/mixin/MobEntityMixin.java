package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_35_DoubleTrouble;
import net.kasax.challengecraft.util.EntityDoublingAccess;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
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

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void onWriteNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("challengecraft_doubled", challengecraft$doubled);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void onReadNbt(NbtCompound nbt, CallbackInfo ci) {
        challengecraft$doubled = nbt.getBoolean("challengecraft_doubled").orElse(false);
    }

    @Inject(method = "baseTick()V", at = @At("HEAD"))
    private void onBaseTick(CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        if (Chal_35_DoubleTrouble.isActive() && !challengecraft$doubled && !mob.getWorld().isClient && !(mob instanceof EnderDragonEntity)) {
            // Only fresh spawns should duplicate; old entities may predate the saved marker.
            if (mob.age < 20) {
                challengecraft$doubled = true;
                ServerWorld world = (ServerWorld) mob.getWorld();
                int multiplier = Chal_35_DoubleTrouble.getMultiplier();
                if (multiplier > 1) {
                    ChallengeCraft.LOGGER.info("[DoubleTrouble] Doubling {} (mult={})", mob.getType().toString(), multiplier);
                    for (int i = 1; i < multiplier; i++) {
                        MobEntity copy = (MobEntity) mob.getType().create(world, SpawnReason.EVENT);
                        if (copy != null) {
                            ((EntityDoublingAccess)copy).challengecraft$setDoubled(true);
                            copy.refreshPositionAndAngles(mob.getX(), mob.getY(), mob.getZ(), mob.getYaw(), mob.getPitch());
                            world.spawnEntity(copy);
                        }
                    }
                }
            } else {
                challengecraft$doubled = true;
            }
        }
    }
}
