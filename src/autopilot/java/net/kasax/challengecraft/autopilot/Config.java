package net.kasax.challengecraft.autopilot;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The settings, from {@code bob-autopilot.properties} in the server's folder (written with every
 * setting and an explanation in German when it is not there yet, so the panel's file editor shows
 * what can be set). A value that does not parse keeps its default, and says so.
 */
final class Config {
    static final String FILE = "bob-autopilot.properties";

    /** League games at once: "auto" (as memory and cores allow) or a number (0: none). */
    String leagueGames = "auto";
    int arenaServers = 0;
    int gameMinutes = 90;
    /** Every so many games a long one of this many minutes (0: never). */
    int longGameEvery = 0;
    int longGameMinutes = 90;
    /** The app's dashboard (127.0.0.1 only). */
    int dashboardPort = 8765;
    /** Round 3's tuning for bot-only servers (properties, JVM flags); false: as before, for a comparison. */
    boolean tuned = true;
    int gameMemoryMb = 4096;
    int arenaMemoryMb = 3072;
    int arenaBots = 6;
    int arenaRestartMinutes = 30;
    int viewDistance = 12;
    int simulationDistance = 8;
    String difficulty = "hard";
    int searchThreads = 4;
    String serverJar = "auto";
    String modsFolder = "mods";
    String java = "auto";
    String extraJvmFlags = "";
    int journalMaxMb = 3000;
    int gameLogsMaxMb = 1500;
    double exportHours = 12;
    int exportMaxMb = 25;
    int keepExports = 6;
    String seeds = "";
    /** Where the games' folders and Minecraft go (auto: beside the trainer, or outside OneDrive when the trainer is in it). */
    String workDir = "auto";
    String machine = "bisect";

    private static final Set<String> KEYS = Set.of("league_games", "arena_servers", "game_minutes", "game_memory_mb", "arena_memory_mb",
            "arena_bots", "arena_restart_minutes", "long_game_every", "long_game_minutes", "dashboard_port", "tuned", "view_distance", "simulation_distance", "difficulty", "search_threads", "server_jar",
            "mods_folder", "java", "extra_jvm_flags", "journal_max_mb", "game_logs_max_mb", "export_hours", "export_max_mb",
            "keep_exports", "seeds", "machine", "work_dir");

    /** Reads the file (writing the default one first if there is none); {@code warn} hears what was wrong. */
    static Config load(Path file, Consumer<String> warn) throws IOException {
        return load(file, warn, TEMPLATE);
    }

    /** As {@link #load(Path, Consumer)}, with this file written when there is none (the app has its own). */
    static Config load(Path file, Consumer<String> warn, String template) throws IOException {
        if (!Files.exists(file)) Files.writeString(file, template, StandardCharsets.UTF_8);
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        Config c = new Config();
        for (String key : p.stringPropertyNames()) {
            if (!KEYS.contains(key)) warn.accept("Unbekannte Einstellung in " + FILE + ": " + key + " (wird ignoriert)");
        }
        c.leagueGames = text(p, "league_games", c.leagueGames).toLowerCase(Locale.ROOT);
        if (!c.leagueGames.equals("auto") && parseInt(c.leagueGames) == null) {
            warn.accept("league_games=" + c.leagueGames + " ist weder auto noch eine Zahl: auto");
            c.leagueGames = "auto";
        }
        c.arenaServers = number(p, "arena_servers", c.arenaServers, 0, 16, warn);
        c.gameMinutes = number(p, "game_minutes", c.gameMinutes, 1, 600, warn);
        c.longGameEvery = number(p, "long_game_every", c.longGameEvery, 0, 1000, warn);
        c.longGameMinutes = number(p, "long_game_minutes", c.longGameMinutes, 1, 600, warn);
        c.dashboardPort = number(p, "dashboard_port", c.dashboardPort, 1024, 65535, warn);
        c.tuned = !text(p, "tuned", "true").equalsIgnoreCase("false");
        c.gameMemoryMb = number(p, "game_memory_mb", c.gameMemoryMb, 1024, 65536, warn);
        c.arenaMemoryMb = number(p, "arena_memory_mb", c.arenaMemoryMb, 1024, 65536, warn);
        c.arenaBots = number(p, "arena_bots", c.arenaBots, 1, 32, warn);
        c.arenaRestartMinutes = number(p, "arena_restart_minutes", c.arenaRestartMinutes, 5, 1440, warn);
        c.viewDistance = number(p, "view_distance", c.viewDistance, 3, 32, warn);
        c.simulationDistance = number(p, "simulation_distance", c.simulationDistance, 3, 32, warn);
        c.difficulty = text(p, "difficulty", c.difficulty).toLowerCase(Locale.ROOT);
        if (!List.of("easy", "normal", "hard").contains(c.difficulty)) {
            warn.accept("difficulty=" + c.difficulty + " gibt es nicht (easy, normal, hard): hard");
            c.difficulty = "hard";
        }
        c.searchThreads = number(p, "search_threads", c.searchThreads, 1, 16, warn);
        c.serverJar = text(p, "server_jar", c.serverJar);
        c.modsFolder = text(p, "mods_folder", c.modsFolder);
        c.java = text(p, "java", c.java);
        c.extraJvmFlags = text(p, "extra_jvm_flags", c.extraJvmFlags);
        c.journalMaxMb = number(p, "journal_max_mb", c.journalMaxMb, 50, 1_000_000, warn);
        c.gameLogsMaxMb = number(p, "game_logs_max_mb", c.gameLogsMaxMb, 20, 1_000_000, warn);
        c.exportHours = decimal(p, "export_hours", c.exportHours, 0, 1000, warn);
        c.exportMaxMb = number(p, "export_max_mb", c.exportMaxMb, 1, 10_000, warn);
        c.keepExports = number(p, "keep_exports", c.keepExports, 1, 1000, warn);
        c.seeds = text(p, "seeds", c.seeds);
        c.workDir = text(p, "work_dir", c.workDir);
        c.machine = text(p, "machine", c.machine).replaceAll("[^A-Za-z0-9_.-]", "_");
        return c;
    }

