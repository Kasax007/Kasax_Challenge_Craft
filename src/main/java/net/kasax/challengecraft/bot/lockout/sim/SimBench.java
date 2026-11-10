package net.kasax.challengecraft.bot.lockout.sim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plays the abstract game many times: two board brains against each other, or Bob against Bob to
 * check the simulator against the self-play leagues. Run with {@code ./gradlew bobSim
 * --args="<mode> [options]"}:
 * <ul>
 * <li>{@code match --a greedy --b search --profile bob --length 5400 --games 1000 --threads 4}:
 * every seed played twice with the seats swapped; wins, tiles, first tile, deaths, time spent;</li>
 * <li>{@code calibrate --leagues docs/bob-leagues}: Bob's greedy brain against itself on the
 * leagues' boards (20 minutes), next to what the leagues measured, tile by tile;</li>
 * <li>{@code show --a greedy --b search --seed 7}: one game with its log;</li>
 * <li>{@code speed}: games per minute on one core.</li>
 * </ul>
 * Brains: {@code greedy} (Bob today), {@code greedy-true} (the same with true figures),
 * {@code cheapest}, {@code cheapest-true}, {@code search}, {@code search-cheat} (sees the
 * opponent's true state), {@code search-fast}.
 */
public final class SimBench {
    private SimBench() {
    }

    public static Policy policy(String name) {
        if (name.startsWith("search:")) {
            // search:c=8,r=4,k=3,h=2400,w=2,e=0.3,obs=1,opp=0 (candidates, first round, rounds, horizon, win bonus, model error, observe, opponent true)
            SearchPolicy s = new SearchPolicy();
            for (String kv : name.substring(7).split(";")) {
                String[] p = kv.split("=");
                double v = Double.parseDouble(p[1]);
                switch (p[0]) {
                    case "c" -> s.candidates = (int) v;
                    case "r" -> s.firstRound = (int) v;
                    case "k" -> s.rounds = (int) v;
                    case "h" -> s.horizon = v;
                    case "w" -> s.winBonus = v;
                    case "e" -> s.modelError = v;
                    case "obs" -> s.observe = v != 0;
                    case "opp" -> s.opponentTrue = v != 0;
                    case "greedyopp" -> s.greedyOpponent = v != 0;
                    case "rollme" -> s.rolloutRoute = v != 0;
                    default -> throw new IllegalArgumentException("search knob " + p[0]);
                }
            }
            return s;
        }
        return switch (name) {
            case "greedy" -> new GreedyPolicy(false, true);
            case "greedy-true" -> new GreedyPolicy(true, true);
            case "cheapest" -> new GreedyPolicy(false, false);
            case "cheapest-true" -> new GreedyPolicy(true, false);
            case "search" -> new SearchPolicy();
            case "search-cheat" -> {
                SearchPolicy s = new SearchPolicy();
                s.observe = false;
                yield s;
            }
            case "search-err30" -> {
                SearchPolicy s = new SearchPolicy();
                s.modelError = 0.3;
                yield s;
            }
            case "search-err60" -> {
                SearchPolicy s = new SearchPolicy();
                s.modelError = 0.6;
                yield s;
            }
            case "search-fast" -> {
                SearchPolicy s = new SearchPolicy();
                s.candidates = 4;
                s.firstRound = 3;
                s.rounds = 2;
                s.horizon = 1500;
                yield s;
            }
            default -> throw new IllegalArgumentException("no brain " + name);
        };
    }

    /** One game's outcome. */
    public record Result(long seed, int[] score, double[] first, int[] deaths, double[][] time, int winner, double end,
                         List<String> claims, double[] decideMillis) {
    }

    /** One game: brain {@code a} as player 0, {@code b} as player 1. */
    public static Result play(long seed, String a, String b, Profile pa, Profile pb, double length, boolean log) {
        World w = World.sample(seed);
        Board board = PoolBoards.board(seed);
        Policy pa0 = policy(a), pb0 = policy(b);
        Sim sim = new Sim(w, board, pa, pb, pa0, pb0, length, seed * 0x9E3779B97F4A7C15L + 17);
        if (log) {
            sim.log = new StringBuilder();
            sim.logSteps = true;
        }
        sim.run();
        if (log) System.out.print(sim.log);
        List<String> claims = new ArrayList<>();
        for (int t = 0; t < Board.SIZE; t++) {
            if (board.owner[t] >= 0) claims.add(board.goal[t].id + "@" + board.owner[t] + "@" + Math.round(board.claimedAt[t]));
        }
        double[] ms = new double[2];
        if (pa0 instanceof SearchPolicy s && s.calls > 0) ms[0] = s.nanos / 1e6 / s.calls;
        if (pb0 instanceof SearchPolicy s && s.calls > 0) ms[1] = s.nanos / 1e6 / s.calls;
        return new Result(seed, board.score.clone(), new double[]{sim.players[0].firstTile, sim.players[1].firstTile},
                new int[]{sim.players[0].deaths, sim.players[1].deaths},
                new double[][]{sim.players[0].time.clone(), sim.players[1].time.clone()}, sim.winner, sim.now, claims, ms);
    }

    // ---- main ------------------------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        if (args.length == 0) {
            System.out.println("modes: match, calibrate, show, speed (see the class comment)");
            return;
        }
        Map<String, String> o = options(args);
        switch (args[0]) {
            case "match" -> match(o);
            case "calibrate" -> calibrate(o);
            case "show" -> {
                long seed = Long.parseLong(o.getOrDefault("seed", "1"));
                Profile p = Profile.named(o.getOrDefault("profile", "bob"));
                Result r = play(seed, o.getOrDefault("a", "greedy"), o.getOrDefault("b", "search"), p, p,
                        Double.parseDouble(o.getOrDefault("length", "5400")), true);
                System.out.println("score " + r.score[0] + ":" + r.score[1] + ", winner " + r.winner + ", end " + Math.round(r.end) + " s");
            }
            case "speed" -> speed(o);
            case "costs" -> costs(o);
            default -> throw new IllegalArgumentException("mode " + args[0]);
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) continue;
            String k = args[i].substring(2);
            String v = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "1";
            o.put(k, v);
        }
        return o;
    }

    // ---- match -------------------------------------------------------------------------------------------

    private static void match(Map<String, String> o) throws Exception {
        String a = o.getOrDefault("a", "greedy"), b = o.getOrDefault("b", "search");
        Profile pa = Profile.named(o.getOrDefault("profile", "bob")).tune(o.get("tune"));
        Profile pb = Profile.named(o.getOrDefault("profile-b", o.getOrDefault("profile", "bob"))).tune(o.get("tune-b") != null ? o.get("tune-b") : o.get("tune"));
        double length = Double.parseDouble(o.getOrDefault("length", "5400"));
        int games = Integer.parseInt(o.getOrDefault("games", "200"));
        int threads = Integer.parseInt(o.getOrDefault("threads", "4"));
        long seed0 = Long.parseLong(o.getOrDefault("seed", "1"));
        long t0 = System.nanoTime();
        List<Result[]> pairs = runPairs(a, b, pa, pb, length, games, threads, seed0);
        double secs = (System.nanoTime() - t0) / 1e9;
        report(a, b, pairs, length);
        System.out.printf("%d games in %.0f s (%.0f games per minute on %d threads)%n", 2 * pairs.size(), secs, 2 * pairs.size() / secs * 60, threads);
    }

    /** Each seed twice: A as player 0, then A as player 1 (the same world, board and luck stream). */
    static List<Result[]> runPairs(String a, String b, Profile pa, Profile pb, double length, int games, int threads, long seed0)
            throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<Result[]>> fs = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            long seed = SearchPolicy.mix(seed0 * 1_000_003L + g);
            fs.add(ex.submit(() -> new Result[]{play(seed, a, b, pa, pb, length, false), play(seed, b, a, pb, pa, length, false)}));
        }
        List<Result[]> out = new ArrayList<>();
        for (Future<Result[]> f : fs) out.add(f.get());
        ex.shutdown();
        return out;
    }

    private static void report(String a, String b, List<Result[]> pairs, double length) {
        int wins = 0, draws = 0, losses = 0;
        double tilesA = 0, tilesB = 0, deathsA = 0, deathsB = 0, msA = 0, msB = 0;
        List<Double> diffs = new ArrayList<>(), firstA = new ArrayList<>(), firstB = new ArrayList<>();
        double[] timeA = new double[Sim.ACCOUNTS.length], timeB = new double[Sim.ACCOUNTS.length];
        int n = 0;
        for (Result[] pr : pairs) {
            double pairDiff = 0;
            for (int k = 0; k < 2; k++) {
                Result r = pr[k];
                int ia = k == 0 ? 0 : 1, ib = 1 - ia;
                n++;
                if (r.winner == ia) wins++;
                else if (r.winner == ib) losses++;
                else draws++;
                tilesA += r.score[ia];
                tilesB += r.score[ib];
                deathsA += r.deaths[ia];
                deathsB += r.deaths[ib];
                msA += r.decideMillis[ia];
                msB += r.decideMillis[ib];
                pairDiff += r.score[ia] - r.score[ib];
                if (r.first[ia] >= 0) firstA.add(r.first[ia]);
                if (r.first[ib] >= 0) firstB.add(r.first[ib]);
                for (int j = 0; j < Sim.ACCOUNTS.length; j++) {
                    timeA[j] += r.time[ia][j];
                    timeB[j] += r.time[ib][j];
                }
            }
            diffs.add(pairDiff / 2);
        }
        double mean = diffs.stream().mapToDouble(d -> d).average().orElse(0);
        double sd = Math.sqrt(diffs.stream().mapToDouble(d -> (d - mean) * (d - mean)).sum() / Math.max(1, diffs.size() - 1));
        double se = sd / Math.sqrt(Math.max(1, diffs.size()));
        System.out.printf("%s vs %s, %d games of %.0f s%n", a, b, n, length);
        System.out.printf("  %s: %d wins, %d draws, %d losses (win rate %.1f%%, of decided %.1f%%)%n", a, wins, draws, losses,
                100.0 * wins / n, 100.0 * wins / Math.max(1, wins + losses));
        System.out.printf("  tiles per game: %s %.2f, %s %.2f; difference %+.2f (SE %.2f)%n", a, tilesA / n, b, tilesB / n, mean, se);
        System.out.printf("  first tile (median s): %s %.0f, %s %.0f; deaths per game: %s %.2f, %s %.2f%n", a, median(firstA), b,
                median(firstB), a, deathsA / n, b, deathsB / n);
        if (msA + msB > 0) System.out.printf("  thinking per decision (ms): %s %.1f, %s %.1f%n", a, msA / n, b, msB / n);
        StringBuilder sb = new StringBuilder("  time per game (s) " + a + ":");
        for (int j = 0; j < Sim.ACCOUNTS.length; j++) sb.append(String.format(" %s %.0f", Sim.ACCOUNTS[j], timeA[j] / n));
        System.out.println(sb);
        sb = new StringBuilder("  time per game (s) " + b + ":");
        for (int j = 0; j < Sim.ACCOUNTS.length; j++) sb.append(String.format(" %s %.0f", Sim.ACCOUNTS[j], timeB[j] / n));
        System.out.println(sb);
    }

    static double median(List<Double> xs) {
        if (xs.isEmpty()) return Double.NaN;
        double[] a = xs.stream().mapToDouble(d -> d).sorted().toArray();
        return a[a.length / 2];
    }

    // ---- calibration against the leagues --------------------------------------------------------------------

    private static final Pattern SEED = Pattern.compile("\"seed\": ?(-?\\d+)");
    private static final Pattern CLAIM = Pattern.compile("\"([^\"@]+)@(\\d+)\"");
    private static final Pattern TILES = Pattern.compile("\"tiles\": ?(\\d+)");
    private static final Pattern FIRST = Pattern.compile("\"first\": ?(-?\\d+)");
    private static final Pattern DEATHS = Pattern.compile("\"deaths\": ?(\\d+)");

    /**
     * Bob (greedy, Bob's pace) against himself on each league game's board, twenty minutes: tiles,
     * first tile, deaths, and per goal how often it was claimed within the twenty minutes - next to
     * the leagues' figures. Titles in the leagues are matched to goals through the board of each
     * seed and the English titles (lang files) - given as a tab-separated file goal-id/title
     * ({@code --titles}, by default {@code docs/bob-leagues/goal-titles.tsv}: the titles as the
     * game showed them in 26.3, from the mod's and the game's English lang files; a goal added to
     * the pool needs a line there to be counted).
     */
    private static void calibrate(Map<String, String> o) throws Exception {
        Path dir = Path.of(o.getOrDefault("leagues", "docs/bob-leagues"));
        Map<String, String> titleToId = new HashMap<>();
        Path titles = Path.of(o.getOrDefault("titles", "docs/bob-leagues/goal-titles.tsv"));
        if (Files.exists(titles)) {
            for (String line : Files.readAllLines(titles)) {
                String[] p = line.split("\t");
                if (p.length == 2) titleToId.put(p[0] + "|" + p[1], p[0]);
            }
        }
        List<Long> seeds = new ArrayList<>();
        Map<String, Integer> onBoard = new HashMap<>(), claimedLeague = new HashMap<>();
        double leagueTiles = 0, leagueDeaths = 0;
        int leagueNone = 0;
        List<Double> leagueFirst = new ArrayList<>();
        int bots = 0;
        try (var files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                if (!f.toString().endsWith(".jsonl")) continue;
                for (String line : Files.readAllLines(f)) {
                    Matcher m = SEED.matcher(line);
                    if (!m.find()) continue;
                    long seed = Long.parseLong(m.group(1));
                    seeds.add(seed);
                    Board board = PoolBoards.board(seed);
                    Map<String, String> byTitle = new HashMap<>();
                    for (int t = 0; t < Board.SIZE; t++) {
                        onBoard.merge(board.goal[t].id, 1, Integer::sum);
                        for (var e : titleToId.entrySet()) if (e.getValue().equals(board.goal[t].id)) byTitle.put(e.getKey().split("\\|", 2)[1], e.getValue());
                    }
                    Matcher c = CLAIM.matcher(line);
                    while (c.find()) {
                        String id = byTitle.get(c.group(1));
                        if (id != null) claimedLeague.merge(id, 1, Integer::sum);
                    }
                    Matcher tm = TILES.matcher(line), fm = FIRST.matcher(line), dm = DEATHS.matcher(line);
                    while (tm.find()) {
                        leagueTiles += Integer.parseInt(tm.group(1));
                        bots++;
                    }
                    while (fm.find()) {
                        if (Integer.parseInt(fm.group(1)) > 0) leagueFirst.add(Double.parseDouble(fm.group(1)));
                        else leagueNone++;
                    }
                    while (dm.find()) leagueDeaths += Integer.parseInt(dm.group(1));
                }
            }
        }
        int reps = Integer.parseInt(o.getOrDefault("reps", "20"));
        int threads = Integer.parseInt(o.getOrDefault("threads", "4"));
        String brain = o.getOrDefault("a", "greedy");
        Profile prof = Profile.named(o.getOrDefault("profile", "bob")).tune(o.get("tune"));
        double length = Double.parseDouble(o.getOrDefault("length", "1200"));
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<Result>> fs = new ArrayList<>();
        for (int rep = 0; rep < reps; rep++) {
            for (long seed : seeds) {
                long luck = SearchPolicy.mix(seed + rep * 7919L);
                fs.add(ex.submit(() -> {
                    World w = World.sample(luck);
                    Board board = PoolBoards.board(seed);
                    Sim sim = new Sim(w, board, prof, prof, policy(brain), policy(brain), length, luck);
                    sim.run();
                    List<String> claims = new ArrayList<>();
                    for (int t = 0; t < Board.SIZE; t++) if (board.owner[t] >= 0) claims.add(board.goal[t].id + "@" + board.owner[t] + "@" + Math.round(board.claimedAt[t]));
                    return new Result(seed, board.score.clone(), new double[]{sim.players[0].firstTile, sim.players[1].firstTile},
                            new int[]{sim.players[0].deaths, sim.players[1].deaths},
                            new double[][]{sim.players[0].time.clone(), sim.players[1].time.clone()}, sim.winner, sim.now, claims, new double[2]);
                }));
            }
        }
        double simTiles = 0, simDeaths = 0;
        int simNone = 0;
        List<Double> simFirst = new ArrayList<>();
        Map<String, Integer> claimedSim = new HashMap<>(), firstSim = new HashMap<>();
        double[] time = new double[Sim.ACCOUNTS.length];
        int games = 0;
        for (Future<Result> f : fs) {
            Result r = f.get();
            games++;
            for (int i = 0; i < 2; i++) {
                simTiles += r.score[i];
                simDeaths += r.deaths[i];
                if (r.first[i] >= 0) simFirst.add(r.first[i]);
                else simNone++;
                for (int j = 0; j < time.length; j++) time[j] += r.time[i][j];
                String first = null;
                double at = 1e9;
                for (String c : r.claims) {
                    String[] parts = c.split("@");
                    if (Integer.parseInt(parts[1]) == i && Double.parseDouble(parts[2]) < at) {
                        at = Double.parseDouble(parts[2]);
                        first = parts[0];
                    }
                }
                if (first != null) firstSim.merge(first, 1, Integer::sum);
            }
            for (String c : r.claims) claimedSim.merge(c.split("@")[0], 1, Integer::sum);
        }
        ex.shutdown();
        System.out.printf("leagues: %d bot-games, tiles per bot %.2f, first tile median %.0f s, deaths per bot %.2f%n", bots, leagueTiles / bots,
                median(leagueFirst), leagueDeaths / bots);
        System.out.printf("sim:     %d bot-games, tiles per bot %.2f, first tile median %.0f s, deaths per bot %.2f%n", 2 * games,
                simTiles / (2 * games), median(simFirst), simDeaths / (2 * games));
        System.out.printf("sim: bot-games without a tile %.0f%% (leagues: %.0f%%)%n", 100.0 * simNone / (2 * games), 100.0 * leagueNone / bots);
        StringBuilder fsb = new StringBuilder("sim first tiles:");
        int botGames = 2 * games;
        firstSim.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(12)
                .forEach(e -> fsb.append(String.format(" %s %.0f%%", e.getKey(), 100.0 * e.getValue() / botGames)));
        System.out.println(fsb);
        StringBuilder sb = new StringBuilder("sim time per bot-game (s):");
        for (int j = 0; j < time.length; j++) sb.append(String.format(" %s %.0f", Sim.ACCOUNTS[j], time[j] / (2 * games)));
        System.out.println(sb);
        // Per goal: the share of games on whose board it was that it got claimed, league against sim.
        List<String> ids = new ArrayList<>(onBoard.keySet());
        ids.sort((x, y) -> Double.compare(rate(claimedLeague, onBoard, y, 1), rate(claimedLeague, onBoard, x, 1)));
        double se = 0, sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        int k = 0;
        System.out.println("goal                                 boards  league  sim");
        for (String id : ids) {
            double x = rate(claimedLeague, onBoard, id, 1), y = rate(claimedSim, onBoard, id, reps);
            int nb = onBoard.get(id);
            System.out.printf("  %-34s %4d   %5.2f  %5.2f%n", id, nb, x, y);
            se += nb * (x - y) * (x - y);
            sx += x;
            sy += y;
            sxx += x * x;
            syy += y * y;
            sxy += x * y;
            k++;
        }
        double cov = sxy / k - sx / k * sy / k, vx = sxx / k - sx / k * sx / k, vy = syy / k - sy / k * sy / k;
        System.out.printf("per-goal claim rates: correlation %.2f, weighted squared error %.3f over %d goals%n", cov / Math.sqrt(vx * vy), se / Math.max(1, ids.size()), k);
    }

    private static double rate(Map<String, Integer> claimed, Map<String, Integer> onBoard, String id, int reps) {
        return claimed.getOrDefault(id, 0) / (double) (onBoard.get(id) * reps);
    }

    /** Each tile's cost at the start (own figures and true average) for one seed - to check the recipes. */
    private static void costs(Map<String, String> o) {
        long seed = Long.parseLong(o.getOrDefault("seed", "1"));
        Profile p = Profile.named(o.getOrDefault("profile", "bob"));
        World w = World.sample(seed);
        Board board = o.containsKey("goals") ? Board.of(List.of(o.get("goals").split(",")), null, null) : PoolBoards.board(seed);
        Sim sim = new Sim(w, board, p, p, policy("greedy"), policy("greedy"), 5400, seed);
        if (o.containsKey("kit")) {
            sim.players[0].ms = Ms.WOOD.bit | Ms.STONE.bit | Ms.FURNACE.bit;
            sim.players[0].res[Res.FOOD.ordinal()] = 6;
        }
        for (Place pl : Place.ALL) {
            if (pl.located()) System.out.printf("  %-14s %s %6.0f m%s%n", pl, w.exists(pl) ? "yes" : " no", w.distance(pl),
                    (sim.players[0].known & World.bit(pl)) != 0 ? " known" : "");
        }
        for (int t = 0; t < Board.SIZE; t++) {
            Recipe r = board.goal[t];
            double own = sim.planner.estimate(sim, 0, r, sim.naive[0]);
            double luck = sim.planner.lastLuck();
            double tru = sim.planner.estimate(sim, 0, r, p);
            System.out.printf("%-36s own %7.0f (luck %5.0f)  true %7.0f%n", r.id, own, luck, tru);
        }
    }

    // ---- speed -------------------------------------------------------------------------------------------

    private static void speed(Map<String, String> o) {
        Profile p = Profile.named(o.getOrDefault("profile", "bob"));
        String a = o.getOrDefault("a", "greedy"), b = o.getOrDefault("b", "greedy");
        double length = Double.parseDouble(o.getOrDefault("length", "5400"));
        double seconds = Double.parseDouble(o.getOrDefault("seconds", "10"));
        long t0 = System.nanoTime();
        int games = 0;
        while ((System.nanoTime() - t0) / 1e9 < seconds) {
            play(SearchPolicy.mix(games + 12345L), a, b, p, p, length, false);
            games++;
        }
        double s = (System.nanoTime() - t0) / 1e9;
        System.out.printf("%s vs %s (%s, %.0f s games): %d games in %.1f s = %.0f games per minute on one core%n", a, b, p.name, length, games, s,
                games / s * 60);
    }

}
