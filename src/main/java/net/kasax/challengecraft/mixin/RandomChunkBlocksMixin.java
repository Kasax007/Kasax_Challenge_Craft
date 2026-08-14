package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_16_RandomChunkBlocks;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
/** Runs deferred chunk replacement after generation has produced its final block states. */
public class RandomChunkBlocksMixin {

    @Inject(
        method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
        at = @At("RETURN")
    )
    private void onGenerateFeatures(WorldGenLevel world, ChunkAccess chunk, StructureManager structureAccessor, CallbackInfo ci) {
        if (!Chal_16_RandomChunkBlocks.isActive()) return;
        Chal_16_RandomChunkBlocks.replaceChunkBlocks(world, chunk);

        Chal_16_RandomChunkBlocks.applyPendingReplacements(world);
    }
}
