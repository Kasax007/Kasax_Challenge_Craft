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

import static net.kasax.challengecraft.ChallengeManager.LOGGER;

@Mixin(LivingEntity.class)
public abstract class NoMobDropsMixin {
    /**
     * Always spawn mob XP orbs, even if doMobLoot==false.
     */
    @Inject(
            method = "dropExperience(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void alwaysDropMobXp(ServerWorld world, @Nullable Entity attacker, CallbackInfo ci) {
        // cast back to the real LivingEntity so we can call its getters
        LivingEntity self = (LivingEntity)(Object)this;

        // compute exactly the same XP the vanilla method would
        int xp = self.getExperienceToDrop(world, attacker);

        // grab the entity’s coords
        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();

        // spawn the orbs unconditionally
        ExperienceOrbEntity.spawn(world, new Vec3d(x, y, z), xp);

        // cancel the vanilla body (so we skip the game‐rule check)
        ci.cancel();
    }
}
