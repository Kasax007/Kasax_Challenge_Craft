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
 * </ul>
 */
public final class BotParams {
    public static final BotParams DEFAULT = new BotParams(Map.of());

    private final Map<String, Double> values;

    private BotParams(Map<String, Double> values) {
        this.values = values;
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
