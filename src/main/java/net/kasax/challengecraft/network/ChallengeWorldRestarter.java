package net.kasax.challengecraft.network;

import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.mixin.MinecraftServerAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
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
            server.getPlayerList().getPlayers().forEach(player -> {
                ServerLevel overworld = server.overworld();
                net.minecraft.core.BlockPos spawn = overworld.getRespawnData().pos();
                player.teleportTo(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5);
                LOGGER.info("[Teleport] Teleported {} to safe spawn at {}", player.getName().getString(), spawn);
            });
        });
    }

    public static void initiateRestart(MinecraftServer server) {
        LOGGER.info("Initiating world restart via offline rotation...");

        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
        data.resetForNewWorld();
        
        // Persist before stopping so the next run can keep challenge settings while resetting progress.
        server.overworld().getDataStorage().saveAndJoin();
        
        LOGGER.info("Reset challenge progress and saved persistent state for the upcoming new world.");

        server.getPlayerList().broadcastSystemMessage(Component.translatable("challengecraft.restart.broadcast").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("challengecraft.restart.preparing").withStyle(ChatFormatting.YELLOW));
        }

        String worldName = ((MinecraftServerAccessor) server).getSession().getLevelId();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new RestartPendingPacket(worldName));
        }

        try {
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            Files.writeString(worldDir.resolve("challengecraft_restart_pending"), "true");
            LOGGER.info("Created restart flag file in {}", worldDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create restart flag file!", e);
        }

        server.halt(false);
    }

    public static void initializeGenerators(MinecraftServer server) {
        net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setOverworldGenerator(null);
        net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setNetherGenerator(null);
        net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setVanillaOverworldGenerator(null);
        net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setVanillaNetherGenerator(null);

        if (!net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.isActive()) {
            restoreVanillaGenerators(server);
            return;
        }

        if (net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.isActive()) {
            try {
                var registries = server.registryAccess();
                var structLookup = registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
                var dimRegistry = registries.lookupOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM);
                
                var overworldOpt = dimRegistry.getValue(net.minecraft.world.level.dimension.LevelStem.OVERWORLD);
                if (overworldOpt != null) {
                    var biomeSource = overworldOpt.generator().getBiomeSource();
                    net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setOverworldGenerator(
                        new net.kasax.challengecraft.world.SkyblockChunkGenerator(structLookup, biomeSource, false)
                    );
                    LOGGER.info("Initialized Skyblock Overworld generator for session.");
                }
                
                var netherOpt = dimRegistry.getValue(net.minecraft.world.level.dimension.LevelStem.NETHER);
                if (netherOpt != null) {
                    var biomeSource = netherOpt.generator().getBiomeSource();
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

    /**
     * Builds vanilla generators to replace a Skyblock generator still stored in the world settings.
     *
     * <p>Only relevant when the challenge is OFF. Minecraft persists whatever
     * {@code LevelStem.generator()} returned, so a world that was ever Skyblock keeps saying so;
     * without this, deselecting the challenge and restarting produced another Skyblock world. The
     * biome source is taken from the stored generator itself — the Skyblock generator wraps the
     * original one — so the replacement keeps the world's own biome layout and only the terrain
     * shaping goes back to vanilla.
     */
    private static void restoreVanillaGenerators(MinecraftServer server) {
        try {
            var registries = server.registryAccess();
            var noiseSettings = registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS);
            var dimRegistry = registries.lookupOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM);

            var overworldOpt = dimRegistry.getValue(net.minecraft.world.level.dimension.LevelStem.OVERWORLD);
            if (overworldOpt != null
                    && overworldOpt.generator() instanceof net.kasax.challengecraft.world.SkyblockChunkGenerator sky) {
                net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setVanillaOverworldGenerator(
                        new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                                sky.getBiomeSource(),
                                noiseSettings.getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD)));
                LOGGER.info("[Skyblock] Challenge is off but the world still stored a Skyblock overworld generator — restored the vanilla one");
            }

            var netherOpt = dimRegistry.getValue(net.minecraft.world.level.dimension.LevelStem.NETHER);
            if (netherOpt != null
                    && netherOpt.generator() instanceof net.kasax.challengecraft.world.SkyblockChunkGenerator sky) {
                net.kasax.challengecraft.challenges.Chal_11_SkyblockWorld.setVanillaNetherGenerator(
                        new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                                sky.getBiomeSource(),
                                noiseSettings.getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.NETHER)));
                LOGGER.info("[Skyblock] Restored the vanilla nether generator");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to restore vanilla generators after Skyblock was switched off!", e);
        }
    }

    public static void randomizeSeed(MinecraftServer server) {
        if (!rotationPending) return;
        
        try {
            net.minecraft.world.level.storage.WorldData properties = ((net.kasax.challengecraft.mixin.MinecraftServerAccessor) server).getSaveProperties();
            if (properties == null) {
                LOGGER.warn("SaveProperties is null during randomization!");
                return;
            }
            long newSeed = new java.util.Random().nextLong();
            LOGGER.info("Randomizing seed in memory for fresh world. New seed: {}", newSeed);

            // Reset the clock instead of randomising every long on the level data.
            //
            // The blanket randomiser dates from when the seed WAS a long field here. It no longer
            // is (see the WorldGenSettings block below), so all it hit was PrimaryLevelData's one
            // remaining long: gameTime — which it set to the seed, roughly 1.6e18 ticks. Measured,
            // not guessed: the restart self-test printed `gameTime=1629089990846195763` on the
            // second boot.
            //
            // That is what made a fresh world open at night. ClientClockManager advances the day
            // clock by the DELTA between game-time updates, so a seed-sized jump throws the sky to
            // an arbitrary hour on the client — while the server's own clock, which is separate
            // saved data and archived with the rest of the world, still reads a correct morning.
            // Hence a bug visible in-game but invisible to a headless server test.
            //
            // A brand-new vanilla world starts at gameTime 0, so that is what a restart restores.
            resetSpawnFields(properties);
            clearNbtFields(properties);

            try {
                net.minecraft.world.level.storage.ServerLevelData mainWorldProps = properties.overworldData();
                if (mainWorldProps != null) {
                    mainWorldProps.setGameTime(0L);
                    LOGGER.info("[SeedReset] Reset gameTime to 0 for the fresh world");
                }
                if (mainWorldProps != null && mainWorldProps != properties) {
                    LOGGER.info("Cleaning internal MainWorldProperties...");
                    resetSpawnFields(mainWorldProps);
                    clearNbtFields(mainWorldProps);
                }
            } catch (Throwable t) {
                LOGGER.warn("[SeedReset] Could not reset gameTime: {}", t.toString());
            }

            // THE SEED. In 26.2 it no longer lives on the level data at all — the chain is
            //   MinecraftServer.getWorldGenSettings() -> WorldGenSettings.options() -> WorldOptions.seed
            // (confirmed by disassembling ServerLevel.getSeed). Searching SaveProperties, as this
            // code used to, found only PrimaryLevelData.gameTime and left the seed untouched: the
            // world really was regenerated, with the OLD seed, so a restart produced a
            // pixel-identical world and looked like it had done nothing at all.
            net.minecraft.world.level.levelgen.WorldGenSettings genSettings = server.getWorldGenSettings();
            Object options = genSettings == null ? null
                    : findFieldOfType(genSettings, net.minecraft.world.level.levelgen.WorldOptions.class);
            if (options != null) {
                randomizeAllLongFields(options, newSeed);
                LOGGER.info("[SeedReset] New world seed applied via WorldGenSettings: {}", newSeed);
            } else {
                LOGGER.error("[SeedReset] Could not reach WorldOptions through WorldGenSettings — "
                        + "the restarted world WILL reuse the old seed. This needs fixing, not ignoring.");
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

    /** First field on {@code obj} (or a superclass) assignable to {@code type}, or null. */
    private static Object findFieldOfType(Object obj, Class<?> type) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object value = f.get(obj);
                        if (value != null) return value;
                    } catch (Exception ignored) {
                        // Fall through and keep looking.
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
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
                
                boolean isNbt = net.minecraft.nbt.CompoundTag.class.isAssignableFrom(type);

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
                        f.set(obj, new net.minecraft.nbt.CompoundTag());
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

        // Move EVERYTHING except a short keep-list, rather than naming the folders to move.
        //
        // This used to be an allow-list of "region", "poi", "entities", "DIM1", "DIM-1",
        // "playerdata", ... — the pre-26.2 save layout. 26.2 moved the chunk data under
        // `dimensions/` and the player data under `players/`, so every one of those names simply
        // did not exist any more. The loop skipped them all WITHOUT WARNING (it only logged on
        // IOException, never on "not found"), archived `level.dat` and `data`, and left the actual
        // chunks in place. The restart then regenerated a world that already had its terrain on
        // disk: a brand-new seed, byte-identical spawn, and a log full of success messages.
        //
        // A deny-list survives the next layout change; an allow-list silently stops working.
        Set<String> keep = Set.of(
                "old worlds",     // the archive we are writing into
                "session.lock"    // held open by the running server; moving it breaks the session
        );

        int moved = 0, skipped = 0;
        try (Stream<Path> entries = Files.list(worldDir)) {
            for (Path src : entries.collect(Collectors.toList())) {
                String name = src.getFileName().toString();
                if (keep.contains(name)) {
                    skipped++;
                    continue;
                }
                try {
                    moveOrRecursive(src, archiveDir.resolve(name));
                    moved++;
                    LOGGER.info("[Rotation] Archived '{}'", name);
                } catch (IOException e) {
                    // Loud on purpose: anything left behind here is old world state that will bleed
                    // into the "fresh" world.
                    LOGGER.error("[Rotation] FAILED to archive '{}' — the new world will inherit it: {}",
                            name, e.getMessage());
                }
            }
        }
        LOGGER.info("[Rotation] Archived {} entr(ies), kept {}", moved, skipped);
        
        // The whole `data` folder has just been archived, so bring the one file back that must
        // survive: the chosen challenge set. (Previously this ran against the live folder and
        // depended on `data` NOT having been moved — the two halves contradicted each other.)
        Path savedChallenges = archiveDir.resolve("data").resolve("challengecraft_challenges.dat");
        if (Files.exists(savedChallenges)) {
            Path liveData = worldDir.resolve("data");
            Files.createDirectories(liveData);
            Files.copy(savedChallenges, liveData.resolve("challengecraft_challenges.dat"),
                    StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[Rotation] Restored challenge configuration into the fresh world");
        } else {
            LOGGER.warn("[Rotation] No challenge configuration found to carry over — "
                    + "the new world will fall back to whatever the client seeds");
        }
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
