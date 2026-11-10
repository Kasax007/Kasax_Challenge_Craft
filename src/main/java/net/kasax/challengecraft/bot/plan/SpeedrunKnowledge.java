package net.kasax.challengecraft.bot.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kasax.challengecraft.bot.BotManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a top Lockout player knows beyond the recipes: for every goal the fastest ways and an
 * expert's time for it, for every mob where it turns up and how long finding and killing one takes,
 * for every looted structure which chests it has and how long the looting takes. Written down in
 * {@code bob-knowledge/speedrun.json} (see docs/bob-reference-times.md for what the numbers mean).
 *
 * <p>The loot chances themselves are not written down: they are rolled from the running game's own
 * chest loot tables, as {@link BotKnowledge} rolls the block drops - so they are always this
 * version's (an abandoned camp's secret chest gives iron three times in four, a shipwreck's
 * treasure chest nearly always).
 */
public final class SpeedrunKnowledge {
    /** One way to a goal, as a player would name it ("loot", "craft", "hunt", ...), for the log. */
    public record Way(String kind, String text) {
    }

    /**
     * A goal: an expert's seconds for it (from deciding to go for it, with the usual early kit; see
     * the doc), its ways (fastest first), what it needs, and when it works.
     */
    public record Goal(String id, double expert, List<Way> ways, String needs, String when) {
        /** The first (fastest) way in words, or "". */
        public String best() {
            return ways.isEmpty() ? "" : ways.get(0).kind() + ": " + ways.get(0).text();
        }
    }

    /**
     * A mob: how it spawns (empty: as its category does - monsters in the dark, animals on grass;
     * {@code structure}: only at its structures; {@code special}: by rules of its own, slimes,
     * phantoms), how many come together, Bob's seconds to kill one once found (the way there under
     * fire included), whether it shoots, the structures where it lives with the seconds to find
     * one there, whether it is only about there at night (witches at a swamp hut), and how much
     * further off than most it is seen ({@code sight}: an enderman stands out across the plain).
     */
    public record Mob(String id, String spawn, int pack, double fight, Map<String, Double> structures,
                      boolean ranged, boolean night, double sight, String note) {
    }

    /** A structure family (shipwreck, village, ...): its chest tables with how many of each, the seconds to loot it. */
    public record Structure(String family, Map<String, Double> chests, double lootSeconds, boolean dangerous, String note) {
    }

    /** One structure family's loot for an item: the chance a visit brings at least one, the mean count, the looting time. */
    public record Loot(String family, double chance, double mean, double lootSeconds, boolean dangerous) {
    }

    private static volatile SpeedrunKnowledge instance;

    private final Map<String, Goal> goals = new LinkedHashMap<>();
    private final Map<String, Mob> mobs = new HashMap<>();
    private final Map<String, Structure> structures = new LinkedHashMap<>();
    private final int version;
    /** Rolled per server (the loot tables are the server's): item -> its structure sources. */
    private Map<Item, List<Loot>> loot;
    private Object lootFor;

