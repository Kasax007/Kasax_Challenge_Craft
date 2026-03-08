package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(DimensionOptionsRegistryHolder.class)
public abstract class DimensionOptionsRegistryHolderMixin {
    
    @ModifyVariable(method = "<init>(Ljava/util/Map;)V", at = @At("HEAD"), argsOnly = true)
    private static Map<RegistryKey<DimensionOptions>, DimensionOptions> swapToSkyblock(Map<RegistryKey<DimensionOptions>, DimensionOptions> dimensions) {
        if (!Chal_11_SkyblockWorld.isActive()) return dimensions;

        // Note: At this early stage of world creation, we might not have all registries available.
        // We'll try to find a way to get the StructureSet lookup.
        // BUT wait! This constructor might be called multiple times.
        
        // Let's see if we can identify if we have enough context to build our Skyblock generator.
        // SkyblockChunkGenerator needs a RegistryEntryLookup<StructureSet>.
        
        // Wait! In 1.20+, ChunkGenerator.getBiomeSource() exists.
        // In 1.21.5, SkyblockChunkGenerator constructor takes (RegistryEntryLookup<StructureSet>, BiomeSource, boolean).

        // If we're in the constructor, we don't have the server yet?
        // But we have the vanilla generators in the map!
        
        boolean hasOverworld = dimensions.containsKey(DimensionOptions.OVERWORLD);
        if (!hasOverworld) return dimensions;

        try {
            DimensionOptions overworldOpts = dimensions.get(DimensionOptions.OVERWORLD);
            ChunkGenerator vanillaOverworldGen = overworldOpts.chunkGenerator();
            
            // We need a structure set lookup. 
            // In 1.21.5, where can we get it from?
            // Maybe from the vanilla generator's structures? 
            // No, that's not easily accessible.
            
            // Wait! If we can't get the lookup here, we might have to use a different approach.
        } catch (Exception ignored) {}

        return dimensions;
    }
}