    /** The seeds to replay in turn (empty: a new random one each game). */
    List<Long> seedList() {
        List<Long> out = new ArrayList<>();
        for (String s : seeds.split("[,\\s]+")) {
            if (s.isBlank()) continue;
            try {
                out.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
                // (Said at the start: see Autopilot.)
            }
        }
        return out;
    }

    /** The extra flags for each game's Java, split on spaces. */
    List<String> extraFlags() {
        List<String> out = new ArrayList<>();
        for (String s : extraJvmFlags.trim().split("\\s+")) if (!s.isBlank()) out.add(s);
        return out;
    }

    static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Properties p, String key, String fallback) {
        String v = p.getProperty(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static int number(Properties p, String key, int fallback, int min, int max, Consumer<String> warn) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return fallback;
        Integer n = parseInt(v);
        if (n == null || n < min || n > max) {
            warn.accept(key + "=" + v.trim() + " geht nicht (eine ganze Zahl von " + min + " bis " + max + "): " + fallback);
            return fallback;
        }
        return n;
    }

    private static double decimal(Properties p, String key, double fallback, double min, double max, Consumer<String> warn) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            double d = Double.parseDouble(v.trim().replace(',', '.'));
            if (d >= min && d <= max) return d;
        } catch (NumberFormatException ignored) {
            // (Said below.)
        }
        warn.accept(key + "=" + v.trim() + " geht nicht (eine Zahl von " + min + " bis " + max + "): " + fallback);
        return fallback;
    }

    /** The file as written the first time: every setting with its default and what it does. */
    static final String TEMPLATE = """
            # Bob-Autopilot (Challenge Craft): Bob spielt auf diesem Server Lockout gegen sich selbst.
            # Aenderungen gelten beim naechsten Start. Zeilen mit # sind Erklaerungen.
            # Anleitung: docs/bob-bisect-server.md im Challenge-Craft-Projekt.

            # Wie viele Liga-Spiele gleichzeitig laufen. Jedes Spiel ist ein eigener Minecraft-Server
            # mit eigener, frischer Welt. auto = so viele, wie Arbeitsspeicher und Prozessor tragen.
            league_games=auto

            # Wie viele Arena-Server zusaetzlich laufen (Kampftraining: Daten fuer das Deckungsmodell).
            # 0 = keine.
            arena_servers=0

            # Laenge eines Liga-Spiels in Spielminuten (90 = ein ganzes Lockout-Spiel).
            game_minutes=90

            # Arbeitsspeicher (MB) je Liga-Spiel und je Arena-Server. Ein Spiel braucht dazu etwa
            # 1,3 GB ausserhalb dieses Wertes.
            game_memory_mb=4096
            arena_memory_mb=3072

            # So viel Platz duerfen Bobs Tagebuch (Journal) und die Spiel-Logs belegen (MB).
            # Darueber werden die aeltesten geloescht, zuerst die schon heruntergeladenen.
            journal_max_mb=3000
            game_logs_max_mb=1500

            # Alle so viele Stunden legt der Autopilot ein Paket zum Herunterladen an
            # (bob-autopilot/export). 0 = nur auf den Konsolenbefehl export.
            export_hours=12

            # ---- Erweitert: normalerweise nicht noetig ----

            # Bots je Arena-Server, und nach wie vielen Minuten eine Arena eine frische Welt bekommt.
            arena_bots=6
            arena_restart_minutes=30

            # Sicht- und Simulationsweite der Spiele (Chunks), Schwierigkeit.
            view_distance=12
            simulation_distance=8
            difficulty=hard

            # Wegsuche-Threads je Spiel.
            search_threads=4

            # Die Fabric-Server-Jar im Hauptordner. auto = die Jar, die Fabric startet (wird erkannt).
            server_jar=auto

            # Der Mods-Ordner, den die Spiele laden.
            mods_folder=mods

            # Java fuer die Spiele. auto = dasselbe Java, mit dem der Autopilot laeuft.
            java=auto

            # Zusaetzliche Java-Parameter fuer jedes Spiel (durch Leerzeichen getrennt).
            extra_jvm_flags=

            # Hoechstgroesse eines Pakets (MB) und wie viele Pakete bleiben.
            export_max_mb=25
            keep_exports=6

            # Feste Seeds, die der Reihe nach gespielt werden (durch Komma getrennt). Leer = zufaellig.
            seeds=

            # Name dieses Rechners in den Ergebnissen.
            machine=bisect
            """;

    /** The Bob-Trainer app's file (its defaults for a laptop: shorter games, every fifth a long one). */
    static final String TEMPLATE_APP = """
            # Bob-Trainer (Challenge Craft): Bob spielt auf diesem Rechner Lockout gegen sich selbst.
            # Die meisten Einstellungen macht das Dashboard (Leistung, Pause). Aenderungen hier gelten
            # beim naechsten Start. Zeilen mit # sind Erklaerungen.

            # Laenge eines Liga-Spiels in Spielminuten, und jedes wievielte Spiel ein langes ist
            # (0 = nie) mit wie vielen Minuten (90 = ein ganzes Lockout-Spiel).
            game_minutes=30
            long_game_every=5
            long_game_minutes=90

            # Arbeitsspeicher (MB) je Spiel. Ein Spiel braucht dazu etwa 1,3 GB ausserhalb dieses Wertes.
            game_memory_mb=3072

            # So viel Platz duerfen Bobs Tagebuch (Journal) und die Spiel-Logs belegen (MB).
            journal_max_mb=3000
            game_logs_max_mb=1500

            # Alle so viele Stunden ein Paket zum Hochladen (0 = nur mit dem Knopf "Export jetzt").
            export_hours=0
            export_max_mb=25
            keep_exports=6

            # ---- Erweitert: normalerweise nicht noetig ----

            # Sicht- und Simulationsweite der Spiele (Chunks), Schwierigkeit. Bob sieht so weit, wie
            # die Sichtweite reicht: kleiner macht die Spiele schneller, aber Bob blinder.
            view_distance=12
            simulation_distance=8
            difficulty=hard

            # Wegsuche-Threads je Spiel.
            search_threads=2

            # Java fuer die Spiele (auto = dasselbe Java wie der Trainer) und zusaetzliche Parameter.
            java=auto
            extra_jvm_flags=

            # Der Port des Dashboards (nur auf diesem Rechner erreichbar: 127.0.0.1).
            dashboard_port=8765

            # Wohin die Spielwelten und Minecraft kommen. auto = neben den Trainer, oder - liegt der
            # Trainer in OneDrive oder einem anderen Sync-Ordner - in einen Ordner dieses Rechners
            # (unter Windows %LOCALAPPDATA%\\Bob-Trainer). Sonst ein Ordner, mit / statt \\, z. B. D:/Bob-Welten
            work_dir=auto

            # Feste Seeds, der Reihe nach (durch Komma getrennt). Leer = jede Partie eine neue Welt.
            seeds=

            # Name dieses Rechners in den Ergebnissen.
            machine=laptop
            """;
}
