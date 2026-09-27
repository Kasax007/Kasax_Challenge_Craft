package net.kasax.challengecraft.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.kasax.challengecraft.ChallengeCraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Named challenge setups the player keeps for reuse, stored as challenge codes in
 * {@code config/challengecraft_presets.json}.
 *
 * <p>A preset IS a code with a name: storing the code string rather than its fields means the format
 * has exactly one definition ({@code ChallengeCode}), every preset can be shared as-is, and a preset
 * file from an older version stays readable for as long as its codes do. Per installation, like the
 * timer settings; never world state, never networked.
 */
@Environment(EnvType.CLIENT)
public final class ChallengePresets {
    public record Preset(String name, String code) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("challengecraft_presets.json");
    private static final int MAX = 40;

    private static List<Preset> presets;

    private ChallengePresets() {
    }

    public static List<Preset> all() {
        if (presets == null) {
            presets = load();
        }
        return List.copyOf(presets);
    }

    /** Saves under a name, replacing a preset of the same name (case-insensitive); newest first. */
    public static void save(String name, String code) {
        all();
        String trimmed = name.trim();
        presets.removeIf(p -> p.name().equalsIgnoreCase(trimmed));
        presets.add(0, new Preset(trimmed, code));
        while (presets.size() > MAX) {
            presets.remove(presets.size() - 1);
        }
        write();
    }

    public static void delete(String name) {
        all();
        presets.removeIf(p -> p.name().equals(name));
        write();
    }

    private static List<Preset> load() {
        List<Preset> out = new ArrayList<>();
        if (!Files.exists(FILE)) {
            return out;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            if (root != null && root.has("presets")) {
                for (var e : root.getAsJsonArray("presets")) {
                    JsonObject o = e.getAsJsonObject();
                    if (o.has("name") && o.has("code")) {
                        out.add(new Preset(o.get("name").getAsString(), o.get("code").getAsString()));
                    }
                }
            }
        } catch (Exception e) {
            // A broken file must not take the presets screen down; the worst case is an empty list.
            ChallengeCraft.LOGGER.warn("Presets nicht lesbar ({}), starte leer", FILE, e);
        }
        return out;
    }

    private static void write() {
        JsonArray array = new JsonArray();
        for (Preset p : presets) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name());
            o.addProperty("code", p.code());
            array.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("presets", array);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ChallengeCraft.LOGGER.warn("Presets nicht schreibbar: {}", FILE, e);
        }
    }
}
