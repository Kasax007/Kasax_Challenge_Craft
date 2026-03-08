package net.kasax.challengecraft.network;

import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld;
import net.kasax.challengecraft.mixin.MinecraftServerAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.gen.GeneratorOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                // Use requestTeleport which is safer across versions
                player.requestTeleport(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5);
                LOGGER.info("[Teleport] Teleported {} to safe spawn at {}", player.getName().getString(), spawn);
            });
        });
    }

    public static void initiateRestart(MinecraftServer server) {
        LOGGER.info("Initiating world restart via offline rotation...");

        // 1. Reset challenge progress for the upcoming new world
        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
        data.resetForNewWorld();
        
        // Save the persistent state manager to ensure ChallengeSavedData is written to disk
        // before we stop the server and move the data folder.
        server.getOverworld().getPersistentStateManager().save();
        
        LOGGER.info("Reset challenge progress and saved persistent state for the upcoming new world.");

        // 2. Broadcast message
        server.getPlayerManager().broadcast(Text.literal("World restart initiated! The server will restart to create a fresh world...").formatted(Formatting.GOLD, Formatting.BOLD), false);

        // 3. Send message to players
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("Preparing for restart...").formatted(Formatting.YELLOW), false);
        }

        String worldName = ((MinecraftServerAccessor) server).getSession().getDirectoryName();

        // 4. Send packet to client so it knows to auto-restart
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new RestartPendingPacket(worldName));
        }

        // 5. Create the flag file
        try {
            Path worldDir = server.getSavePath(WorldSavePath.ROOT);
            Files.writeString(worldDir.resolve("challengecraft_restart_pending"), "true");
            LOGGER.info("Created restart flag file in {}", worldDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create restart flag file!", e);
        }

        // 6. Stop the server
        server.stop(false);
    }

    public static void initializeGenerators(MinecraftServer server) {
        // Pre-initialize Skyblock generators if active
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

            // 1. Try to randomize all long fields in the main properties object
            randomizeAllLongFields(properties, newSeed);
            
            // 2. Reset spawn coordinates so the game finds a new safe spot
            resetSpawnFields(properties);

            // 3. Clear player data and boss events so they don't persist in the new world
            clearNbtFields(properties);
            
            // 4. Specifically target MainWorldProperties if separate
            try {
                Object mainWorldProps = properties.getMainWorldProperties();
                if (mainWorldProps != null && mainWorldProps != properties) {
                    LOGGER.info("Cleaning internal MainWorldProperties...");
                    randomizeAllLongFields(mainWorldProps, newSeed);
                    resetSpawnFields(mainWorldProps);
                    clearNbtFields(mainWorldProps);
                }
            } catch (Throwable ignored) {}

            // 5. Try to randomize all long fields in GeneratorOptions
            net.minecraft.world.gen.GeneratorOptions options = properties.getGeneratorOptions();
            if (options != null) {
                randomizeAllLongFields(options, newSeed);
            }

            // 6. Force lifecycle to Stable to avoid "Experimental settings" warning
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
                
                // Debug log all fields in LevelProperties to identify coordinate fields
                if (clazz.getSimpleName().equals("LevelProperties") || clazz.getSimpleName().equals("class_31")) {
                     try {
                         f.setAccessible(true);
                         Object val = f.get(obj);
                         LOGGER.info("[SpawnDebug] Field: {} (type: {}, value: {})", f.getName(), f.getType().getSimpleName(), val);
                         
                         // In 1.21.5+, spawnPos might be a BlockPos field (e.g. field_48380)
                         // We definitely want to AVOID resetting it to 0,0,0 here.
                         if (f.getType().getSimpleName().contains("BlockPos") || f.getType().getSimpleName().contains("class_2338")) {
                             LOGGER.info("[SpawnDebug] Identified BlockPos field '{}', skipping reset to keep safe spawn.", f.getName());
                             continue;
                         }
                     } catch (Exception ignored) {}
                }

                // Target wandering trader state, but avoid resetting spawn coordinates directly to 0,0,0
                // as this can spawn players in the void or inside blocks.
                // We want to keep wandering trader reset but let Minecraft handle the spawn location.
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
                    // Only reset x, y, z if they belong to a WorldBorder-like object (handled via clearNbtFields call)
                    // or if explicitly handled here for non-spawn objects.
                    // When called on LevelProperties, we want to AVOID resetting spawnX, spawnY, spawnZ.
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
                
                // Use class references directly for better reliability
                boolean isNbt = net.minecraft.nbt.NbtCompound.class.isAssignableFrom(type);

                if (isNbt) {
                    String name = f.getName().toLowerCase();
                    // Avoid clearing playerdata in a way that breaks login ("Not a string" error)
                    // Instead of new NbtCompound(), we'll just let Minecraft re-initialize it or clear it carefully
                    if (name.contains("playerdata") || name.equals("field_169")) {
                        try {
                            f.setAccessible(true);
                            // Set to null to force Minecraft to use world spawn instead of an empty record
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
                    // Reset 'initialized' and other flags to force fresh world/boss setup
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
                     // Try to reset world border state in properties if possible
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

        // Generic check: is it a record-like class with dragon-related fields?
        try {
            boolean hasKilled = false;
            boolean hasSeen = false;
            for (java.lang.reflect.Field f : type.getDeclaredFields()) {
                String fn = f.getName().toLowerCase();
                // Check for dragonKilled or field_21052 (record component)
                if (fn.contains("dragonkilled") || fn.equals("field_21052") || fn.equals("dragonKilled") || fn.equals("comp_582")) hasKilled = true;
                // Check for needsStateScanning or field_21051
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

            // Robust fallback: Find any static field of the same type
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

        // Files/Folders to move to archive
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
                    // RECREATE important directories immediately to prevent potential save issues
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
        
        // Also move 'data' folder BUT preserve 'challengecraft_challenges.dat'
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
        
        // Clean up any remaining files in DIM folders just in case
        deleteRecursive(worldDir.resolve("DIM1"));
        deleteRecursive(worldDir.resolve("DIM-1"));
        
        // The server will now start and see no level.dat, so it will generate a brand new world with a new seed.
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

    private static void tryRotateFile(Path src, Path dst) {
        if (Files.exists(src)) {
            try {
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Could not move file {}: {}", src, e.getMessage());
            }
        }
    }

    private static void createDirs(Path base) throws IOException {
        Files.createDirectories(base.resolve("data"));
        Files.createDirectories(base.resolve("region"));
        Files.createDirectories(base.resolve("poi"));
        Files.createDirectories(base.resolve("entities"));
    }

    private static void moveOrRecursive(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) return;
        try {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Folder might be partially locked. Try to move individual files.
            if (Files.isDirectory(src)) {
                Files.createDirectories(dst);
                try (Stream<Path> stream = Files.list(src)) {
                    for (Path p : stream.collect(Collectors.toList())) {
                        moveOrRecursive(p, dst.resolve(p.getFileName()));
                    }
                }
                // Try to delete the directory if it's now empty
                try {
                    Files.deleteIfExists(src);
                } catch (IOException ignored) {}
            } else {
                LOGGER.warn("Could not move locked file {}: {}", src, e.getMessage());
            }
        }
    }
}
