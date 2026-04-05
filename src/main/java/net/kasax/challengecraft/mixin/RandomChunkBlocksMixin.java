package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_16_RandomChunkBlocks;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class RandomChunkBlocksMixin {

    @Inject(
        method = "generateFeatures(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/StructureAccessor;)V",
        at = @At("RETURN")
    )
    private void onGenerateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor, CallbackInfo ci) {
        if (!Chal_16_RandomChunkBlocks.isActive()) return;
        Chal_16_RandomChunkBlocks.replaceChunkBlocks(world, chunk);

        // Apply any pending replacements (e.g. leaves that were skipped during generation)
        Chal_16_RandomChunkBlocks.applyPendingReplacements(world);
    }
}
