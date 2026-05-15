package net.kasax.challengecraft.network;

import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.mixin.MinecraftServerAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rotates a finished world out of the active save directory and prepares the next run in place.
 *
 * Most of the restart work happens before the replacement world boots, so a few helpers use
 * reflection to reset state that Minecraft does not expose through public APIs.
 */
public class ChallengeWorldRestarter {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Restarter");
    private static boolean needsTeleport = false;
    private static boolean rotationPending = false;

    public static void setNeedsTeleport(boolean v) {
        needsTeleport = v;
        if (v) LOGGER.info("needsTeleport set to true for next join/tick");
    }

    public static void setRotationPending(boolean v) {
        rotationPending = v;
        if (v) LOGGER.info("rotationPending set to true for randomization");
    }

    public static boolean isRotationPending() {
        return rotationPending;
    }

    public static void clearRotationPending() {
        rotationPending = false;
    }

    public static void handlePlayerTeleport(MinecraftServer server) {
        if (!needsTeleport) {
            LOGGER.info("[Teleport] No teleport pending.");
            return;
        }
        
        server.execute(() -> {
            needsTeleport = false; // Reset inside execute to avoid race if multiple JOINS happen fast
            server.getPlayerManager().getPlayerList().forEach(player -> {
                ServerWorld overworld = server.getOverworld();
                net.minecraft.util.math.BlockPos spawn = overworld.getSpawnPos();
                player.requestTeleport(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5);
                LOGGER.info("[Teleport] Teleported {} to safe spawn at {}", player.getName().getString(), spawn);
            });
        });
    }