    private SpeedrunKnowledge(JsonObject root) {
        version = root.has("version") ? root.get("version").getAsInt() : 0;
        if (root.has("structures")) {
            for (var e : root.getAsJsonObject("structures").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Map<String, Double> chests = new LinkedHashMap<>();
                if (o.has("chests")) for (var c : o.getAsJsonObject("chests").entrySet()) chests.put(c.getKey(), c.getValue().getAsDouble());
                structures.put(e.getKey(), new Structure(e.getKey(), chests, num(o, "loot_seconds", 60),
                        o.has("dangerous") && o.get("dangerous").getAsBoolean(), str(o, "note")));
            }
        }
        if (root.has("mobs")) {
            for (var e : root.getAsJsonObject("mobs").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Map<String, Double> at = new LinkedHashMap<>();
                if (o.has("structures")) for (var s : o.getAsJsonObject("structures").entrySet()) at.put(s.getKey(), s.getValue().getAsDouble());
                mobs.put(e.getKey(), new Mob(e.getKey(), str(o, "spawn"), (int) num(o, "pack", 1), num(o, "fight", 15), at,
                        o.has("ranged") && o.get("ranged").getAsBoolean(), o.has("night") && o.get("night").getAsBoolean(),
                        num(o, "sight", 1), str(o, "note")));
            }
        }
        if (root.has("goals")) {
            for (var e : root.getAsJsonObject("goals").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                List<Way> ways = new ArrayList<>();
                if (o.has("ways")) {
                    for (JsonElement w : o.getAsJsonArray("ways")) {
                        // ("kind: what to do" - the kind is the word before the colon.)
                        String s = w.getAsString();
                        int colon = s.indexOf(':');
                        ways.add(colon > 0 ? new Way(s.substring(0, colon).trim(), s.substring(colon + 1).trim()) : new Way("", s));
                    }
                }
                goals.put(e.getKey(), new Goal(e.getKey(), num(o, "expert", Double.NaN), List.copyOf(ways), str(o, "needs"), str(o, "when")));
            }
        }
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    /** The knowledge base (read once; an empty one if the file is missing or broken - Bob plays on without it). */
    public static SpeedrunKnowledge get() {
        SpeedrunKnowledge k = instance;
        if (k != null) return k;
        synchronized (SpeedrunKnowledge.class) {
            if (instance != null) return instance;
            JsonObject root = new JsonObject();
            try (var in = SpeedrunKnowledge.class.getResourceAsStream("/bob-knowledge/speedrun.json")) {
                if (in != null) root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                else BotManager.LOG.warn("[Bot] bob-knowledge/speedrun.json missing: no speedrun knowledge");
            } catch (Exception e) {
                BotManager.LOG.warn("[Bot] bob-knowledge/speedrun.json not usable: {}", e.toString());
            }
            instance = new SpeedrunKnowledge(root);
            BotManager.LOG.info("[Bot] speedrun knowledge v{}: {} goals, {} mobs, {} structures", instance.version,
                    instance.goals.size(), instance.mobs.size(), instance.structures.size());
            return instance;
        }
    }

    public int version() {
        return version;
    }

    public Goal goal(String id) {
        return goals.get(id);
    }

    public Map<String, Goal> goals() {
        return goals;
    }

    /** An expert's seconds for the goal (NaN: not known). */
    public double expert(String goalId) {
        Goal g = goals.get(goalId);
        return g == null ? Double.NaN : g.expert();
    }

    public Map<String, Mob> mobs() {
        return mobs;
    }

    public Mob mob(EntityType<?> type) {
        return mobs.get(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    public Structure structure(String family) {
        return structures.get(family);
    }

    public Map<String, Structure> structures() {
        return structures;
    }

    /** The structures whose chests may hold this item, the likeliest first (rolled from the game's tables once per server). */
    public List<Loot> loot(MinecraftServer server, Item item) {
        if (server == null) return List.of();
        synchronized (this) {
            if (loot == null || lootFor != server) {
                loot = rollLoot(server.overworld());
                lootFor = server;
            }
            return loot.getOrDefault(item, List.of());
        }
    }

    /** Rolls of each chest table: enough for the one-in-twenty things to show. */
    private static final int ROLLS = 300;

    /**
     * Per structure family, the chance that a visit (its chests, as many of each as it has) brings
     * at least one of an item, and how many on average: each chest table rolled {@link #ROLLS} times.
     */
    private Map<Item, List<Loot>> rollLoot(ServerLevel level) {
        long t = System.nanoTime();
        Map<Item, List<Loot>> out = new IdentityHashMap<>();
        Map<String, Map<Item, double[]>> perTable = new HashMap<>();
        for (Structure s : structures.values()) {
            // P(none of the item in the whole visit) = product over its chests; mean adds up.
            Map<Item, double[]> visit = new IdentityHashMap<>(); // item -> {P(none), mean}
            for (var c : s.chests().entrySet()) {
                Map<Item, double[]> stats = perTable.computeIfAbsent(c.getKey(), k -> roll(level, k));
                for (var e : stats.entrySet()) {
                    double[] v = visit.computeIfAbsent(e.getKey(), k -> new double[]{1, 0});
                    // (A fraction of a chest - "one in two shipwrecks has it" - as a fractional power.)
                    v[0] *= Math.pow(1 - e.getValue()[0], c.getValue());
                    v[1] += e.getValue()[1] * c.getValue();
                }
            }
            for (var e : visit.entrySet()) {
                double chance = 1 - e.getValue()[0];
                if (chance < 0.02) continue;
                out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Loot(s.family(), chance, e.getValue()[1], s.lootSeconds(), s.dangerous()));
            }
        }
        for (List<Loot> l : out.values()) l.sort(Comparator.comparingDouble(Loot::chance).reversed());
        BotManager.LOG.info("[Bot] structure loot rolled in {} ms: {} kinds of items", (System.nanoTime() - t) / 1_000_000, out.size());
        return out;
    }

    /** One chest table: item -> {chance of at least one, mean count}. */
    private static Map<Item, double[]> roll(ServerLevel level, String table) {
        Map<Item, double[]> out = new IdentityHashMap<>();
        try {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table));
            LootTable lt = level.getServer().reloadableRegistries().getLootTable(key);
            if (lt == LootTable.EMPTY) return out;
            Map<Item, Integer> seen = new IdentityHashMap<>(), total = new IdentityHashMap<>();
            // (No place given: an explorer map in the table - a shipwreck's, an ocean ruin's, a
            // camp's - would otherwise search the world for its structure on every roll. Without
            // one it stays a blank map; what is tied to a biome there does not roll.)
            LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
            for (int i = 0; i < ROLLS; i++) {
                Map<Item, Integer> once = new IdentityHashMap<>();
                for (ItemStack s : lt.getRandomItems(params)) once.merge(s.getItem(), s.getCount(), Integer::sum);
                once.forEach((item, n) -> {
                    seen.merge(item, 1, Integer::sum);
                    total.merge(item, n, Integer::sum);
                });
            }
            seen.forEach((item, n) -> out.put(item, new double[]{n / (double) ROLLS, total.get(item) / (double) ROLLS}));
        } catch (RuntimeException e) {
            BotManager.LOG.debug("[Bot] loot table {} not rolled: {}", table, e.toString());
        }
        out.remove(Items.AIR);
        return out;
    }
}
