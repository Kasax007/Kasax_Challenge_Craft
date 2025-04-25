package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.border.WorldBorder;

import java.util.HashMap;
import java.util.Map;

public class Chal_9_ExpWorldBorder {
    private static boolean active = false;
    private static Map<ServerPlayerEntity,Integer> lastLevels = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register((world) -> {
            if (!active) return;
            WorldBorder border = world.getWorldBorder();
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                int lvl = player.experienceLevel;
                int prev = lastLevels.getOrDefault(player, -1);
                if (lvl != prev) {
                    lastLevels.put(player, lvl);
                    // border size = lvl blocks (diameter)
                    double size = Math.max(1, lvl);
                    border.setSize(size);
                }
            }
        });
    }
    public static void setActive(boolean v) { active = v; }
}