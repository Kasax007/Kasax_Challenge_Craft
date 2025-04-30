// src/main/java/net/kasax/challengecraft/mixin/CreateWorldMixinIntegratedServer.java
package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.*;
import net.minecraft.server.integrated.IntegratedServerLoader;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.HashMap;
import java.util.Map;

@Mixin(CreateWorldScreen.class)
public class CreateWorldMixinIntegratedServer {
    @Shadow private WorldCreator worldCreator;

    @ModifyArg(
            method = "createAndClearTempDir(Lnet/minecraft/registry/CombinedDynamicRegistries;"
                    + "Lnet/minecraft/world/level/LevelProperties;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/world/CreateWorldCallback;"
                            + "create(Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;"
                            + "Lnet/minecraft/registry/CombinedDynamicRegistries;"
                            + "Lnet/minecraft/world/level/LevelProperties;"
                            + "Ljava/nio/file/Path;)Z"
            ),
            index = 1
    )
    private CombinedDynamicRegistries<ServerDynamicRegistryType> swapInSkyblockGenerator(
            CombinedDynamicRegistries<ServerDynamicRegistryType> original
    ) {
        // only swap if challenge #11 is active
        if (!ChallengeCraftClient.LAST_CHOSEN.contains(11)) {
            return original;
        }

        // grab what the screen built
        GeneratorOptionsHolder opts      = this.worldCreator.getGeneratorOptionsHolder();
        DimensionOptionsRegistryHolder oldHolder = opts.selectedDimensions();

        // fetch the live StructureSet lookup from the same registry context
        var dyn = original.getCombinedRegistryManager();
        @SuppressWarnings("unchecked")
        RegistryEntryLookup<StructureSet> structLookup =
                ((RegistryEntryLookup.RegistryLookup) dyn)
                        .getOrThrow(RegistryKeys.STRUCTURE_SET);

        // 1) Overworld → skyblock
        DimensionOptions overworldOpt = oldHolder
                .getOrEmpty(DimensionOptions.OVERWORLD)
                .orElseThrow(() -> new IllegalStateException("Missing overworld options"));
        ChunkGenerator vanillaOW     = overworldOpt.chunkGenerator();
        BiomeSource biomeOW         = vanillaOW.getBiomeSource();
        ChunkGenerator skyOW        = new SkyblockChunkGenerator(structLookup, biomeOW, /*isNether=*/false);
        DimensionOptions newOW       = new DimensionOptions(overworldOpt.dimensionTypeEntry(), skyOW);

        // 2) Nether → void Nether
        DimensionOptions netherOpt   = oldHolder
                .getOrEmpty(DimensionOptions.NETHER)
                .orElseThrow(() -> new IllegalStateException("Missing nether options"));
        ChunkGenerator vanillaNether = netherOpt.chunkGenerator();
        BiomeSource biomeNether      = vanillaNether.getBiomeSource();
        ChunkGenerator skyNether     = new SkyblockChunkGenerator(structLookup, biomeNether, /*isNether=*/true);
        DimensionOptions newNether   = new DimensionOptions(netherOpt.dimensionTypeEntry(), skyNether);

        // 3) Copy the old map, overwrite both keys
        Map<RegistryKey<DimensionOptions>, DimensionOptions> map = new HashMap<>(oldHolder.dimensions());
        map.put(DimensionOptions.OVERWORLD, newOW);
        map.put(DimensionOptions.NETHER,   newNether);

        // 4) Rebuild holder & registry‐manager
        DimensionOptionsRegistryHolder newHolder = new DimensionOptionsRegistryHolder(map);
        var cfg    = newHolder.toConfig(opts.dimensionOptionsRegistry());
        var newMgr = cfg.toDynamicRegistryManager();

        // 5) Swap just the DIMENSIONS registry in the combined registries
        return original.with(ServerDynamicRegistryType.DIMENSIONS, newMgr);
    }
}
