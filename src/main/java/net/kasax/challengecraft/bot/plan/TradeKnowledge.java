package net.kasax.challengecraft.bot.plan;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kasax.challengecraft.bot.BotManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What villagers trade, read from the game's own data (the trade sets per profession and level):
 * who sells what for what, and at which level. With it Bob can weigh buying against making: a bow
 * from a fletcher of the second level for two emeralds (32 sticks each), a bucket of cod from a
 * fisherman for three.
 */
public final class TradeKnowledge {
    /** One trade: a {@code profession} of {@code level} gives {@code gives} for {@code wants} (and {@code wantsB}). */
    public record Trade(String profession, int level, Item wants, int wantsCount, Item wantsB, int wantsBCount, Item gives, int givesCount, int xp) {
    }

    /** The block that gives a jobless villager the profession. */
    public static final Map<String, Block> JOB_SITES = Map.ofEntries(
            Map.entry("fletcher", Blocks.FLETCHING_TABLE), Map.entry("fisherman", Blocks.BARREL), Map.entry("farmer", Blocks.COMPOSTER),
            Map.entry("cleric", Blocks.BREWING_STAND), Map.entry("librarian", Blocks.LECTERN), Map.entry("toolsmith", Blocks.SMITHING_TABLE),
            Map.entry("armorer", Blocks.BLAST_FURNACE), Map.entry("weaponsmith", Blocks.GRINDSTONE), Map.entry("butcher", Blocks.SMOKER),
            Map.entry("cartographer", Blocks.CARTOGRAPHY_TABLE), Map.entry("leatherworker", Blocks.CAULDRON), Map.entry("mason", Blocks.STONECUTTER),
            Map.entry("shepherd", Blocks.LOOM));

    /** Experience a villager needs to reach a level (index = level). */
    public static final int[] LEVEL_XP = {0, 0, 10, 70, 150, 250};

    private static TradeKnowledge instance;
    private static ResourceManager loadedFrom;

    private final Map<Item, List<Trade>> byGoods = new HashMap<>();
    private final List<Trade> all = new ArrayList<>();

    public static TradeKnowledge get(MinecraftServer server) {
        ResourceManager rm = server.getResourceManager();
        if (instance == null || loadedFrom != rm) {
            instance = new TradeKnowledge(rm);
            loadedFrom = rm;
        }
        return instance;
    }

    private TradeKnowledge(ResourceManager rm) {
        for (String profession : JOB_SITES.keySet()) {
            for (int level = 1; level <= 5; level++) {
                Set<String> ids = new HashSet<>();
                tag(rm, profession + "/level_" + level, ids, 0);
                for (String id : ids) {
                    Trade t = trade(rm, id, profession, level);
                    if (t == null) continue;
                    all.add(t);
                    byGoods.computeIfAbsent(t.gives(), k -> new ArrayList<>()).add(t);
                }
            }
        }
        BotManager.LOG.info("[Bot] trade knowledge: {} villager trades for {} kinds of goods", all.size(), byGoods.size());
    }

    private static void tag(ResourceManager rm, String path, Set<String> out, int depth) {
        if (depth > 4) return;
        var res = rm.getResource(Identifier.withDefaultNamespace("tags/villager_trade/" + path + ".json"));
        if (res.isEmpty()) return;
        try (Reader r = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            for (var v : o.getAsJsonArray("values")) {
                String s = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                if (s.startsWith("#")) tag(rm, Identifier.parse(s.substring(1)).getPath(), out, depth + 1);
                else out.add(s);
            }
        } catch (Exception e) {
            BotManager.LOG.debug("[Bot] trade tag {}: {}", path, e.toString());
        }
    }

    private static Trade trade(ResourceManager rm, String id, String profession, int level) {
        Identifier key = Identifier.parse(id);
        var res = rm.getResource(Identifier.fromNamespaceAndPath(key.getNamespace(), "villager_trade/" + key.getPath() + ".json"));
        if (res.isEmpty()) return null;
        try (Reader r = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            JsonObject wants = o.getAsJsonObject("wants"), gives = o.getAsJsonObject("gives");
            if (wants == null || gives == null) return null;
            JsonObject b = o.has("additional_wants") && o.get("additional_wants").isJsonObject() ? o.getAsJsonObject("additional_wants") : null;
            return new Trade(profession, level, item(wants), count(wants), b == null ? null : item(b), b == null ? 0 : count(b),
                    item(gives), count(gives), o.has("xp") ? o.get("xp").getAsInt() : 1);
        } catch (Exception e) {
            return null;
        }
    }

    private static Item item(JsonObject o) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(o.get("id").getAsString()));
    }

    private static int count(JsonObject o) {
        if (!o.has("count")) return 1;
        var c = o.get("count");
        // (A range in some trades: the most it may ask.)
        if (c.isJsonPrimitive()) return c.getAsInt();
        JsonObject range = c.getAsJsonObject();
        return range.has("max") ? range.get("max").getAsInt() : range.has("min") ? range.get("min").getAsInt() : 1;
    }

    /** The trades that give this item. */
    public List<Trade> selling(Item item) {
        return byGoods.getOrDefault(item, List.of());
    }

    /** The trades a profession offers at (or below) a level that pay emeralds, cheapest kind first. */
    public List<Trade> earning(String profession, int maxLevel) {
        List<Trade> out = new ArrayList<>();
        for (Trade t : all) if (t.profession().equals(profession) && t.level() <= maxLevel && t.gives() == Items.EMERALD) out.add(t);
        return out;
    }

    public List<Trade> all() {
        return all;
    }
}
