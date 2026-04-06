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

    private LevelJourneyLayoutStore() {
    }

    static Map<String, LayoutOffset> load() {
        Map<String, LayoutOffset> offsets = new HashMap<>();
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

    record LayoutOffset(float x, float y) {
    }
}
