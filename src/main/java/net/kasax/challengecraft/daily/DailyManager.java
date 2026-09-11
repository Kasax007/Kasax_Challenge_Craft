package net.kasax.challengecraft.daily;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.kasax.challengecraft.data.StatsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which daily is today's, and who has already played it.
 *
 * <p>State lives in a flat file next to {@code challengecraft_xp.txt} and
 * {@code challengecraft_stats.json}, not in {@code ChallengeSavedData}. Two reasons, and both matter:
 * the saved data's codec group is at its hard 16-field maximum, and — more importantly — "have I
 * played today's daily" is a fact about the player, not about a world. A daily creates a fresh world
 * every time; world-scoped state would be forgotten the moment it mattered.
 *
 * <p>The day is computed in <b>UTC</b> so every player worldwide is on the same daily at the same
 * moment. A local-timezone day would mean New Zealand and California disagree about which run they
 * are comparing times on, which quietly destroys the point of a shared personal best.
 */
public final class DailyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Daily");
    private static final Path FILE = FabricLoader.getInstance().getGameDir()
            .resolve("challengecraft_daily.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Personal bests reuse {@link StatsManager}, which is keyed by challenge id. Dailies get
     * synthetic ids well above every real challenge (1-46) and perk (101-109), so the two can never
     * collide and no storage format has to change.
     */
    private static final int SYNTHETIC_ID_BASE = 900_000;

    /** uuid -> the UTC epoch day on which that player last started a daily. */
    private static Map<String, Long> lastPlayed = new HashMap<>();
    private static boolean loaded = false;

    private DailyManager() {
    }

    public static long epochDayUtc() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** Index into {@link DailyChallenges}; wraps automatically once the rotation runs out. */
    public static int todayIndex() {
        return Math.floorMod((int) epochDayUtc(), DailyChallenges.size());
    }

    public static DailyEntry today() {
        return DailyChallenges.get(todayIndex());
    }

    /** The {@link StatsManager} key for a given rotation index. */
    public static int syntheticChallengeId(int dayIndex) {
        return SYNTHETIC_ID_BASE + Math.floorMod(dayIndex, DailyChallenges.size());
    }

    public static synchronized boolean hasPlayedToday(UUID uuid) {
        load();
        Long day = lastPlayed.get(uuid.toString());
        return day != null && day == epochDayUtc();
    }

    public static synchronized void markPlayedToday(UUID uuid) {
        load();
        lastPlayed.put(uuid.toString(), epochDayUtc());
        save();
    }

    /** Best time in ticks for the given rotation index, or -1 if never finished. */
    public static int bestTicks(UUID uuid, int dayIndex) {
        Integer best = StatsManager.getBestTimes(uuid.toString()).get(syntheticChallengeId(dayIndex));
        return best == null ? -1 : best;
    }

    /**
     * Records a finished daily. Returns whether this run is a new personal best — which the caller
     * must ask <b>before</b> the value is written, so the comparison is made against the old record
     * rather than against itself.
     */
    public static boolean recordCompletion(UUID uuid, int dayIndex, int ticks) {
        int previous = bestTicks(uuid, dayIndex);
        boolean improved = previous < 0 || ticks < previous;
        StatsManager.recordCompletion(uuid.toString(), syntheticChallengeId(dayIndex), ticks);
        return improved;
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            String json = Files.readString(FILE);
            Map<String, Long> read = GSON.fromJson(json, new TypeToken<Map<String, Long>>() {
            }.getType());
            if (read != null) {
                lastPlayed = read;
            }
        } catch (IOException | RuntimeException e) {
            // A corrupt file must not stop the mod: the worst case of starting empty is that a
            // player gets one extra daily attempt, which is far better than a boot failure.
            LOGGER.warn("Daily-Datei nicht lesbar, starte leer: {}", e.toString());
            lastPlayed = new HashMap<>();
        }
    }

    private static synchronized void save() {
        try {
            Files.writeString(FILE, GSON.toJson(lastPlayed));
        } catch (IOException e) {
            LOGGER.error("Daily-Datei konnte nicht geschrieben werden", e);
        }
    }
}
