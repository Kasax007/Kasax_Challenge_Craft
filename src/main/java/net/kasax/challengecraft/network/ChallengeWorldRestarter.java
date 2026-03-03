package net.kasax.challengecraft.network;

import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.LevelManager;
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

    public static void randomizeSeed(MinecraftServer server) {
        try {
            net.minecraft.world.SaveProperties properties = ((net.kasax.challengecraft.mixin.MinecraftServerAccessor) server).getSaveProperties();
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
            GeneratorOptions options = properties.getGeneratorOptions();
            if (options != null) {
                randomizeAllLongFields(options, newSeed);
            }
            
            LOGGER.info("Seed randomization and state cleaning completed.");
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
                // Target spawn coordinates and wandering trader state
                if (name.contains("spawn") || name.contains("wandering") || name.equals("x") || name.equals("y") || name.equals("z")) {
                    try {
                        f.setAccessible(true);
                        if (f.getType() == int.class || f.getType() == Integer.class) {
                            f.set(obj, 0);
                            LOGGER.info("Reset int field '{}.{}' to 0", clazz.getSimpleName(), f.getName());
                        } else if (f.getType() == float.class || f.getType() == Float.class) {
                            f.set(obj, 0.0f);
                            LOGGER.info("Reset float field '{}.{}' to 0.0", clazz.getSimpleName(), f.getName());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Could not reset field '{}.{}'", clazz.getSimpleName(), f.getName());
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
                    try {
                        f.setAccessible(true);
                        // Using a new NbtCompound instead of null to avoid potential corruption/NPEs when saving
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
                        name.equals("field_192") || name.equals("field_176")) { // Common obfuscated names for initialized/dragonKilled
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
            "playerdata", "advancements", "stats", 
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
