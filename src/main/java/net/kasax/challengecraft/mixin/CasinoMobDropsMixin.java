package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.casino.LossWaves;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Monsters the House sends after a lost bet drop no loot. Without this a vindicator wave would pay
 * out emeralds and an evoker a totem, turning the punishment into a profit.
 */
@Mixin(LivingEntity.class)
public abstract class CasinoMobDropsMixin {
    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void challengecraft$noCasinoLoot(ServerLevel world, DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).entityTags().contains(LossWaves.TAG)) {
            ci.cancel();
        }
    }
}
