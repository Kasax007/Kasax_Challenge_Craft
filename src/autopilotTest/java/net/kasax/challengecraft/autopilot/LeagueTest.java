package net.kasax.challengecraft.autopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LeagueTest {
    /** A [BOTVS] result of one game: the champion's and the challenger's tiles and deaths. */
    static Map<String, Object> result(League.Game g, int champTiles, int champDeaths, int challTiles, int challDeaths) {
        List<Object> bots = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            boolean champ = g.knobs()[i].equals(g.champion());
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("name", g.names()[i]);
            // (Written back by the mod in its own way: whole numbers without a fraction, the order given.)
            b.put("params", g.knobs()[i]);
            b.put("tiles", (long) (champ ? champTiles : challTiles));
            b.put("deaths", (long) (champ ? champDeaths : challDeaths));
            b.put("hearts", 10.0);
            bots.add(b);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("seed", g.seed());
        r.put("secs", 5400L);
        r.put("bots", bots);
        return r;
    }

    @Test
    void aGamePutsTheChampionAgainstAChallenger() {
        League l = new League(new Random(1));
        League.Game g = l.nextGame(42L);
        assertEquals(42L, g.seed());
        assertNotNull(l.challenger);
        assertTrue(List.of(g.knobs()).contains(g.champion()) && List.of(g.knobs()).contains(g.challenger()));
        assertFalse(g.champion().equals(g.challenger()));
        assertEquals("BobA:" + g.knobs()[0] + " BobB:" + g.knobs()[1], g.sides());
    }

    @Test
    void aClearlyBetterChallengerIsPromotedAfterEightGames() {
        League l = new League(new Random(2));
        Map<String, Object> verdict = null;
        for (int i = 0; i < League.MIN_GAMES; i++) {
            League.Game g = l.nextGame(null);
            String challenger = g.challenger();
            League.Outcome o = l.record(g, result(g, 2, 0, 5 + i % 2, 0));
            assertTrue(o.counted());
            if (i < League.MIN_GAMES - 1) assertNull(o.verdict(), "decided before eight games");
            else {
                verdict = o.verdict();
                assertEquals(challenger, Knobs.text(l.champion));
            }
        }
        assertNotNull(verdict);
        assertEquals("promoted", verdict.get("verdict"));
        assertNull(l.challenger);
        assertTrue(l.games.isEmpty());
        assertTrue(l.tried.isEmpty(), "a new champion: everything may be tried again");
    }

    @Test
    void deathsCountHalfATile() {
        League l = new League(new Random(3));
        // One tile more each game, but two deaths: worse by a tile a game.
        Map<String, Object> verdict = null;
        for (int i = 0; i < League.MIN_GAMES && verdict == null; i++) {
            League.Game g = l.nextGame(null);
            verdict = l.record(g, result(g, 3, 0, 4, 4)).verdict();
        }
        assertNotNull(verdict);
        assertEquals("rejected", verdict.get("verdict"));
        assertEquals(1, l.tried.size());
    }

    @Test
    void aDrawnOutDuelEndsAtItsCap() {
        League l = new League(new Random(4));
        Map<String, Object> verdict = null;
        int n = 0;
        while (verdict == null) {
            League.Game g = l.nextGame(null);
            int k = n++;
            verdict = l.record(g, result(g, 3, 0, k % 2 == 0 ? 4 : 2, 0)).verdict();
        }
        assertEquals(League.MAX_GAMES, n);
        assertEquals("rejected", verdict.get("verdict"));
    }

    @Test
    void aGameOfAnEndedDuelDoesNotCount() {
        League l = new League(new Random(5));
        League.Game old = l.nextGame(null);
        l.challenger = Knobs.mutate(l.challenger, new Random(9), new java.util.HashSet<>());
        assertFalse(l.record(old, result(old, 1, 0, 2, 0)).counted());
        assertTrue(l.games.isEmpty());
        assertFalse(l.record(l.nextGame(null), null).counted());
    }

    @Test
    void theStateGoesOnAfterARestart() {
        League l = new League(new Random(6));
        for (int i = 0; i < 3; i++) {
            League.Game g = l.nextGame(null);
            l.record(g, result(g, 3, 0, 3, 1));
        }
        Map<String, Object> json = Json.map(Json.parse(Json.pretty(l.toJson())));
        League again = League.fromJson(json, true, new Random(7));
        assertEquals(Knobs.text(l.champion), Knobs.text(again.champion));
        assertEquals(Knobs.text(l.challenger), Knobs.text(again.challenger));
        assertEquals(3, again.games.size());
        League fresh = League.fromJson(json, false, new Random(7));
        assertNull(fresh.challenger);
        assertTrue(fresh.games.isEmpty());
    }

    /** The league the mod ships (the laptop's state.json) is read: its champion, what was tried, the history. */
    @Test
    void readsTheLaptopTrainersState() throws IOException {
        String text = Files.readString(KnobsTest.repo().resolve("src/main/resources/bob-models/state.json"));
        Map<String, Object> st = Json.map(Json.parse(text));
        League l = League.fromJson(Json.map(st.get("league")), false, new Random(8));
        Map<String, Object> champion = Json.map(Json.parse(Files.readString(KnobsTest.repo().resolve("src/main/resources/bob-models/champion.json"))));
        assertTrue(Knobs.same(Json.str(champion.get("params"), ""), Knobs.text(l.champion)));
        assertFalse(l.history.isEmpty());
        assertNull(l.challenger);
        // Written in the trainer's form again: the same champion knobs, integers as integers.
        Map<String, Object> back = Json.map(l.toJson().get("champion"));
        assertEquals(30L, back.get("quick"));
        assertEquals(1.0, back.get("foodstock"));
    }

    @Test
    void untestedKnobsFirstRoundThreeLeading() {
        League l = new League(new Random(5));
        League.Game g = l.nextGame(null);
        Map<String, Double> ch = Knobs.parse(g.challenger()), cp = Knobs.parse(g.champion());
        String changed = null;
        for (String k : ch.keySet()) if (Double.compare(ch.get(k), cp.getOrDefault(k, Knobs.ALL.get(k).def())) != 0) changed = k;
        assertTrue(List.of("riskpath", "beliefs", "motionsearch").contains(changed), "first challenger changes " + changed);
    }

    @Test
    void thompsonPrefersAKnobThatWonBefore() {
        League l = new League(new Random(6));
        // Every value tried once and found even; one of them clearly ahead.
        for (League.Arm a : l.arms()) {
            Map<String, Double> ch = new java.util.TreeMap<>(l.champion);
            ch.put(a.knob(), a.value());
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("challenger", Knobs.text(ch));
            h.put("champion", Knobs.text(l.champion));
            h.put("mean_diff", a.knob().equals("beliefs") ? 1.5 : 0.0);
            h.put("se", 0.3);
            l.history.add(h);
        }
        int beliefs = 0;
        for (int i = 0; i < 200; i++) if (l.choose().get("beliefs") == 1.0) beliefs++;
        assertTrue(beliefs > 150, "beliefs drawn " + beliefs + " of 200");
    }

    @Test
    void thePosteriorIsSceptical() {
        assertEquals(0.5, League.posterior(new double[0]).pBetter(), 1e-9);
        assertTrue(League.posterior(new double[]{3, 3, 3, 3, 3, 3, 3, 3}).pBetter() > 0.99);
        double[] noisy = {2, -1, 3, -2, 1, 0, 2, -1};
        assertTrue(League.posterior(noisy).pBetter() < 0.95, "half a tile on a noisy eight worlds is not proof");
        assertEquals(0.975, League.phi(1.96), 1e-3);
    }
}
