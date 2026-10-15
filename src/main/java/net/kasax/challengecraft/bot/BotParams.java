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
