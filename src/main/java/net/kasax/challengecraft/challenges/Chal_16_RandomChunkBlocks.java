package net.kasax.challengecraft.challenges;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.util.*;

/** Rewrites chunk blocks through a deterministic palette mapping. */
public class Chal_16_RandomChunkBlocks {
    private static boolean active = false;
    private static List<Block> blockList = null;
    private static final Map<WorldGenLevel, Set<BlockPos>> pendingReplacements = Collections.synchronizedMap(new WeakHashMap<>());

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    private static void ensureBlockList() {
        if (blockList == null) {
            blockList = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                if (id.getNamespace().equals("challengecraft")) continue;
                if (isException(block)) continue;
                if (block == Blocks.TNT) continue;

                if (block instanceof LiquidBlock) continue;
                if (block instanceof FallingBlock) continue;
                if (block instanceof BaseFireBlock) continue;

                if (!block.defaultBlockState().blocksMotion()) continue;

                // Replacement targets must remain self-supporting and usable as terrain.
                if (block instanceof SlabBlock) continue;
                if (block instanceof StairBlock) continue;
                if (block instanceof FenceBlock) continue;
                if (block instanceof WallBlock) continue;
                if (block instanceof IronBarsBlock) continue;
                if (block instanceof LeavesBlock) continue;
                if (block instanceof ShulkerBoxBlock) continue;
                if (block instanceof AbstractChestBlock) continue;
                if (block instanceof AbstractFurnaceBlock) continue;
                if (block instanceof BambooStalkBlock) continue;
                if (block instanceof CactusBlock) continue;
                if (block instanceof FenceGateBlock) continue;
                if (block instanceof TrapDoorBlock) continue;

                if (block instanceof BarrierBlock) continue;
                if (block instanceof StructureVoidBlock) continue;
                if (block instanceof LightBlock) continue;
                if (block instanceof CommandBlock) continue;
                if (block instanceof StructureBlock) continue;
                if (block instanceof JigsawBlock) continue;
                if (block instanceof EndGatewayBlock) continue;
                if (block instanceof SpawnerBlock) continue;
                if (block instanceof TrialSpawnerBlock) continue;
                if (block instanceof VaultBlock) continue;
                if (block instanceof MovingPistonBlock) continue;
                if (block instanceof BubbleColumnBlock) continue;
                if (block instanceof NetherPortalBlock) continue;
                if (block instanceof EndPortalBlock) continue;
                if (block instanceof AbstractSkullBlock) continue;
                if (block instanceof BeaconBlock) continue;
                if (block instanceof EnchantingTableBlock) continue;
                if (block instanceof EnderChestBlock) continue;
                if (block instanceof DaylightDetectorBlock) continue;
                if (block instanceof HopperBlock) continue;
                if (block instanceof LecternBlock) continue;
                if (block instanceof CampfireBlock) continue;
                if (block instanceof BeehiveBlock) continue;
                if (block instanceof RespawnAnchorBlock) continue;
                if (block instanceof SculkSensorBlock) continue;
                if (block instanceof SculkShriekerBlock) continue;
                if (block instanceof SculkCatalystBlock) continue;
                if (block instanceof CalibratedSculkSensorBlock) continue;
                if (block instanceof CrafterBlock) continue;
                if (block instanceof ChiseledBookShelfBlock) continue;
                if (block instanceof DecoratedPotBlock) continue;
                if (block instanceof CreakingHeartBlock) continue;

                // Many decorative blocks pop off immediately when placed without support.
                if (block instanceof TorchBlock) continue;
                if (block instanceof VegetationBlock) continue;
                if (block instanceof CarpetBlock) continue;
                if (block instanceof ButtonBlock) continue;
                if (block instanceof LeverBlock) continue;
                if (block instanceof RedStoneWireBlock) continue;
                if (block instanceof RedstoneTorchBlock) continue;
                if (block instanceof DiodeBlock) continue;
                if (block instanceof SignBlock) continue;
                if (block instanceof PressurePlateBlock) continue;
                if (block instanceof WeightedPressurePlateBlock) continue;

                if (block instanceof CoralPlantBlock) continue;
                if (block instanceof CoralFanBlock) continue;
                if (block instanceof CoralWallFanBlock) continue;
                if (block instanceof BaseCoralPlantBlock) continue;
                if (block instanceof BaseCoralFanBlock) continue;
                if (block instanceof BaseCoralWallFanBlock) continue;

                if (block instanceof RailBlock) continue;
                if (block instanceof AbstractBannerBlock) continue;
                if (block instanceof BedBlock) continue;
                if (block instanceof FlowerPotBlock) continue;
                if (block instanceof DoorBlock) continue;
                if (block instanceof SnowLayerBlock) continue;
                if (block instanceof LadderBlock) continue;
                if (block instanceof VineBlock) continue;
                if (block instanceof TripWireHookBlock) continue;
                if (block instanceof TripWireBlock) continue;
                if (block instanceof AbstractCandleBlock) continue;
                if (block instanceof GrowingPlantBlock) continue;
                if (block instanceof SeaPickleBlock) continue;
                if (block instanceof TurtleEggBlock) continue;
                if (block instanceof FrogspawnBlock) continue;
                if (block instanceof SnifferEggBlock) continue;
                if (block instanceof LanternBlock) continue;
                if (block instanceof BellBlock) continue;
                if (block instanceof ConduitBlock) continue;
                if (block instanceof EndRodBlock) continue;
                if (block instanceof ScaffoldingBlock) continue;
                if (block instanceof AmethystClusterBlock) continue;
                if (block instanceof PointedDripstoneBlock) continue;
                if (block instanceof AzaleaBlock) continue;
                if (block instanceof BigDripleafBlock) continue;
                if (block instanceof SmallDripleafBlock) continue;
                if (block instanceof CakeBlock) continue;
                if (block instanceof ChorusFlowerBlock) continue;
                if (block instanceof ChorusPlantBlock) continue;
                if (block instanceof CocoaBlock) continue;
                if (block instanceof HangingRootsBlock) continue;
                if (block instanceof SporeBlossomBlock) continue;
                if (block instanceof SweetBerryBushBlock) continue;
                if (block instanceof MultifaceSpreadeableBlock) continue;

                blockList.add(block);
            }
        }
    }

    public static Block getRandomBlockForChunk(long worldSeed, ChunkPos pos) {
        ensureBlockList();
        if (blockList.isEmpty()) return Blocks.STONE;

        // Stable per-chunk mapping keeps reloads and newly created chunks consistent.
        long seed = worldSeed ^ pos.x() ^ (long) pos.z() << 32;
        Random random = new Random(seed);
        return blockList.get(random.nextInt(blockList.size()));
    }

    public static void replaceChunkBlocks(WorldGenLevel world, ChunkAccess chunk) {
        Block randomBlock = getRandomBlockForChunk(world.getSeed(), chunk.getPos());
        BlockState randomState = randomBlock.defaultBlockState();

        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight();
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z);
                    BlockState currentState = chunk.getBlockState(mutable);
                    if (!isException(currentState.getBlock())) {
                        chunk.setBlockState(mutable, randomState, 0);
                    }
                }
            }
        }
    }

    public static void replaceChunkBlocks(ServerLevel world, ChunkPos chunkPos) {
        Block randomBlock = getRandomBlockForChunk(world.getSeed(), chunkPos);
        BlockState randomState = randomBlock.defaultBlockState();
        ChunkAccess chunk = world.getChunk(chunkPos.x(), chunkPos.z());

        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z);
                    if (!isException(world.getBlockState(mutable).getBlock())) {
                        world.setBlock(mutable, randomState, 3);
                    }
                }
            }
        }
    }

    public static boolean isException(Block block) {
        return block == Blocks.CHEST ||
               block == Blocks.TRAPPED_CHEST ||
               block == Blocks.SPAWNER ||
               block == Blocks.END_PORTAL_FRAME ||
               block == Blocks.BEDROCK ||
               block == Blocks.AIR ||
               block == Blocks.CAVE_AIR ||
               block == Blocks.VOID_AIR ||
               block == Blocks.WATER ||
               block == Blocks.LAVA;
    }

    public static void recordPendingReplacement(WorldGenLevel world, BlockPos pos) {
        pendingReplacements.computeIfAbsent(world, k -> Collections.synchronizedSet(new HashSet<>())).add(pos.immutable());
    }

    public static void applyPendingReplacements(WorldGenLevel world) {
        Set<BlockPos> positions = pendingReplacements.remove(world);
        if (positions != null) {
            long seed = world.getSeed();
            synchronized (positions) {
                for (BlockPos pos : positions) {
                    Block randomBlock = getRandomBlockForChunk(seed, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
                    world.setBlock(pos, randomBlock.defaultBlockState(), 3);
                }
            }
        }
    }
}
