package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_16_RandomChunkBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.StructureWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRegion.class)
public abstract class ChunkRegionMixin {

    @Shadow public abstract long getSeed();
    @Shadow public abstract boolean setBlockState(BlockPos pos, BlockState state, int flags, int limit);

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSetBlockState(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
        if (Chal_16_RandomChunkBlocks.isActive()) {
            // Safety: Don't replace LeavesBlock as it might cause a crash in TreeFeature.
            // These will be recorded and replaced at the end of feature generation.
            if (state.getBlock() instanceof net.minecraft.block.LeavesBlock) {
                Chal_16_RandomChunkBlocks.recordPendingReplacement((StructureWorldAccess)(Object)this, pos);
                return;
            }

            if (!Chal_16_RandomChunkBlocks.isException(state.getBlock())) {
                long seed = this.getSeed();
                Block randomBlock = Chal_16_RandomChunkBlocks.getRandomBlockForChunk(seed, new ChunkPos(pos));
                BlockState randomState = randomBlock.getDefaultState();
                
                // If the block being placed is NOT our random block, replace it.
                // This will recurse once, and the second call will have state == randomState, 
                // so the recursion stops.
                if (state.getBlock() != randomState.getBlock()) {
                    cir.setReturnValue(this.setBlockState(pos, randomState, flags, limit));
                }
            }
        }
    }
}
