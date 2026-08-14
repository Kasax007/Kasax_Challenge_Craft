package net.kasax.challengecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Void-world generator that preserves the small amount of structure support skyblock runs need. */
public class SkyblockChunkGenerator extends ChunkGenerator {
    private final HolderGetter<StructureSet> structureSets;
    private final BiomeSource biomeSource;
    private final boolean isNether;

    public static final MapCodec<SkyblockChunkGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                            RegistryOps.retrieveGetter(Registries.STRUCTURE_SET),
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

    public SkyblockChunkGenerator(HolderGetter<StructureSet> structureSets,
                                  BiomeSource biomeSource,
                                  boolean isNether) {
        super(biomeSource);
        this.structureSets = structureSets;
        this.biomeSource   = biomeSource;
        this.isNether      = isNether;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MAP_CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState noiseConfig,
            StructureManager structureAccessor,
            ChunkAccess chunk
    ) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(
            WorldGenRegion region,
            StructureManager structureAccessor,
            RandomState noiseConfig,
            ChunkAccess chunk
    ) {
        if (isNether) return;
        ChunkPos pos = chunk.getPos();
        if (pos.x() != 0 || pos.z() != 0) return;

        ServerLevelAccessor worldAccess = (ServerLevelAccessor) region;
        ServerLevel serverWorld = worldAccess.getLevel();

        // NO safety block here any more. It sat at the template's origin — beside the island, not
        // on it — and spawn was pinned to it. Breaking it and dying then dropped the player into the
        // void with no way back. Chal_11_SkyblockWorld.anchorSpawnOnIsland now finds the island's
        // real surface, makes that block bedrock and spawns there, which also covers the
        // missing-template case this block was guarding against.

        StructureTemplateManager stm = serverWorld.getServer().getStructureManager();
        Identifier id = Identifier.fromNamespaceAndPath("challengecraft", "classic_skyblock");
        StructureTemplate template = stm.getOrCreate(id);

        StructurePlaceSettings placement = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(0, 64, 0));

        template.placeInWorld(
                worldAccess,
                new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0),
                placement,
                serverWorld.getRandom(),
                2
        );
    }


    @Override
    public void applyCarvers(
            WorldGenRegion region,
            long seed,
            RandomState noiseConfig,
            net.minecraft.world.level.biome.BiomeManager biomeAccess,
            StructureManager structureAccessor,
            ChunkAccess chunk
    ) {
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(
            RandomState noiseConfig,
            Blender blender,
            StructureManager structureAccessor,
            ChunkAccess chunk
    ) {
        return super.createBiomes(noiseConfig, blender, structureAccessor, chunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) { /* vanilla */ }

    @Override public int getGenDepth()   { return 384; }

    @Override public int getSeaLevel()      { return 0; }
    @Override public int getMinY()      { return -64; }

    // Worldgen still asks for a surface height when placing structures in an otherwise empty world.
    @Override
    public int getBaseHeight(
            int x, int z, Heightmap.Types type, LevelHeightAccessor world, RandomState config
    ) {
        return 64;
    }

    @Override
    public NoiseColumn getBaseColumn(
            int x, int z, LevelHeightAccessor world, RandomState config
    ) {
        int minY = world.getMinY();
        int maxY = world.getMaxY() - minY + 1;
        var states = new net.minecraft.world.level.block.state.BlockState[maxY];
        java.util.Arrays.fill(states, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        if (!isNether && Math.abs(x) <= 1 && Math.abs(z) <= 1) {
            if (x == 0 && z == 0) {
                states[64 - minY] = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
                states[63 - minY] = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            } else {
                states[63 - minY] = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            }
        }

        return new NoiseColumn(minY, states);
    }

    @Override
    public ChunkGeneratorStructureState createState(
            HolderLookup<StructureSet> registry,
            RandomState noiseConfig,
            long seed
    ) {
        HolderLookup.RegistryLookup<StructureSet> base = (HolderLookup.RegistryLookup<StructureSet>) registry;

        ResourceKey<StructureSet> OVERWORLD_STRONGHOLDS =
                ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath("minecraft", "strongholds"));
        ResourceKey<StructureSet> NETHER_COMPLEXES =
                ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath("minecraft", "nether_complexes"));

        // Keep progression structures available without repopulating the whole void world.
        HolderLookup.RegistryLookup<StructureSet> filtered = new HolderLookup.RegistryLookup.Delegate<StructureSet>() {
            @Override
            public HolderLookup.RegistryLookup<StructureSet> parent() {
                return base;
            }

            @Override
            public Stream<Holder.Reference<StructureSet>> listElements() {
                return base.listElements().filter(entry -> {
                    ResourceKey<StructureSet> key = entry.key();
                    if (isNether) {
                        return key.equals(NETHER_COMPLEXES);
                    } else {
                        return key.equals(OVERWORLD_STRONGHOLDS);
                    }
                });
            }

            @Override public Stream<HolderSet.Named<StructureSet>> listTags() {
                return base.listTags();
            }
        };

        return ChunkGeneratorStructureState.createForNormal(
                noiseConfig,
                seed,
                this.biomeSource,
                filtered
        );
    }

    @Override
    public void addDebugScreenInfo(
            java.util.List<String> text, RandomState noiseConfig, BlockPos pos
    ) {
    }
}
