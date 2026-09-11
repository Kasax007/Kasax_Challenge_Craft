package net.kasax.challengecraft.daily;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A development tool for judging a world seed before it goes into the daily rotation.
 *
 * <p>Not player-facing and not wired into anything by default — it only does something when the
 * environment variable {@code CHALLENGECRAFT_SEED_PROBE} is set, exactly like
 * {@code BlockSurvey}'s autostart. Set it, boot a server on the seed, and it appends one JSON
 * record to {@code challengecraft_seed_probe.json} in the game directory and halts the server, so a
 * shell loop can sweep many seeds unattended ({@code scripts/probe_seeds.sh}).
 *
 * <p>It exists because seed lists on the web are almost all written for older versions and quietly
 * reused. Worldgen changes between versions, so a seed that is famous for a mansion at spawn in one
 * version is ordinary terrain in the next. A daily seed ships to every player and cannot be checked
 * afterwards, so the claims in {@link DailyChallenges} come from this, measured on the version the
 * mod is actually built against.
 *
 * <p>Structure searches use the generator's placement check, not terrain generation, so a sweep
 * costs seconds per seed rather than minutes.
 */
public final class SeedProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-SeedProbe");
    /**
     * Presence of this file in the server directory arms the probe.
     *
     * <p>A marker file rather than an environment variable on purpose: Gradle's run tasks inherit
     * the environment of the long-lived Gradle <em>daemon</em>, not of the shell that invoked them,
     * so exporting a variable before {@code ./gradlew runServer} does not reliably reach the game.
     * A file is read at the moment it matters and cannot go stale.
     */
    private static final String MARKER = "challengecraft_seed_probe.on";

    /** Chunks, not blocks. 80 ≈ 1280 blocks — beyond that nothing is "near spawn" any more. */
    private static final int SEARCH_CHUNKS = 80;
    /** Half-width of the biome sample around spawn, in blocks. */
    private static final int SAMPLE_RADIUS = 768;
    private static final int SAMPLE_STEP = 48;

    private static final Map<String, ResourceKey<Structure>> WANTED = new LinkedHashMap<>();

    static {
        WANTED.put("village_plains", BuiltinStructures.VILLAGE_PLAINS);
        WANTED.put("village_desert", BuiltinStructures.VILLAGE_DESERT);
        WANTED.put("village_savanna", BuiltinStructures.VILLAGE_SAVANNA);
        WANTED.put("village_snowy", BuiltinStructures.VILLAGE_SNOWY);
        WANTED.put("village_taiga", BuiltinStructures.VILLAGE_TAIGA);
        WANTED.put("pillager_outpost", BuiltinStructures.PILLAGER_OUTPOST);
        WANTED.put("desert_pyramid", BuiltinStructures.DESERT_PYRAMID);
        WANTED.put("jungle_temple", BuiltinStructures.JUNGLE_TEMPLE);
        WANTED.put("swamp_hut", BuiltinStructures.SWAMP_HUT);
        WANTED.put("igloo", BuiltinStructures.IGLOO);
        WANTED.put("woodland_mansion", BuiltinStructures.WOODLAND_MANSION);
        WANTED.put("ocean_monument", BuiltinStructures.OCEAN_MONUMENT);
        WANTED.put("shipwreck", BuiltinStructures.SHIPWRECK);
        WANTED.put("shipwreck_beached", BuiltinStructures.SHIPWRECK_BEACHED);
        WANTED.put("ruined_portal", BuiltinStructures.RUINED_PORTAL_STANDARD);
        WANTED.put("ancient_city", BuiltinStructures.ANCIENT_CITY);
        WANTED.put("trial_chambers", BuiltinStructures.TRIAL_CHAMBERS);
        WANTED.put("trail_ruins", BuiltinStructures.TRAIL_RUINS);
        WANTED.put("stronghold", BuiltinStructures.STRONGHOLD);
    }

    private SeedProbe() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(SeedProbe::run);
    }

    private static void run(MinecraftServer server) {
        if (!Files.exists(server.getServerDirectory().resolve(MARKER))) {
            return;
        }
        ServerLevel level = server.overworld();
        long started = System.nanoTime();
        String json;
        try {
            json = probe(level);
        } catch (RuntimeException e) {
            LOGGER.error("Sonde fehlgeschlagen für Seed {}", level.getSeed(), e);
            json = "{\"seed\":" + level.getSeed() + ",\"error\":\"" + e + "\"}";
        }
        Path out = server.getServerDirectory().resolve("challengecraft_seed_probe.json");
        try {
            Files.writeString(out, json + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Sondenergebnis nicht schreibbar: {}", out, e);
        }
        LOGGER.info("Seed {} vermessen in {} ms → {}", level.getSeed(),
                (System.nanoTime() - started) / 1_000_000, out);
        LOGGER.info("PROBE {}", json);
        // The sweep script boots one server per seed, so this one is done.
        server.halt(false);
    }

    private static String probe(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().pos();
        var chunks = level.getChunkSource();
        var generator = chunks.getGenerator();
        var sampler = chunks.randomState().sampler();

        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"seed\":").append(level.getSeed());
        sb.append(",\"spawn\":[").append(spawn.getX()).append(',').append(spawn.getZ()).append(']');
        sb.append(",\"spawn_biome\":\"")
                .append(biomeAt(generator, sampler, spawn.getX(), spawn.getY(), spawn.getZ()))
                .append('"');

        // Terrain shape around spawn. Surface height alone separates "flat plains" from "you spawn
        // halfway up a mountain", and the water share separates a mainland spawn from an island.
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int water = 0;
        int samples = 0;
        var biomes = new TreeSet<String>();
        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz += SAMPLE_STEP) {
                int x = spawn.getX() + dx;
                int z = spawn.getZ() + dz;
                int h = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        level, chunks.randomState());
                min = Math.min(min, h);
                max = Math.max(max, h);
                if (h <= level.getSeaLevel()) {
                    water++;
                }
                samples++;
                biomes.add(biomeAt(generator, sampler, x, h, z));
            }
        }
        sb.append(",\"surface_min\":").append(min).append(",\"surface_max\":").append(max);
        sb.append(",\"water_share\":").append(Math.round(100.0 * water / samples));
        sb.append(",\"biome_count\":").append(biomes.size());
        sb.append(",\"biomes\":[");
        List<String> quoted = new ArrayList<>(biomes.size());
        biomes.forEach(b -> quoted.add('"' + b + '"'));
        sb.append(String.join(",", quoted)).append(']');

        sb.append(",\"structures\":{");
        List<String> found = new ArrayList<>();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (var entry : WANTED.entrySet()) {
            var holder = registry.get(entry.getValue()).orElse(null);
            if (holder == null) {
                continue;
            }
            var hit = generator.findNearestMapStructure(
                    level, HolderSet.direct(holder), spawn, SEARCH_CHUNKS, false);
            if (hit == null) {
                continue;
            }
            BlockPos at = hit.getFirst();
            long dx = at.getX() - spawn.getX();
            long dz = at.getZ() - spawn.getZ();
            long distance = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            found.add("\"" + entry.getKey() + "\":" + distance);
        }
        sb.append(String.join(",", found)).append("}}");
        return sb.toString();
    }

    private static String biomeAt(net.minecraft.world.level.chunk.ChunkGenerator generator,
                                  net.minecraft.world.level.biome.Climate.Sampler sampler,
                                  int x, int y, int z) {
        // Quart coordinates: the biome grid is one cell per 4 blocks. Going through the biome source
        // rather than level.getBiome keeps this from generating chunks, which is the whole trick
        // that makes a sweep affordable.
        Holder<Biome> biome = generator.getBiomeSource()
                .getNoiseBiome(x >> 2, y >> 2, z >> 2, sampler);
        return biome.unwrapKey().<String>map(k -> k.identifier().getPath()).orElse("?");
    }
}
