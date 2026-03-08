package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionOptions.class)
public abstract class DimensionOptionsMixin {

    @Inject(method = "chunkGenerator", at = @At("HEAD"), cancellable = true)
    private void onGetChunkGenerator(CallbackInfoReturnable<ChunkGenerator> cir) {
        if (!Chal_11_SkyblockWorld.isActive()) return;

        DimensionOptions self = (DimensionOptions) (Object) this;
        
        if (self.dimensionTypeEntry().matchesKey(DimensionTypes.OVERWORLD)) {
            ChunkGenerator skyOW = Chal_11_SkyblockWorld.getOverworldGenerator();
            if (skyOW != null) {
                cir.setReturnValue(skyOW);
            }
        } else if (self.dimensionTypeEntry().matchesKey(DimensionTypes.THE_NETHER)) {
            ChunkGenerator skyNether = Chal_11_SkyblockWorld.getNetherGenerator();
            if (skyNether != null) {
                cir.setReturnValue(skyNether);
            }
        }
    }
}
