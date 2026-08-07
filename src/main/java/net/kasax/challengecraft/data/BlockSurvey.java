package net.kasax.challengecraft.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optional data-collection tool: while enabled, it surveys the chunks around online players as
 * they explore and records how often every block type occurs — a real dataset of blocks that
 * actually generate in this world (including structures), with frequencies.
 *
 * <p><b>Efficiency:</b> chunks are counted through {@code ChunkSection.getBlockStateContainer()
 * .count(...)} — the palette counter vanilla itself uses — so a chunk costs a handful of palette
 * walks instead of ~98k {@code getBlockState} calls. On top of that, per tick the scanner obeys a
 * hard time budget (~2 ms) and a chunk cap, and the player-neighborhood sweep (radius = server
 * view distance) runs only once a second. With no players online, the overworld spawn chunk
 * anchors the sweep instead (also makes the tool verifiable from a dedicated-server console).
 *
 * <p><b>Storage:</b> {@code src/main/generated/data/challengecraft/block_survey.json} in the dev
 * repo (counts, sorted descending) — resolved relative to the run dir; falls back to the game dir
 * outside a dev checkout. Note that {@code runDatagen} may clean unrecognized files under
 * {@code generated/}, so copy the dataset elsewhere before regenerating datagen output.
 *
 * <p>Self-contained and removable: Chal_44 prefers this dataset but falls back without it. For
 * automated testing, setting the env var {@code CHALLENGECRAFT_SURVEY_AUTOSTART=1} starts the
 * survey on the first server tick.
 */
public class BlockSurvey {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-BlockSurvey");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final int MAX_CHUNKS_PER_TICK = 4;
    private static final long TICK_TIME_BUDGET_NANOS = 2_000_000L; // 2 ms
    private static final int SWEEP_INTERVAL_TICKS = 20;            // re-scan neighborhood 1x/s
    private static final long AUTOSAVE_INTERVAL_TICKS = 200;       // ~10 s

    private static final Map<String, Long> blockCounts = new HashMap<>();
    private static final Set<String> scannedChunks = new HashSet<>(); // marked AFTER a successful scan
    private static final Set<String> queuedKeys = new HashSet<>();    // prevents duplicate enqueues
    private static final Deque<PendingChunk> queue = new ArrayDeque<>();
    private static boolean active = false;
    private static boolean loaded = false;
    private static boolean dirty = false;
    private static boolean autostartDone = false;
    private static long tickCounter = 0;

    // ---- Self-generating mode (spirals outward from spawn, force-generating chunks) -----------
    private static final int GEN_MAX_CHUNKS_PER_TICK = 2;
    private static final long GEN_TIME_BUDGET_NANOS = 10_000_000L; // 10 ms (worldgen is heavy)
    private static final int GEN_LOG_INTERVAL = 64;
    private static boolean generating = false;
    private static ServerWorld genWorld = null;
    private static ChunkPos genCenter = null;
    private static int genRadius = 0;
    private static int genRing = 0;
    private static int genRingIndex = 0;
    private static List<int[]> genRingCoords = null;
    private static int genGenerated = 0;

    private record PendingChunk(ServerWorld world, int cx, int cz, String key) {
    }

