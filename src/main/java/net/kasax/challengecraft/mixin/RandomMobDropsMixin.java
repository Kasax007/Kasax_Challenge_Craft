package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_15_RandomMobDrops;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class RandomMobDropsMixin {
    @Inject(method = "dropLoot(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;Z)V", at = @At("HEAD"))
    private void beforeDropLoot(ServerWorld world, DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        if (Chal_15_RandomMobDrops.isActive()) {
            Chal_15_RandomMobDrops.setCurrentWorld(world);
        }
    }

    @Inject(method = "dropLoot(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;Z)V", at = @At("TAIL"))
    private void afterDropLoot(ServerWorld world, DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        Chal_15_RandomMobDrops.setCurrentWorld(null);
    }
}
