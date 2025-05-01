package net.kasax.challengecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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

        // 1) view the region as a ServerWorldAccess
        ServerWorldAccess worldAccess = (ServerWorldAccess) region;
        // 2) pull out the real ServerWorld so we can get at server.getStructureTemplateManager() & a Random
        ServerWorld serverWorld = worldAccess.toServerWorld();

        // load your structure from data/challengecraft/structures/classic_skyblock.nbt
        StructureTemplateManager stm = serverWorld.getServer().getStructureTemplateManager();
        Identifier id = Identifier.of("challengecraft", "classic_skyblock");
        StructureTemplate template = stm.getTemplateOrBlank(id);

        // build your placement data (no rotation/mirror in this case)
        StructurePlacementData placement = new StructurePlacementData()
                .setIgnoreEntities(false)
                .setRotation(BlockRotation.NONE)
                .setMirror(BlockMirror.NONE)
                .setPosition(new BlockPos(0, 64, 0));

        // stamp it down at world-coords (0,64,0), both as origin and pivot
        template.place(
                worldAccess,              // -> ServerWorldAccess
                new BlockPos(0, 64, 0),   // -> structure origin
                new BlockPos(0, 64, 0),   // -> pivot (for rotation/mirror)
                placement,                // -> our StructurePlacementData
                serverWorld.getRandom(),  // -> a Random
                2                         // -> flags (update neighbors, notify physics)
        );
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

    // 9) Only allow strongholds (or nether complexes):
    // --- filter which structure sets will generate ---
    @Override
    public StructurePlacementCalculator createStructurePlacementCalculator(
            RegistryWrapper<StructureSet> registry,
            NoiseConfig noiseConfig,
            long seed
    ) {
        // cast to the Impl so we can delegate
        RegistryWrapper.Impl<StructureSet> base = (RegistryWrapper.Impl<StructureSet>) registry;

        // prepare the two keys we actually want
        RegistryKey<StructureSet> OVERWORLD_STRONGHOLDS =
                RegistryKey.of(RegistryKeys.STRUCTURE_SET, Identifier.of("minecraft", "strongholds"));
        RegistryKey<StructureSet> NETHER_COMPLEXES =
                RegistryKey.of(RegistryKeys.STRUCTURE_SET, Identifier.of("minecraft", "nether_complexes"));

        // build a tiny delegating wrapper that only "sees" our one desired set
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

            // we don’t need tags, so just pass through
            @Override public Stream<RegistryEntryList.Named<StructureSet>> getTags() {
                return base.getTags();
            }
        };

        // now hand *that* filtered wrapper to the vanilla placement calculator
        return StructurePlacementCalculator.create(
                noiseConfig,
                seed,
                this.biomeSource,
                filtered
        );
    }

    // 10) Debug HUD (no extra info):
    @Override
    public void appendDebugHudText(
            java.util.List<String> text, NoiseConfig noiseConfig, BlockPos pos
    ) {
        // nothing extra
    }
}
