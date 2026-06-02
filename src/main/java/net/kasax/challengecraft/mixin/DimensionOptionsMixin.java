package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelStem.class)
/** Swaps in custom dimension generators while creating challenge worlds. */
public abstract class DimensionOptionsMixin {

    @Inject(method = "chunkGenerator", at = @At("HEAD"), cancellable = true)
    private void onGetChunkGenerator(CallbackInfoReturnable<ChunkGenerator> cir) {
        if (!Chal_11_SkyblockWorld.isActive()) return;

        LevelStem self = (LevelStem) (Object) this;
        
        if (self.type().is(BuiltinDimensionTypes.OVERWORLD)) {
            ChunkGenerator skyOW = Chal_11_SkyblockWorld.getOverworldGenerator();
            if (skyOW != null) {
                cir.setReturnValue(skyOW);
            }
        } else if (self.type().is(BuiltinDimensionTypes.NETHER)) {
            ChunkGenerator skyNether = Chal_11_SkyblockWorld.getNetherGenerator();
            if (skyNether != null) {
                cir.setReturnValue(skyNether);
            }
        }
    }
}
