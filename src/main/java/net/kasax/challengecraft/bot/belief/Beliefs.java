package net.kasax.challengecraft.bot.belief;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kasax.challengecraft.bot.BotManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Bob's world model (knob {@code beliefs}, round 3): how likely he is to find iron and coal, logs
 * and animals where, from the game's own generation data and what he has seen - so the time a
 * thing takes is worked out for this world and this spot, not a fixed average ("the estimated
 * times depend on the context Bob had when they were measured").
 *
 * <ul>
 *   <li><b>Priors</b> ({@code bob-knowledge/beliefs.json}, made by {@code scripts/bot/belief_priors.py}
 *   from the 26.3 server jar): ore blocks per chunk at each height from the placed features (count,
 *   height distribution, vein size), and per biome the trees per chunk and the spawn weights.</li>
 *   <li><b>Observations</b> (per chunk column, the overworld): the biome and the logs of every chunk
 *   read by the memory (all the trees of a chunk in view are seen - none there is evidence), the
 *   animals seen and where.</li>
 *   <li><b>Estimates</b>: ore - the best height to dig at from here (the staircase down against the
 *   ore's density there, a cave known at that height, the tool's pace); logs - the walk until
 *   the first chunk with trees, chunk by chunk from the edge of what was seen, the biome there
 *   guessed from the biome seen at the edge (biomes are hundreds of blocks wide) blending into
 *   the world's average; animals - a factor on the spawn-rule search time from how much country
 *   was seen without one and how near the last one was; travel - the straight way with water at
 *   swimming pace.</li>
 *   <li><b>Search</b>: a dig at the believed best height gives up when the posterior that the place
 *   holds ore at the prior's rate has dropped low (two hypotheses: as the prior says, or a fifth
 *   of it - a poor spot), not after a fixed time.</li>
 * </ul>
 * Everything here is arithmetic over what was seen and fixed tables: deterministic, no wall clock.
 */
public final class Beliefs {
    /** Wall blocks newly in view per block a 1x2 tunnel advances (two walls two high, floor, roof). */
    static final double EXPOSED_PER_STEP = 6;
    /** A cave walked shows this many times the wall a tunnel does in the same time. */
    static final double CAVE_GAIN = 3;
    /** Overland and swimming pace, blocks a second (sprinting with stops; sprint-swimming). */
    public static final double WALK = 4.3, SWIM = 2.4;
    /** Biomes are some hundreds of blocks across: the edge's biome fades into the average over this. */
    static final double BIOME_SCALE = 400;
    /** A hypothesis of the dig's give-up rule: a poor spot holds this share of the prior's ore. */
    static final double POOR = 0.2, PRIOR_GOOD = 0.8;
    public static final double GIVE_UP_BELOW = 0.2;

    // ---- the priors -------------------------------------------------------------------------

    record Biome(double trees, Map<String, Double> creature, double creatureTotal) {
    }

    static final class Priors {
        final Map<String, double[]> ores = new HashMap<>();
        int minY = -64;
        final Map<String, Biome> biomes = new HashMap<>();
        double treeAverage = 0.5;
        /** Per land biome, the chance a chunk of it has a tree. */
        final java.util.List<Double> treeChances = new java.util.ArrayList<>();
    }

    private static volatile Priors priors;

