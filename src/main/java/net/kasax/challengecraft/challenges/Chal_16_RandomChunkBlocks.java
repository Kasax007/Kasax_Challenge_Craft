package net.kasax.challengecraft.challenges;

import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;

import java.util.*;

public class Chal_16_RandomChunkBlocks {
    private static boolean active = false;
    private static List<Block> blockList = null;
    private static final Map<StructureWorldAccess, Set<BlockPos>> pendingReplacements = Collections.synchronizedMap(new WeakHashMap<>());

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
            for (Block block : Registries.BLOCK) {
                if (isException(block)) continue;
                if (block == Blocks.TNT) continue;

                // Exclude fluids (Water, Lava)
                if (block instanceof FluidBlock) continue;

                // Exclude blocks affected by gravity
                if (block instanceof FallingBlock) continue;

                // Exclude fire
                if (block instanceof AbstractFireBlock) continue;

                // Exclude non-solid blocks (generally things you can walk through)
                if (!block.getDefaultState().blocksMovement()) continue;

                // Exclude non-full blocks that might be technically "solid" but not ideal for chunk replacement
                if (block instanceof SlabBlock) continue;
                if (block instanceof StairsBlock) continue;
                if (block instanceof FenceBlock) continue;
                if (block instanceof WallBlock) continue;
                if (block instanceof PaneBlock) continue; // Glass Panes, Iron Bars
                if (block instanceof LeavesBlock) continue; // They decay/are semi-transparent
                if (block instanceof ShulkerBoxBlock) continue;
                if (block instanceof AbstractChestBlock) continue;
                if (block instanceof AbstractFurnaceBlock) continue;
                if (block instanceof BambooBlock) continue;
                if (block instanceof CactusBlock) continue;
                if (block instanceof FenceGateBlock) continue;
                if (block instanceof TrapdoorBlock) continue;

                // Exclude technical/invisible/special blocks
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
                if (block instanceof PistonExtensionBlock) continue;
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
                if (block instanceof ChiseledBookshelfBlock) continue;
                if (block instanceof DecoratedPotBlock) continue;
                if (block instanceof CreakingHeartBlock) continue;

                // Exclude blocks that break when floating (as requested)
                if (block instanceof TorchBlock) continue;
                if (block instanceof PlantBlock) continue; // Saplings, Flowers, Mushrooms, Tall Grass, etc.
                if (block instanceof CarpetBlock) continue;
                if (block instanceof ButtonBlock) continue;
                if (block instanceof LeverBlock) continue;
                if (block instanceof RedstoneWireBlock) continue;
                if (block instanceof RedstoneTorchBlock) continue;
                if (block instanceof AbstractRedstoneGateBlock) continue; // Repeaters, Comparators
                if (block instanceof AbstractSignBlock) continue;
                if (block instanceof PressurePlateBlock) continue;
                if (block instanceof WeightedPressurePlateBlock) continue;

                // Exclude corals
                if (block instanceof CoralBlock) continue;
                if (block instanceof CoralFanBlock) continue;
                if (block instanceof CoralWallFanBlock) continue;
                if (block instanceof DeadCoralBlock) continue;
                if (block instanceof DeadCoralFanBlock) continue;
                if (block instanceof DeadCoralWallFanBlock) continue;

                // Other common "break-on-float" blocks for better experience
                if (block instanceof RailBlock) continue;
                if (block instanceof AbstractBannerBlock) continue;
                if (block instanceof BedBlock) continue;
                if (block instanceof FlowerPotBlock) continue;
                if (block instanceof DoorBlock) continue;
                if (block instanceof SnowBlock) continue;
                if (block instanceof LadderBlock) continue;
                if (block instanceof VineBlock) continue;
                if (block instanceof TripwireHookBlock) continue;
                if (block instanceof TripwireBlock) continue;
                if (block instanceof AbstractCandleBlock) continue;
                if (block instanceof AbstractPlantPartBlock) continue; // Kelp, Weeping Vines, etc.
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
                if (block instanceof CakeBlock) continue; // Needs support
                if (block instanceof ChorusFlowerBlock) continue;
                if (block instanceof ChorusPlantBlock) continue;
                if (block instanceof CocoaBlock) continue;
                if (block instanceof HangingRootsBlock) continue;
                if (block instanceof SporeBlossomBlock) continue;
                if (block instanceof SweetBerryBushBlock) continue;
                if (block instanceof MultifaceGrowthBlock) continue; // Glow Lichen, etc.

                blockList.add(block);
            }
        }
    }

    public static Block getRandomBlockForChunk(long worldSeed, ChunkPos pos) {
        ensureBlockList();
        if (blockList.isEmpty()) return Blocks.STONE;

        // Use a combination of seed and chunk coordinates for a stable random
        long seed = worldSeed ^ pos.x ^ (long) pos.z << 32;
        Random random = new Random(seed);
        return blockList.get(random.nextInt(blockList.size()));
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

    public static void recordPendingReplacement(StructureWorldAccess world, BlockPos pos) {
        pendingReplacements.computeIfAbsent(world, k -> Collections.synchronizedSet(new HashSet<>())).add(pos.toImmutable());
    }

    public static void applyPendingReplacements(StructureWorldAccess world) {
        Set<BlockPos> positions = pendingReplacements.remove(world);
        if (positions != null) {
            long seed = world.getSeed();
            synchronized (positions) {
                for (BlockPos pos : positions) {
                    Block randomBlock = getRandomBlockForChunk(seed, new ChunkPos(pos));
                    world.setBlockState(pos, randomBlock.getDefaultState(), 3);
                }
            }
        }
    }
}
