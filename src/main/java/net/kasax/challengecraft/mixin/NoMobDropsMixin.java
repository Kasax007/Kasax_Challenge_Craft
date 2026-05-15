package net.kasax.challengecraft.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
/** Suppresses mob loot while leaving vanilla XP behavior intact. */
public abstract class NoMobDropsMixin {
    /** Mob loot can be disabled without removing the XP reward for the kill. */
    @Inject(
            method = "dropExperience(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void alwaysDropMobXp(ServerWorld world, @Nullable Entity attacker, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        int xp = self.getExperienceToDrop(world, attacker);
        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();
        ExperienceOrbEntity.spawn(world, new Vec3d(x, y, z), xp);
        ci.cancel();
    }
}
