package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Starts a one-mob encounter whenever players enter a fresh chunk and locks the border around it.
 * Target entities carry command tags so encounters can be recovered after reloads before new mobs spawn.
 */
public class Chal_38_ChunkHunt {
    private static final double LOCKED_BORDER_SIZE = 16.0;
    private static final double NORMAL_BORDER_SIZE = 6.0E7;
    private static final int NETHER_ROOF_MIN_Y = 120;
    private static final String TARGET_TAG = "challengecraft_chunk_hunt_target";
    private static final String CHUNK_X_TAG_PREFIX = "challengecraft_chunk_hunt_x_";
    private static final String CHUNK_Z_TAG_PREFIX = "challengecraft_chunk_hunt_z_";
    private static final String PLAYER_CHUNK_TAG_PREFIX = "challengecraft_chunk_hunt_player_chunk|";
    private static final Map<ServerLevel, EncounterState> ENCOUNTERS = new WeakHashMap<>();
    private static final Map<UUID, PlayerChunkState> LAST_PLAYER_CHUNKS = new HashMap<>();
    private static final Set<EntityType<?>> WATER_ENTITIES = Set.of(
            EntityType.AXOLOTL,
            EntityType.COD,
            EntityType.DOLPHIN,
            EntityType.ELDER_GUARDIAN,
            EntityType.GLOW_SQUID,
            EntityType.GUARDIAN,
            EntityType.PUFFERFISH,
            EntityType.SALMON,
            EntityType.SQUID,
            EntityType.TADPOLE,
            EntityType.TROPICAL_FISH
    );

