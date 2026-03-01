package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.border.WorldBorder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Chal_9_ExpWorldBorder {
    private static boolean active = false;
    private static final Map<UUID, Integer> lastLevels = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register((world) -> {
            if (!active) return;
            WorldBorder border = world.getWorldBorder();

            // Determine target size based on highest level among all players
            int maxLvl = 0;
            boolean anyPlayer = false;
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                anyPlayer = true;
                maxLvl = Math.max(maxLvl, player.experienceLevel);
                lastLevels.put(player.getUuid(), player.experienceLevel);
            }

            if (!anyPlayer) return;

            double target = Math.max(1.0, (double) maxLvl);
            double current = border.getSize();

            // Enforce size every tick if it's way off or not interpolating
            if (current > 1000000 || (Math.abs(current - target) > 0.1 && border.getSizeLerpTime() <= 0)) {
                if (current > 1000000 || target < current) {
                    border.setSize(target);
                } else {
                    border.interpolateSize(current, target, (long) ((target - current) * 1000));
                }
            }

            // Also ensure center is correct
            if (Math.abs(border.getCenterX() - 0.5) > 0.001 || Math.abs(border.getCenterZ() - 0.5) > 0.001) {
                border.setCenter(0.5, 0.5);
            }

            // Safety teleport for players outside the border
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!border.contains(player.getX(), player.getZ())) {
                    int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, 0, 0);
                    player.requestTeleport(0.5, (double) y, 0.5);
                }
            }
        });
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) lastLevels.clear();
    }

    public static boolean isActive() { return active; }
}