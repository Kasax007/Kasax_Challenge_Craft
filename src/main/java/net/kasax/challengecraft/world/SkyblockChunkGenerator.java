package net.kasax.challengecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.*;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.*;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;

import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;

import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Void-world generator that preserves the small amount of structure support skyblock runs need. */
public class SkyblockChunkGenerator extends ChunkGenerator {
    private final RegistryEntryLookup<StructureSet> structureSets;
    private final BiomeSource biomeSource;
    private final boolean isNether;

    public static final MapCodec<SkyblockChunkGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                            RegistryOps.getEntryLookupCodec(RegistryKeys.STRUCTURE_SET),
                            BiomeSource.CODEC
                                    .fieldOf("biome_source")
                                    .forGetter(gen -> gen.biomeSource),
                            Codec.BOOL
                                    .fieldOf("is_nether")
                                    .forGetter(gen -> gen.isNether)
                    )
                    .apply(inst, SkyblockChunkGenerator::new)
    );

    public static final Codec<SkyblockChunkGenerator> CODEC = MAP_CODEC.codec();

    public SkyblockChunkGenerator(RegistryEntryLookup<StructureSet> structureSets,
                                  BiomeSource biomeSource,
                                  boolean isNether) {
        super(biomeSource);
        this.structureSets = structureSets;
        this.biomeSource   = biomeSource;
        this.isNether      = isNether;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return MAP_CODEC;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(
            Blender blender,
            NoiseConfig noiseConfig,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
        return CompletableFuture.completedFuture(chunk);
    }

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

        ServerWorldAccess worldAccess = (ServerWorldAccess) region;
        ServerWorld serverWorld = worldAccess.toServerWorld();

        // Keep spawn survivable even if the template is missing or shifted.
        worldAccess.setBlockState(new BlockPos(0, 64, 0), Blocks.GRASS_BLOCK.getDefaultState(), 2);
        worldAccess.setBlockState(new BlockPos(0, 63, 0), Blocks.DIRT.getDefaultState(), 2);

        StructureTemplateManager stm = serverWorld.getServer().getStructureTemplateManager();
        Identifier id = Identifier.of("challengecraft", "classic_skyblock");
        StructureTemplate template = stm.getTemplateOrBlank(id);

        StructurePlacementData placement = new StructurePlacementData()
                .setIgnoreEntities(false)
                .setRotation(BlockRotation.NONE)
                .setMirror(BlockMirror.NONE)
                .setPosition(new BlockPos(0, 64, 0));

        template.place(
                worldAccess,
                new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0),
                placement,
                serverWorld.getRandom(),
                2
        );
    }


    @Override
    public void carve(
            ChunkRegion region,
            long seed,
            NoiseConfig noiseConfig,
            net.minecraft.world.biome.source.BiomeAccess biomeAccess,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
    }

    @Override
    public CompletableFuture<Chunk> populateBiomes(
            NoiseConfig noiseConfig,
            Blender blender,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
        return super.populateBiomes(noiseConfig, blender, structureAccessor, chunk);
    }

    @Override
    public void populateEntities(ChunkRegion region) { /* vanilla */ }

    @Override public int getWorldHeight()   { return 384; }

    @Override public int getSeaLevel()      { return 0; }
    @Override public int getMinimumY()      { return -64; }

    // Worldgen still asks for a surface height when placing structures in an otherwise empty world.
    @Override
    public int getHeight(
            int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig config
    ) {
        return 64;
    }

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

    @Override
    public StructurePlacementCalculator createStructurePlacementCalculator(
            RegistryWrapper<StructureSet> registry,
            NoiseConfig noiseConfig,
            long seed
    ) {
        RegistryWrapper.Impl<StructureSet> base = (RegistryWrapper.Impl<StructureSet>) registry;

        RegistryKey<StructureSet> OVERWORLD_STRONGHOLDS =
                RegistryKey.of(RegistryKeys.STRUCTURE_SET, Identifier.of("minecraft", "strongholds"));
        RegistryKey<StructureSet> NETHER_COMPLEXES =
                RegistryKey.of(RegistryKeys.STRUCTURE_SET, Identifier.of("minecraft", "nether_complexes"));

        // Keep progression structures available without repopulating the whole void world.
        RegistryWrapper.Impl<StructureSet> filtered = new RegistryWrapper.Impl.Delegating<StructureSet>() {
            @Override
            public RegistryWrapper.Impl<StructureSet> getBase() {
                return base;
            }

            @Override
            public Stream<RegistryEntry.Reference<StructureSet>> streamEntries() {
                return base.streamEntries().filter(entry -> {
                    RegistryKey<StructureSet> key = entry.registryKey();
                    if (isNether) {
                        return key.equals(NETHER_COMPLEXES);
                    } else {
                        return key.equals(OVERWORLD_STRONGHOLDS);
                    }
                });
            }

            @Override public Stream<RegistryEntryList.Named<StructureSet>> getTags() {
                return base.getTags();
            }
        };

        return StructurePlacementCalculator.create(
                noiseConfig,
                seed,
                this.biomeSource,
                filtered
        );
    }

    @Override
    public void appendDebugHudText(
            java.util.List<String> text, NoiseConfig noiseConfig, BlockPos pos
    ) {
    }
}
