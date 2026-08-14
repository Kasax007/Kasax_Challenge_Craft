package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sizes the world border from the highest current vanilla XP level among online players. */
public class Chal_9_ExpWorldBorder {
    private static boolean active = false;
    private static final Map<UUID, Integer> lastLevels = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register((world) -> {
            if (!active) return;
            WorldBorder border = world.getWorldBorder();

            int maxLvl = 0;
            boolean anyPlayer = false;
            for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
                anyPlayer = true;
                maxLvl = Math.max(maxLvl, player.experienceLevel);
                lastLevels.put(player.getUUID(), player.experienceLevel);
            }

            if (!anyPlayer) return;

            double target = Math.max(1.0, (double) maxLvl);
            double current = border.getSize();

            if (current > 1000000 || (Math.abs(current - target) > 0.1 && border.getLerpTime() <= 0)) {
                if (current > 1000000 || target < current) {
                    border.setSize(target);
                } else {
                    // 26.2: duration is in game TICKS now, and the start time is passed explicitly
                    // (see MovementAndDamageMixin for the evidence). 1000 ms == 20 ticks.
                    border.lerpSizeBetween(current, target, (long) ((target - current) * 20), world.getGameTime());
                }
            }

            // Also ensure center is correct
            if (Math.abs(border.getCenterX() - 0.5) > 0.001 || Math.abs(border.getCenterZ() - 0.5) > 0.001) {
                border.setCenter(0.5, 0.5);
            }

            // Safety teleport for players outside the border
            for (ServerPlayer player : world.players()) {
                if (!border.isWithinBounds(player.getX(), player.getZ())) {
                    int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                    player.teleportTo(0.5, (double) y, 0.5);
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
