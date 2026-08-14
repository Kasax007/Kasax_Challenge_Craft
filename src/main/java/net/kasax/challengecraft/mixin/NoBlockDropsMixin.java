package net.kasax.challengecraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
/** Suppresses block loot while leaving vanilla XP behavior intact. */
public class NoBlockDropsMixin {
    @Shadow @Final private static Logger LOGGER;

    /** Block drops can be disabled without suppressing the XP vanilla would have awarded. */
    @Inject(
            method = "popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void alwaysDropBlockXp(ServerLevel world, BlockPos pos, int size, CallbackInfo ci) {
        ExperienceOrb.award(world, Vec3.atCenterOf(pos), size);
        ci.cancel();
    }
}
