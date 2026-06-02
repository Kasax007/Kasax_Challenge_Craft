package net.kasax.challengecraft.challenges;

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

    public static void setActive(boolean v) { active = v; }

    public static boolean isActive() {
        return active;
    }

    /** Pins spawn to the skyblock island after the server finishes creating the world. */
    public static void onWorldCreated(MinecraftServer server) {
        if (!active) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            BlockPos spawn = new BlockPos(0, 65, 0);
            overworld.setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(overworld.dimension(), spawn, 0.0f, 0.0f));
        }
    }
}