    public static void initiateRestart(MinecraftServer server) {
        LOGGER.info("Initiating world restart via offline rotation...");

        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
        data.resetForNewWorld();
        
        // Persist before stopping so the next run can keep challenge settings while resetting progress.
        server.getOverworld().getPersistentStateManager().save();
        
        LOGGER.info("Reset challenge progress and saved persistent state for the upcoming new world.");

        server.getPlayerManager().broadcast(Text.translatable("challengecraft.restart.broadcast").formatted(Formatting.GOLD, Formatting.BOLD), false);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.translatable("challengecraft.restart.preparing").formatted(Formatting.YELLOW), false);
        }

        String worldName = ((MinecraftServerAccessor) server).getSession().getDirectoryName();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new RestartPendingPacket(worldName));
        }

        try {
            Path worldDir = server.getSavePath(WorldSavePath.ROOT);
            Files.writeString(worldDir.resolve("challengecraft_restart_pending"), "true");
            LOGGER.info("Created restart flag file in {}", worldDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create restart flag file!", e);
        }

        server.stop(false);
    }

    public static void initializeGenerators(MinecraftServer server) {
        net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setOverworldGenerator(null);
        net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setNetherGenerator(null);

        if (net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.isActive()) {
            try {
                var registries = server.getRegistryManager();
                var structLookup = registries.getOrThrow(net.minecraft.registry.RegistryKeys.STRUCTURE_SET);
                var dimRegistry = registries.getOrThrow(net.minecraft.registry.RegistryKeys.DIMENSION);
                
                var overworldOpt = dimRegistry.get(net.minecraft.world.dimension.DimensionOptions.OVERWORLD);
                if (overworldOpt != null) {
                    var biomeSource = overworldOpt.chunkGenerator().getBiomeSource();
                    net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setOverworldGenerator(
                        new net.kasax.challengecraft.world.SkyblockChunkGenerator(structLookup, biomeSource, false)
                    );
                    LOGGER.info("Initialized Skyblock Overworld generator for session.");
                }
                
                var netherOpt = dimRegistry.get(net.minecraft.world.dimension.DimensionOptions.NETHER);
                if (netherOpt != null) {
                    var biomeSource = netherOpt.chunkGenerator().getBiomeSource();
                    net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setNetherGenerator(
                        new net.kasax.challengecraft.world.SkyblockChunkGenerator(structLookup, biomeSource, true)
                    );
                    LOGGER.info("Initialized Skyblock Nether generator for session.");
                }
            } catch (Exception e) {
                LOGGER.error("Failed to pre-initialize Skyblock generators!", e);
            }
        }
    }

    public static void randomizeSeed(MinecraftServer server) {
        if (!rotationPending) return;
        
        try {
            net.minecraft.world.SaveProperties properties = ((net.kasax.challengecraft.mixin.MinecraftServerAccessor) server).getSaveProperties();
            if (properties == null) {
                LOGGER.warn("SaveProperties is null during randomization!");
                return;
            }
            long newSeed = new java.util.Random().nextLong();
            LOGGER.info("Randomizing seed in memory for fresh world. New seed: {}", newSeed);

            randomizeAllLongFields(properties, newSeed);
            
            resetSpawnFields(properties);

            clearNbtFields(properties);
            
            try {
                Object mainWorldProps = properties.getMainWorldProperties();
                if (mainWorldProps != null && mainWorldProps != properties) {
                    LOGGER.info("Cleaning internal MainWorldProperties...");
                    randomizeAllLongFields(mainWorldProps, newSeed);
                    resetSpawnFields(mainWorldProps);
                    clearNbtFields(mainWorldProps);
                }
            } catch (Throwable ignored) {}

            net.minecraft.world.gen.GeneratorOptions options = properties.getGeneratorOptions();
            if (options != null) {
                randomizeAllLongFields(options, newSeed);
            }

            try {
                for (java.lang.reflect.Field f : properties.getClass().getDeclaredFields()) {
                    if (f.getType() == com.mojang.serialization.Lifecycle.class) {
                        f.setAccessible(true);
                        f.set(properties, com.mojang.serialization.Lifecycle.stable());
                        LOGGER.info("[SeedReset] Forced world lifecycle to STABLE");
                        break;
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[SeedReset] Could not force world lifecycle to stable: {}", e.getMessage());
            }
            
            LOGGER.info("Seed randomization and state cleaning completed.");
            rotationPending = false; // Successfully handled
        } catch (Exception e) {
            LOGGER.error("Critical failure during seed randomization!", e);
        }
    }

    private static void randomizeAllLongFields(Object obj, long newVal) {
        if (obj == null) return;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                if (f.getType() == long.class) {
                    try {
                        f.setAccessible(true);
                        f.set(obj, newVal);
                        LOGGER.info("Updated long field '{}.{}' to {}", clazz.getSimpleName(), f.getName(), newVal);
                    } catch (Exception e) {
                        LOGGER.warn("Could not set long field '{}.{}'", clazz.getSimpleName(), f.getName());
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void resetSpawnFields(Object obj) {
        if (obj == null) return;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                String name = f.getName().toLowerCase();
                
                if (clazz.getSimpleName().equals("LevelProperties") || clazz.getSimpleName().equals("class_31")) {
                     try {
                         f.setAccessible(true);
                         Object val = f.get(obj);
                         LOGGER.info("[SpawnDebug] Field: {} (type: {}, value: {})", f.getName(), f.getType().getSimpleName(), val);
                         
                         // Current versions keep spawn as BlockPos; resetting that to zero can create unsafe starts.
                         if (f.getType().getSimpleName().contains("BlockPos") || f.getType().getSimpleName().contains("class_2338")) {
                             LOGGER.info("[SpawnDebug] Identified BlockPos field '{}', skipping reset to keep safe spawn.", f.getName());
                             continue;
                         }
                     } catch (Exception ignored) {}
                }

                if (name.contains("wandering") || name.contains("spawnangle") || name.contains("spawnforced")) {
                    try {
                        f.setAccessible(true);
                        if (f.getType() == int.class || f.getType() == Integer.class) {
                            f.set(obj, 0);
                            LOGGER.info("Reset int field '{}.{}' to 0", clazz.getSimpleName(), f.getName());
                        } else if (f.getType() == float.class || f.getType() == Float.class) {
                            f.set(obj, 0.0f);
                            LOGGER.info("Reset float field '{}.{}' to 0.0", clazz.getSimpleName(), f.getName());
                        } else if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                            f.set(obj, false);
                            LOGGER.info("Reset boolean field '{}.{}' to false", clazz.getSimpleName(), f.getName());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Could not reset field '{}.{}'", clazz.getSimpleName(), f.getName());
                    }
                } else if (name.equals("x") || name.equals("y") || name.equals("z") || name.contains("center")) {
                    // Leave spawn fields alone; zeroing them can move players into unsafe terrain.
                    if (!clazz.getSimpleName().contains("Properties") && !clazz.getSimpleName().contains("Level")) {
                        try {
                            f.setAccessible(true);
                            if (f.getType() == int.class || f.getType() == Integer.class) {
                                f.set(obj, 0);
                                LOGGER.info("Reset coordinate field '{}.{}' to 0", clazz.getSimpleName(), f.getName());
                            } else if (f.getType() == double.class || f.getType() == Double.class) {
                                f.set(obj, 0.0);
                                LOGGER.info("Reset coordinate field '{}.{}' to 0.0", clazz.getSimpleName(), f.getName());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void clearNbtFields(Object obj) {
        if (obj == null) return;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                Class<?> type = f.getType();
                String typeName = type.getName();
                
                boolean isNbt = net.minecraft.nbt.NbtCompound.class.isAssignableFrom(type);

                if (isNbt) {
                    String name = f.getName().toLowerCase();
                    // Empty player data compounds break login decoding; null makes vanilla rebuild the record.
                    if (name.contains("playerdata") || name.equals("field_169")) {
                        try {
                            f.setAccessible(true);
                            f.set(obj, null);
                            LOGGER.info("[SeedReset] Set playerData field '{}.{}' to null", clazz.getSimpleName(), f.getName());
                        } catch (Exception e) {
                            LOGGER.warn("[SeedReset] Could not nullify playerData field '{}.{}'", clazz.getSimpleName(), f.getName());
                        }
                        continue;
                    }
                    
                    try {
                        f.setAccessible(true);
                        f.set(obj, new net.minecraft.nbt.NbtCompound());
                        LOGGER.info("[SeedReset] Reset NBT field '{}.{}' to empty compound", clazz.getSimpleName(), f.getName());
                    } catch (Exception e) {
                        LOGGER.warn("[SeedReset] Could not clear NBT field '{}.{}'", clazz.getSimpleName(), f.getName());
                    }
                } else if (type == java.util.Optional.class) {
                    try {
                        f.setAccessible(true);
                        Object opt = f.get(obj);
                        if (opt instanceof java.util.Optional<?> optional && optional.isPresent()) {
                            Object value = optional.get();
                            if (isDragonFightData(value.getClass())) {
                                resetDragonFightField(f, obj, value.getClass(), true);
                                continue;
                            }
                        }
                        f.set(obj, java.util.Optional.empty());
                        LOGGER.info("[SeedReset] Cleared Optional field '{}.{}' in-memory", clazz.getSimpleName(), f.getName());
                    } catch (Exception e) {
                        LOGGER.warn("[SeedReset] Could not clear Optional field '{}.{}'", clazz.getSimpleName(), f.getName());
                    }
                } else if (isDragonFightData(type)) {
                    resetDragonFightField(f, obj, type, false);
                } else if (type == boolean.class || type == Boolean.class) {
                    String name = f.getName().toLowerCase();
                    if (name.equals("initialized") || name.contains("spawned") || name.contains("killed") || name.contains("dragon") || 
                        name.equals("field_192") || name.equals("field_176") || name.equals("field_185")) { // Common obfuscated names for initialized/dragonKilled
                        try {
                            f.setAccessible(true);
                            f.set(obj, false);
                            LOGGER.info("[SeedReset] Reset boolean field '{}.{}' to false", clazz.getSimpleName(), f.getName());
                        } catch (Exception e) {
                            LOGGER.warn("[SeedReset] Could not reset boolean field '{}.{}'", clazz.getSimpleName(), f.getName());
                        }
                    }
                } else if (typeName.contains("WorldBorder") || typeName.contains("class_2784")) {
                     try {
                         f.setAccessible(true);
                         Object border = f.get(obj);
                         if (border != null) {
                             resetSpawnFields(border); // Reuse resetSpawnFields for border coordinates
                         }
                     } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static boolean isDragonFightData(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        if (name.contains("EnderDragonFight$Data") || name.contains("class_4472$class_4473")) return true;

        try {
            boolean hasKilled = false;
            boolean hasSeen = false;
            for (java.lang.reflect.Field f : type.getDeclaredFields()) {
                String fn = f.getName().toLowerCase();
                if (fn.contains("dragonkilled") || fn.equals("field_21052") || fn.equals("dragonKilled") || fn.equals("comp_582")) hasKilled = true;
                if (fn.contains("needsstatescanning") || fn.equals("field_21051") || fn.equals("needsStateScanning")) hasSeen = true;
            }
            return (hasKilled && hasSeen) || type.isRecord();
        } catch (Throwable ignored) {}
        return false;
    }

    private static void resetDragonFightField(java.lang.reflect.Field f, Object obj, Class<?> type, boolean isOptional) {
        try {
            f.setAccessible(true);
            java.lang.reflect.Field defaultField = null;
            try {
                defaultField = type.getDeclaredField("field_20371"); // Intermediary for DEFAULT
            } catch (NoSuchFieldException ignored) {}

            if (defaultField == null) {
                try {
                    defaultField = type.getDeclaredField("DEFAULT");
                } catch (NoSuchFieldException ignored) {}
            }

            if (defaultField == null) {
                for (java.lang.reflect.Field staticField : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(staticField.getModifiers()) && staticField.getType() == type) {
                        defaultField = staticField;
                        break;
                    }
                }
            }

            if (defaultField != null) {
                Object defaultValue = defaultField.get(null);
                if (isOptional) {
                    f.set(obj, java.util.Optional.ofNullable(defaultValue));
                    LOGGER.info("[SeedReset] Reset Optional dragon fight field '{}.{}' to Optional(DEFAULT)", obj.getClass().getSimpleName(), f.getName());
                } else {
                    f.set(obj, defaultValue);
                    LOGGER.info("[SeedReset] Reset dragon fight field '{}.{}' to DEFAULT", obj.getClass().getSimpleName(), f.getName());
                }
            } else {
                LOGGER.warn("[SeedReset] Could not find DEFAULT field for dragon fight data in '{}'", type.getName());
            }
        } catch (Exception e) {
            LOGGER.warn("[SeedReset] Error resetting dragon fight field: {}", e.getMessage());
        }
    }

    public static void performOfflineRotation(Path worldDir) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path archiveDir = worldDir.resolve("old worlds").resolve("run_" + timestamp);
        Files.createDirectories(archiveDir);

        LOGGER.info("Performing offline world rotation to {}", archiveDir);

        String[] toMove = {
            "region", "poi", "entities", "DIM1", "DIM-1", 
            "playerdata", "advancements", "stats", "data",
            "level.dat", "level.dat_old", "uid.dat"
        };

        for (String name : toMove) {
            Path src = worldDir.resolve(name);
            if (Files.exists(src)) {
                boolean isDir = Files.isDirectory(src);
                try {
                    moveOrRecursive(src, archiveDir.resolve(name));
                    // Minecraft expects these directories to exist again before the next save pass.
                    if (isDir && (name.equals("playerdata") || name.equals("advancements") || name.equals("stats") || 
                                  name.equals("region") || name.equals("poi") || name.equals("entities"))) {
                        Files.createDirectories(src);
                        LOGGER.info("[Rotation] Recreated empty directory: {}", name);
                    }
                } catch (IOException e) {
                    LOGGER.warn("Could not move {} during offline rotation: {}", name, e.getMessage());
                }
            }
        }
        
        // Keep the challenge configuration file in place so the next run preserves the chosen rules.
        Path dataDir = worldDir.resolve("data");
        if (Files.exists(dataDir)) {
            Path archiveDataDir = archiveDir.resolve("data");
            Files.createDirectories(archiveDataDir);
            try (Stream<Path> files = Files.list(dataDir)) {
                for (Path p : files.collect(Collectors.toList())) {
                    String fileName = p.getFileName().toString();
                    if (!fileName.equals("challengecraft_challenges.dat")) {
                        try {
                            moveOrRecursive(p, archiveDataDir.resolve(fileName));
                        } catch (IOException e) {
                            LOGGER.warn("Could not move data item {}: {}", fileName, e.getMessage());
                        }
                    }
                }
            }
        }
        
        deleteRecursive(worldDir.resolve("DIM1"));
        deleteRecursive(worldDir.resolve("DIM-1"));
    }

    private static void deleteRecursive(Path path) {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                  .forEach(p -> {
                      try {
                          Files.delete(p);
                      } catch (IOException ignored) {}
                  });
        } catch (IOException ignored) {}
    }

    private static void moveOrRecursive(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) return;
        try {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (Files.isDirectory(src)) {
                Files.createDirectories(dst);
                try (Stream<Path> stream = Files.list(src)) {
                    for (Path p : stream.collect(Collectors.toList())) {
                        moveOrRecursive(p, dst.resolve(p.getFileName()));
                    }
                }
                try {
                    Files.deleteIfExists(src);
                } catch (IOException ignored) {}
            } else {
                LOGGER.warn("Could not move locked file {}: {}", src, e.getMessage());
            }
        }
    }
}
