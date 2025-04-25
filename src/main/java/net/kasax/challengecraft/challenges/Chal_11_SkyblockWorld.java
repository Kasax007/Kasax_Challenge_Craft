package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.world.World;

public class Chal_11_SkyblockWorld {
    private static boolean active = false;

    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (!active || world.getRegistryKey() != World.OVERWORLD) return;
            // Replace chunk generator with void generator and place island at spawn
            // Implementation left as exercise: use FabricDimension API to set up VoidChunkGenerator
        });
    }
    public static void setActive(boolean v) { active = v; }
}