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
        searchBeatsTheGreedyBrain(args.length > 0 ? Integer.parseInt(args[0]) : 150);
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
