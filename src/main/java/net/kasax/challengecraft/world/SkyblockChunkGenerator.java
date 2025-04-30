package net.kasax.challengecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;

import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;

import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.concurrent.CompletableFuture;

public class SkyblockChunkGenerator extends ChunkGenerator {
    private final RegistryEntryLookup<StructureSet> structureSets;
    private final BiomeSource biomeSource;
    private final boolean isNether;

    // 1) The MAP_CODEC that actually builds your generator:
    public static final MapCodec<SkyblockChunkGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                            // 1) Pull the live StructureSet lookup from the registry context:
                            RegistryOps.getEntryLookupCodec(RegistryKeys.STRUCTURE_SET),

                            // 2) Our chosen biome source:
                            BiomeSource.CODEC
                                    .fieldOf("biome_source")
                                    .forGetter(gen -> gen.biomeSource),

                            // 3) Are we the Nether flavour?
                            Codec.BOOL
                                    .fieldOf("is_nether")
                                    .forGetter(gen -> gen.isNether)
                    )
                    .apply(inst, SkyblockChunkGenerator::new)
    );

    // 2) A plain Codec wrapper, used for registering your generator type:
    public static final Codec<SkyblockChunkGenerator> CODEC = MAP_CODEC.codec();

    public SkyblockChunkGenerator(RegistryEntryLookup<StructureSet> structureSets,
                                  BiomeSource biomeSource,
                                  boolean isNether) {
        super(biomeSource);
        this.structureSets = structureSets;
        this.biomeSource   = biomeSource;
        this.isNether      = isNether;
    }

    // 3) Override getCodec() to return the MAP_CODEC:
    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return MAP_CODEC;
    }

    // 1) No terrain—leave every block as air:
    // --- this replaces vanilla terrain noise generation ---
    @Override
    public CompletableFuture<Chunk> populateNoise(
            Blender blender,
            NoiseConfig noiseConfig,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
        // leave everything as air
        return CompletableFuture.completedFuture(chunk);
    }

    // 2) Place island in the spawn chunk (0,0) of the overworld:
    @Override
    public void buildSurface(
            ChunkRegion region,
            StructureAccessor structureAccessor,
            NoiseConfig noiseConfig,
            Chunk chunk
    ) {
        if (isNether) return;
        ChunkPos pos = chunk.getPos();
        if (pos.x != 0 || pos.z != 0) return;

        int y = 64;
        BlockPos.Mutable m = new BlockPos.Mutable();

        // 3×3 dirt platform with grass in center
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                m.set(dx, y - 1, dz);
                region.setBlockState(m,
                        dx == 0 && dz == 0
                                ? net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState()
                                : net.minecraft.block.Blocks.DIRT.getDefaultState(),
                        0);
            }
        }

        // Oak log trunk
        for (int dy = 0; dy < 4; dy++) {
            m.set(0, y + dy, 0);
            region.setBlockState(m, net.minecraft.block.Blocks.OAK_LOG.getDefaultState(), 0);
        }

        // Leaves—simple layers
        int leafY = y + 3;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    m.set(dx, leafY, dz);
                    region.setBlockState(m, net.minecraft.block.Blocks.OAK_LEAVES.getDefaultState(), 0);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                m.set(dx, leafY - 1, dz);
                region.setBlockState(m, net.minecraft.block.Blocks.OAK_LEAVES.getDefaultState(), 0);
            }
        }

        // Chest with starter items
        m.set(1, y - 1, 0);
        region.setBlockState(m, net.minecraft.block.Blocks.CHEST.getDefaultState(), 0);
        var be = region.getBlockEntity(m);
        if (be instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
            chest.setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.LAVA_BUCKET));
            chest.setStack(1, new net.minecraft.item.ItemStack(net.minecraft.item.Items.ICE));
        }
    }

    // 3) No caves/carvers:
    @Override
    public void carve(
            ChunkRegion region,
            long seed,
            NoiseConfig noiseConfig,
            net.minecraft.world.biome.source.BiomeAccess biomeAccess,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
        // nothing
    }

    // 4) Default biome population:
    // --- this replaces vanilla biome population ---
    @Override
    public CompletableFuture<Chunk> populateBiomes(
            NoiseConfig noiseConfig,
            Blender blender,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
        // default biome‐filling (air columns still get their biome data)
        return super.populateBiomes(noiseConfig, blender, structureAccessor, chunk);
    }

    // 5) Default entity spawns:
    @Override
    public void populateEntities(ChunkRegion region) { /* vanilla */ }

    // 6) World heights:
    @Override public int getWorldHeight()   { return 384; }

    @Override public int getSeaLevel()      { return 0; }
    @Override public int getMinimumY()      { return -64; }

    // 7) Fake “surface” height so structures can spawn at y=64:
    @Override
    public int getHeight(
            int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig config
    ) {
        return 64;
    }

    // 8) Return our tiny island column sample (or all air):
    @Override
    public VerticalBlockSample getColumnSample(
            int x, int z, HeightLimitView world, NoiseConfig config
    ) {
        int minY = world.getBottomY();
        int maxY = world.getTopYInclusive() - minY + 1;
        var states = new net.minecraft.block.BlockState[maxY];
        java.util.Arrays.fill(states, net.minecraft.block.Blocks.AIR.getDefaultState());

        if (!isNether && Math.abs(x) <= 1 && Math.abs(z) <= 1) {
            if (x == 0 && z == 0) {
                states[64 - minY] = net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState();
                states[63 - minY] = net.minecraft.block.Blocks.DIRT.getDefaultState();
            } else {
                states[63 - minY] = net.minecraft.block.Blocks.DIRT.getDefaultState();
            }
        }

        return new VerticalBlockSample(minY, states);
    }

//    // 9) Only allow strongholds (or nether complexes):
//    // --- filter which structure sets will generate ---
//    @Override
//    public Stream<RegistryEntry<StructureSet>> streamStructureSets() {
//        return structureSets.streamEntries().filter(entry -> {
//            var key = entry.getKey().orElse(null);
//            if (key == null) return false;
//            return isNether
//                    ? key.equals(RegistryKeys.NETHER_COMPLEXES)
//                    : key.equals(RegistryKeys.STRONGHOLDS);
//        });
//    }

    // 10) Debug HUD (no extra info):
    @Override
    public void appendDebugHudText(
            java.util.List<String> text, NoiseConfig noiseConfig, BlockPos pos
    ) {
        // nothing extra
    }
}
