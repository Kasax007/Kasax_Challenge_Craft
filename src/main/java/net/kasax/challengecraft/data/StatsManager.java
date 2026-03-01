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
    private static Map<Integer, Integer> bestTimes = new HashMap<>(); // challengeId -> bestTime in ticks
    private static boolean loaded = false;

    public static synchronized Map<Integer, Integer> getBestTimes() {
        if (!loaded) {
            load();
        }
        return new HashMap<>(bestTimes);
    }

    public static synchronized void recordCompletion(int challengeId, int ticks) {
        if (!loaded) {
            load();
        }
        int currentBest = bestTimes.getOrDefault(challengeId, Integer.MAX_VALUE);
        if (ticks < currentBest) {
            bestTimes.put(challengeId, ticks);
            LOGGER.info("New best time for challenge {}: {} ticks", challengeId, ticks);
            save();
        }
    }

    private static void load() {
        if (Files.exists(STATS_FILE)) {
            try {
                String content = Files.readString(STATS_FILE);
                bestTimes = GSON.fromJson(content, new TypeToken<Map<Integer, Integer>>() {}.getType());
                if (bestTimes == null) bestTimes = new HashMap<>();
                LOGGER.info("Loaded {} best times", bestTimes.size());
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
