package net.kasax.challengecraft.autopilot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Bob's strategy, learnt by playing against himself - the laptop trainer's league
 * ({@code scripts/train/bobleague.py}) on a rented server. The champion (the knobs Bob plays with)
 * meets a challenger (the same knobs with one or two changed) on the same board in the same world,
 * game after game; the challenger takes over only once it has won clearly - more tiles, game for
 * game, a death costing half a tile - and is turned down when it is clearly worse or after sixteen
 * games. The state is kept in the trainer's format (its state.json, "league"), so a league begun on
 * the laptop can go on here and the other way round.
 */
final class League {
    /**
     * A league of twelve games has a standard error of 0.6 to 0.9 tiles (the mirror match showed
     * 0.8 tiles between the same code): a challenger must win by more than the noise, looked at no
     * earlier than after six games.
     */
    static final int MIN_GAMES = 6, MAX_GAMES = 16;

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
            Map<String, Double> ch = Knobs.mutate(champion, rng, new HashSet<>(tried.keySet()));
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

    /** After enough games: the challenger takes over (clearly better), is turned down, or plays on. */
    Map<String, Object> decide(double now) {
        int n = games.size();
        if (n < MIN_GAMES) return null;
        double[] diffs = new double[n];
        double sum = 0;
        int wins = 0, losses = 0;
        for (int k = 0; k < n; k++) {
            Map<String, Object> g = games.get(k);
            List<Object> t = Json.list(g.get("tiles")), d = Json.list(g.get("deaths"));
            // (Tiles count; a death costs half a tile on top - it is the player's own worst moment.)
            diffs[k] = (Json.num(t.get(1), 0) - 0.5 * Json.num(d.get(1), 0)) - (Json.num(t.get(0), 0) - 0.5 * Json.num(d.get(0), 0));
            sum += diffs[k];
            if (diffs[k] > 0) wins++;
            if (diffs[k] < 0) losses++;
        }
        double mean = sum / n;
        double var = 0;
        for (double x : diffs) var += (x - mean) * (x - mean);
        var /= Math.max(1, n - 1);
        double se = var > 0 ? Math.sqrt(var / n) : 0.25;
        String verdict = null;
        if (mean - 1.64 * se > 0 && wins > losses) verdict = "promoted";
        else if (mean + se < 0 || n >= MAX_GAMES) verdict = "rejected";
        if (verdict == null) return null;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", now);
        entry.put("challenger", Knobs.text(challenger));
        entry.put("champion", Knobs.text(champion));
        entry.put("games", (long) n);
        entry.put("mean_diff", Math.round(mean * 100) / 100.0);
        entry.put("se", Math.round(se * 100) / 100.0);
        entry.put("wins", (long) wins);
        entry.put("losses", (long) losses);
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