    static Priors priors() {
        Priors p = priors;
        if (p != null) return p;
        synchronized (Beliefs.class) {
            if (priors != null) return priors;
            Priors out = new Priors();
            try (var in = Beliefs.class.getResourceAsStream("/bob-knowledge/beliefs.json")) {
                if (in == null) throw new IllegalStateException("missing");
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : root.getAsJsonObject("ores").entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    out.minY = o.get("min_y").getAsInt();
                    var arr = o.getAsJsonArray("per_chunk");
                    double[] v = new double[arr.size()];
                    for (int i = 0; i < v.length; i++) v[i] = arr.get(i).getAsDouble();
                    out.ores.put(e.getKey(), v);
                }
                double treeSum = 0;
                int n = 0;
                for (var e : root.getAsJsonObject("biomes").entrySet()) {
                    JsonObject b = e.getValue().getAsJsonObject();
                    Map<String, Double> creature = new HashMap<>();
                    for (var c : b.getAsJsonObject("creature").entrySet()) creature.put(c.getKey(), c.getValue().getAsDouble());
                    double total = creature.getOrDefault("_total", 0.0);
                    double trees = b.get("trees").getAsDouble();
                    out.biomes.put(e.getKey(), new Biome(trees, creature, total));
                    // (The average over the land biomes: what an unknown chunk far off holds.)
                    if (total > 0) {
                        treeSum += 1 - Math.exp(-trees);
                        out.treeChances.add(1 - Math.exp(-trees));
                        n++;
                    }
                }
                if (n > 0) out.treeAverage = treeSum / n;
            } catch (Exception e) {
                BotManager.LOG.warn("[Bot] bob-knowledge/beliefs.json not usable ({}): the world model has no priors", e.toString());
            }
            priors = out;
            return out;
        }
    }

    /** Ore blocks of this kind ("iron", "coal") per block of rock at height {@code y} (a 1x2 tunnel: y and y+1). */
    public static double density(String ore, int y) {
        Priors p = priors();
        double[] v = p.ores.get(ore);
        if (v == null) return 0;
        int i = y - p.minY;
        if (i < 0 || i + 1 >= v.length) return 0;
        return (v[i] + v[i + 1]) / 2 / 256;
    }

    // ---- what was seen ------------------------------------------------------------------------

    /** A chunk column as seen: its biome, the logs in view, the animals seen there (kind: count). */
    static final class Cell {
        String biome;
        int logs;
        long seenAt;
        final Map<String, Integer> animals = new HashMap<>();
    }

    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Cell> cells = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    /** A chunk read by the memory (all of its surface in view): its biome and the logs seen in it. */
    public void observeChunk(ServerLevel level, LevelChunk chunk, int logs, long now) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        ChunkPos cp = chunk.getPos();
        int x = cp.getMinBlockX() + 8, z = cp.getMinBlockZ() + 8;
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 8, 8);
        Cell c = cells.computeIfAbsent(cp.pack(), k -> new Cell());
        c.biome = level.getBiome(new BlockPos(x, y, z)).unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
        c.logs = logs;
        c.seenAt = now;
    }

    /** An animal seen (the memory's look about). */
    public void observeMob(EntityType<?> type, BlockPos at) {
        Cell c = cells.computeIfAbsent(ChunkPos.pack(at.getX() >> 4, at.getZ() >> 4), k -> new Cell());
        c.animals.merge(EntityType.getKey(type).getPath(), 1, Integer::sum);
    }

    /** For tests: a chunk column as if seen, with this biome and this many logs. */
    public void pretend(int cx, int cz, String biome, int logs) {
        Cell c = cells.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new Cell());
        c.biome = biome;
        c.logs = logs;
        c.seenAt = 1;
    }

    public int cellsSeen() {
        return cells.size();
    }

    // ---- estimates ----------------------------------------------------------------------------

    /** A believed way to get it: seconds in all, the luck part of them, where (for the plan), the height to dig at. */
    public record Estimate(double seconds, double luck, BlockPos at, int y) {
    }

    /**
     * Ore not in sight: the best height to look at from {@code from} (feet) - the staircase down
     * ({@code stairSeconds} a level) and the wait for the first ore in the walls there, the walls
     * coming into view at {@code exposePerSecond} blocks a second ({@code cave}: a cave known at
     * about that height, walked instead - {@link #CAVE_GAIN} times the wall, after the walk to
     * it). Not above the surface ({@code surfaceY}), nor below bedrock's rows.
     */
    public static Estimate ore(String ore, BlockPos from, int surfaceY, double stairSeconds, double exposePerSecond, BlockPos cave) {
        double best = Double.MAX_VALUE, bestLuck = 0;
        int bestY = from.getY();
        int top = Math.min(from.getY(), surfaceY - 2);
        for (int y = top; y >= -60; y -= 2) {
            double d = density(ore, y);
            if (d <= 1e-7) continue;
            double down = Math.max(0, from.getY() - y) * stairSeconds;
            double luck = 1 / (exposePerSecond * d);
            if (cave != null && Math.abs(cave.getY() - y) <= 10) {
                double walk = Math.sqrt(cave.distSqr(from)) / WALK;
                double viaCave = walk + Math.max(0, Math.abs(cave.getY() - y) - 2) * stairSeconds + luck / CAVE_GAIN;
                if (viaCave < down + luck) {
                    down = viaCave - luck / CAVE_GAIN;
                    luck /= CAVE_GAIN;
                }
            }
            if (down + luck < best) {
                best = down + luck;
                bestLuck = luck;
                bestY = y;
            }
        }
        if (best == Double.MAX_VALUE) return new Estimate(1e9, 1e9, null, from.getY());
        return new Estimate(best, bestLuck, new BlockPos(from.getX(), bestY, from.getZ()), bestY);
    }

    /**
     * Wall blocks a tunnel shows a second with a pickaxe breaking stone in {@code stoneSeconds}
     * (a step: two blocks, the drops, the step; measured 2.5 s a step with a stone pickaxe).
     */
    public static double exposePerSecond(double stoneSeconds) {
        return EXPOSED_PER_STEP / (2 * stoneSeconds + 1.3);
    }

    /** Seconds a level of staircase takes with that pickaxe (measured 2.3 s with a stone one: three blocks a level). */
    public static double stairSeconds(double stoneSeconds) {
        return 1.1 + 2 * stoneSeconds;
    }

    /**
     * The give-up rule of a dig at the believed height: the posterior that this spot holds ore at
     * the prior's rate after {@code seconds} of looking without a find, against a poor spot (a
     * fifth of it). Below {@link #GIVE_UP_BELOW}: elsewhere, or another way.
     */
    public static double stillGood(double expectedLuck, double seconds) {
        if (expectedLuck <= 0) return 1;
        double r = 1 / expectedLuck;
        double good = PRIOR_GOOD * Math.exp(-r * seconds), poor = (1 - PRIOR_GOOD) * Math.exp(-r * POOR * seconds);
        return good / (good + poor);
    }

    /**
     * Logs (of the common trees: oak, birch, spruce, dark oak) not in sight: the walk until the
     * first chunk with trees. Every chunk read with no logs is a chunk without them; the chunks
     * beyond what was seen are guessed from the biome at the edge of the seen country, fading
     * into the world's average with distance; a chunk with trees is walked into and its trees
     * reached.
     */
    public Estimate logs(ServerLevel level, BlockPos from, int viewChunks) {
        Priors p = priors();
        int cx = from.getX() >> 4, cz = from.getZ() >> 4;
        // The chunks of the view: none seen with logs (else the memory had a log to walk to).
        // What they were: the biome mix at the edge of the view, for what lies beyond.
        double edgeTrees = 0;
        int edge = 0, seen = 0;
        for (int dx = -viewChunks; dx <= viewChunks; dx++) {
            for (int dz = -viewChunks; dz <= viewChunks; dz++) {
                Cell c = cells.get(ChunkPos.pack(cx + dx, cz + dz));
                if (c == null || c.biome == null) continue;
                seen++;
                if (Math.max(Math.abs(dx), Math.abs(dz)) >= viewChunks - 2) {
                    Biome b = p.biomes.get(c.biome);
                    edgeTrees += c.logs > 0 ? 1 : b == null ? p.treeAverage : 1 - Math.exp(-b.trees());
                    edge++;
                }
            }
        }
        double pEdge;
        if (edge > 0) pEdge = edgeTrees / edge;
        else {
            // Nothing read yet: the biome underfoot.
            String here = level.getBiome(from).unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
            Biome b = p.biomes.get(here);
            pEdge = b == null ? p.treeAverage : 1 - Math.exp(-b.trees());
        }
        // Walking out from the edge of the view, a row of chunks comes into view every 16 blocks
        // (the width of the view), its trees a view away. The country goes on as at the edge of
        // the view until it changes - a biome boundary every BIOME_SCALE blocks or so - and then it
        // is the world's mix. (Rows are not drawn one chunk at a time: a desert is desert across.)
        // With nothing read yet the view itself is unknown country, from underfoot.
        int width = 2 * viewChunks + 1;
        boolean viewRead = seen >= 0.9 * width * width;
        double reach = viewRead ? viewChunks * 16 : 24;
        double pSame = 1 - Math.pow(1 - pEdge, width), pMix = 0;
        for (double pb : p.treeChances) pMix += 1 - Math.pow(1 - pb, width);
        pMix = p.treeChances.isEmpty() ? 0.5 : pMix / p.treeChances.size();
        double change = 16 / BIOME_SCALE;
        // (Joint chances of no trees so far: still in the same country, or in other country.)
        double same = 1, other = 0, expected = 0;
        for (int step = 0; step < 400 && same + other > 1e-4; step++) {
            double hit = same * pSame + other * pMix;
            expected += hit * (step * 16 + reach);
            double stay = same * (1 - pSame);
            other = other * (1 - pMix) + stay * change;
            same = stay * (1 - change);
        }
        expected += (same + other) * 400 * 16;
        double seconds = expected / WALK;
        return new Estimate(seconds, 0.6 * seconds, null, from.getY());
    }

    /**
     * A factor on the spawn-rule search time for an animal ({@link net.kasax.challengecraft.bot.plan.MobSearch}):
     * seen in the chunks about - nearer than looking about (down to a half); much country read
     * and none of that kind in it (where the biomes seen let it spawn) - longer, up to four times.
     */
    public double animalFactor(EntityType<?> type, BlockPos from, int viewChunks) {
        String id = EntityType.getKey(type).getPath();
        Priors p = priors();
        int cx = from.getX() >> 4, cz = from.getZ() >> 4;
        int read = 0, where = 0, with = 0;
        int r = Math.min(viewChunks, 8);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Cell c = cells.get(ChunkPos.pack(cx + dx, cz + dz));
                if (c == null) continue;
                if (c.animals.getOrDefault(id, 0) > 0) with++;
                if (c.biome == null) continue;
                read++;
                Biome b = p.biomes.get(c.biome);
                if (b != null && b.creature().getOrDefault(id, 0.0) > 0) where++;
            }
        }
        if (with > 0) return Math.max(0.5, 1 - 0.1 * with);
        if (read < 9) return 1;
        // Country read without one: the more of it where the kind could have been, the longer.
        double share = where / (double) read;
        return 1 + 3 * Math.min(1, read / 150.0) * (0.3 + 0.7 * share);
    }

    /**
     * Seconds to get from {@code from} to {@code to} as the crow flies, water at swimming pace and
     * climbs at a staircase's (a herd across a river is further than it looks).
     */
    public static double travelSeconds(ServerLevel level, BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(flat / 2));
        int water = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            int x = (int) Math.floor(from.getX() + dx * f), z = (int) Math.floor(from.getZ() + dz * f);
            if (!level.hasChunkAt(m.set(x, from.getY(), z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (!level.getFluidState(m.set(x, top - 1, z)).isEmpty()) water++;
        }
        double wet = flat * water / steps;
        return (flat - wet) / WALK + wet / SWIM + Math.abs(to.getY() - from.getY()) * 0.5;
    }

    // ---- the context of a goal attempt (the journal) ---------------------------------------------

    /**
     * Where Bob stands when he sets out for a tile, as the journal keeps it next to the estimate
     * and the time it really took - so the estimates can be calibrated per context (the depth,
     * the biome, the time of day, the kit, how near the nearest known source is), not per seed.
     */
    public static com.google.gson.JsonObject context(net.kasax.challengecraft.bot.Bot bot) {
        var o = new com.google.gson.JsonObject();
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        BlockPos at = body.blockPosition();
        boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        o.addProperty("dim", level.dimension().identifier().getPath());
        o.addProperty("biome", level.getBiome(at).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
        o.addProperty("night", overworld && level.isDarkOutside());
        o.addProperty("y", at.getY());
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        o.addProperty("surface", surface);
        o.addProperty("kit", kit(body));
        BlockPos iron = bot.memory().nearest(level, at, st -> st.is(net.minecraft.world.level.block.Blocks.IRON_ORE)
                || st.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE), java.util.Set.of());
        o.addProperty("iron_seen", iron == null ? -1 : (int) Math.sqrt(iron.distSqr(at)));
        BlockPos log = bot.memory().nearest(level, at, st -> st.is(net.minecraft.tags.BlockTags.LOGS), java.util.Set.of());
        o.addProperty("log_seen", log == null ? -1 : (int) Math.sqrt(log.distSqr(at)));
        o.addProperty("cells_read", bot.memory().beliefs.cellsSeen());
        o.addProperty("beliefs", bot.params.on("beliefs"));
        if (overworld) {
            double stone = Math.min(1.15, Math.min(15, bot.tools().breakTicks(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()) / 20.0));
            Estimate e = ore("iron", at, surface, stairSeconds(stone), exposePerSecond(stone), bot.memory().cave(level, at, 16, 24, 128, java.util.List.of()));
            o.addProperty("iron_s", Math.round(e.seconds()));
            o.addProperty("iron_y", e.y());
            o.addProperty("logs_s", Math.round(bot.memory().beliefs.logs(level, at, net.kasax.challengecraft.bot.BotWorld.viewChunks(level)).seconds()));
        }
        return o;
    }

    /** The kit in a word: the best pickaxe and sword, the armour points, a shield. */
    static String kit(net.minecraft.world.entity.player.Player body) {
        String pick = "none", sword = "none";
        String[] tiers = {"wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"};
        int bp = -1, bs = -1;
        var inv = body.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).getPath();
            for (int t = 0; t < tiers.length; t++) {
                if (id.equals(tiers[t] + "_pickaxe") && t > bp) {
                    bp = t;
                    pick = tiers[t];
                }
                if (id.equals(tiers[t] + "_sword") && t > bs) {
                    bs = t;
                    sword = tiers[t];
                }
            }
        }
        boolean shield = body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD) || inv.countItem(net.minecraft.world.item.Items.SHIELD) > 0;
        return "pick:" + pick + ",sword:" + sword + ",armour:" + body.getArmorValue() + ",shield:" + (shield ? 1 : 0);
    }
}
