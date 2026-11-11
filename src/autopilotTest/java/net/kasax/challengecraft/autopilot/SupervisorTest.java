package net.kasax.challengecraft.autopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The autopilot's loop from start to stop, with a stand-in for the Minecraft server (a Java process
 * of its own, as the real one is): games played, written down, counted, cleaned up, packed.
 */
class SupervisorTest {
    @TempDir
    Path tmp;

    private ByteArrayOutputStream console;

    private Supervisor supervisor(Path root) throws Exception {
        console = new ByteArrayOutputStream();
        Log log = new Log(new PrintStream(console, true, StandardCharsets.UTF_8));
        Config cfg = Config.load(root.resolve(Config.FILE), log::warn);
        return new Supervisor(root, cfg, Layout.find(root, cfg, null), null, FakeRoot.java(), log, 1);
    }

    @Test
    void playsLeagueGamesWritesThemDownAndCleansUp() throws Exception {
        Path root = FakeRoot.create(tmp, "league_games=1\ngame_minutes=2\nexport_hours=0\nserver_jar=fake-server.jar\n");
        Supervisor sup = supervisor(root);
        Thread loop = new Thread(sup::run);
        loop.start();
        Path results = root.resolve("bob-autopilot/league/results.jsonl");
        waitFor(() -> lines(results).size() >= 2, 120_000);
        sup.commands.add("stop");
        loop.join(90_000);
        assertFalse(loop.isAlive(), "the loop did not end on stop");

        // Two games, each counted for the duel, played with the mod's switches set (as the laptop trainer sets them).
        List<String> games = lines(results);
        assertTrue(games.size() >= 2);
        for (String line : games) {
            Map<String, Object> g = Json.map(Json.parse(line));
            assertEquals(true, g.get("counted"), line);
            Map<String, Object> r = Json.map(g.get("result"));
            assertEquals("1", r.get("sim"));
            assertEquals("40", r.get("challenges"));
            assertEquals(120L, r.get("secs"));
            assertEquals("abc1234567", Json.map(g.get("code")).get("commit"));
            List<String> params = new ArrayList<>();
            for (Object b : Json.list(r.get("bots"))) params.add(Json.str(Json.map(b).get("params"), ""));
            assertTrue(params.stream().anyMatch(p -> Knobs.same(p, Json.str(g.get("champion"), ""))));
            assertTrue(params.stream().anyMatch(p -> Knobs.same(p, Json.str(g.get("challenger"), ""))));
        }
        // The league goes on from the laptop's champion, with the games of the duel kept.
        Map<String, Object> state = Json.map(Json.map(Json.parse(Files.readString(root.resolve("bob-autopilot/league/state.json")))).get("league"));
        assertEquals(games.size(), Json.list(state.get("games")).size());
        String shipped = Json.str(Json.map(Json.parse(Files.readString(KnobsTest.repo().resolve("src/main/resources/bob-models/champion.json"))))
                .get("params"), "");
        assertTrue(Knobs.same(shipped, Knobs.text(Knobs.fromJson(state.get("champion")))));

        // Each game's log kept, gzipped; the worlds gone; the server's own files untouched.
        Path gameLogs = root.resolve("bob-autopilot/games");
        List<Path> logs = list(gameLogs);
        assertTrue(logs.size() >= 2, logs.toString());
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(logs.get(0)))) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("Done (") && text.contains("[BOTVS] result"), text);
        }
        Path game = root.resolve("bob-autopilot/work/liga-1");
        assertFalse(Files.exists(game.resolve("world")));
        assertTrue(Files.isSymbolicLink(game.resolve("mods")));
        assertTrue(Files.isSymbolicLink(game.resolve("libraries")));
        assertTrue(Files.isSymbolicLink(game.resolve("fake-server.jar")));
        assertTrue(Files.isDirectory(game.resolve("config")) && !Files.isSymbolicLink(game.resolve("config")));
        String props = Files.readString(game.resolve("server.properties"));
        assertTrue(props.contains("server-ip=127.0.0.1") && props.contains("enable-rcon=false") && props.contains("level-name=world"), props);
        assertTrue(Files.exists(root.resolve("mods/challengecraft-1.1.4.jar")));
        assertTrue(Files.exists(root.resolve("libraries/net/example/lib.jar")));
        assertEquals("the player's world", Files.readString(root.resolve("world/level.dat")));
        assertEquals("level-name=world\nmotd=The player's own server\n", Files.readString(root.resolve("server.properties")));

        // The status, for people and as JSON.
        assertTrue(Files.readString(root.resolve("bob-autopilot/STATUS.txt")).contains("Spiele fertig"));
        Map<String, Object> status = Json.map(Json.parse(Files.readString(root.resolve("bob-autopilot/status.json"))));
        assertEquals((long) games.size(), Json.map(status.get("league")).get("games"));

        // A package: the league, the logs; the next one leaves out what is unchanged.
        Path zip = sup.export.write();
        List<String> names = entries(zip);
        assertTrue(names.contains("league/results.jsonl") && names.contains("MANIFEST.txt") && names.contains("bob-autopilot.properties"), names.toString());
        assertTrue(names.stream().filter(n -> n.startsWith("games/")).count() >= 2, names.toString());
        Thread.sleep(1100);
        List<String> again = entries(sup.export.write());
        assertTrue(again.stream().noneMatch(n -> n.startsWith("games/")), again.toString());
        assertTrue(again.contains("league/results.jsonl"));

        // Started again (the panel's restart): the league goes on where it was - champion, duel, history, counts.
        Supervisor next = supervisor(root);
        assertEquals(Knobs.text(sup.league.champion), Knobs.text(next.league.champion));
        assertEquals(Knobs.text(sup.league.challenger), Knobs.text(next.league.challenger));
        assertEquals(sup.league.games.size(), next.league.games.size());
        assertEquals(sup.league.history.size(), next.league.history.size());
        assertFalse(next.league.history.isEmpty(), "the laptop's duels are kept");
        assertEquals(sup.gamesDone, next.gamesDone);
        assertTrue(next.gameCounter >= sup.gamesDone);
    }

    /** A trainer in OneDrive (11.11.2026): the games' folders outside it, the old work folder gone (not what it linked to). */
    @Test
    void aTrainerInOneDrivePlaysItsGamesOutsideIt() throws Exception {
        System.setProperty("bob.localDir", tmp.resolve("local").toString());
        try {
            Path root = FakeRoot.create(tmp.resolve("OneDrive/Documents/Bob Trainer/Bob-Trainer/server"),
                    "league_games=1\ngame_minutes=2\nexport_hours=0\nserver_jar=fake-server.jar\n");
            Path oldGame = Files.createDirectories(root.resolve("bob-autopilot/work/liga-1"));
            Files.createDirectories(oldGame.resolve("world/region"));
            Files.createSymbolicLink(oldGame.resolve("mods"), oldGame.relativize(root.resolve("mods")));
            Supervisor sup = supervisor(root);
            assertTrue(sup.relocated && sup.work.startsWith(tmp.resolve("local")), sup.work.toString());
            Thread loop = new Thread(sup::run);
            loop.start();
            Path results = root.resolve("bob-autopilot/league/results.jsonl");
            waitFor(() -> lines(results).size() >= 2, 120_000);
            sup.commands.add("stop");
            loop.join(90_000);
            assertFalse(loop.isAlive(), "the loop did not end on stop");
            assertFalse(Files.exists(root.resolve("bob-autopilot/work")), "the old work folder is gone");
            assertTrue(Files.exists(root.resolve("mods/challengecraft-1.1.4.jar")), "and not what it linked to");
            Path game = sup.work.resolve("liga-1");
            assertTrue(Files.isSymbolicLink(game.resolve("mods")));
            assertTrue(Files.isDirectory(game.resolve("config")));
            assertFalse(Files.exists(game.resolve("world")));
            assertTrue(list(root.resolve("bob-autopilot/games")).size() >= 2, "the logs stay beside the trainer");
            assertTrue(console.toString(StandardCharsets.UTF_8).contains("liegt in OneDrive"));
        } finally {
            System.clearProperty("bob.localDir");
        }
    }

    @Test
    void aServerThatBreaksDownAtTheStartIsWrittenDownWithItsReason() throws Exception {
        Path root = FakeRoot.create(tmp, "league_games=1\nexport_hours=0\nserver_jar=fake-server.jar\nextra_jvm_flags=-Dfake.mode=crash\n");
        Supervisor sup = supervisor(root);
        Thread loop = new Thread(sup::run);
        loop.start();
        Path failures = root.resolve("bob-autopilot/league/failures.jsonl");
        waitFor(() -> !lines(failures).isEmpty(), 60_000);
        sup.commands.add("stop");
        loop.join(90_000);
        Map<String, Object> f = Json.map(Json.parse(lines(failures).get(0)));
        assertTrue(Json.str(f.get("reason"), "").contains("boom"), f.toString());
        assertEquals(1, sup.gamesFailed);
        // (Its log is kept for a look at why.)
        waitFor(() -> !list(root.resolve("bob-autopilot/games")).isEmpty(), 10_000);
        assertTrue(console.toString(StandardCharsets.UTF_8).contains("boom"));
    }

    @Test
    void consoleCommandsAreAnswered() throws Exception {
        Path root = FakeRoot.create(tmp, "league_games=1\nexport_hours=0\nserver_jar=fake-server.jar\ngame_minutes=600\n");
        Supervisor sup = supervisor(root);
        Thread loop = new Thread(sup::run);
        loop.start();
        sup.commands.add("help");
        sup.commands.add("/status");
        sup.commands.add("bogus");
        sup.commands.add("export");
        waitFor(() -> !list(root.resolve("bob-autopilot/export")).stream().filter(p -> p.toString().endsWith(".zip")).toList().isEmpty(), 30_000);
        sup.commands.add("stop");
        loop.join(90_000);
        String out = console.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Befehle des Bob-Autopilots"), out);
        assertTrue(out.contains("Bob-Autopilot - Stand"), out);
        assertTrue(out.contains("Unbekannter Befehl: bogus"), out);
        assertTrue(out.contains("Paket zum Herunterladen"), out);
    }

    static void waitFor(BooleanSupplier done, long ms) throws InterruptedException {
        long until = System.currentTimeMillis() + ms;
        while (!done.getAsBoolean()) {
            if (System.currentTimeMillis() > until) throw new AssertionError("waited " + ms + " ms in vain");
            Thread.sleep(200);
        }
    }

    static List<String> lines(Path f) {
        try {
            return Files.exists(f) ? Files.readAllLines(f).stream().filter(l -> !l.isBlank()).toList() : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    static List<Path> list(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static List<String> entries(Path zip) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            return z.stream().map(e -> e.getName()).toList();
        }
    }
}
