package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;

/** Configures the world border as a damaging hazard rather than a hard wall. */
public class Chal_25_DamageWorldBorder {
    private static boolean active = false;
    private static double currentDiameter = 2.0;

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register((world) -> {
            if (!active) return;
            WorldBorder border = world.getWorldBorder();
            double target = currentDiameter;

            if (border.getSize() > 1000000 || (Math.abs(border.getSize() - target) > 0.1 && border.getSizeLerpTime() <= 0)) {
                border.setSize(target);
            }

            if (Math.abs(border.getCenterX() - 0.5) > 0.001 || Math.abs(border.getCenterZ() - 0.5) > 0.001) {
                border.setCenter(0.5, 0.5);
            }

            // First joins can happen outside the tiny opening border before the server corrects spawn.
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
    }

    public static boolean isActive() {
        return active;
    }

    public static double getDiameter() {
        return currentDiameter;
    }

    public static void setDiameter(double diameter) {
        currentDiameter = Math.max(2.0, diameter);
    }

    public static void updateWorldBorder(ServerWorld world) {
        if (!active) return;
        world.getWorldBorder().setSize(currentDiameter);
    }
}
