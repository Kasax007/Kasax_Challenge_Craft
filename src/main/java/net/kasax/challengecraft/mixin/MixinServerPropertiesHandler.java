package net.kasax.challengecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import net.minecraft.server.dedicated.ServerPropertiesHandler;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(ServerPropertiesHandler.class)
public class MixinServerPropertiesHandler {
    @Inject(
            method = "createDimensionsRegistryHolder",
            at = @At("RETURN"),
            cancellable = true
    )
    private void onCreateDimensionsRegistryHolder(
            RegistryWrapper.WrapperLookup registries,
            CallbackInfoReturnable<DimensionOptionsRegistryHolder> cir
    ) {
        if (!Chal_11_SkyblockWorld.isActive()) return;

        // Grab the holder vanilla just created:
        DimensionOptionsRegistryHolder holder = cir.getReturnValue();

        // Copy its backing map so we can mutate it:
        Map<RegistryKey<DimensionOptions>, DimensionOptions> oldMap = holder.dimensions();
        Map<RegistryKey<DimensionOptions>, DimensionOptions> newMap = new LinkedHashMap<>(oldMap);

        // 1) Extract the vanilla Overworld options & biome source:
        DimensionOptions overworldOpts = oldMap.get(DimensionOptions.OVERWORLD);
        ChunkGenerator vanillaOverworldGen = overworldOpts.chunkGenerator();
        BiomeSource vanillaOverworldBS = vanillaOverworldGen.getBiomeSource();

        // 2) Extract the vanilla Nether options & biome source:
        DimensionOptions netherOpts = oldMap.get(DimensionOptions.NETHER);
        ChunkGenerator vanillaNetherGen = netherOpts.chunkGenerator();
        BiomeSource vanillaNetherBS = vanillaNetherGen.getBiomeSource();

        // 3) Pull in the structure-set lookup for our generator:
        RegistryEntryLookup<StructureSet> structLookup =
                registries.getOrThrow(RegistryKeys.STRUCTURE_SET);

        // 4) Build our void skyblock generators, re-using the exact same BiomeSource
        ChunkGenerator skyOverworld = new SkyblockChunkGenerator(
                structLookup, vanillaOverworldBS, false
        );
        ChunkGenerator skyNether = new SkyblockChunkGenerator(
                structLookup, vanillaNetherBS, true
        );

        // 5) Replace the chunkGenerator in each DimensionOptions, leave the DimensionType unchanged:
        newMap.put(
                DimensionOptions.OVERWORLD,
                new DimensionOptions(overworldOpts.dimensionTypeEntry(), skyOverworld)
        );
        newMap.put(
                DimensionOptions.NETHER,
                new DimensionOptions(netherOpts.dimensionTypeEntry(), skyNether)
        );
        // (The End stays exactly as vanilla, since we never touch DimensionOptions.END.)

        // 6) Build a brand new holder and return it:
        DimensionOptionsRegistryHolder newHolder = new DimensionOptionsRegistryHolder(newMap);
        cir.setReturnValue(newHolder);
    }
}
