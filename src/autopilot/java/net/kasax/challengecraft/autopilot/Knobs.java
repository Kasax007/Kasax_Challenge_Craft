package net.kasax.challengecraft.autopilot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * The knobs of Bob's play the league tunes (what each does: BotParams in the mod), with the range
 * a challenger may move them in. The same table as the laptop trainer's
 * ({@code scripts/train/bobleague.py}, KNOBS): a test compares the two, so a knob added there and
 * not here (or the other way round) fails the build of the tests.
 */
final class Knobs {
    enum Kind { CHOICE, INT, FLOAT }

    /** A knob: its kind, the values it may take (CHOICE) or its range (INT, FLOAT), its default. */
    record Knob(String name, Kind kind, double[] values, double lo, double hi, double def) {
    }

    /** By name, sorted (the order a challenger's knobs are drawn from, as the trainer does). */
    static final Map<String, Knob> ALL;

    static {
        Map<String, Knob> m = new TreeMap<>();
        choice(m, "guardian", 1, 0, 1);
        range(m, "quick", Kind.INT, 0, 120, 30);
        choice(m, "shield", 1, 0, 1);
        choice(m, "foodcap", -1, -1, 30, 60, 120, 240);
        range(m, "foodstock", Kind.FLOAT, 0.5, 3.0, 1.0);
        choice(m, "nightarmour", 0, 0, 1);
        choice(m, "copper", 1, 0, 1);
        choice(m, "descent", 1, 0, 1);
        choice(m, "worldsense", 1, 0, 1);
        choice(m, "goalmodel", 1, 0, 1);
        choice(m, "cover", 1, 0, 1);
        // Round 2 of 10.11.2026 (BotParams): off in normal play until the league finds them better.
        for (String k : new String[] {"pillarblocks", "unarmedrun", "coverveto", "swordstone", "bowmanwary",
                "veinfirst", "smeltgo", "cookalong", "foodsight", "treasureiron", "mountainiron", "itemslow",
                "estfix", "goalprior", "failexp", "fallheal"}) choice(m, k, 0, 0, 1);
        choice(m, "search", 0, 0, 2);
        ALL = Collections.unmodifiableMap(m);
    }

    private static void choice(Map<String, Knob> m, String name, double def, double... values) {
        m.put(name, new Knob(name, Kind.CHOICE, values, values[0], values[values.length - 1], def));
    }

    private static void range(Map<String, Knob> m, String name, Kind kind, double lo, double hi, double def) {
        m.put(name, new Knob(name, kind, new double[0], lo, hi, def));
    }

    private Knobs() {
    }

    static Map<String, Double> defaults() {
        Map<String, Double> p = new TreeMap<>();
        for (Knob k : ALL.values()) p.put(k.name(), k.def());
        return p;
    }

    /**
     * The knobs as the mod takes them, {@code k=v,k=v} sorted by name: whole numbers without a
     * fraction, others as short as they are ({@code foodstock=1.35}) - as the trainer writes them,
     * so the same knobs give the same text on the laptop and here.
     */
    static String text(Map<String, Double> params) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Double> e : new TreeMap<>(params).entrySet()) {
            if (!b.isEmpty()) b.append(',');
            b.append(e.getKey()).append('=').append(format(e.getValue()));
        }
        return b.toString();
    }

    static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /** From {@code k=v,...} ("-" or empty: none); what does not parse is left out. */
    static Map<String, Double> parse(String s) {
        Map<String, Double> p = new TreeMap<>();
        if (s == null) return p;
        for (String part : s.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try {
                p.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), Double.parseDouble(part.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
                // (A knob the mod would refuse too.)
            }
        }
        return p;
    }

    /** The same knobs, whatever the order and however written ("1" and "1.0"). */
    static boolean same(String a, String b) {
        return parse(a).equals(parse(b));
    }

    /**
     * A challenger: the champion with one knob changed (three times in ten, two), and not one that
     * lost lately ({@code tried}); null when fifty draws found none. As the trainer draws it: a
     * choice moves to another of its values, a whole number by up to a quarter of its range, a
     * fraction by a factor around one (log-normal, sigma 0.35).
     */
    static Map<String, Double> mutate(Map<String, Double> params, Random rng, Set<String> tried) {
        List<String> names = new ArrayList<>(ALL.keySet());
        String before = text(params);
        for (int attempt = 0; attempt < 50; attempt++) {
            Map<String, Double> p = new TreeMap<>(params);
            int count = rng.nextDouble() < 0.3 ? 2 : 1;
            List<String> pool = new ArrayList<>(names);
            for (int n = 0; n < count; n++) {
                Knob k = ALL.get(pool.remove(rng.nextInt(pool.size())));
                double cur = p.getOrDefault(k.name(), k.def());
                switch (k.kind()) {
                    case CHOICE -> {
                        List<Double> other = new ArrayList<>();
                        for (double v : k.values()) if (v != cur) other.add(v);
                        if (other.isEmpty()) for (double v : k.values()) other.add(v);
                        p.put(k.name(), other.get(rng.nextInt(other.size())));
                    }
                    case INT -> {
                        int step = Math.max(1, (int) ((k.hi() - k.lo()) * 0.25));
                        double moved = cur + (rng.nextBoolean() ? 1 : -1) * (1 + rng.nextInt(step));
                        p.put(k.name(), (double) (long) Math.min(k.hi(), Math.max(k.lo(), moved)));
                    }
                    case FLOAT -> {
                        double moved = cur * Math.exp(rng.nextGaussian() * 0.35);
                        p.put(k.name(), Math.round(Math.min(k.hi(), Math.max(k.lo(), moved)) * 100) / 100.0);
                    }
                }
            }
            String t = text(p);
            if (!t.equals(before) && !tried.contains(t)) return p;
        }
        return null;
    }

    /** As JSON keeps them (the trainer's state.json): whole numbers as integers, fractions as they are. */
    static Map<String, Object> toJson(Map<String, Double> params) {
        Map<String, Object> o = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : params.entrySet()) {
            Knob k = ALL.get(e.getKey());
            double v = e.getValue();
            o.put(e.getKey(), k != null && k.kind() == Kind.FLOAT || v != Math.rint(v) ? (Object) v : (Object) (long) v);
        }
        return o;
    }

    static Map<String, Double> fromJson(Object json) {
        Map<String, Double> p = new TreeMap<>();
        for (Map.Entry<String, Object> e : Json.map(json).entrySet()) {
            if (e.getValue() instanceof Number n) p.put(e.getKey(), n.doubleValue());
        }
        return p;
    }
}
