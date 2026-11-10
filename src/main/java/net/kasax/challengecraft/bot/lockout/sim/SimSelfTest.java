package net.kasax.challengecraft.bot.lockout.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks of the simulator and the planner, run by the build: {@code ./gradlew bobSimTest}. Fails
 * (exit code 1) on the first broken check. About a minute: most of it the planner's small match.
 */
public final class SimSelfTest {
    private static int checks;

    private SimSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        java.util.Locale.setDefault(java.util.Locale.ROOT);
        long t0 = System.nanoTime();
        everyGoalHasARecipe();
        boardsAreTheModsBoards();
        everyPossibleGoalHasAWay();
        sharedStepsMakeLaterTilesCheaper();
        monstersComeOutAtNight();
        clinchRule();
        gamesKeepTheRules();
        gamesAreRepeatable();
        aClaimStopsTheOther();
        fastEnough();
        searchWorkIsAFixedCount();
        ownFigureIsAFloor();
        goalsFailAsTheJournalsSay();
        monstersMetByTheWay();
        shieldAndArmourEachTakeAShare();
        lookAheadDecidesChores();
        calibratedToTheLeagues();
        searchBeatsTheGreedyBrain(args.length > 0 ? Integer.parseInt(args[0]) : 150);
        choresBeatTilesOnly(args.length > 0 ? Integer.parseInt(args[0]) : 150);
        System.out.printf("SimSelfTest: %d checks passed in %.0f s%n", checks, (System.nanoTime() - t0) / 1e9);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            System.out.println("FAILED: " + what);
            System.exit(1);
        }
    }

    /** Every goal the mod's pool may deal has a recipe here (a new goal needs a line in GoalBook). */
    static void everyGoalHasARecipe() {
        for (String[] g : PoolBoards.pool()) check(GoalBook.get(g[0]) != null, "no recipe for pool goal " + g[0]);
    }

    /** The boards are the mod's own: 25 different goals, the same for the same seed. */
    static void boardsAreTheModsBoards() {
        for (long seed : new long[]{1, 77, -2951244321692375177L}) {
            Board a = PoolBoards.board(seed), b = PoolBoards.board(seed);
            Set<String> ids = new HashSet<>();
            for (int t = 0; t < Board.SIZE; t++) {
                ids.add(a.goal[t].id);
                check(a.goal[t] == b.goal[t], "board of seed " + seed + " differs between two deals");
            }
            check(ids.size() == Board.SIZE, "board of seed " + seed + " has a goal twice");
            List<String> real = new ArrayList<>();
            for (var g : net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool.pickBoard(seed)) real.add(g.id());
            for (int t = 0; t < Board.SIZE; t++) check(real.get(t).equals(a.goal[t].id), "board differs from pickBoard at tile " + t);
        }
    }

    /** In a world with everything near and known, every goal not marked impossible has a finite cost. */
    static void everyPossibleGoalHasAWay() {
        World w = World.sample(5);
        Board board = RandomBoards.board(5);
        Profile p = Profile.expert();
        Sim sim = new Sim(w, board, p, p, new RolloutPolicy(true), new RolloutPolicy(true), 5400, 5);
        sim.players[0].known = -1L;
        for (Place pl : Place.ALL) w.exists[pl.ordinal()] = pl != Place.END && pl != Place.STRONGHOLD && pl != Place.MANSION || w.exists[pl.ordinal()];
        sim.now = 3700; // (phantoms come after three nights)
        for (Recipe r : GoalBook.all()) {
            if (r.impossible || r.place == Place.MANSION || r.place == Place.STRONGHOLD) continue;
            double c = sim.planner.estimate(sim, 0, r, p);
            check(c < Planner.INF / 2 && c > 0, "no way to " + r.id + " (" + c + ")");
        }
    }

    /** What one goal leaves in hand makes the next cheaper: the iron of the bucket for the shears. */
    static void sharedStepsMakeLaterTilesCheaper() {
        World w = World.sample(9);
        Board board = only("craft_bucket", "craft_shears", "obtain_iron_ingot");
        Profile p = Profile.bob();
        Sim sim = new Sim(w, board, p, p, new RolloutPolicy(true), new RolloutPolicy(true), 5400, 9);
        sim.players[0].ms = Ms.WOOD.bit | Ms.STONE.bit | Ms.FURNACE.bit;
        double shears = sim.planner.estimate(sim, 0, GoalBook.get("craft_shears"), p);
        int[] ms = new int[1], res = new int[Res.N];
        sim.planner.yields(sim, 0, GoalBook.get("craft_bucket"), p, ms, res);
        double after = sim.planner.estimateWith(sim, 0, GoalBook.get("craft_shears"), p, ms[0], new int[Res.N]);
        // (With the iron trip of the bucket behind it: no new trip down for the shears' two ingots.)
        sim.players[0].ms |= Ms.BUCKET.bit;
        sim.players[0].res[Res.IRON.ordinal()] = 2;
        double withIron = sim.planner.estimate(sim, 0, GoalBook.get("craft_shears"), p);
        check(withIron < 0.2 * shears, "shears with two ingots in hand not much cheaper: " + withIron + " vs " + shears);
        check(after <= shears + 1e-9, "the bucket's milestones make the shears dearer");
    }

    /** Monsters are found sooner at night (and in caves) than on the surface by day. */
    static void monstersComeOutAtNight() {
        check(Planner.monsterFactor(700, 0, 0) == 1, "night on the surface is not the monsters' time");
        check(Planner.monsterFactor(100, 0, 0) > 2, "by day on the surface monsters are as easy as at night");
        check(Planner.monsterFactor(100, 1, 0) < Planner.monsterFactor(100, 0, 0), "caves by day not better than the surface");
        check(Planner.night(700) && !Planner.night(100) && Planner.night(1900), "night times wrong");
    }

    /** A player has won once the other cannot catch up: 13 always does, 10:2 with 2 open does too. */
    static void clinchRule() {
        Board b = Board.of(fill(), null, null);
        for (int t = 0; t < 13; t++) b.claim(t, 0, t);
        check(b.clinched() == 0, "13 tiles did not win");
        Board c = Board.of(fill(), null, null);
        for (int t = 0; t < 10; t++) c.claim(t, 0, t);
        for (int t = 10; t < 21; t++) c.claim(t, 1, t);
        check(c.clinched() == -1, "10:11 with 4 open counted as decided");
        c.claim(21, 1, 22);
        c.claim(22, 1, 23);
        check(c.clinched() == 1, "10:13 not won");
    }

    /** Many games: tiles claimed once, scores add up, the game ends by the rules. */
    static void gamesKeepTheRules() {
        for (int g = 0; g < 200; g++) {
            long seed = SearchPolicy.mix(g + 1000);
            double length = g % 2 == 0 ? 1200 : 5400;
            SimBench.Result r = SimBench.play(seed, "greedy", g % 3 == 0 ? "cheapest-true" : "greedy", Profile.bob(), Profile.bob(), length, false);
            int sum = r.score()[0] + r.score()[1];
            check(sum <= Board.SIZE && sum == r.claims().size(), "game " + g + ": score and claims do not add up");
            Set<String> seen = new HashSet<>();
            for (String c : r.claims()) check(seen.add(c.split("@")[0]), "game " + g + ": a tile claimed twice");
            int a = r.score()[0], b = r.score()[1];
            boolean clinched = a > b + (Board.SIZE - sum) || b > a + (Board.SIZE - sum);
            check(r.end() <= length + 1e-6, "game " + g + " ran past its length");
            check(clinched || r.end() >= length - 1e-6 || sum == Board.SIZE, "game " + g + " ended without a reason");
            int expected = a > b ? 0 : b > a ? 1 : -1;
            check(r.winner() == expected, "game " + g + ": winner " + r.winner() + " for " + a + ":" + b);
            for (int i = 0; i < 2; i++) check(r.first()[i] < 0 || r.first()[i] <= r.end(), "first tile after the end");
        }
    }

    /** The same seed plays the same game. */
    static void gamesAreRepeatable() {
        for (long seed : new long[]{3, 4}) {
            SimBench.Result a = SimBench.play(seed, "greedy", "search", Profile.bob(), Profile.bob(), 1200, false);
            SimBench.Result b = SimBench.play(seed, "greedy", "search", Profile.bob(), Profile.bob(), 1200, false);
            check(a.claims().equals(b.claims()) && a.end() == b.end(), "seed " + seed + " did not repeat");
        }
    }

    /** A tile claimed by one player stops the other at once (and the other keeps what it gathered). */
    static void aClaimStopsTheOther() {
        World w = World.sample(11);
        Board board = only("obtain_32_cobblestone");
        Profile p = Profile.expert();
        Sim sim = new Sim(w, board, p, p, new RolloutPolicy(true), new RolloutPolicy(true), 5400, 11);
        for (PlayerState s : sim.players) s.ms = Ms.WOOD.bit | Ms.STONE.bit;
        sim.runUntil(1);
        int tile = board.index("obtain_32_cobblestone");
        check(sim.players[0].target == tile && sim.players[1].target == tile, "both should go for the only tile");
        sim.runUntil(400);
        check(!board.isOpen(tile), "the cobblestone was not claimed");
        int winner = board.owner[tile];
        check(sim.players[1 - winner].target != tile, "the other player still goes for a claimed tile");
    }

    /** Thousands of games a minute on one core (Bob against Bob, 90 minutes, today's brain). */
    static void fastEnough() {
        long t0 = System.nanoTime();
        int games = 300;
        for (int g = 0; g < games; g++) SimBench.play(SearchPolicy.mix(g + 50_000), "greedy", "greedy", Profile.bob(), Profile.bob(), 5400, false);
        double perMinute = games / ((System.nanoTime() - t0) / 1e9) * 60;
        System.out.printf("  %.0f games per minute (greedy against greedy, 90 minutes, one core)%n", perMinute);
        check(perMinute > 1000, "too slow: " + perMinute + " games per minute");
    }

    /**
     * The look-ahead's work is a count of games, not a time (the real game counts it against the
     * game's ticks in self-play): the same question gets the same answer after the same number of
     * games, on any thread; a question no longer wanted stops (null).
     */
    static void searchWorkIsAFixedCount() throws Exception {
        long seed = SearchPolicy.mix(4242);
        Board board = PoolBoards.board(seed);
        Profile p = Profile.bob();
        Sim game = new Sim(World.sample(seed), board, p, p, new GreedyPolicy(), new GreedyPolicy(), 5400, seed);
        game.runUntil(900);
        check(!game.over, "the test game is over too soon");
        SearchPolicy a = new SearchPolicy(), b = new SearchPolicy();
        Decision da = a.choose(game.fork(game.world, null, null, 1), 0);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            Sim copy = game.fork(game.world, null, null, 1);
            Decision db = worker.submit(() -> b.choose(copy, 0)).get();
            check(da != null && db != null && da.tile == db.tile && a.progress == b.progress && a.progress >= 100,
                    "the same question answered differently (" + da + " after " + a.progress + " games, " + db + " after " + b.progress + ")");
            SearchPolicy c = new SearchPolicy();
            c.cancelled = true;
            check(c.choose(game.fork(game.world, null, null, 1), 0) == null && c.progress == 0, "a cancelled question still worked");
            // (Another salt: other luck in the games played ahead, the same amount of work.)
            SearchPolicy d = new SearchPolicy();
            d.salt = 7;
            d.choose(game.fork(game.world, null, null, 1), 0);
            check(d.progress == a.progress, "the work depends on the luck: " + d.progress + " vs " + a.progress);
        } finally {
            worker.shutdown();
        }
    }

    /**
     * Bob's own figure as a floor ({@link SearchPolicy#floor}): a tile his planner believes far
     * dearer than the simulator does is not taken while another will do.
     */
    static void ownFigureIsAFloor() {
        long seed = SearchPolicy.mix(777);
        World w = World.sample(seed);
        Board board = only("craft_furnace", "obtain_32_cobblestone", "craft_bucket");
        Profile p = Profile.bob();
        Sim sim = new Sim(w, board, p, p, new GreedyPolicy(), new GreedyPolicy(), 5400, seed);
        for (PlayerState s : sim.players) s.ms = Ms.WOOD.bit | Ms.STONE.bit;
        Decision free = new SearchPolicy().choose(sim.fork(w, null, null, 1), 0);
        check(free != null, "no choice on a board of three");
        SearchPolicy floored = new SearchPolicy();
        floored.floor = new double[Board.SIZE];
        floored.floor[free.tile] = 5000;
        Decision d = floored.choose(sim.fork(w, null, null, 1), 0);
        check(d != null && d.tile != free.tile, "a tile Bob's own planner puts at 5000 s still taken: " + board.goal[free.tile].id);
    }

    /** The planner wins more than it loses against today's brain (Bob's pace, 90 minutes). */
    static void searchBeatsTheGreedyBrain(int seeds) throws Exception {
        List<SimBench.Result[]> pairs = SimBench.runPairs("greedy", "search", Profile.bob(), Profile.bob(), 5400, seeds, 1, 777);
        int wins = 0, losses = 0;
        double diff = 0;
        for (SimBench.Result[] pr : pairs) {
            for (int k = 0; k < 2; k++) {
                int search = k == 0 ? 1 : 0;
                SimBench.Result r = pr[k];
                if (r.winner() == search) wins++;
                else if (r.winner() == 1 - search) losses++;
                diff += r.score()[search] - r.score()[1 - search];
            }
        }
        System.out.printf("  search against greedy: %d wins, %d losses, %+.2f tiles per game over %d games%n", wins, losses, diff / (2 * seeds), 2 * seeds);
        check(wins > losses && diff > 0, "the planner does not beat the greedy brain");
    }

    /**
     * Each goal's failure chance from the leagues' journals (goal-priors.json "outcomes"): one got
     * once in six starts fails more often than its kind, one got nine times in eleven less often.
     */
    static void goalsFailAsTheJournalsSay() {
        Recipe saplings = GoalBook.get("obtain_3_sapling_types"), bricks = GoalBook.get("craft_bricks");
        check(saplings.seenStarts >= 5 && saplings.seenDone * 3 <= saplings.seenStarts, "the journals' sapling starts not read: "
                + saplings.seenDone + " of " + saplings.seenStarts);
        Sim sim = new Sim(World.sample(1), only("obtain_3_sapling_types", "craft_bricks"), Profile.bob(), Profile.bob(),
                new GreedyPolicy(), new GreedyPolicy(), 1200, 1);
        Profile p = Profile.bob();
        double fs = sim.failChance(0, saplings), fb = sim.failChance(0, bricks);
        check(fs > p.fail[saplings.kind.ordinal()] * 1.4, "3 Sapling Types (1 of 6) fails no more often than its kind: " + fs);
        check(fb < p.fail[bricks.kind.ordinal()] * 0.7, "Bricks (9 of 11) fails as often as its kind: " + fb);
        p.goalFails = false;
        Sim plain = new Sim(World.sample(1), only("obtain_3_sapling_types"), p, p, new GreedyPolicy(), new GreedyPolicy(), 1200, 1);
        check(plain.failChance(0, saplings) == p.fail[saplings.kind.ordinal()], "goalFails off still per goal");
    }

    /** A zombie tile, armed, a night out on the surface: met and claimed by the way more often than not; never without chances. */
    static void monstersMetByTheWay() {
        int met = 0, metOff = 0;
        for (int g = 0; g < 40; g++) {
            for (boolean on : new boolean[]{true, false}) {
                Profile p = Profile.bob();
                p.chances = on;
                Board board = only("kill_zombie", "obtain_16_end_stone");
                Sim sim = new Sim(World.sample(g), board, p, p, new IdleAfter(), new IdleAfter(), 1200, g);
                for (PlayerState s : sim.players) {
                    s.ms = Ms.WOOD.bit | Ms.STONE.bit;
                    s.res[Res.FOOD.ordinal()] = 9;
                }
                sim.now = 660; // (night)
                sim.runUntil(1100);
                if (board.owner[0] >= 0) {
                    if (on) met++;
                    else metOff++;
                }
            }
        }
        check(met >= 12, "a zombie met by the way in a night on the surface only " + met + " times in 40");
        check(metOff == 0, "met by the way with chances off: " + metOff);
    }

    /** A brain that never goes for a tile (only the chores and the night): for the chances. */
    private static final class IdleAfter implements Policy {
        @Override
        public String name() {
            return "idle";
        }

        @Override
        public Decision choose(Sim sim, int me) {
            return null;
        }

        @Override
        public Policy fresh() {
            return this;
        }
    }

    /** Copper armour on top of the shield takes off more of the danger (before round 2 the one or the other counted). */
    static void shieldAndArmourEachTakeAShare() {
        Profile p = Profile.bob();
        Sim sim = new Sim(World.sample(3), only("kill_zombie"), p, p, new GreedyPolicy(), new GreedyPolicy(), 1200, 3);
        PlayerState s = sim.players[0];
        s.ms = Ms.WOOD.bit | Ms.STONE.bit | Ms.SHIELD.bit;
        double shield = sim.hazard(0, Planner.S_WAIT, 0);
        s.ms |= Ms.COPPER_ARMOR.bit;
        double both = sim.hazard(0, Planner.S_WAIT, 0);
        check(both < 0.85 * shield, "copper armour on top of the shield changes nothing: " + shield + " -> " + both);
    }

    /**
     * The look-ahead deciding the chores (search=2): with nothing to eat and the board's tiles far,
     * a stock of food is among its answers; with chores off it never answers a chore.
     */
    static void lookAheadDecidesChores() {
        int chores = 0, off = 0, floored = 0;
        for (int g = 0; g < 12; g++) {
            long seed = SearchPolicy.mix(4242 + g);
            World w = World.sample(seed);
            Board board = only("craft_shield", "craft_bucket", "obtain_iron_ingot", "kill_spider");
            Profile p = Profile.bob();
            Sim sim = new Sim(w, board, p, p, new GreedyPolicy(), new GreedyPolicy(), 5400, seed);
            for (PlayerState s : sim.players) {
                s.ms = Ms.WOOD.bit | Ms.STONE.bit | Ms.FURNACE.bit;
                s.fedAt = 0;
            }
            sim.now = 400;
            SearchPolicy c = new SearchPolicy();
            c.chores = true;
            Decision d = c.choose(sim.fork(w, null, null, g), 0);
            if (d != null && d.chore()) chores++;
            Decision e = new SearchPolicy().choose(sim.fork(w, null, null, g), 0);
            if (e != null && e.chore()) off++;
            // (Every chore floored far off by Bob's own figure, SearchPolicy#choreFloor: a tile instead.)
            SearchPolicy f = new SearchPolicy();
            f.chores = true;
            f.choreFloor = new java.util.HashMap<>();
            f.choreFloor.put(SearchPolicy.FOOD_BIT, 5000.0);
            for (Ms m : SearchPolicy.CHORES) f.choreFloor.put((long) m.bit, 5000.0);
            Decision x = f.choose(sim.fork(w, null, null, g), 0);
            if (x != null && x.chore()) floored++;
        }
        check(chores >= 1, "the look-ahead never chose a chore, hungry with iron tiles to do");
        check(off == 0, "a chore chosen with chores off");
        check(floored == 0, "a chore Bob's own figure puts at 5000 s still chosen (" + floored + " of 12)");
    }

    /**
     * The calibration (Profile.bob against leagues 9 and 10, Bob against Bob, 20 minutes): tiles
     * per bot within the leagues' 2.86 +- 0.5, deaths within 0.28 +- 0.15.
     */
    static void calibratedToTheLeagues() throws Exception {
        List<SimBench.Result[]> pairs = SimBench.runPairs("greedy", "greedy", Profile.bob(), Profile.bob(), 1200, 150, 1, 99);
        double tiles = 0, deaths = 0;
        int n = 0;
        for (SimBench.Result[] pr : pairs) {
            for (SimBench.Result r : pr) {
                for (int i = 0; i < 2; i++) {
                    tiles += r.score()[i];
                    deaths += r.deaths()[i];
                    n++;
                }
            }
        }
        System.out.printf("  Bob against Bob, 20 minutes: %.2f tiles and %.2f deaths a bot (leagues 9 and 10: 2.86, 0.28)%n", tiles / n, deaths / n);
        check(Math.abs(tiles / n - 2.86) < 0.5, "tiles per bot off the leagues' 2.86: " + tiles / n);
        check(Math.abs(deaths / n - 0.28) < 0.15, "deaths per bot off the leagues' 0.28: " + deaths / n);
    }

    /** The look-ahead deciding the chores beats the look-ahead on tiles alone (20 minutes). */
    static void choresBeatTilesOnly(int seeds) throws Exception {
        List<SimBench.Result[]> pairs = SimBench.runPairs("search", "search-chores", Profile.bob(), Profile.bob(), 1200, seeds, 1, 4711);
        double diff = 0;
        for (SimBench.Result[] pr : pairs) {
            for (int k = 0; k < 2; k++) {
                int chores = k == 0 ? 1 : 0;
                diff += pr[k].score()[chores] - pr[k].score()[1 - chores];
            }
        }
        System.out.printf("  search with chores against search on tiles alone: %+.2f tiles per game over %d games%n", diff / (2 * seeds), 2 * seeds);
        check(diff > 0, "deciding the chores does not help the look-ahead");
    }

    /** A board of these goals first, filled up with other goals to 25. */
    static List<String> fill(String... first) {
        List<String> ids = new ArrayList<>(List.of(first));
        for (Recipe r : GoalBook.all()) {
            if (ids.size() >= Board.SIZE) break;
            if (!ids.contains(r.id)) ids.add(r.id);
        }
        return ids;
    }

    /** A board where only these goals are in play (the rest taken out of the game). */
    static Board only(String... goals) {
        Board b = Board.of(fill(goals), null, null);
        for (int t = goals.length; t < Board.SIZE; t++) b.close(t);
        return b;
    }
}
