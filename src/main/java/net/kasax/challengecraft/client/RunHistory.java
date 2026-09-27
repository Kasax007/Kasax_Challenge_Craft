package net.kasax.challengecraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.RunSummaryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The last few singleplayer runs, for the title screen's hub: "continue" and "recent runs".
 *
 * <p>One entry per world, newest first, kept in {@code challengecraft_runs.json} next to the XP and
 * stats files (per installation and player machine, like them). While a singleplayer world with
 * challenges is open the entry is refreshed every two seconds from the integrated server's own
 * saved data, so the time shown is the world's run clock, not a guess; leaving the world writes it
 * out as OPEN. A run summary marks it DONE with its final time and XP. Multiplayer is not tracked —
 * a server world cannot be "continued" from the title screen.
 */
@Environment(EnvType.CLIENT)
public final class RunHistory {
    public enum State { OPEN, DONE }

    public record Entry(String worldId, String worldName, List<Integer> challengeIds, int dailyIndex,
                        long ticks, State state, long xp, long when) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("challengecraft_runs.json");
    private static final int MAX = 12;

    private static List<Entry> entries;
    private static Entry current;
    private static int clock;

    private RunHistory() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++clock % 40 == 0) {
                snapshot(client);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (current != null) {
                put(current);
                current = null;
            }
        });
    }

    /** Newest first. */
    public static List<Entry> all() {
        ensureLoaded();
        return List.copyOf(entries);
    }

    /** The newest OPEN run whose world still exists, or null. */
    public static Entry continuable() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : all()) {
            if (e.state() == State.OPEN && mc.getLevelSource().levelExists(e.worldId())) {
                return e;
            }
        }
        return null;
    }

    /** Called when a run summary arrives: this world's run is finished. */
    public static void onRunFinished(RunSummaryPacket summary) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }
        Entry base = current != null ? current : describe(server);
        if (base == null) {
            return;
        }
        current = new Entry(base.worldId(), base.worldName(), summary.activeIds, summary.daily ? summary.dailyIndex : -1,
                summary.runTicks, State.DONE, summary.xpGained, System.currentTimeMillis());
        put(current);
    }

    private static void snapshot(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.level == null) {
            return;
        }
        Entry now = describe(server);
        if (now == null) {
            return;
        }
        // A finished run stays finished even though the world is still open.
        if (current != null && current.state() == State.DONE && current.worldId().equals(now.worldId())) {
            return;
        }
        current = now;
    }

    private static Entry describe(IntegratedServer server) {
        try {
            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            List<Integer> ids = data.getActive();
            if (ids.isEmpty() && !data.isDailyRun()) {
                return null;   // plain survival is not a challenge run
            }
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return new Entry(root.getFileName().toString(), server.getWorldData().getLevelName(), List.copyOf(ids),
                    data.getDailyIndex(), data.getRunTicks(), State.OPEN, 0, System.currentTimeMillis());
        } catch (RuntimeException e) {
            return null;       // world still loading or already closing
        }
    }

    private static void put(Entry e) {
        ensureLoaded();
        entries.removeIf(x -> x.worldId().equals(e.worldId()));
        entries.add(0, e);
        while (entries.size() > MAX) {
            entries.remove(entries.size() - 1);
        }
        save();
    }

    private static void ensureLoaded() {
        if (entries != null) {
            return;
        }
        entries = new ArrayList<>();
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            for (var el : root.getAsJsonArray("runs")) {
                JsonObject o = el.getAsJsonObject();
                List<Integer> ids = new ArrayList<>();
                for (var id : o.getAsJsonArray("challenges")) {
                    ids.add(id.getAsInt());
                }
                entries.add(new Entry(o.get("world").getAsString(), o.get("name").getAsString(), ids,
                        o.has("daily") ? o.get("daily").getAsInt() : -1, o.get("ticks").getAsLong(),
                        State.valueOf(o.get("state").getAsString()), o.has("xp") ? o.get("xp").getAsLong() : 0,
                        o.has("when") ? o.get("when").getAsLong() : 0));
            }
        } catch (Exception e) {
            ChallengeCraft.LOGGER.warn("Run-Verlauf nicht lesbar ({}), starte leer", FILE, e);
            entries = new ArrayList<>();
        }
    }

    private static void save() {
        JsonArray runs = new JsonArray();
        for (Entry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("world", e.worldId());
            o.addProperty("name", e.worldName());
            JsonArray ids = new JsonArray();
            e.challengeIds().forEach(ids::add);
            o.add("challenges", ids);
            o.addProperty("daily", e.dailyIndex());
            o.addProperty("ticks", e.ticks());
            o.addProperty("state", e.state().name());
            o.addProperty("xp", e.xp());
            o.addProperty("when", e.when());
            runs.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("runs", runs);
        try {
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ChallengeCraft.LOGGER.warn("Run-Verlauf nicht schreibbar: {}", FILE, e);
        }
    }

    /** "Würfel + Hardcore", a daily's name, or the world name when nothing else fits. */
    public static String label(Entry e) {
        if (e.dailyIndex() >= 0) {
            return net.minecraft.network.chat.Component.translatable("challengecraft.hub.daily_run").getString();
        }
        if (e.challengeIds().isEmpty()) {
            return e.worldName();
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(2, e.challengeIds().size()); i++) {
            names.add(ChallengeCodeClient.name(e.challengeIds().get(i)).getString());
        }
        String joined = String.join(" + ", names);
        return e.challengeIds().size() > 2 ? joined + " +" + (e.challengeIds().size() - 2) : joined;
    }
}
