package net.kasax.challengecraft.client.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.kasax.challengecraft.ChallengeCraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class LevelJourneyLayoutStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("challengecraft_level_journey_layout.json");
    private static final Map<String, LayoutOffset> DEFAULT_OFFSETS = createDefaultOffsets();

    private LevelJourneyLayoutStore() {
    }

    static Map<String, LayoutOffset> load() {
        Map<String, LayoutOffset> offsets = defaultOffsets();
        if (!Files.exists(FILE)) {
            return offsets;
        }

        try {
            String raw = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null) {
                return offsets;
            }

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                float x = value.has("x") ? value.get("x").getAsFloat() : 0.0f;
                float y = value.has("y") ? value.get("y").getAsFloat() : 0.0f;
                offsets.put(entry.getKey(), new LayoutOffset(x, y));
            }
        } catch (Exception e) {
            ChallengeCraft.LOGGER.warn("Failed to load level journey layout config {}", FILE, e);
        }

        return offsets;
    }

    static Map<String, LayoutOffset> defaultOffsets() {
        return new HashMap<>(DEFAULT_OFFSETS);
    }

    static void save(Map<String, LayoutOffset> offsets) {
        JsonObject root = new JsonObject();
        offsets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("x", round(entry.getValue().x()));
                    value.addProperty("y", round(entry.getValue().y()));
                    root.add(entry.getKey(), value);
                });

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ChallengeCraft.LOGGER.warn("Failed to save level journey layout config {}", FILE, e);
        }
    }

    static void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException e) {
            ChallengeCraft.LOGGER.warn("Failed to clear level journey layout config {}", FILE, e);
        }
    }

    private static float round(float value) {
        return Math.round(value * 10.0f) / 10.0f;
    }

    private static Map<String, LayoutOffset> createDefaultOffsets() {
        Map<String, LayoutOffset> offsets = new HashMap<>();
        offsets.put("level_1", new LayoutOffset(-67.6f, -20.0f));
        offsets.put("level_2", new LayoutOffset(-105.7f, -7.3f));
        offsets.put("level_3", new LayoutOffset(-73.0f, -18.7f));
        offsets.put("level_4", new LayoutOffset(-95.3f, -23.9f));
        offsets.put("level_5", new LayoutOffset(-31.0f, -11.9f));
        offsets.put("level_6", new LayoutOffset(20.3f, -26.0f));
        offsets.put("level_7", new LayoutOffset(72.3f, -26.7f));
        offsets.put("level_8", new LayoutOffset(65.4f, -24.9f));
        offsets.put("level_9", new LayoutOffset(14.4f, -45.3f));
        offsets.put("level_10", new LayoutOffset(-12.6f, -44.7f));
        offsets.put("level_11", new LayoutOffset(-22.3f, -35.4f));
        offsets.put("level_12", new LayoutOffset(-10.0f, -27.0f));
        offsets.put("level_13", new LayoutOffset(3.0f, -21.4f));
        offsets.put("level_14", new LayoutOffset(41.3f, -10.6f));
        offsets.put("level_15", new LayoutOffset(66.7f, 6.4f));
        offsets.put("level_16", new LayoutOffset(66.7f, -190.5f));
        offsets.put("level_17", new LayoutOffset(-70.3f, -153.1f));
        offsets.put("level_18", new LayoutOffset(-131.9f, -113.8f));
        offsets.put("level_19", new LayoutOffset(-139.3f, -63.0f));
        offsets.put("level_20", new LayoutOffset(-217.7f, -16.4f));
        offsets.put("star_1", new LayoutOffset(-30.3f, -41.0f));
        offsets.put("star_3", new LayoutOffset(-47.7f, 18.3f));
        offsets.put("star_5", new LayoutOffset(-43.0f, 90.0f));
        offsets.put("star_8", new LayoutOffset(19.0f, 126.0f));
        offsets.put("star_10", new LayoutOffset(72.0f, 166.0f));
        offsets.put("star_15", new LayoutOffset(2.7f, 196.3f));
        offsets.put("star_20", new LayoutOffset(-99.7f, 213.3f));
        return offsets;
    }

    record LayoutOffset(float x, float y) {
    }
}
