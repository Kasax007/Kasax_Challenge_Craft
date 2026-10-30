package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Void Trail" (challenge 51): the moment a player leaves a block, the whole column of that block
 * is gone - every block from the build limit down to the bottom of the world, bedrock included,
 * water and lava too. Only End portal frames and the End portal itself stay, so the stronghold
 * keeps its portal and the run stays winnable.
 *
 * <p>"Leaving" is the player's column (block X and Z of the feet) changing. Faster than a column
 * per tick (elytra, minecarts) every column on the way is taken, not just the last one; a jump
 * that cannot be the player's own movement (ender pearls, portals, teleport commands) takes only
 * the column that was left. Creative and spectator players are followed but take nothing, and a
 * death simply starts the trail again where the player respawns.
 */
public class Chal_51_VoidTrail {
    /** A single-tick move longer than this (blocks, horizontally) is treated as a teleport. */
    private static final double TELEPORT_DIST = 8.0;
    /** Step along a tick's path when collecting the columns it crossed (blocks). */
    private static final double PATH_STEP = 0.2;

    private static boolean active = false;
    private static final Map<UUID, Spot> LAST = new HashMap<>();

    record Spot(ResourceKey<Level> dimension, double x, double z) {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isDeadOrDying()) {
                    LAST.remove(player.getUUID());
                    continue;
                }
                Spot now = new Spot(player.level().dimension(), player.getX(), player.getZ());
                Spot before = LAST.put(player.getUUID(), now);
                if (before == null || player.isCreative() || player.isSpectator()) continue;
                int bx = Mth.floor(before.x()), bz = Mth.floor(before.z());
                int nx = Mth.floor(now.x()), nz = Mth.floor(now.z());
                if (before.dimension() == now.dimension() && bx == nx && bz == nz) continue;
                for (long column : leftColumns(before, now)) {
                    erase(server, before.dimension(), BlockPos.getX(column), BlockPos.getZ(column));
                }
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> LAST.remove(newPlayer.getUUID()));
    }

    /**
     * The columns a player left between two ticks, oldest first: the one they stood in, and on a
     * walk (same dimension, no teleport) every column the path crossed on the way - but never the
     * column they stand in now. Packed as {@link BlockPos#asLong} with y = 0.
     */
    static Set<Long> leftColumns(Spot before, Spot now) {
        Set<Long> out = new LinkedHashSet<>();
        int bx = Mth.floor(before.x()), bz = Mth.floor(before.z());
        int nx = Mth.floor(now.x()), nz = Mth.floor(now.z());
        out.add(BlockPos.asLong(bx, 0, bz));
        double dx = now.x() - before.x(), dz = now.z() - before.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (before.dimension() == now.dimension() && dist <= TELEPORT_DIST) {
            int steps = (int) Math.ceil(dist / PATH_STEP);
            for (int i = 1; i < steps; i++) {
                double f = (double) i / steps;
                int cx = Mth.floor(before.x() + dx * f), cz = Mth.floor(before.z() + dz * f);
                if (cx != nx || cz != nz) out.add(BlockPos.asLong(cx, 0, cz));
            }
        }
        if (before.dimension() == now.dimension()) out.remove(BlockPos.asLong(nx, 0, nz));
        return out;
    }

    /** Takes the column at x/z out of the world, top down (so nothing in it is left to fall). */
    static void erase(MinecraftServer server, ResourceKey<Level> dimension, int x, int z) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return;
        erase(level, x, z);
    }

    public static void erase(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getMinY(), z);
        if (!level.isLoaded(pos)) return;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = level.getMinY() + level.getHeight() - 1; y >= level.getMinY(); y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || keeps(state)) continue;
            level.setBlock(pos, air, Block.UPDATE_ALL);
        }
    }

    /** What the void leaves standing: the End portal and its frames. */
    public static boolean keeps(BlockState state) {
        return state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_PORTAL);
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) LAST.clear();
    }

    public static boolean isActive() {
        return active;
    }
}
