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
        ChallengeSavedData.get(server.getOverworld()).resetForNewWorld();
        LOGGER.info("Reset challenge progress for the upcoming new world.");

        // 2. Broadcast message
        server.getPlayerManager().broadcast(Text.literal("World restart initiated! The server will restart to create a fresh world...").formatted(Formatting.GOLD, Formatting.BOLD), false);

        // 3. Reset all players
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            resetPlayer(player);
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
                String typeName = f.getType().getName();
                String simpleTypeName = f.getType().getSimpleName();
                
                // Be broad with NBT type matching
                boolean isNbt = typeName.contains("nbt.NbtCompound") || 
                               typeName.contains("nbt.CompoundTag") || 
                               simpleTypeName.equals("NbtCompound") || 
                               simpleTypeName.equals("CompoundTag");

                if (isNbt) {
                    try {
                        f.setAccessible(true);
                        f.set(obj, null);
                        LOGGER.info("Cleared NBT field '{}.{}' in-memory", clazz.getSimpleName(), f.getName());
                    } catch (Exception e) {
                        LOGGER.warn("Could not clear NBT field '{}.{}'", clazz.getSimpleName(), f.getName());
                    }
                } else if (typeName.contains("EnderDragonFight$Data")) {
                    try {
                        f.setAccessible(true);
                        // Reset the dragon fight record to its DEFAULT state
                        java.lang.reflect.Field defaultField = f.getType().getDeclaredField("DEFAULT");
                        f.set(obj, defaultField.get(null));
                        LOGGER.info("Reset dragon fight data record '{}.{}' to DEFAULT", clazz.getSimpleName(), f.getName());
                    } catch (Exception e) {
                        LOGGER.warn("Could not reset dragon fight data record '{}.{}'", clazz.getSimpleName(), f.getName());
                    }
                } else if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                    String name = f.getName().toLowerCase();
                    // Reset 'initialized' and other flags to force fresh world/boss setup
                    if (name.equals("initialized") || name.contains("spawned") || name.contains("killed") || name.contains("dragon")) {
                        try {
                            f.setAccessible(true);
                            f.set(obj, false);
                            LOGGER.info("Reset boolean field '{}.{}' to false", clazz.getSimpleName(), f.getName());
                        } catch (Exception e) {
                            LOGGER.warn("Could not reset boolean field '{}.{}'", clazz.getSimpleName(), f.getName());
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
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
                try {
                    moveOrRecursive(src, archiveDir.resolve(name));
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

    private static void resetPlayer(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        player.setExperienceLevel(0);
        player.setExperiencePoints(0);
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.clearStatusEffects();
        
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "advancement revoke " + player.getNameForScoreboard() + " everything");
        }
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
