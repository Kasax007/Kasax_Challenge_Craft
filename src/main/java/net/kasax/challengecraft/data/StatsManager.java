package net.kasax.challengecraft.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class StatsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Stats");
    private static final Path STATS_FILE = FabricLoader.getInstance().getGameDir().resolve("challengecraft_stats.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Map<Integer, Integer>> bestTimes = new HashMap<>(); // uuid -> (challengeId -> bestTime in ticks)
    private static boolean loaded = false;

    public static synchronized Map<Integer, Integer> getBestTimes(String uuid) {
        if (!loaded) {
            load();
        }
        return new HashMap<>(bestTimes.getOrDefault(uuid, new HashMap<>()));
    }

    public static synchronized void recordCompletion(String uuid, int challengeId, int ticks) {
        if (!loaded) {
            load();
        }
        Map<Integer, Integer> playerTimes = bestTimes.computeIfAbsent(uuid, k -> new HashMap<>());
        int currentBest = playerTimes.getOrDefault(challengeId, Integer.MAX_VALUE);
        if (ticks < currentBest) {
            playerTimes.put(challengeId, ticks);
            LOGGER.info("New best time for player {} challenge {}: {} ticks", uuid, challengeId, ticks);
            save();
        }
    }

    public static synchronized void updateStatsFromServer(String uuid, Map<Integer, Integer> serverTimes) {
        if (!loaded) {
            load();
        }
        Map<Integer, Integer> playerTimes = bestTimes.computeIfAbsent(uuid, k -> new HashMap<>());
        boolean changed = false;
        for (var entry : serverTimes.entrySet()) {
            int cid = entry.getKey();
            int ticks = entry.getValue();
            int currentBest = playerTimes.getOrDefault(cid, Integer.MAX_VALUE);
            if (ticks < currentBest) {
                playerTimes.put(cid, ticks);
                changed = true;
            }
        }
        if (changed) {
            LOGGER.info("Updated stats for player {} from server", uuid);
            save();
        }
    }

    private static void load() {
        if (Files.exists(STATS_FILE)) {
            try {
                String content = Files.readString(STATS_FILE);
                try {
                    bestTimes = GSON.fromJson(content, new TypeToken<Map<String, Map<Integer, Integer>>>() {}.getType());
                    if (bestTimes == null) bestTimes = new HashMap<>();
                } catch (Exception e) {
                    LOGGER.warn("Old stats format or corrupt JSON, attempting conversion or reset: {}", e.getMessage());
                    // Try to see if it's the old Map<Integer, Integer> format
                    try {
                        Map<Integer, Integer> oldMap = GSON.fromJson(content, new TypeToken<Map<Integer, Integer>>() {}.getType());
                        bestTimes = new HashMap<>();
                        if (oldMap != null && !oldMap.isEmpty()) {
                            // We don't know the UUID, so we can't really migrate it properly.
                            // But we'll at least not crash.
                            LOGGER.info("Discarded old format stats (UUID mapping unknown)");
                        }
                    } catch (Exception ex) {
                        bestTimes = new HashMap<>();
                    }
                }
                LOGGER.info("Loaded best times for {} players", bestTimes.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load stats file", e);
                bestTimes = new HashMap<>();
            }
        } else {
            bestTimes = new HashMap<>();
            save();
        }
        loaded = true;
    }

    private static void save() {
        try {
            Files.writeString(STATS_FILE, GSON.toJson(bestTimes));
            LOGGER.info("Saved stats");
        } catch (IOException e) {
            LOGGER.error("Failed to save stats file", e);
        }
    }
}
