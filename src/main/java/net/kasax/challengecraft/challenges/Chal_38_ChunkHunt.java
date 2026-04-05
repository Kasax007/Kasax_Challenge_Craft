package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.WorldBorderInitializeS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class Chal_38_ChunkHunt {
    private static final double LOCKED_BORDER_SIZE = 16.0;
    private static final double NORMAL_BORDER_SIZE = 6.0E7;
    private static final int NETHER_ROOF_MIN_Y = 120;
    private static final String TARGET_TAG = "challengecraft_chunk_hunt_target";
    private static final String CHUNK_X_TAG_PREFIX = "challengecraft_chunk_hunt_x_";
    private static final String CHUNK_Z_TAG_PREFIX = "challengecraft_chunk_hunt_z_";
    private static final String PLAYER_CHUNK_TAG_PREFIX = "challengecraft_chunk_hunt_player_chunk|";
    private static final Map<ServerWorld, EncounterState> ENCOUNTERS = new WeakHashMap<>();
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
        ServerTickEvents.END_WORLD_TICK.register(Chal_38_ChunkHunt::tickWorld);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_PLAYER_CHUNKS.remove(handler.player.getUuid()));
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

    public static void updateWorldBorder(ServerWorld world) {
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

    public static void resetWorldBorder(ServerWorld world) {
        applyBorderState(world, null, null, NORMAL_BORDER_SIZE);
        syncClientWorldBorder(world, true);
    }

    private static void tickWorld(ServerWorld world) {
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

        for (ServerPlayerEntity player : world.getPlayers()) {
            ChunkPos currentChunk = player.getChunkPos();
            PlayerChunkState currentState = new PlayerChunkState(world.getRegistryKey(), currentChunk);
            PlayerChunkState previous = getRememberedChunk(player);
            rememberChunk(player, currentState);
            if (previous == null || !previous.matches(world, currentChunk)) {
                startEncounter(world, player, currentChunk, state);
                return;
            }
        }
    }

    private static void startEncounter(ServerWorld world, ServerPlayerEntity player, ChunkPos chunkPos, EncounterState state) {
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

        EntityType<?> entityType = entities.get(world.random.nextInt(entities.size()));
        Entity entity = entityType.create(world, SpawnReason.EVENT);
        if (entity == null) {
            resetWorldBorder(world);
            return;
        }

        Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        BlockPos spawnPos = findSafeSpawnPos(world, chunk, entity, player.getBlockPos());
        entity.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
        entity.setGlowing(true);
        entity.addCommandTag(TARGET_TAG);
        entity.addCommandTag(CHUNK_X_TAG_PREFIX + chunkPos.x);
        entity.addCommandTag(CHUNK_Z_TAG_PREFIX + chunkPos.z);
        if (entity instanceof MobEntity mob) {
            mob.setPersistent();
        }

        if (!world.isSpaceEmpty(entity) || !world.spawnEntity(entity)) {
            resetWorldBorder(world);
            return;
        }

        state.chunkPos = chunkPos;
        state.targetEntityUuid = entity.getUuid();
        state.safeTeleportPos = spawnPos;
        lockWorldBorder(world, chunkPos);
        syncClientWorldBorder(world, true);
    }

    private static boolean restoreEncounter(ServerWorld world, EncounterState state, ChunkPos preferredChunk) {
        Entity restoredTarget = null;
        ChunkPos restoredChunk = null;

        for (Entity entity : world.iterateEntities()) {
            if (!entity.isAlive() || !entity.getCommandTags().contains(TARGET_TAG)) {
                continue;
            }

            ChunkPos entityChunk = readChunkPos(entity);
            if (entityChunk == null) {
                entityChunk = entity.getChunkPos();
            }

            if (preferredChunk != null && (entityChunk.x != preferredChunk.x || entityChunk.z != preferredChunk.z)) {
                continue;
            }

            if (restoredTarget == null) {
                restoredTarget = entity;
                restoredChunk = entityChunk;
                continue;
            }

            clearTargetTags(entity);
            entity.setGlowing(false);
        }

        if (restoredTarget == null || restoredChunk == null) {
            return false;
        }

        restoredTarget.setGlowing(true);
        state.chunkPos = restoredChunk;
        state.targetEntityUuid = restoredTarget.getUuid();
        state.safeTeleportPos = restoredTarget.getBlockPos();
        return true;
    }

    private static ChunkPos readChunkPos(Entity entity) {
        Integer chunkX = null;
        Integer chunkZ = null;

        for (String tag : entity.getCommandTags()) {
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
        entity.removeCommandTag(TARGET_TAG);
        entity.getCommandTags().stream()
                .filter(tag -> tag.startsWith(CHUNK_X_TAG_PREFIX) || tag.startsWith(CHUNK_Z_TAG_PREFIX))
                .toList()
                .forEach(entity::removeCommandTag);
    }

    private static BlockPos findSafeSpawnPos(ServerWorld world, Chunk chunk, Entity entity, BlockPos fallback) {
        int minY = chunk.getBottomY();
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
        int x = Math.max(chunkPos.getStartX(), Math.min(chunkPos.getEndX(), fallback.getX()));
        int z = Math.max(chunkPos.getStartZ(), Math.min(chunkPos.getEndZ(), fallback.getZ()));
        BlockPos fallbackPos = new BlockPos(x, preferredY, z);
        if (!isDisallowedNetherRoofPos(world, fallbackPos)) {
            return fallbackPos;
        }

        int y = Math.max(minY + 1, Math.min(maxY - 1, preferredY));
        return new BlockPos(x, y, z);
    }

    private static BlockPos findSafeSpawnPos(ServerWorld world, Chunk chunk, Entity entity, boolean waterRequired, int preferredY) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getBottomY();
        int maxY = minY + chunk.getHeight() - 1;
        int xOffset = Math.floorMod(chunkPos.x * 31 + chunkPos.z * 17, 16);
        int zOffset = Math.floorMod(chunkPos.z * 29 + chunkPos.x * 13, 16);

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkPos.getStartX() + ((dx + xOffset) & 15);
                int z = chunkPos.getStartZ() + ((dz + zOffset) & 15);

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

    private static BlockPos findSurfaceSpawnPos(ServerWorld world, Entity entity, int x, int z, int minY, int maxY, int preferredY) {
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

    private static BlockPos trySurfaceSpawnPos(ServerWorld world, Entity entity, int x, int z, int y) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState floorState = world.getBlockState(pos.down());
        if (!floorState.blocksMovement()) {
            return null;
        }
        if (isDisallowedNetherRoofPos(world, pos)) {
            return null;
        }
        if (!world.getFluidState(pos).isEmpty() || !world.getFluidState(pos.up()).isEmpty()) {
            return null;
        }
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            return null;
        }

        entity.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0.0f, 0.0f);
        return world.isSpaceEmpty(entity) ? pos : null;
    }

    private static BlockPos findWaterSpawnPos(ServerWorld world, Entity entity, int x, int z, int minY, int maxY, int preferredY) {
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

    private static BlockPos tryWaterSpawnPos(ServerWorld world, Entity entity, int x, int z, int y) {
        BlockPos pos = new BlockPos(x, y, z);
        if (isDisallowedNetherRoofPos(world, pos)) {
            return null;
        }
        if (world.getFluidState(pos).isEmpty()) {
            return null;
        }
        if (!world.getFluidState(pos.up()).isEmpty() && !world.getBlockState(pos.up()).isAir()) {
            return null;
        }

        entity.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0.0f, 0.0f);
        return world.isSpaceEmpty(entity) ? pos : null;
    }

    private static boolean isDisallowedNetherRoofPos(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey() == World.NETHER
                && pos.getY() >= NETHER_ROOF_MIN_Y
                && world.getBlockState(pos.down()).isOf(Blocks.BEDROCK);
    }

    private static void keepPlayersInsideBorder(ServerWorld world, EncounterState state) {
        if (state.safeTeleportPos == null || state.chunkPos == null) return;

        double x = state.safeTeleportPos.getX() + 0.5;
        double y = state.safeTeleportPos.getY();
        double z = state.safeTeleportPos.getZ() + 0.5;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isInsideChunkBounds(player.getX(), player.getZ(), state.chunkPos)) {
                player.requestTeleport(x, y, z);
            }
        }
    }

    private static boolean isInsideChunkBounds(double x, double z, ChunkPos chunkPos) {
        return x >= chunkPos.getStartX() && x < chunkPos.getStartX() + 16
                && z >= chunkPos.getStartZ() && z < chunkPos.getStartZ() + 16;
    }

    private static void keepTargetInsideChunk(ServerWorld world, EncounterState state, Entity target) {
        if (state.chunkPos == null) return;
        boolean inAssignedChunk = target.getChunkPos().x == state.chunkPos.x && target.getChunkPos().z == state.chunkPos.z;
        boolean disallowedPosition = isDisallowedNetherRoofPos(world, target.getBlockPos());
        if (inAssignedChunk && !disallowedPosition) {
            return;
        }

        BlockPos fallback = getPreferredAnchor(world, state, target);
        BlockPos safePos = findSafeSpawnPos(world, world.getChunk(state.chunkPos.x, state.chunkPos.z), target, fallback);
        state.safeTeleportPos = safePos;
        target.setVelocity(0.0, 0.0, 0.0);
        target.refreshPositionAndAngles(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, target.getYaw(), target.getPitch());
    }

    private static BlockPos getPreferredAnchor(ServerWorld world, EncounterState state, Entity target) {
        ServerPlayerEntity bestPlayer = null;
        double bestDistance = Double.MAX_VALUE;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (state.chunkPos != null && (player.getChunkPos().x != state.chunkPos.x || player.getChunkPos().z != state.chunkPos.z)) {
                continue;
            }

            double distance = player.squaredDistanceTo(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPlayer = player;
            }
        }

        if (bestPlayer != null) {
            return bestPlayer.getBlockPos();
        }
        if (state.safeTeleportPos != null) {
            return state.safeTeleportPos;
        }
        return target.getBlockPos();
    }

    private static void rememberCurrentChunks(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            rememberChunk(player, new PlayerChunkState(world.getRegistryKey(), player.getChunkPos()));
        }
    }

    private static PlayerChunkState getRememberedChunk(ServerPlayerEntity player) {
        PlayerChunkState cached = LAST_PLAYER_CHUNKS.get(player.getUuid());
        if (cached != null) {
            return cached;
        }

        PlayerChunkState persisted = readPlayerChunkTag(player);
        if (persisted != null) {
            LAST_PLAYER_CHUNKS.put(player.getUuid(), persisted);
        }
        return persisted;
    }

    private static void rememberChunk(ServerPlayerEntity player, PlayerChunkState state) {
        PlayerChunkState previous = LAST_PLAYER_CHUNKS.put(player.getUuid(), state);
        if (state.equals(previous)) {
            return;
        }
        writePlayerChunkTag(player, state);
    }

    private static PlayerChunkState readPlayerChunkTag(ServerPlayerEntity player) {
        for (String tag : player.getCommandTags()) {
            if (!tag.startsWith(PLAYER_CHUNK_TAG_PREFIX)) {
                continue;
            }

            String[] parts = tag.substring(PLAYER_CHUNK_TAG_PREFIX.length()).split("\\|", 3);
            if (parts.length != 3) {
                continue;
            }

            try {
                RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(parts[0]));
                return new PlayerChunkState(worldKey, new ChunkPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static void writePlayerChunkTag(ServerPlayerEntity player, PlayerChunkState state) {
        player.getCommandTags().stream()
                .filter(tag -> tag.startsWith(PLAYER_CHUNK_TAG_PREFIX))
                .toList()
                .forEach(player::removeCommandTag);
        player.addCommandTag(PLAYER_CHUNK_TAG_PREFIX + state.worldKey().getValue() + "|" + state.chunkPos().x + "|" + state.chunkPos().z);
    }

    private static void endEncounter(ServerWorld world, EncounterState state) {
        state.clear();
        resetWorldBorder(world);
    }

    private static void lockWorldBorder(ServerWorld world, ChunkPos chunkPos) {
        double centerX = chunkPos.getStartX() + 8.0;
        double centerZ = chunkPos.getStartZ() + 8.0;
        applyBorderState(world, centerX, centerZ, LOCKED_BORDER_SIZE);
    }

    private static void applyBorderState(ServerWorld world, Double centerX, Double centerZ, double size) {
        ServerWorld rootWorld = world.getServer() != null ? world.getServer().getOverworld() : world;
        Double rawCenterX = centerX;
        Double rawCenterZ = centerZ;
        if (centerX != null && centerZ != null && rootWorld != world) {
            double scale = DimensionType.getCoordinateScaleFactor(world.getDimension(), rootWorld.getDimension());
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

    private static void syncClientWorldBorder(ServerWorld world, boolean force) {
        EncounterState state = ENCOUNTERS.computeIfAbsent(world, unused -> new EncounterState());
        ServerWorld rootWorld = world.getServer() != null ? world.getServer().getOverworld() : world;
        WorldBorder authoritativeBorder = rootWorld.getWorldBorder();
        int playerCount = world.getPlayers().size();
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

        WorldBorderInitializeS2CPacket packet = new WorldBorderInitializeS2CPacket(authoritativeBorder);
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.networkHandler.sendPacket(packet);
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

    private record PlayerChunkState(net.minecraft.registry.RegistryKey<World> worldKey, ChunkPos chunkPos) {
        private boolean matches(ServerWorld world, ChunkPos currentChunk) {
            return worldKey.equals(world.getRegistryKey()) && chunkPos.x == currentChunk.x && chunkPos.z == currentChunk.z;
        }
    }
}
