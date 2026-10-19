package net.kasax.challengecraft.bot.learn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kasax.challengecraft.bot.BotManager;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The learnt models Bob plays with, read from {@code bob-models/} in the server's folder (or where
 * {@code BOB_MODELS} points): {@code cover.json} (which cover, when a fight goes badly),
 * {@code goal.json} (how long a tile really takes), {@code champion.json} (the knobs that won the
 * self-play league). The trainer puts a new one there once it has beaten the one before; the store
 * looks at the files' times every ten seconds and takes the new one without a restart. A file that
 * does not parse is ignored (and said once): Bob plays on with the rules.
 */
public final class ModelStore {
    private static final long CHECK_MS = 10_000;

    private record Entry(Object model, long modified, long checkedAt) {
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();

    private ModelStore() {
    }

    /** The folder the models are read from. */
    public static Path directory() {
        String v = System.getProperty("bob.models", System.getenv("BOB_MODELS"));
        return Path.of(v == null || v.isBlank() ? "bob-models" : v).toAbsolutePath();
    }

    /** Whether learnt models are used at all ({@code BOB_LEARNED=0}: the rules only, whatever is there). */
    public static boolean enabled() {
        String v = System.getProperty("bob.learned", System.getenv("BOB_LEARNED"));
        return v == null || !(v.equals("0") || v.equalsIgnoreCase("off"));
    }

    /** The cover model (null: none, or switched off). */
    public static CoverModel cover() {
        return enabled() ? load("cover.json", CoverModel::parse) : null;
    }

    /** A cover model by file name (the arena's evaluation compares a candidate with the rules). */
    public static CoverModel cover(String file) {
        return load(file, CoverModel::parse);
    }

    /** The goal-time model (null: none, or switched off). */
    public static GoalModel goal() {
        return enabled() ? load("goal.json", GoalModel::parse) : null;
    }

    /** The knobs that won the league ({@code params}: "k=v,..."), or null. */
    public static String championParams() {
        if (!enabled()) return null;
        JsonObject o = load("champion.json", j -> j);
        return o != null && o.has("params") ? o.get("params").getAsString() : null;
    }

    /** What each model is ("cover v12, goal v3, ..."), for the status. */
    public static synchronized String describe() {
        StringBuilder b = new StringBuilder();
        for (var e : CACHE.entrySet()) {
            if (e.getValue().model() == null) continue;
            if (!b.isEmpty()) b.append(", ");
            b.append(e.getKey());
            if (e.getValue().model() instanceof CoverModel c) b.append(' ').append(c.version);
            if (e.getValue().model() instanceof GoalModel g) b.append(' ').append(g.version);
        }
        return b.isEmpty() ? "none" : b.toString();
    }

    @SuppressWarnings("unchecked")
    private static synchronized <T> T load(String name, Function<JsonObject, T> parse) {
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(name);
        if (e != null && now - e.checkedAt() < CHECK_MS) return (T) e.model();
        Path f = directory().resolve(name);
        long modified;
        try {
            modified = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : -1;
        } catch (Exception ex) {
            modified = -1;
        }
        if (e != null && e.modified() == modified) {
            CACHE.put(name, new Entry(e.model(), modified, now));
            return (T) e.model();
        }
        Object model = null;
        if (modified < 0) {
            // (No file here: the models shipped inside the mod, src/main/resources/bob-models.)
            try (var in = ModelStore.class.getResourceAsStream("/bob-models/" + name)) {
                if (in != null) model = parse.apply(JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception ex) {
                BotManager.LOG.warn("[Models] bundled {} not usable: {}", name, ex.toString());
            }
        }
        if (modified >= 0) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                model = parse.apply(JsonParser.parseReader(r).getAsJsonObject());
                BotManager.LOG.info("[Models] {} loaded from {}", name, f);
            } catch (Exception ex) {
                BotManager.LOG.warn("[Models] {} not usable, the rules instead: {}", f, ex.toString());
            }
        }
        CACHE.put(name, new Entry(model, modified, now));
        return (T) model;
    }
}
