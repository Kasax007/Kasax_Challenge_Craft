package net.kasax.challengecraft.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class NoBlockDropsMixin {
    @Shadow @Final private static Logger LOGGER;

    /**
     * Always drop block‐mining XP orbs, regardless of GameRules.DO_TILE_DROPS.
     */
    @Inject(
            method = "dropExperience(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void alwaysDropBlockXp(ServerWorld world, BlockPos pos, int size, CallbackInfo ci) {
        // spawn XP exactly as vanilla would:
        ExperienceOrbEntity.spawn(world, Vec3d.ofCenter(pos), size);
        // skip the vanilla rule‐check:
        ci.cancel();
    }
}
