package net.kasax.challengecraft.bot;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Knobs of a bot's play that one bot may have set otherwise than another - for the self-play
 * matches, where two variants of Bob play the same board in the same world against each other.
 * Written {@code key=value,key=value}; whatever is not named keeps its default.
 *
 * <ul>
 *   <li>{@code guardian} - 1: the guardian steps in when a fight is lost before it is won; 0: never.</li>
 *   <li>{@code quick} - seconds: a tile that takes no longer from where things stand is done before
 *       anything else (the opening, the kit); 0: no such tiles.</li>
 *   <li>{@code shield} - 1: on hard the shield is got before the plan (one ingot); 0: only when a
 *       tile or the iron kit brings it.</li>
 *   <li>{@code foodcap} - seconds: a stock of food while not starving only if it costs no more
 *       (-1, the default: the usual rule).</li>
 *   <li>{@code nightarmour} - 0 (the default): geared for the night is a weapon, food and the
 *       hearts; 1: armour too (6, or 2 and a shield), else dug in for the night.</li>
 *   <li>{@code copper} - 1 (the default): on hard, copper armour once the shield is in hand; 0:
 *       none (only what the board's iron and the chests bring).</li>
 *   <li>{@code descent} - 1 (the default): what is dug for deep down and not seen yet costs the
 *       staircase down to it in the plan; 0: only the looking about down there (as before).</li>
 *   <li>{@code cover} - how the guardian picks its way out of a fight going badly: 1 (the
 *       default) the learnt model where there is one (bob-models/cover.json), else the rules;
 *       0: the rules always; 2: at random among the possible ways (data for the trainer).</li>
 *   <li>{@code goalmodel} - 1 (the default): a tile's estimate corrected by the learnt goal
 *       model where there is one (bob-models/goal.json); 0: by the per-tile experience only.</li>
 *   <li>{@code worldsense} - 1 (the default): structures out to some five hundred blocks and
 *       biomes out to some eight hundred are known, as a player has a fair idea of them; 0: only
 *       what is in view.</li>
 *   <li>{@code foodstock} - a factor on what a stock of food may cost while not starving yet
 *       (1, the default; 2: twice as much is worth it - fewer fights begun hungry).</li>
 *   <li>{@code search} - 0 (the default): the tiles are picked by the strategist's route; 1: by the
 *       look-ahead ({@code lockout.BoardSearch}): the game played out many times in the abstract
 *       simulator ({@code lockout.sim}), calibrated figures for what tiles really take, no iron kit
 *       or Nether phase for their own sake; 2: the look-ahead decides the chores too - the stock of
 *       food while not starving, the shield until the evening, copper armour, the iron pickaxe and
 *       the bucket, the way into the Nether are candidates beside the tiles (round 2).</li>
 *   <li>{@code searchfloor} - 1 (the default): with the look-ahead, a tile that what was learnt of
 *       it (the leagues' prior, the server's experience) says takes longer than planned is believed
 *       to take at least Bob's whole estimate; 0: at least half of it, as in round 1.</li>
 *   <li>{@code fastopen} - 1 (the default): the stone tools before the stock of food (unless
 *       hungry or hurt), the food on the way; 0: the stock first, as before.</li>
 *   <li>{@code interleave} - 1 (the default): a dear investment (the shield's iron, the iron kit,
 *       copper armour with no ore in sight) waits for the quick tiles until the evening or two
 *       tiles; 0: invested in at once.</li>
 *   <li>{@code meanwhile} - 1 (the default): while a furnace cooks, the kit's stone or wood is
 *       mined beside it; 0: it waits at the furnace.</li>
 *   <li>{@code rocksight} - 1 (the default): rock showing in the open up to forty blocks off is
 *       walked to before a staircase is dug for stone; 0: the staircase at once.</li>
 *   <li>{@code estfix} - 1 (the default): the estimates of round 2 - leaves found where their tree
 *       is (as dear as its logs, in its biome only) and a step up the crown each, a cave biome's
 *       way down, a pillar's real pace for the falls, a staircase's pace for "Reach Y -50"; 0: as
 *       before.</li>
 *   <li>{@code goalprior} - 1 (the default): a tile's estimate times what the self-play leagues
 *       measured for it (bob-models/goal-priors.json) besides what this server learnt
 *       (GoalExperience); 0: only what was learnt here.</li>
 *   <li>{@code failexp} - 1 (the default): a failed start is learnt as an overrun is (the tile takes
 *       at least what was spent and another go); 0: only overruns and successes are learnt. (The
 *       experience file is the server's: in a league both sides learn from either's failures.)</li>
 * </ul>
 */
public final class BotParams {
    public static final BotParams DEFAULT = new BotParams(Map.of());

    private final Map<String, Double> values;

    private BotParams(Map<String, Double> values) {
        this.values = values;
    }

    /** The knobs that won the self-play league (bob-models/champion.json), or the defaults. */
    public static BotParams champion() {
        String c = net.kasax.challengecraft.bot.learn.ModelStore.championParams();
        if (c == null) return DEFAULT;
        try {
            return parse(c);
        } catch (RuntimeException e) {
            BotManager.LOG.warn("[Models] champion.json has knobs that do not parse ({}): the defaults", e.toString());
            return DEFAULT;
        }
    }

    /** From {@code key=value,...} (an empty or "-" text: the defaults). */
    public static BotParams parse(String text) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (text != null && !text.isBlank() && !text.equals("-")) {
            for (String part : text.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) throw new IllegalArgumentException("not key=value: " + part);
                out.put(kv[0].trim().toLowerCase(Locale.ROOT), Double.parseDouble(kv[1].trim()));
            }
        }
        // (In the order written: the league tells the variants apart by this text.)
        return new BotParams(java.util.Collections.unmodifiableMap(out));
    }

    public double get(String key, double fallback) {
        return values.getOrDefault(key, fallback);
    }

    public boolean on(String key) {
        return get(key, 1) != 0;
    }

    @Override
    public String toString() {
        if (values.isEmpty()) return "-";
        StringBuilder b = new StringBuilder();
        values.forEach((k, v) -> b.append(b.isEmpty() ? "" : ",").append(k).append('=').append(v % 1 == 0 ? String.valueOf(v.longValue()) : String.valueOf(v)));
        return b.toString();
    }
}
