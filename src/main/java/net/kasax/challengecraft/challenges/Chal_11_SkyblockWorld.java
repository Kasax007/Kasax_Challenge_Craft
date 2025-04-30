package net.kasax.challengecraft.challenges;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class Chal_11_SkyblockWorld {
    private static boolean active = false;

    /** Call this method to activate the Skyblock challenge mode (Challenge 11). */
    public static void setActive(boolean v) { active = v; }

    /** Whether challenge 11 is currently active. Used by world-gen mixins. */
    public static boolean isActive() {
        return active;
    }

    /**
     * Perform any setup after the world is created for the challenge.
     * This should be called once the server has finished generating spawn.
     */
    public static void onWorldCreated(MinecraftServer server) {
        if (!active) return;
        // Ensure the spawn point is on our island (0,65,0)
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld != null) {
            BlockPos spawn = new BlockPos(0, 65, 0);
            overworld.setSpawnPos(spawn, 0.0f);
        }
        // (The player will start near 0,0; this guarantees they're on the island)

        // We could also give challenge-specific starter items to the player here if needed.
    }
}
