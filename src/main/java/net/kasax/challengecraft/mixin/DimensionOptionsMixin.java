package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelStem.class)
/**
 * Keeps a dimension's chunk generator in step with whether Skyblock (11) is switched on.
 *
 * <p>Both directions matter, and that is the whole point of this class. Forcing the Skyblock
 * generator when the challenge is ON is the obvious half. The other half exists because this
 * accessor is what Minecraft SERIALISES: once a Skyblock world has been saved, its own generator
 * settings say {@code challengecraft:skyblock}, and merely ceasing to force the swap leaves the
 * world loading the Skyblock generator off disk forever. Deselecting the challenge and restarting
 * produced another Skyblock world for exactly that reason — the challenge really was off, the
 * WORLD was still Skyblock.
 *
 * <p>So when the challenge is off and the stored generator is ours, a vanilla generator is
 * substituted instead. That also repairs the file: the next save writes the vanilla generator back.
 *
 * <p>Injected at RETURN rather than HEAD because the decision needs to see what the stored
 * generator actually is.
 */
public abstract class DimensionOptionsMixin {

    @Inject(method = "generator", at = @At("RETURN"), cancellable = true)
    private void onGetChunkGenerator(CallbackInfoReturnable<ChunkGenerator> cir) {
        LevelStem self = (LevelStem) (Object) this;
        boolean overworld = self.type().is(BuiltinDimensionTypes.OVERWORLD);
        boolean nether = self.type().is(BuiltinDimensionTypes.NETHER);
        if (!overworld && !nether) return;

        if (Chal_11_SkyblockWorld.isActive()) {
            ChunkGenerator sky = overworld
                    ? Chal_11_SkyblockWorld.getOverworldGenerator()
                    : Chal_11_SkyblockWorld.getNetherGenerator();
            if (sky != null) {
                cir.setReturnValue(sky);
            }
            return;
        }

        // Challenge off: strip a Skyblock generator left behind in the saved world settings.
        if (cir.getReturnValue() instanceof SkyblockChunkGenerator) {
            ChunkGenerator vanilla = overworld
                    ? Chal_11_SkyblockWorld.getVanillaOverworldGenerator()
                    : Chal_11_SkyblockWorld.getVanillaNetherGenerator();
            if (vanilla != null) {
                cir.setReturnValue(vanilla);
            }
        }
    }
}
