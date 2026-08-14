package net.kasax.challengecraft.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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
            method = "dropExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void alwaysDropMobXp(ServerLevel world, @Nullable Entity attacker, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        int xp = self.getExperienceReward(world, attacker);
        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();
        ExperienceOrb.award(world, new Vec3(x, y, z), xp);
        ci.cancel();
    }
}
