// src/main/java/net/kasax/challengecraft/mixin/CreateWorldMixinIntegratedServer.java
package net.kasax.challengecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.CreateWorldCallback;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.*;
import net.minecraft.server.integrated.IntegratedServerLoader;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.file.Path;

@Mixin(CreateWorldScreen.class)
@Environment(EnvType.CLIENT)
public class CreateWorldMixinIntegratedServer {
    // Pull the screen’s private WorldCreator so we can get at the GeneratorOptionsHolder
    @Shadow private WorldCreator worldCreator;

    /**
     * Intercept the invoke of CreateWorldCallback.create(this, combinedRegistries, levelProperties, tempDir)
     * in CreateWorldScreen#createAndClearTempDir(...) and replace the 2nd argument (the CombinedDynamicRegistries)
     * with one whose OVERWORLD generator has been swapped to our void‐+‐island SkyblockChunkGenerator.
     */
    @ModifyArg(
            method = "createAndClearTempDir(Lnet/minecraft/registry/CombinedDynamicRegistries;Lnet/minecraft/world/level/LevelProperties;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/world/CreateWorldCallback;" +
                            "create(Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;" +
                            "Lnet/minecraft/registry/CombinedDynamicRegistries;" +
                            "Lnet/minecraft/world/level/LevelProperties;" +
                            "Ljava/nio/file/Path;)Z"
            ),
            index = 1
    )
    private CombinedDynamicRegistries<ServerDynamicRegistryType> swapInSkyblockGenerator(
            CombinedDynamicRegistries<ServerDynamicRegistryType> original
    ) {
        // only when the Skyblock challenge is active
        if (!Chal_11_SkyblockWorld.isActive()) {
            return original;
        }

        // 1) grab the GeneratorOptionsHolder the screen built earlier
        GeneratorOptionsHolder opts = this.worldCreator.getGeneratorOptionsHolder();

        // 2) pull out its DimensionOptionsRegistryHolder (which holds the vanilla Overworld generator)
        DimensionOptionsRegistryHolder oldHolder = opts.selectedDimensions();

        // 3) extract the vanilla Overworld generator and its BiomeSource
        ChunkGenerator vanillaOverworld = oldHolder.getChunkGenerator();
        var vanillaBiomeSource = vanillaOverworld.getBiomeSource();

        // 4) fetch the live StructureSet lookup
        DynamicRegistryManager.Immutable dyn = original.getCombinedRegistryManager();
        RegistryEntryLookup<StructureSet> structLookup =
                dyn.getOrThrow(RegistryKeys.STRUCTURE_SET);

        // 5) build our SkyblockChunkGenerator re‐using the vanilla biome source
        ChunkGenerator skyOverworld = new SkyblockChunkGenerator(
                structLookup,
                vanillaBiomeSource,
                false
        );

        // 6) produce a new DimensionOptionsRegistryHolder override _only_ the overworld
        //    we pass in the same DimensionType registry wrapper that the screen used
        DimensionOptionsRegistryHolder newHolder = oldHolder.with(
                (RegistryWrapper.WrapperLookup) opts.dimensionOptionsRegistry(), // registry of DimensionType
                skyOverworld
        );

        // 7) turn that into a DimensionsConfig and then into a DynamicRegistryManager slice
        var dimsConfig = newHolder.toConfig(opts.dimensionOptionsRegistry());
        var newDimRegistryMgr = dimsConfig.toDynamicRegistryManager();

        // 8) finally, swap _just_ the DIMENSIONS slice in the CombinedDynamicRegistries
        return original.with(
                ServerDynamicRegistryType.DIMENSIONS,
                newDimRegistryMgr
        );
    }
}
