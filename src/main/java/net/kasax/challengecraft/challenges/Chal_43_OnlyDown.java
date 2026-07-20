package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Only Down": each player's lowest reached Y is their ceiling — climbing more than 0.25 above
 * it kills them. A rolling minimum (instead of a per-tick delta) is deliberate: ladders, slow
 * swimming, and scaffolding would all slip under a per-tick threshold.
 *
 * The baseline resets whenever the Y change cannot be the player's own movement: dimension
 * change (nether/end portals), a single-tick displacement too large to walk (teleport commands,
 * chorus fruit, gateways), and respawning after death.
 */
public class Chal_43_OnlyDown {
    private static final double UP_TOLERANCE = 0.25;
    /** Squared single-tick displacement beyond which the movement is treated as a teleport. */
    private static final double TELEPORT_DIST_SQ = 12 * 12;

    private static boolean active = false;
    private static final Map<UUID, Baseline> BASELINES = new HashMap<>();

    private static final class Baseline {
        RegistryKey<World> dimension;
        double minY;
        double lastX;
        double lastY;
        double lastZ;

        Baseline(ServerPlayerEntity player) {
            reset(player);
        }

        void reset(ServerPlayerEntity player) {
            this.dimension = player.getWorld().getRegistryKey();
            this.minY = player.getY();
            this.lastX = player.getX();
            this.lastY = player.getY();
            this.lastZ = player.getZ();
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isCreative() || player.isSpectator() || player.isDead()) continue;

                Baseline baseline = BASELINES.get(player.getUuid());
                if (baseline == null) {
                    BASELINES.put(player.getUuid(), new Baseline(player));
                    continue;
                }

                if (baseline.dimension != player.getWorld().getRegistryKey()) {
                    baseline.reset(player);
                    continue;
                }

                double dx = player.getX() - baseline.lastX;
                double dy = player.getY() - baseline.lastY;
                double dz = player.getZ() - baseline.lastZ;
                if (dx * dx + dy * dy + dz * dz > TELEPORT_DIST_SQ) {
                    baseline.reset(player);
                    continue;
                }

                baseline.lastX = player.getX();
                baseline.lastY = player.getY();
                baseline.lastZ = player.getZ();
                baseline.minY = Math.min(baseline.minY, player.getY());

                if (player.getY() > baseline.minY + UP_TOLERANCE) {
                    ServerWorld world = (ServerWorld) player.getWorld();
                    player.sendMessage(Text.translatable("challengecraft.worldcreate.challenge43.death")
                            .formatted(Formatting.RED, Formatting.BOLD), false);
                    player.damage(world, world.getDamageSources().genericKill(), Float.MAX_VALUE);
                    BASELINES.remove(player.getUuid());
                }
            }
        });

        // Without this, respawning at a bed less than 12 blocks from (and above) the death spot
        // would inherit the old baseline and kill the player again instantly.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            BASELINES.remove(newPlayer.getUuid());
        });
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) BASELINES.clear();
    }

    public static boolean isActive() {
        return active;
    }
}
