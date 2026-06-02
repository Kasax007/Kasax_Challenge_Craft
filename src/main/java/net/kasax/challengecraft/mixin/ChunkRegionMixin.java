package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_16_RandomChunkBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
/** Captures structure placement writes so random chunk replacement can finish after generation. */
public abstract class ChunkRegionMixin {

    @Shadow public abstract long getSeed();
    @Shadow public abstract boolean setBlockState(BlockPos pos, BlockState state, int flags, int limit);

    @Inject(
        method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSetBlockState(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
        if (Chal_16_RandomChunkBlocks.isActive()) {
            // Tree features expect their leaves during placement, so defer those replacements.
            if (state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) {
                Chal_16_RandomChunkBlocks.recordPendingReplacement((WorldGenLevel)(Object)this, pos);
                return;
            }

            if (!Chal_16_RandomChunkBlocks.isException(state.getBlock())) {
                long seed = this.getSeed();
                Block randomBlock = Chal_16_RandomChunkBlocks.getRandomBlockForChunk(seed, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
                BlockState randomState = randomBlock.defaultBlockState();
                
                // setBlockState recurses once through the injected method; the randomized state stops the loop.
                if (state.getBlock() != randomState.getBlock()) {
                    cir.setReturnValue(this.setBlockState(pos, randomState, flags, limit));
                }
            }
        }
    }
}