    private static boolean active = false;

    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(Chal_38_ChunkHunt::tickWorld);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_PLAYER_CHUNKS.remove(handler.player.getUUID()));
    }

    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            ENCOUNTERS.clear();
            LAST_PLAYER_CHUNKS.clear();
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void updateWorldBorder(ServerLevel world) {
        if (!active) return;

        EncounterState state = ENCOUNTERS.computeIfAbsent(world, unused -> new EncounterState());
        if (state != null && state.chunkPos != null && state.targetEntityUuid != null) {
            Entity target = world.getEntity(state.targetEntityUuid);
            if (target != null && target.isAlive()) {
                keepTargetInsideChunk(world, state, target);
                lockWorldBorder(world, state.chunkPos);
                rememberCurrentChunks(world);
                return;
            }
        }

        if (restoreEncounter(world, state, null)) {
            Entity target = world.getEntity(state.targetEntityUuid);
            if (target != null && target.isAlive()) {
                keepTargetInsideChunk(world, state, target);
            }
            lockWorldBorder(world, state.chunkPos);
            rememberCurrentChunks(world);
            return;
        }

        resetWorldBorder(world);
    }

    public static void resetWorldBorder(ServerLevel world) {
        applyBorderState(world, null, null, NORMAL_BORDER_SIZE);
        syncClientWorldBorder(world, true);
    }

    private static void tickWorld(ServerLevel world) {
        if (!active) return;

        EncounterState state = ENCOUNTERS.computeIfAbsent(world, unused -> new EncounterState());
        if (state.targetEntityUuid != null) {
            Entity target = world.getEntity(state.targetEntityUuid);
            if (target != null && target.isAlive()) {
                keepTargetInsideChunk(world, state, target);
                lockWorldBorder(world, state.chunkPos);
                keepPlayersInsideBorder(world, state);
                rememberCurrentChunks(world);
                syncClientWorldBorder(world, false);
                return;
            }

            if (restoreEncounter(world, state, state.chunkPos)) {
                Entity restoredTarget = world.getEntity(state.targetEntityUuid);
                if (restoredTarget != null && restoredTarget.isAlive()) {
                    keepTargetInsideChunk(world, state, restoredTarget);
                }
                lockWorldBorder(world, state.chunkPos);
                keepPlayersInsideBorder(world, state);
                rememberCurrentChunks(world);
                syncClientWorldBorder(world, true);
                return;
            }

            endEncounter(world, state);
        }

        if (restoreEncounter(world, state, null)) {
            Entity restoredTarget = world.getEntity(state.targetEntityUuid);
            if (restoredTarget != null && restoredTarget.isAlive()) {
                keepTargetInsideChunk(world, state, restoredTarget);
            }
            lockWorldBorder(world, state.chunkPos);
            keepPlayersInsideBorder(world, state);
            rememberCurrentChunks(world);
            syncClientWorldBorder(world, true);
            return;
        }

        for (ServerPlayer player : world.players()) {
            ChunkPos currentChunk = player.chunkPosition();
            PlayerChunkState currentState = new PlayerChunkState(world.dimension(), currentChunk);
            PlayerChunkState previous = getRememberedChunk(player);
            rememberChunk(player, currentState);
            if (previous == null || !previous.matches(world, currentChunk)) {
                startEncounter(world, player, currentChunk, state);
                return;
            }
        }
    }

    private static void startEncounter(ServerLevel world, ServerPlayer player, ChunkPos chunkPos, EncounterState state) {
        if (restoreEncounter(world, state, chunkPos)) {
            lockWorldBorder(world, state.chunkPos);
            keepPlayersInsideBorder(world, state);
            return;
        }

        Chal_16_RandomChunkBlocks.replaceChunkBlocks(world, chunkPos);

        List<EntityType<?>> entities = Chal_23_AllEntities.getEntitiesWithSpawnEggs();
        if (entities.isEmpty()) {
            resetWorldBorder(world);
            return;
        }

        EntityType<?> entityType = entities.get(world.getRandom().nextInt(entities.size()));
        Entity entity = entityType.create(world, EntitySpawnReason.EVENT);
        if (entity == null) {
            resetWorldBorder(world);
            return;
        }

        ChunkAccess chunk = world.getChunk(chunkPos.x(), chunkPos.z());
        BlockPos spawnPos = findSafeSpawnPos(world, chunk, entity, player.blockPosition());
        entity.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, world.getRandom().nextFloat() * 360.0f, 0.0f);
        entity.setGlowingTag(true);
        entity.addTag(TARGET_TAG);
        entity.addTag(CHUNK_X_TAG_PREFIX + chunkPos.x());
        entity.addTag(CHUNK_Z_TAG_PREFIX + chunkPos.z());
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        if (!world.noCollision(entity) || !world.addFreshEntity(entity)) {
            resetWorldBorder(world);
            return;
        }

        state.chunkPos = chunkPos;
        state.targetEntityUuid = entity.getUUID();
        state.safeTeleportPos = spawnPos;
        lockWorldBorder(world, chunkPos);
        syncClientWorldBorder(world, true);
    }

    private static boolean restoreEncounter(ServerLevel world, EncounterState state, ChunkPos preferredChunk) {
        Entity restoredTarget = null;
        ChunkPos restoredChunk = null;

        for (Entity entity : world.getAllEntities()) {
            if (!entity.isAlive() || !entity.entityTags().contains(TARGET_TAG)) {
                continue;
            }

            ChunkPos entityChunk = readChunkPos(entity);
            if (entityChunk == null) {
                entityChunk = entity.chunkPosition();
            }

            if (preferredChunk != null && (entityChunk.x() != preferredChunk.x() || entityChunk.z() != preferredChunk.z())) {
                continue;
            }

            if (restoredTarget == null) {
                restoredTarget = entity;
                restoredChunk = entityChunk;
                continue;
            }

            // Old saves can contain duplicates if a reload happens mid-encounter; keep only one target.
            clearTargetTags(entity);
            entity.setGlowingTag(false);
        }

        if (restoredTarget == null || restoredChunk == null) {
            return false;
        }

        restoredTarget.setGlowingTag(true);
        state.chunkPos = restoredChunk;
        state.targetEntityUuid = restoredTarget.getUUID();
        state.safeTeleportPos = restoredTarget.blockPosition();
        return true;
    }

    private static ChunkPos readChunkPos(Entity entity) {
        Integer chunkX = null;
        Integer chunkZ = null;

        for (String tag : entity.entityTags()) {
            if (tag.startsWith(CHUNK_X_TAG_PREFIX)) {
                chunkX = parseChunkCoordinate(tag, CHUNK_X_TAG_PREFIX);
            } else if (tag.startsWith(CHUNK_Z_TAG_PREFIX)) {
                chunkZ = parseChunkCoordinate(tag, CHUNK_Z_TAG_PREFIX);
            }
        }

        if (chunkX == null || chunkZ == null) {
            return null;
        }

        return new ChunkPos(chunkX, chunkZ);
    }

    private static Integer parseChunkCoordinate(String tag, String prefix) {
        try {
            return Integer.parseInt(tag.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void clearTargetTags(Entity entity) {
        entity.removeTag(TARGET_TAG);
        entity.entityTags().stream()
                .filter(tag -> tag.startsWith(CHUNK_X_TAG_PREFIX) || tag.startsWith(CHUNK_Z_TAG_PREFIX))
                .toList()
                .forEach(entity::removeTag);
    }

    private static BlockPos findSafeSpawnPos(ServerLevel world, ChunkAccess chunk, Entity entity, BlockPos fallback) {
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        int preferredY = Math.max(minY + 1, Math.min(maxY - 1, fallback.getY()));
        boolean prefersWater = WATER_ENTITIES.contains(entity.getType());
        BlockPos found = findSafeSpawnPos(world, chunk, entity, prefersWater, preferredY);
        if (found == null && prefersWater) {
            found = findSafeSpawnPos(world, chunk, entity, false, preferredY);
        }
        if (found != null) {
            return found;
        }

        ChunkPos chunkPos = chunk.getPos();
        int x = Math.max(chunkPos.getMinBlockX(), Math.min(chunkPos.getMaxBlockX(), fallback.getX()));
        int z = Math.max(chunkPos.getMinBlockZ(), Math.min(chunkPos.getMaxBlockZ(), fallback.getZ()));
        BlockPos fallbackPos = new BlockPos(x, preferredY, z);
        if (!isDisallowedNetherRoofPos(world, fallbackPos)) {
            return fallbackPos;
        }

        int y = Math.max(minY + 1, Math.min(maxY - 1, preferredY));
        return new BlockPos(x, y, z);
    }

    private static BlockPos findSafeSpawnPos(ServerLevel world, ChunkAccess chunk, Entity entity, boolean waterRequired, int preferredY) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        int xOffset = Math.floorMod(chunkPos.x() * 31 + chunkPos.z() * 17, 16);
        int zOffset = Math.floorMod(chunkPos.z() * 29 + chunkPos.x() * 13, 16);

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkPos.getMinBlockX() + ((dx + xOffset) & 15);
                int z = chunkPos.getMinBlockZ() + ((dz + zOffset) & 15);

                BlockPos candidate = waterRequired
                        ? findWaterSpawnPos(world, entity, x, z, minY, maxY, preferredY)
                        : findSurfaceSpawnPos(world, entity, x, z, minY, maxY, preferredY);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static BlockPos findSurfaceSpawnPos(ServerLevel world, Entity entity, int x, int z, int minY, int maxY, int preferredY) {
        int startY = Math.max(minY + 1, Math.min(maxY - 1, preferredY));
        int maxOffset = Math.max(maxY - startY, startY - (minY + 1));

        for (int offset = 0; offset <= maxOffset; offset++) {
            int upY = startY + offset;
            if (upY <= maxY - 1) {
                BlockPos candidate = trySurfaceSpawnPos(world, entity, x, z, upY);
                if (candidate != null) {
                    return candidate;
                }
            }

            if (offset == 0) {
                continue;
            }

            int downY = startY - offset;
            if (downY >= minY + 1) {
                BlockPos candidate = trySurfaceSpawnPos(world, entity, x, z, downY);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static BlockPos trySurfaceSpawnPos(ServerLevel world, Entity entity, int x, int z, int y) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState floorState = world.getBlockState(pos.below());
        if (!floorState.blocksMotion()) {
            return null;
        }
        if (isDisallowedNetherRoofPos(world, pos)) {
            return null;
        }
        if (!world.getFluidState(pos).isEmpty() || !world.getFluidState(pos.above()).isEmpty()) {
            return null;
        }
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.above()).isAir()) {
            return null;
        }

        entity.snapTo(x + 0.5, y, z + 0.5, 0.0f, 0.0f);
        return world.noCollision(entity) ? pos : null;
    }

    private static BlockPos findWaterSpawnPos(ServerLevel world, Entity entity, int x, int z, int minY, int maxY, int preferredY) {
        int startY = Math.max(minY, Math.min(maxY, preferredY));
        int maxOffset = Math.max(maxY - startY, startY - minY);

        for (int offset = 0; offset <= maxOffset; offset++) {
            int upY = startY + offset;
            if (upY <= maxY) {
                BlockPos candidate = tryWaterSpawnPos(world, entity, x, z, upY);
                if (candidate != null) {
                    return candidate;
                }
            }

            if (offset == 0) {
                continue;
            }

            int downY = startY - offset;
            if (downY >= minY) {
                BlockPos candidate = tryWaterSpawnPos(world, entity, x, z, downY);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static BlockPos tryWaterSpawnPos(ServerLevel world, Entity entity, int x, int z, int y) {
        BlockPos pos = new BlockPos(x, y, z);
        if (isDisallowedNetherRoofPos(world, pos)) {
            return null;
        }
        if (world.getFluidState(pos).isEmpty()) {
            return null;
        }
        if (!world.getFluidState(pos.above()).isEmpty() && !world.getBlockState(pos.above()).isAir()) {
            return null;
        }

        entity.snapTo(x + 0.5, y, z + 0.5, 0.0f, 0.0f);
        return world.noCollision(entity) ? pos : null;
    }

    private static boolean isDisallowedNetherRoofPos(ServerLevel world, BlockPos pos) {
        return world.dimension() == Level.NETHER
                && pos.getY() >= NETHER_ROOF_MIN_Y
                && world.getBlockState(pos.below()).is(Blocks.BEDROCK);
    }

    private static void keepPlayersInsideBorder(ServerLevel world, EncounterState state) {
        if (state.safeTeleportPos == null || state.chunkPos == null) return;

        double x = state.safeTeleportPos.getX() + 0.5;
        double y = state.safeTeleportPos.getY();
        double z = state.safeTeleportPos.getZ() + 0.5;

        for (ServerPlayer player : world.players()) {
            if (!isInsideChunkBounds(player.getX(), player.getZ(), state.chunkPos)) {
                player.teleportTo(x, y, z);
            }
        }
    }

    private static boolean isInsideChunkBounds(double x, double z, ChunkPos chunkPos) {
        return x >= chunkPos.getMinBlockX() && x < chunkPos.getMinBlockX() + 16
                && z >= chunkPos.getMinBlockZ() && z < chunkPos.getMinBlockZ() + 16;
    }

    private static void keepTargetInsideChunk(ServerLevel world, EncounterState state, Entity target) {
        if (state.chunkPos == null) return;
        boolean inAssignedChunk = target.chunkPosition().x() == state.chunkPos.x() && target.chunkPosition().z() == state.chunkPos.z();
        boolean disallowedPosition = isDisallowedNetherRoofPos(world, target.blockPosition());
        if (inAssignedChunk && !disallowedPosition) {
            return;
        }

        BlockPos fallback = getPreferredAnchor(world, state, target);
        BlockPos safePos = findSafeSpawnPos(world, world.getChunk(state.chunkPos.x(), state.chunkPos.z()), target, fallback);
        state.safeTeleportPos = safePos;
        target.setDeltaMovement(0.0, 0.0, 0.0);
        target.snapTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, target.getYRot(), target.getXRot());
    }

    private static BlockPos getPreferredAnchor(ServerLevel world, EncounterState state, Entity target) {
        ServerPlayer bestPlayer = null;
        double bestDistance = Double.MAX_VALUE;

        for (ServerPlayer player : world.players()) {
            if (state.chunkPos != null && (player.chunkPosition().x() != state.chunkPos.x() || player.chunkPosition().z() != state.chunkPos.z())) {
                continue;
            }

            double distance = player.distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPlayer = player;
            }
        }

        if (bestPlayer != null) {
            return bestPlayer.blockPosition();
        }
        if (state.safeTeleportPos != null) {
            return state.safeTeleportPos;
        }
        return target.blockPosition();
    }

    private static void rememberCurrentChunks(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            rememberChunk(player, new PlayerChunkState(world.dimension(), player.chunkPosition()));
        }
    }

    private static PlayerChunkState getRememberedChunk(ServerPlayer player) {
        PlayerChunkState cached = LAST_PLAYER_CHUNKS.get(player.getUUID());
        if (cached != null) {
            return cached;
        }

        PlayerChunkState persisted = readPlayerChunkTag(player);
        if (persisted != null) {
            LAST_PLAYER_CHUNKS.put(player.getUUID(), persisted);
        }
        return persisted;
    }

    private static void rememberChunk(ServerPlayer player, PlayerChunkState state) {
        PlayerChunkState previous = LAST_PLAYER_CHUNKS.put(player.getUUID(), state);
        if (state.equals(previous)) {
            return;
        }
        writePlayerChunkTag(player, state);
    }

    private static PlayerChunkState readPlayerChunkTag(ServerPlayer player) {
        for (String tag : player.entityTags()) {
            if (!tag.startsWith(PLAYER_CHUNK_TAG_PREFIX)) {
                continue;
            }

            String[] parts = tag.substring(PLAYER_CHUNK_TAG_PREFIX.length()).split("\\|", 3);
            if (parts.length != 3) {
                continue;
            }

            try {
                ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(parts[0]));
                return new PlayerChunkState(worldKey, new ChunkPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static void writePlayerChunkTag(ServerPlayer player, PlayerChunkState state) {
        player.entityTags().stream()
                .filter(tag -> tag.startsWith(PLAYER_CHUNK_TAG_PREFIX))
                .toList()
                .forEach(player::removeTag);
        player.addTag(PLAYER_CHUNK_TAG_PREFIX + state.worldKey().identifier() + "|" + state.chunkPos().x() + "|" + state.chunkPos().z());
    }

    private static void endEncounter(ServerLevel world, EncounterState state) {
        state.clear();
        resetWorldBorder(world);
    }

    private static void lockWorldBorder(ServerLevel world, ChunkPos chunkPos) {
        double centerX = chunkPos.getMinBlockX() + 8.0;
        double centerZ = chunkPos.getMinBlockZ() + 8.0;
        applyBorderState(world, centerX, centerZ, LOCKED_BORDER_SIZE);
    }

    private static void applyBorderState(ServerLevel world, Double centerX, Double centerZ, double size) {
        ServerLevel rootWorld = world.getServer() != null ? world.getServer().overworld() : world;
        Double rawCenterX = centerX;
        Double rawCenterZ = centerZ;
        if (centerX != null && centerZ != null && rootWorld != world) {
            double scale = DimensionType.getTeleportationScale(world.dimensionType(), rootWorld.dimensionType());
            rawCenterX = centerX * scale;
            rawCenterZ = centerZ * scale;
        }

        updateBorder(rootWorld.getWorldBorder(), rawCenterX, rawCenterZ, size);
        if (rootWorld != world) {
            updateBorder(world.getWorldBorder(), rawCenterX, rawCenterZ, size);
        }
    }

    private static void updateBorder(WorldBorder border, Double centerX, Double centerZ, double size) {
        if (centerX != null && centerZ != null
                && (Math.abs(border.getCenterX() - centerX) > 0.001 || Math.abs(border.getCenterZ() - centerZ) > 0.001)) {
            border.setCenter(centerX, centerZ);
        }
        if (Math.abs(border.getSize() - size) > 0.001) {
            border.setSize(size);
        }
    }

    private static void syncClientWorldBorder(ServerLevel world, boolean force) {
        EncounterState state = ENCOUNTERS.computeIfAbsent(world, unused -> new EncounterState());
        ServerLevel rootWorld = world.getServer() != null ? world.getServer().overworld() : world;
        WorldBorder authoritativeBorder = rootWorld.getWorldBorder();
        int playerCount = world.players().size();
        double centerX = authoritativeBorder.getCenterX();
        double centerZ = authoritativeBorder.getCenterZ();
        double size = authoritativeBorder.getSize();

        if (!force
                && Math.abs(state.lastSyncedCenterX - centerX) < 0.001
                && Math.abs(state.lastSyncedCenterZ - centerZ) < 0.001
                && Math.abs(state.lastSyncedSize - size) < 0.001
                && state.lastSyncedPlayerCount == playerCount) {
            return;
        }

        ClientboundInitializeBorderPacket packet = new ClientboundInitializeBorderPacket(authoritativeBorder);
        for (ServerPlayer player : world.players()) {
            player.connection.send(packet);
        }

        state.lastSyncedCenterX = centerX;
        state.lastSyncedCenterZ = centerZ;
        state.lastSyncedSize = size;
        state.lastSyncedPlayerCount = playerCount;
    }

    private static final class EncounterState {
        private ChunkPos chunkPos;
        private UUID targetEntityUuid;
        private BlockPos safeTeleportPos;
        private double lastSyncedCenterX = Double.NaN;
        private double lastSyncedCenterZ = Double.NaN;
        private double lastSyncedSize = Double.NaN;
        private int lastSyncedPlayerCount = -1;

        private void clear() {
            this.chunkPos = null;
            this.targetEntityUuid = null;
            this.safeTeleportPos = null;
        }
    }

    private record PlayerChunkState(net.minecraft.resources.ResourceKey<Level> worldKey, ChunkPos chunkPos) {
        private boolean matches(ServerLevel world, ChunkPos currentChunk) {
            return worldKey.equals(world.dimension()) && chunkPos.x() == currentChunk.x() && chunkPos.z() == currentChunk.z();
        }
    }
}
