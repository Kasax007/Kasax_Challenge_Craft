package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_15_RandomMobDrops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
/** Redirects loot-table lookup for the randomized mob-drops challenge. */
public abstract class RandomMobDropsMixin {
    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("HEAD"))
    private void beforeDropLoot(ServerLevel world, DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        if (Chal_15_RandomMobDrops.isActive()) {
            Chal_15_RandomMobDrops.setCurrentWorld(world);
        }
    }

    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("TAIL"))
    private void afterDropLoot(ServerLevel world, DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        Chal_15_RandomMobDrops.setCurrentWorld(null);
    }
}
