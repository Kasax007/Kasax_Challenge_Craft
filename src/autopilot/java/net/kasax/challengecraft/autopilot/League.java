package net.kasax.challengecraft.autopilot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Bob's strategy, learnt by playing against himself - the laptop trainer's league
 * ({@code scripts/train/bobleague.py}) on a rented server or the Bob-Trainer app. The champion (the
 * knobs Bob plays with) meets a challenger (the same knobs with one changed) on the same board in
 * the same world, a fresh world every game (paired: the world's luck is the same for both).
 *
 * <p>Round 3: the duel is a sequential Bayesian test on the per-world differences (tiles, a death
 * costing half a tile): a sceptical prior on the challenger's edge (sd one tile a game), the
 * spread of a game from the games themselves (with a few pseudo-games of two tiles); promoted when
 * P(better) passes 0.95 after at least eight worlds, turned down when it falls under 0.05 (from
 * four on) or at twenty-four worlds. The challenger: a knob value never tried first (round 3's
 * knobs, then round 2's, then the rest), then Thompson sampling over every knob value's estimated
 * edge from all duels so far. The state is kept in the trainer's format (its state.json, "league").
 */
final class League {
    /** A duel is looked at from eight worlds on, turned down early (from four) when clearly worse, ended at twenty-four. */
    static final int MIN_GAMES = 8, MIN_REJECT = 4, MAX_GAMES = 24;
    static final double PROMOTE = 0.95, REJECT = 0.05;
    /** The prior on an edge (tiles a game) and a game's spread (tiles, worth so many pseudo-games). */
    static final double PRIOR_SD = 1.0, NOISE_SD = 2.0, NOISE_WEIGHT = 3;

    Map<String, Double> champion = Knobs.defaults();
    /** Null between duels: the next game draws a new one. */
    Map<String, Double> challenger;
    /** The current duel's games: seed, [champion, challenger] tiles, deaths, hearts, game seconds. */
    final List<Map<String, Object>> games = new ArrayList<>();
    /** Challengers turned down (or the last promoted) since the champion last changed: not drawn again. */
    final Map<String, String> tried = new LinkedHashMap<>();
    final List<Map<String, Object>> history = new ArrayList<>();
    private final Random rng;

    League(Random rng) {
        this.rng = rng;
    }

    /** One game to play: the world's seed, the two sides (name and knobs) and which knobs are which. */
    record Game(long seed, String[] names, String[] knobs, String champion, String challenger) {
        /** The command's sides: {@code BobA:k=v,... BobB:k=v,...}. */
        String sides() {
            return names[0] + ":" + knobs[0] + " " + names[1] + ":" + knobs[1];
        }
    }

    /** The next game; {@code seed} as given (a list of seeds to replay), or null for a random one. */
    Game nextGame(Long seed) {
        if (challenger == null) {
            Map<String, Double> ch = choose();
            if (ch == null) ch = Knobs.mutate(champion, rng, new HashSet<>(tried.keySet()));
            if (ch == null) ch = Knobs.mutate(Knobs.defaults(), rng, new HashSet<>());
            challenger = ch;
            games.clear();
        }
        String a = Knobs.text(champion), b = Knobs.text(challenger);
        // (The names swap at random: no edge from the order the bots are ticked in.)
        boolean swap = rng.nextBoolean();
        return new Game(seed != null ? seed : rng.nextLong(), new String[]{"BobA", "BobB"},
                swap ? new String[]{b, a} : new String[]{a, b}, a, b);
    }

    /** What became of a finished game: whether it counted for the duel, and a verdict when one fell. */
    record Outcome(boolean counted, Map<String, Object> verdict) {
    }

    /**
     * A finished game (the mod's {@code [BOTVS] result}): counted for the duel it belongs to, if
     * that duel is still on (games running side by side may outlast the duel they were begun for).
     */
    Outcome record(Game game, Map<String, Object> result) {
        if (result == null || challenger == null || !Knobs.same(game.challenger(), Knobs.text(challenger))) {
            return new Outcome(false, null);
        }
        Map<String, Object> champ = null, chall = null;
        for (Object o : Json.list(result.get("bots"))) {
            Map<String, Object> bot = Json.map(o);
            // (The mod writes the knobs back as it read them: matched by their values.)
            String params = Json.str(bot.get("params"), "");
            if (champ == null && Knobs.same(params, game.champion())) champ = bot;
            else if (chall == null && Knobs.same(params, game.challenger())) chall = bot;
        }
        if (champ == null || chall == null) return new Outcome(false, null);
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("seed", result.get("seed"));
        g.put("tiles", List.of(Json.num(champ.get("tiles"), 0), Json.num(chall.get("tiles"), 0)));
        g.put("deaths", List.of(Json.num(champ.get("deaths"), 0), Json.num(chall.get("deaths"), 0)));
        g.put("hearts", List.of(Json.num(champ.get("hearts"), 0), Json.num(chall.get("hearts"), 0)));
        g.put("secs", result.get("secs"));
        games.add(g);
        return new Outcome(true, decide(System.currentTimeMillis() / 1000.0));
    }

    /** The per-world differences of the duel so far: the challenger's tiles less the champion's, a death half a tile. */
    double[] diffs() {
        double[] d = new double[games.size()];
        for (int k = 0; k < d.length; k++) {
            Map<String, Object> g = games.get(k);
            List<Object> t = Json.list(g.get("tiles")), dd = Json.list(g.get("deaths"));
            d[k] = (Json.num(t.get(1), 0) - 0.5 * Json.num(dd.get(1), 0)) - (Json.num(t.get(0), 0) - 0.5 * Json.num(dd.get(0), 0));
        }
        return d;
    }

    /** The posterior on the challenger's edge: its mean and sd, P(better), and the plain mean and its standard error. */
    record Posterior(int n, double mean, double sd, double pBetter, double rawMean, double se) {
    }

    static Posterior posterior(double[] d) {
        int n = d.length;
        if (n == 0) return new Posterior(0, 0, PRIOR_SD, 0.5, 0, 0);
        double m = 0;
        for (double x : d) m += x;
        m /= n;
        double ss = 0;
        for (double x : d) ss += (x - m) * (x - m);
        double s2 = (ss + NOISE_WEIGHT * NOISE_SD * NOISE_SD) / (n - 1 + NOISE_WEIGHT);
        double prec = 1 / (PRIOR_SD * PRIOR_SD) + n / s2;
        double mean = n / s2 * m / prec, sd = Math.sqrt(1 / prec);
        double se = n > 1 ? Math.sqrt(ss / (n - 1) / n) : NOISE_SD;
        return new Posterior(n, mean, sd, phi(mean / sd), m, se);
    }

    /** The standard normal distribution function (Abramowitz and Stegun 26.2.17, error under 1e-7). */
    static double phi(double z) {
        double t = 1 / (1 + 0.2316419 * Math.abs(z));
        double poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        double tail = Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI) * poly;
        return z >= 0 ? 1 - tail : tail;
    }

    /** After each game: the challenger takes over (P(better) high), is turned down (low, or the duel at its cap), or plays on. */
    Map<String, Object> decide(double now) {
        int n = games.size();
        if (n < MIN_REJECT) return null;
        double[] d = diffs();
        Posterior post = posterior(d);
        int wins = 0, losses = 0;
        for (double x : d) {
            if (x > 0) wins++;
            if (x < 0) losses++;
        }
        String verdict = null;
        if (n >= MIN_GAMES && post.pBetter() > PROMOTE) verdict = "promoted";
        else if (post.pBetter() < REJECT || n >= MAX_GAMES) verdict = "rejected";
        if (verdict == null) return null;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", now);
        entry.put("challenger", Knobs.text(challenger));
        entry.put("champion", Knobs.text(champion));
        entry.put("games", (long) n);
        entry.put("mean_diff", Math.round(post.rawMean() * 100) / 100.0);
        entry.put("se", Math.round(Math.max(post.se(), 0.05) * 100) / 100.0);
        entry.put("wins", (long) wins);
        entry.put("losses", (long) losses);
        entry.put("p_better", Math.round(post.pBetter() * 1000) / 1000.0);
        entry.put("verdict", verdict);
        history.add(entry);
        tried.put(Knobs.text(challenger), verdict);
        if (verdict.equals("promoted")) {
            champion = challenger;
            tried.clear();
        }
        challenger = null;
        games.clear();
        return entry;
    }

    // ---- the challenger: untested first, then Thompson sampling ------------------------------------

    /** A knob's value tried against the champion: how many duels, the posterior of its edge (tiles a game). */
    record Arm(String knob, double value, int duels, double mean, double sd) {
    }

    /** The values a challenger may set a knob to: a choice's values; a range on a grid. */
    static List<Double> candidates(Knobs.Knob k) {
        List<Double> out = new ArrayList<>();
        switch (k.kind()) {
            case CHOICE -> {
                for (double v : k.values()) out.add(v);
            }
            case INT -> {
                for (double f : new double[]{0, 0.125, 0.25, 0.5, 0.75, 1}) {
                    double v = Math.round(k.lo() + f * (k.hi() - k.lo()));
                    if (!out.contains(v)) out.add(v);
                }
            }
            case FLOAT -> {
                for (double f : new double[]{0, 0.25, 0.5, 0.75, 1}) {
                    double v = Math.round(k.lo() * Math.pow(k.hi() / k.lo(), f) * 20) / 20.0;
                    if (!out.contains(v)) out.add(v);
                }
                if (!out.contains(k.def())) out.add(k.def());
            }
        }
        return out;
    }

    /** Knobs tried first while untested: round 3's, then round 2's, then the rest. */
    static int priority(String knob) {
        if (List.of("riskpath", "beliefs", "motionsearch").contains(knob)) return 0;
        Knobs.Knob k = Knobs.ALL.get(knob);
        return k != null && k.kind() == Knobs.Kind.CHOICE && k.def() == 0 && k.values().length == 2 ? 1 : 2;
    }

    /**
     * Every knob value other than the champion's, with what all duels so far say of it: a duel
     * that changed it (with one other knob: half the weight each) is one look at its edge
     * (the duel's mean difference, its standard error); a sceptical prior (sd one tile).
     */
    List<Arm> arms() {
        Map<String, double[]> obs = new HashMap<>(); // knob=value -> {sum x/v, sum 1/v, count}
        for (Map<String, Object> h : history) {
            Map<String, Double> ch = Knobs.parse(Json.str(h.get("challenger"), "")), cp = Knobs.parse(Json.str(h.get("champion"), ""));
            List<String> changed = new ArrayList<>();
            for (Map.Entry<String, Double> e : ch.entrySet()) {
                Double was = cp.get(e.getKey());
                if (was == null || Double.compare(was, e.getValue()) != 0) changed.add(e.getKey());
            }
            if (changed.isEmpty()) continue;
            double x = Json.num(h.get("mean_diff"), 0), se = Math.max(0.1, Json.num(h.get("se"), NOISE_SD));
            double v = se * se * changed.size();
            for (String k : changed) {
                double[] o = obs.computeIfAbsent(k + "=" + Knobs.format(ch.get(k)), z -> new double[3]);
                o[0] += x / v;
                o[1] += 1 / v;
                o[2]++;
            }
        }
        List<Arm> out = new ArrayList<>();
        for (Knobs.Knob k : Knobs.ALL.values()) {
            double cur = champion.getOrDefault(k.name(), k.def());
            for (double v : candidates(k)) {
                if (Double.compare(v, cur) == 0) continue;
                double[] o = obs.get(k.name() + "=" + Knobs.format(v));
                double prec = 1 / (PRIOR_SD * PRIOR_SD) + (o == null ? 0 : o[1]);
                out.add(new Arm(k.name(), v, o == null ? 0 : (int) o[2], o == null ? 0 : o[0] / prec, Math.sqrt(1 / prec)));
            }
        }
        return out;
    }

    /** The next challenger: the champion with one knob value - an untested one first, else Thompson's draw. Null: none. */
    Map<String, Double> choose() {
        List<Arm> arms = arms();
        if (arms.isEmpty()) return null;
        Arm pick = null;
        for (Arm a : arms) {
            if (a.duels() > 0) continue;
            if (pick == null || priority(a.knob()) < priority(pick.knob())
                    || priority(a.knob()) == priority(pick.knob()) && (a.knob() + a.value()).compareTo(pick.knob() + pick.value()) < 0) pick = a;
        }
        if (pick == null) {
            double best = -Double.MAX_VALUE;
            for (Arm a : arms) {
                double draw = a.mean() + a.sd() * rng.nextGaussian();
                if (draw > best) {
                    best = draw;
                    pick = a;
                }
            }
        }
        Map<String, Double> p = new TreeMap<>(champion);
        p.put(pick.knob(), pick.value());
        return p;
    }

    /** The duel so far: games, mean tiles ahead of the champion (deaths at half a tile), wins and losses. */
    Map<String, Object> duel() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("games", (long) games.size());
        double sum = 0;
        int wins = 0, losses = 0;
        for (Map<String, Object> g : games) {
            List<Object> t = Json.list(g.get("tiles")), d = Json.list(g.get("deaths"));
            double diff = (Json.num(t.get(1), 0) - 0.5 * Json.num(d.get(1), 0)) - (Json.num(t.get(0), 0) - 0.5 * Json.num(d.get(0), 0));
            sum += diff;
            if (diff > 0) wins++;
            if (diff < 0) losses++;
        }
        o.put("mean_diff", games.isEmpty() ? 0.0 : Math.round(sum / games.size() * 100) / 100.0);
        o.put("wins", (long) wins);
        o.put("losses", (long) losses);
        Posterior post = posterior(diffs());
        o.put("p_better", Math.round(post.pBetter() * 1000) / 1000.0);
        o.put("edge", Math.round(post.mean() * 100) / 100.0);
        o.put("edge_sd", Math.round(post.sd() * 100) / 100.0);
        return o;
    }

    // ---- the state, in the trainer's format -----------------------------------------------------

    Map<String, Object> toJson() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("champion", Knobs.toJson(champion));
        o.put("challenger", challenger == null ? null : Knobs.toJson(challenger));
        o.put("games", new ArrayList<>(games));
        o.put("tried", new LinkedHashMap<>(tried));
        o.put("history", new ArrayList<>(history));
        return o;
    }

    /**
     * From the trainer's "league" object. {@code keepDuel}: the challenger and its games go on (a
     * state of our own after a restart); else only the champion and what was tried (a state from
     * elsewhere: its duel was begun under other conditions).
     */
    static League fromJson(Map<String, Object> o, boolean keepDuel, Random rng) {
        League l = new League(rng);
        Map<String, Double> champ = Knobs.fromJson(o.get("champion"));
        // (A champion of an older version may lack knobs added since: their defaults.)
        Map<String, Double> full = Knobs.defaults();
        full.putAll(champ);
        l.champion = full;
        for (Map.Entry<String, Object> e : Json.map(o.get("tried")).entrySet()) l.tried.put(e.getKey(), Json.str(e.getValue(), "-"));
        for (Object h : Json.list(o.get("history"))) l.history.add(Json.map(h));
        if (keepDuel && o.get("challenger") != null) {
            Map<String, Double> ch = Knobs.defaults();
            ch.putAll(Knobs.fromJson(o.get("challenger")));
            l.challenger = ch;
            for (Object g : Json.list(o.get("games"))) l.games.add(Json.map(g));
        }
        return l;
    }
}
