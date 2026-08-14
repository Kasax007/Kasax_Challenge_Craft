package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** Tracks the alternate chunk generators and spawn placement used by skyblock worlds. */
public class Chal_11_SkyblockWorld {
    private static boolean active = false;
    private static ChunkGenerator overworldGenerator = null;
    private static ChunkGenerator netherGenerator = null;

    public static void setOverworldGenerator(ChunkGenerator g) { overworldGenerator = g; }
    public static void setNetherGenerator(ChunkGenerator g) { netherGenerator = g; }
    public static ChunkGenerator getOverworldGenerator() { return overworldGenerator; }
    public static ChunkGenerator getNetherGenerator() { return netherGenerator; }

    /**
     * Vanilla generators used to UNDO a Skyblock world once the challenge is switched off.
     *
     * <p>These are needed because the swap is not only in memory. Minecraft serialises whatever
     * {@code LevelStem.generator()} returns into the world's own generator settings, so as soon as
     * a Skyblock world has been saved once, {@code challengecraft:skyblock} is what the world says
     * it is. Deselecting the challenge then changed nothing at all: the mod stopped forcing the
     * generator, and the world simply loaded the Skyblock one straight off disk. Measured, not
     * guessed — {@code world_gen_settings.dat} contained "skyblock" after a restart with the
     * challenge off, and the server reported {@code generator=SkyblockChunkGenerator}.
     */
    private static ChunkGenerator vanillaOverworldGenerator = null;
    private static ChunkGenerator vanillaNetherGenerator = null;

    public static void setVanillaOverworldGenerator(ChunkGenerator g) { vanillaOverworldGenerator = g; }
    public static void setVanillaNetherGenerator(ChunkGenerator g) { vanillaNetherGenerator = g; }
    public static ChunkGenerator getVanillaOverworldGenerator() { return vanillaOverworldGenerator; }
    public static ChunkGenerator getVanillaNetherGenerator() { return vanillaNetherGenerator; }

    public static void setActive(boolean v) { active = v; }

    public static boolean isActive() {
        return active;
    }

    /** Pins spawn to the skyblock island after the server finishes creating the world. */
    public static void onWorldCreated(MinecraftServer server) {
        if (!active) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(
                    overworld.dimension(), anchorSpawnOnIsland(overworld), 0.0f, 0.0f));
        }
    }

    /** How far from the origin to look for the island's surface. */
    private static final int SEARCH_RADIUS = 8;
    private static final int SEARCH_TOP = 96;
    private static final int SEARCH_BOTTOM = 40;

    /**
     * Turns the island's top surface block into bedrock and returns the position to stand on.
     *
     * <p>Spawn used to be hardcoded to (0, 64, 0) — a lone grass block the generator placed at the
     * template's origin, beside the island rather than on it. Break that block and die, and you
     * respawn in mid-air over the void, permanently. Bedrock cannot be broken, so the same trap
     * cannot be rebuilt.
     *
     * <p>The position is found by scanning rather than hardcoded, so it still lands correctly if the
     * island template is ever moved or reshaped. Logs and leaves are skipped: the classic island has
     * a tree, and its canopy is the highest thing around without being anywhere to stand.
     */
    public static BlockPos anchorSpawnOnIsland(ServerLevel world) {
        // This runs on world load, which is BEFORE the spawn chunks exist. Reading block states
        // then just returns air everywhere and the scan finds nothing — verified: the first version
        // of this method logged "No island surface found" on every boot. Force the chunks the search
        // area covers (radius 8 around the origin spans chunks -1..0 on both axes) to FULL first.
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                world.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            }
        }

        BlockPos best = null;
        int bestDistSq = Integer.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                for (int y = SEARCH_TOP; y >= SEARCH_BOTTOM; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = world.getBlockState(p);
                    if (state.isAir()) continue;
                    if (state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
                            || state.is(net.minecraft.tags.BlockTags.LOGS)) {
                        break;   // a tree column: nothing to stand on here
                    }
                    if (!state.blocksMotion()) break;
                    if (world.getBlockEntity(p) != null) {
                        // The starting chest is part of the island and is usually its highest
                        // block. Standing on it would be odd, and this scan used to REPLACE its
                        // pick with bedrock — which silently destroyed the chest and everything in
                        // it. Leave any block with a block entity alone and spawn on plain ground.
                        break;
                    }

                    int distSq = x * x + z * z;
                    // Highest surface wins; ties break toward the origin so spawn stays centred.
                    if (best == null || y > best.getY() || (y == best.getY() && distSq < bestDistSq)) {
                        best = p;
                        bestDistSq = distSq;
                    }
                    break;       // only the topmost block of each column matters
                }
            }
        }

        // Only when there is no island at all (missing or shifted template) is a block placed, and
        // then it is bedrock, because the player would otherwise spawn over open void.
        if (best == null) {
            best = new BlockPos(0, 64, 0);
            world.setBlock(best, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(), 3);
            ChallengeCraft.LOGGER.warn("[Skyblock] No island surface found near origin — placed a bedrock anchor at {}", best);
            return best.above();
        }

        // The island's own surface is left exactly as the template built it. Converting it to
        // bedrock was a leftover from when spawn was pinned to a single loose block: it destroyed
        // whatever it landed on, which in practice was the starting chest and its contents. Pinning
        // the world spawn here is enough on its own — the island has plenty of blocks, so losing one
        // no longer strands anybody.
        ChallengeCraft.LOGGER.info("[Skyblock] Spawn anchored on the island surface at {}", best);
        return best.above();
    }
}
