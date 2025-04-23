package net.kasax.challengecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_3_NoMobDrops;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.SERVER)
@Mixin(LivingEntity.class)
public abstract class NoMobDropsMixin {
    /**
     * Intercept the protected drop(ServerWorld, DamageSource) method.
     * When Chal_3_NoMobDrops is active, only spawn XP orbs and skip all item drops.
     */
    @Inject(
            method = "drop(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onDrop(ServerWorld world, DamageSource source, CallbackInfo ci) {
        if (Chal_3_NoMobDrops.isActive()) {
            LivingEntity self = (LivingEntity)(Object)this;
            // attacker may be null
            var attacker = source.getAttacker();
            // get the correct XP amount
            int xp = self.getExperienceToDrop(world, attacker);
            // spawn XP orbs at the mob's position
            ExperienceOrbEntity.spawn(world, self.getPos(), xp);
            // cancel the rest: no loot, equipment, inventory drops
            ci.cancel();
        }
    }
}
