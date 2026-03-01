package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_16_RandomChunkBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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

        ChunkPos centerPos = chunk.getPos();
        
        long seed = world.getSeed();
        Block randomBlock = Chal_16_RandomChunkBlocks.getRandomBlockForChunk(seed, centerPos);
        BlockState randomState = randomBlock.getDefaultState();

        int minY = chunk.getBottomY();
        int maxY = minY + chunk.getHeight();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(centerPos.getStartX() + x, y, centerPos.getStartZ() + z);
                    BlockState currentState = chunk.getBlockState(mutable);
                    
                    if (!Chal_16_RandomChunkBlocks.isException(currentState.getBlock())) {
                        chunk.setBlockState(mutable, randomState, 0);
                    }
                }
            }
        }

        // Apply any pending replacements (e.g. leaves that were skipped during generation)
        Chal_16_RandomChunkBlocks.applyPendingReplacements(world);
    }
}