    private static final String AUTOSTART_MODE = System.getenv("CHALLENGECRAFT_SURVEY_AUTOSTART");

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!autostartDone && AUTOSTART_MODE != null && !AUTOSTART_MODE.isBlank()) {
                autostartDone = true;
                if (AUTOSTART_MODE.startsWith("generate")) {
                    int r = AUTOSTART_MODE.contains(":") ? parseIntOr(AUTOSTART_MODE.split(":")[1], 6) : 6;
                    startGenerate(server, r);
                    LOGGER.info("[Survey] autostarted generation (radius {}) via env", r);
                } else {
                    start();
                    LOGGER.info("[Survey] autostarted passive scan via env");
                }
            }

            if (generating) {
                generationTick();
                if (dirty && tickCounter % AUTOSAVE_INTERVAL_TICKS == 0) save();
                return;
            }

            if (!active) return;
            ensureLoaded();
            tickCounter++;

            if (tickCounter % SWEEP_INTERVAL_TICKS == 0) {
                sweep(server);
            }

            long deadline = System.nanoTime() + TICK_TIME_BUDGET_NANOS;
            int processed = 0;
            while (!queue.isEmpty() && processed < MAX_CHUNKS_PER_TICK && System.nanoTime() < deadline) {
                scanChunk(queue.poll());
                processed++;
            }

            if (dirty && tickCounter % AUTOSAVE_INTERVAL_TICKS == 0) {
                save();
            }
        });
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- Generation --------------------------------------------------------------------------

    /** Begins force-generating chunks outward from the overworld spawn up to {@code radius}. */
    public static void startGenerate(MinecraftServer server, int radius) {
        ensureLoaded();
        genWorld = server.getOverworld();
        genCenter = new ChunkPos(genWorld.getSpawnPos());
        genRadius = Math.max(1, radius);
        genRing = 0;
        genRingIndex = 0;
        genRingCoords = null;
        genGenerated = 0;
        generating = true;
        LOGGER.info("[Survey] generation started: radius {} (~{} chunks) around {}",
                genRadius, (2 * genRadius + 1) * (2 * genRadius + 1), genCenter);
    }

    private static void generationTick() {
        tickCounter++;
        long deadline = System.nanoTime() + GEN_TIME_BUDGET_NANOS;
        int done = 0;
        while (generating && done < GEN_MAX_CHUNKS_PER_TICK && System.nanoTime() < deadline) {
            int[] offset = nextSpiralOffset();
            if (offset == null) {
                finishGeneration();
                return;
            }
            int cx = genCenter.x + offset[0];
            int cz = genCenter.z + offset[1];
            String key = genWorld.getRegistryKey().getValue() + ":" + cx + ":" + cz;
            if (scannedChunks.contains(key)) continue; // already counted (e.g. spawn) — don't regen
            // FULL + create=true forces synchronous worldgen (features + structures). No
            // persistent ticket, so the chunk unloads naturally afterwards.
            Chunk chunk = genWorld.getChunk(cx, cz, ChunkStatus.FULL, true);
            countChunkSections(chunk);
            scannedChunks.add(key);
            dirty = true;
            genGenerated++;
            done++;
            if (genGenerated % GEN_LOG_INTERVAL == 0) {
                LOGGER.info("[Survey] generated {} chunks (ring {}/{}), {} block types so far",
                        genGenerated, genRing, genRadius, blockCounts.size());
            }
        }
    }

    private static void finishGeneration() {
        generating = false;
        LOGGER.info("[Survey] generation complete: {} chunks generated, {} block types, {} blocks total",
                genGenerated, blockCounts.size(), getTotalBlocksCounted());
        save();
    }

    /** Next Chebyshev-ring offset outward from center, or {@code null} once radius is exhausted. */
    private static int[] nextSpiralOffset() {
        while (true) {
            if (genRing > genRadius) return null;
            if (genRingCoords == null) genRingCoords = ringCoords(genRing);
            if (genRingIndex >= genRingCoords.size()) {
                genRing++;
                genRingIndex = 0;
                genRingCoords = null;
                continue;
            }
            return genRingCoords.get(genRingIndex++);
        }
    }

    /** The perimeter offsets of the Chebyshev ring at distance {@code r} (r=0 is just the center). */
    private static List<int[]> ringCoords(int r) {
        List<int[]> coords = new ArrayList<>();
        if (r == 0) {
            coords.add(new int[]{0, 0});
            return coords;
        }
        for (int x = -r; x <= r; x++) {
            coords.add(new int[]{x, -r});
            coords.add(new int[]{x, r});
        }
        for (int z = -r + 1; z <= r - 1; z++) {
            coords.add(new int[]{-r, z});
            coords.add(new int[]{r, z});
        }
        return coords;
    }

    /** Enqueue unscanned loaded chunks around every player (or spawn, when the server is empty). */
    private static void sweep(MinecraftServer server) {
        int radius = Math.max(2, server.getPlayerManager().getViewDistance());
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        if (players.isEmpty()) {
            ServerWorld overworld = server.getOverworld();
            enqueueAround(overworld, new ChunkPos(overworld.getSpawnPos()), radius);
            return;
        }
        for (ServerPlayerEntity player : players) {
            if (player.isSpectator()) continue;
            enqueueAround((ServerWorld) player.getWorld(), player.getChunkPos(), radius);
        }
    }

    private static void enqueueAround(ServerWorld world, ChunkPos center, int radius) {
        String dimId = world.getRegistryKey().getValue().toString();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                String key = dimId + ":" + cx + ":" + cz;
                if (scannedChunks.contains(key) || queuedKeys.contains(key)) continue;
                if (!world.isChunkLoaded(cx, cz)) continue;
                queuedKeys.add(key);
                queue.add(new PendingChunk(world, cx, cz, key));
            }
        }
    }

    private static void scanChunk(PendingChunk pending) {
        queuedKeys.remove(pending.key());
        ServerWorld world = pending.world();
        if (!world.isChunkLoaded(pending.cx(), pending.cz())) {
            return; // unloaded since enqueue — NOT marked scanned, a later sweep retries it
        }
        WorldChunk chunk = world.getChunk(pending.cx(), pending.cz());
        countChunkSections(chunk);
        scannedChunks.add(pending.key());
        dirty = true;
    }

    /**
     * Palette-based counting: per non-empty section, each palette entry is attributed its
     * occurrence count in one pass — no per-block state lookups at all. Shared by the passive
     * scanner and the generation mode.
     */
    private static void countChunkSections(Chunk chunk) {
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            section.getBlockStateContainer().count((state, count) -> {
                if (state.isAir()) return;
                String id = Registries.BLOCK.getId(state.getBlock()).toString();
                blockCounts.merge(id, (long) count, Long::sum);
            });
        }
    }

    public static void start() {
        ensureLoaded();
        active = true;
    }

    public static void stop() {
        active = false;
        generating = false;
        queue.clear();
        queuedKeys.clear();
        save();
    }

    public static boolean isActive() {
        return active || generating;
    }

    public static boolean isGenerating() {
        return generating;
    }

    public static int getCollectedCount() {
        ensureLoaded();
        return blockCounts.size();
    }

    public static long getTotalBlocksCounted() {
        ensureLoaded();
        return blockCounts.values().stream().mapToLong(Long::longValue).sum();
    }

    public static int getScannedChunkCount() {
        return scannedChunks.size();
    }

    /** Alphabetically sorted block ids — the dataset Chal_44 builds its target order from. */
    public static List<String> getCollectedIds() {
        ensureLoaded();
        List<String> ids = new ArrayList<>(blockCounts.keySet());
        Collections.sort(ids);
        return ids;
    }

    /** Classpath copy shipped inside the built jar, used when no writable dataset exists yet. */
    private static final String BUNDLED_RESOURCE = "/data/challengecraft/block_survey.json";

    /**
     * Dataset location: the dev repo's <b>resources</b> folder when present, else the game dir.
     *
     * <p>Deliberately NOT under {@code src/main/generated/} — datagen prunes every file there it
     * did not itself write ({@code runDatagen} logs "removed stale: 1" and deleted this dataset
     * once). {@code src/main/resources/} is untouched by datagen, and has the bonus that the
     * dataset ships inside the built jar, so players get a good Chal_44 block pool without having
     * to run a survey themselves.
     */
    private static Path surveyFile() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path resources = gameDir.resolve("..").resolve("src").resolve("main").resolve("resources").normalize();
        if (Files.isDirectory(resources)) {
            return resources.resolve("data").resolve("challengecraft").resolve("block_survey.json");
        }
        return gameDir.resolve("challengecraft_block_survey.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = surveyFile();
        if (Files.exists(file)) {
            try {
                if (mergeJson(Files.readString(file))) {
                    LOGGER.info("[Survey] loaded {} block types from {}", blockCounts.size(), file);
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("[Survey] failed to load {}", file, e);
            }
        }
        // No writable dataset yet — fall back to the copy bundled in the jar so a shipped build
        // still has a usable block pool.
        try (java.io.InputStream in = BlockSurvey.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) return;
            if (mergeJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))) {
                LOGGER.info("[Survey] loaded {} block types from the bundled dataset", blockCounts.size());
            }
        } catch (Exception e) {
            LOGGER.error("[Survey] failed to load bundled dataset", e);
        }
    }

    private static boolean mergeJson(String json) {
        Map<String, Long> counts = GSON.fromJson(json, new TypeToken<Map<String, Long>>() {}.getType());
        if (counts == null || counts.isEmpty()) return false;
        blockCounts.putAll(counts);
        return true;
    }

    public static synchronized void save() {
        Path file = surveyFile();
        try {
            Files.createDirectories(file.getParent());
            // Sorted by count descending — the interesting way to read the dataset.
            Map<String, Long> sorted = new LinkedHashMap<>();
            blockCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> sorted.put(e.getKey(), e.getValue()));
            Files.writeString(file, GSON.toJson(sorted));
            dirty = false;
            LOGGER.info("[Survey] saved {} block types ({} blocks, {} chunks) to {}",
                    blockCounts.size(), getTotalBlocksCounted(), scannedChunks.size(), file);
        } catch (Exception e) {
            LOGGER.error("[Survey] failed to save {}", file, e);
        }
    }
}
