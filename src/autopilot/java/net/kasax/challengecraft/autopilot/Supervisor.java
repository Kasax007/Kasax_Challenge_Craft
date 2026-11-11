package net.kasax.challengecraft.autopilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The autopilot's loop: league games and arenas in their slots, each a server of its own, started
 * one at a time, watched, ended and cleaned up; the results counted into the league, written down,
 * and the folder kept within its limits. Everything here runs on the one loop thread; only the
 * slow parts (stopping a server, gzipping its log, packing an export) go to worker threads, so the
 * loop never waits on them.
 */
final class Supervisor {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** The real seconds a league game may take at most beyond four times its game time (a server stuck at a crawl). */
    private static final long SLOW_GRACE_MS = 15 * 60_000L;
    /** No output and no status for this long: the server hangs. */
    private static final long HANG_MS = 10 * 60_000L;
    private static final long START_TIMEOUT_MS = 15 * 60_000L;

    enum State { IDLE, STARTING, SPAWNING, PLAYING, ARENA, STOPPING }

    /** A place for one server at a time, and what it is doing. */
    final class Slot {
        final GameServer server;
        State state = State.IDLE;
        long since, retryAt, readyAt;
        League.Game game;
        long gameNo;
        /** The server's tick when the game began (from Bob's status file), for its progress. */
        long tickAtStart = -1;
        /** This game's length in game minutes (every so many games a long one). */
        int gameMinutes;
        /** The league slot's number (1, 2, ...), 0 for an arena. */
        int leagueNo;
        Future<?> stopping;

        Slot(GameServer server) {
            this.server = server;
        }

        String name() {
            return server.name;
        }
    }

    final Path root, home, work, leagueDir, journal, games, exports, models;
    /** The folder of Minecraft's own server jar, one for all games (each game's Fabric is its own). */
    final Path minecraftDir;
    /** The games' folders and Minecraft are outside the trainer's folder (work_dir, or the trainer in a synced folder). */
    final boolean relocated;
    /** After a start that failed, no other start for a while: one failure is reported, not one per slot. */
    private long startsBlockedUntil;
    final Config cfg;
    final Layout layout;
    final Path ownJar;
    final String javaCommand;
    final Log log;
    final League league;
    final List<Slot> slots = new ArrayList<>();
    final long startedAt = System.currentTimeMillis();
    final int leagueSlots;
    final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Bob-Autopilot worker");
        t.setDaemon(true);
        return t;
    });
    private final Random rng = new Random();
    private final List<Long> seedList;
    private int seedIndex;

    // What has happened, for the status.
    long gamesDone, gamesFailed, gameCounter;
    final Deque<Map<String, Object>> lastGames = new ArrayDeque<>();
    final Deque<String> events = new ArrayDeque<>();
    private final Deque<Long> recentFailures = new ArrayDeque<>();
    long pausedUntil;
    volatile boolean stopRequested, finishRequested;
    /** Paused (the app's button): no new games; kept across a restart (bob-autopilot/paused). */
    volatile boolean paused;
    /** League games at once now (the app's power setting); at most the slots there are. */
    volatile int activeLeague;
    /** The app's power setting: niedrig, mittel, hoch (null: not the app). */
    volatile String power;
    /** The latest status as JSON, made by the loop each second (the dashboard serves it). */
    volatile String snapshot = "{}";
    /** The last export's file and when (for the dashboard). */
    volatile Path lastExportFile;
    volatile long lastExportAt;
    private long lastStatus, lastReport, lastHousekeeping, lastSummary, lastExport;
    volatile long diskBytes = -1;
    Future<?> exporting;
    final Export export;

    Supervisor(Path root, Config cfg, Layout layout, Path ownJar, String javaCommand, Log log, int leagueSlots) throws IOException {
        this.root = root;
        this.cfg = cfg;
        this.layout = layout;
        this.ownJar = ownJar;
        this.javaCommand = javaCommand;
        this.log = log;
        this.leagueSlots = leagueSlots;
        home = root.resolve("bob-autopilot");
        Path local = localBase(root, cfg);
        relocated = local != null;
        work = relocated ? local.resolve("work") : home.resolve("work");
        // (Beside the trainer where earlier versions kept it: a Minecraft downloaded before is used.)
        minecraftDir = relocated ? local.resolve("minecraft") : root.resolve(".fabric").resolve("server");
        leagueDir = home.resolve("league");
        journal = home.resolve("journal");
        games = home.resolve("games");
        exports = home.resolve("export");
        models = home.resolve("models");
        for (Path p : List.of(home, work, leagueDir, journal, games, exports, models)) Files.createDirectories(p);
        seedList = cfg.seedList();
        league = loadLeague();
        saveLeague();
        for (int i = 1; i <= leagueSlots; i++) {
            Slot slot = new Slot(new GameServer("liga-" + i, GameServer.Kind.LEAGUE, work.resolve("liga-" + i), work));
            slot.leagueNo = i;
            slots.add(slot);
        }
        activeLeague = leagueSlots;
        paused = Files.exists(home.resolve("paused"));
        for (int i = 1; i <= cfg.arenaServers; i++) {
            slots.add(new Slot(new GameServer("arena-" + i, GameServer.Kind.ARENA, work.resolve("arena-" + i), work)));
        }
        export = new Export(this);
        lastExport = System.currentTimeMillis();
        // (The first short status on the console ten minutes in: at the start there is nothing to say yet.)
        lastSummary = lastExport;
    }

    /**
     * Where the games' folders and Minecraft go when not beside the trainer: the folder set as
     * work_dir; else, with the trainer in OneDrive or another synced folder, a folder of this
     * computer's own (the sync program opens and marks every file a game writes, and on Windows a
     * world then cannot be deleted, nor a file replaced). Null: beside it, in bob-autopilot/work.
     */
    static Path localBase(Path root, Config cfg) {
        if (cfg.workDir != null && !cfg.workDir.isBlank() && !cfg.workDir.equalsIgnoreCase("auto")) {
            return root.resolve(cfg.workDir.trim()).toAbsolutePath().normalize();
        }
        if (!Disk.cloudSynced(root)) return null;
        // (One folder for each trainer folder: two copies of the trainer never share their games.)
        String id = Integer.toHexString(root.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT).hashCode());
        return Disk.localDataDir().resolve("trainer-" + id);
    }

    /** Minecraft's own server jar for all games (null: the Fabric jar is no downloading launcher). */
    Path minecraftJar() {
        return layout.minecraftJar(minecraftDir);
    }

    /** Minecraft is there (downloaded once, by the first game), or nothing is to be downloaded. */
    boolean minecraftReady() {
        Path jar = minecraftJar();
        return jar == null || Files.isRegularFile(jar) && Disk.sizeOf(jar) > 10_000_000L;
    }

    // ---- the league's state -------------------------------------------------------------------------

    /** Our own state after a restart; else the league the mod brought from the laptop (its champion); else the defaults. */
    private League loadLeague() {
        Path own = leagueDir.resolve("state.json");
        try {
            if (Files.exists(own)) {
                // (The trainer's layout: the league under "league", the counts beside it.)
                Map<String, Object> st = Json.map(Json.parse(Files.readString(own, StandardCharsets.UTF_8)));
                if (!(st.get("league") instanceof Map)) throw new IllegalArgumentException("no \"league\" in it");
                League l = League.fromJson(Json.map(st.get("league")), true, rng);
                gamesDone = (long) Json.num(st.get("league_games"), 0);
                gamesFailed = (long) Json.num(st.get("league_failed"), 0);
                gameCounter = (long) Json.num(st.get("game_counter"), gamesDone + gamesFailed);
                log.info("Liga geladen: " + gamesDone + " Spiele bisher, Champion " + Knobs.text(l.champion)
                        + (l.challenger == null ? "" : ", Herausforderer " + Knobs.text(l.challenger) + " nach " + l.games.size() + " Spiel(en)"));
                return l;
            }
        } catch (IOException | RuntimeException e) {
            // (Kept aside, not overwritten: it may be put right by hand.)
            try {
                Files.move(own, own.resolveSibling("state-unreadable-" + System.currentTimeMillis() + ".json"));
            } catch (IOException ignored) {
                // (Then it is overwritten.)
            }
            log.warn("Liga-Stand " + own + " nicht lesbar (" + e.getMessage() + "): beginne mit dem Stand aus der Mod");
        }
        String shipped = Layout.entry(layout.modJar, "bob-models/state.json");
        if (shipped != null) {
            try {
                Map<String, Object> st = Json.map(Json.parse(shipped));
                if (st.get("league") != null) {
                    League l = League.fromJson(Json.map(st.get("league")), false, rng);
                    log.info("Liga beginnt mit dem Champion vom Laptop-Training: " + Knobs.text(l.champion));
                    return l;
                }
            } catch (RuntimeException e) {
                log.warn("Der Liga-Stand in der Mod ist nicht lesbar: " + e.getMessage());
            }
        }
        log.info("Liga beginnt mit Bobs Standard-Knoepfen");
        return new League(rng);
    }

    void saveLeague() {
        try {
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("league", league.toJson());
            st.put("league_games", gamesDone);
            st.put("league_failed", gamesFailed);
            st.put("game_counter", gameCounter);
            writeAtomically(leagueDir.resolve("state.json"), Json.pretty(st));
        } catch (IOException e) {
            log.warn("Liga-Stand nicht gespeichert: " + e.getMessage());
        }
    }

    static void writeAtomically(Path file, String text) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        // (Tried again for a few seconds: a sync program or a virus scanner may hold the file a moment.)
        Disk.retrying(() -> {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }

    private void appendLine(Path file, Map<String, Object> o) {
        try {
            Files.writeString(file, Json.write(o) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn(file.getFileName() + " nicht geschrieben: " + e.getMessage());
        }
    }

    // ---- the loop -----------------------------------------------------------------------------------

    void run() {
        event("Autopilot gestartet: " + leagueSlots + " Liga-Spiel(e) gleichzeitig, " + cfg.arenaServers + " Arena(s), "
                + cfg.gameMinutes + " Spielminuten je Spiel");
        if (relocated) {
            event(Disk.cloudSynced(root)
                    ? "Der Trainer-Ordner liegt in OneDrive (oder einem anderen Sync-Ordner): die Spielwelten und Minecraft liegen deshalb in "
                    + work.getParent() + ". Dein Liga-Stand, Journal und Export bleiben in " + home + "."
                    : "Spielwelten und Minecraft liegen in " + work.getParent() + " (work_dir)");
            // (The work folder of an earlier start beside the trainer: thrown-away worlds, gone now.)
            Path old = home.resolve("work");
            if (Files.exists(old, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                workers.submit(() -> {
                    try {
                        Disk.deleteTreeRetrying(old, home);
                        log.info("Alter Arbeitsordner geloescht: " + old);
                    } catch (IOException e) {
                        log.warn("Alter Arbeitsordner " + old + " nicht ganz geloescht (" + Disk.describe(e) + "): er kann von Hand geloescht werden");
                    }
                });
            }
        }
        while (true) {
            handleCommands();
            if (stopRequested) break;
            long now = System.currentTimeMillis();
            for (Slot s : slots) {
                try {
                    step(s, now);
                } catch (RuntimeException | IOException e) {
                    failure(s, "Fehler im Autopilot: " + e);
                    beginStop(s);
                }
            }
            if (finishRequested && slots.stream().allMatch(s -> s.state == State.IDLE)) {
                event("Alle Spiele zu Ende gespielt: Autopilot endet");
                break;
            }
            housekeeping(now);
            try {
                snapshot = Json.write(Report.json(this));
            } catch (RuntimeException e) {
                // (The dashboard keeps the last one.)
            }
            sleep(1000);
        }
        shutdown();
    }

    private void step(Slot s, long now) throws IOException {
        GameServer g = s.server;
        switch (s.state) {
            case IDLE -> {
                if (finishRequested || paused || now < s.retryAt || now < pausedUntil || now < startsBlockedUntil) return;
                // (The app's power setting: only so many league games at once.)
                if (s.leagueNo > activeLeague) return;
                // (One server starting at a time: the first one also downloads Minecraft, if need be.)
                if (slots.stream().anyMatch(o -> o.state == State.STARTING)) return;
                // (Minecraft not yet downloaded: the first game's launcher downloads it, alone - a game
                // that started meanwhile would find half a file.)
                if (!minecraftReady() && slots.stream().anyMatch(o -> o != s && o.state != State.IDLE)) return;
                start(s, now);
            }
            case STARTING -> {
                if (g.ready()) {
                    s.readyAt = now;
                    if (g.kind == GameServer.Kind.LEAGUE) {
                        for (String name : s.game.names()) g.command("challengecraft_bot spawn " + name);
                        s.state = State.SPAWNING;
                        s.since = now;
                    } else {
                        g.command("challengecraft_bot arena start " + cfg.arenaBots + " explore");
                        s.state = State.ARENA;
                        s.since = now;
                        log.info(g.name + ": Arena laeuft (" + cfg.arenaBots + " Bots)");
                    }
                } else if (!g.alive()) {
                    g.drainOutput(2000);
                    failure(s, "Server beim Start beendet" + why(g));
                    beginStop(s);
                } else if (now - s.since > START_TIMEOUT_MS) {
                    failure(s, "Server kam in 15 Minuten nicht hoch" + why(g));
                    beginStop(s);
                }
            }
            case SPAWNING -> {
                if (!g.alive()) {
                    g.drainOutput(2000);
                    failure(s, "Server beendet" + why(g));
                    beginStop(s);
                } else if (now - s.since >= 3000) {
                    int secs = s.gameMinutes * 60;
                    g.command("difficulty " + cfg.difficulty);
                    // (Sprint: as fast as the machine goes - the bots count their searches in game ticks then.)
                    g.command("tick sprint " + (secs * 20 + 400));
                    g.command("challengecraft_bot versus " + secs + " " + cfg.difficulty + " " + s.game.sides());
                    s.state = State.PLAYING;
                    s.since = now;
                    s.tickAtStart = -1;
                }
            }
            case PLAYING -> {
                if (s.tickAtStart < 0) {
                    Map<String, Object> st = g.status();
                    if (st != null) s.tickAtStart = (long) Json.num(st.get("tick"), -1);
                }
                // (A server that ended: its output read to the end first - the result may be in its last lines.)
                if (g.result() == null && !g.alive()) g.drainOutput(2000);
                if (g.result() != null) {
                    finishGame(s, now);
                    beginStop(s);
                } else if (!g.alive()) {
                    failure(s, "Server waehrend des Spiels beendet" + why(g));
                    beginStop(s);
                } else if (hangs(g, now)) {
                    failure(s, "Server haengt (10 Minuten ohne Ausgabe)" + why(g));
                    beginStop(s);
                } else if (now - s.since > s.gameMinutes * 60_000L * 4 + SLOW_GRACE_MS) {
                    failure(s, "Spiel zu langsam (unter einem Viertel der Echtzeit)" + why(g));
                    beginStop(s);
                }
            }
            case ARENA -> {
                Map<String, Object> st = g.status();
                if (!g.alive()) {
                    g.drainOutput(2000);
                    failure(s, "Arena-Server beendet" + why(g));
                    beginStop(s);
                } else if (now - s.readyAt > 3 * 60_000L && (st == null || Json.num(st.get("age"), 999) > 180)) {
                    failure(s, "Arena haengt (3 Minuten ohne Status)" + why(g));
                    beginStop(s);
                } else if (now - s.readyAt > cfg.arenaRestartMinutes * 60_000L) {
                    log.info(g.name + ": frische Welt fuer die Arena (nach " + cfg.arenaRestartMinutes + " min)");
                    beginStop(s);
                }
            }
            case STOPPING -> {
                if (s.stopping == null || s.stopping.isDone()) {
                    s.stopping = null;
                    s.state = State.IDLE;
                    s.game = null;
                }
            }
        }
    }

    private boolean hangs(GameServer g, long now) {
        if (now - g.lastOutput() < HANG_MS) return false;
        Map<String, Object> st = g.status();
        return st == null || Json.num(st.get("age"), 1e9) * 1000 > HANG_MS;
    }

    private static String why(GameServer g) {
        return g.hint() == null ? "" : ": " + g.hint();
    }

    /** A new server in this slot: a league game (the next of the league) or an arena. */
    private void start(Slot s, long now) throws IOException {
        GameServer g = s.server;
        long seed;
        if (g.kind == GameServer.Kind.LEAGUE) {
            Long fixed = seedList.isEmpty() ? null : seedList.get(seedIndex++ % seedList.size());
            s.game = league.nextGame(fixed);
            s.gameNo = ++gameCounter;
            seed = s.game.seed();
            // (Every so many games a long one: the endgame is learnt from too.)
            s.gameMinutes = cfg.longGameEvery > 0 && s.gameNo % cfg.longGameEvery == 0 ? cfg.longGameMinutes : cfg.gameMinutes;
        } else {
            seed = rng.nextLong();
        }
        try {
            g.prepare(layout, ownJar, GameServer.properties(cfg, g.kind, seed, g.name), log, minecraftDir);
            g.start(command(g.kind), environment(g));
        } catch (IOException e) {
            failure(s, "Start nicht moeglich: " + Disk.describe(e));
            s.state = State.IDLE;
            s.retryAt = now + 60_000;
            startsBlockedUntil = now + 20_000;
            return;
        }
        s.state = State.STARTING;
        s.since = now;
        if (g.kind == GameServer.Kind.LEAGUE) {
            log.info(g.name + ": Spiel " + s.gameNo + " startet (Seed " + seed + ", " + s.gameMinutes + " Spielminuten): " + s.game.names()[0] + " " + s.game.knobs()[0]
                    + " gegen " + s.game.names()[1] + " " + s.game.knobs()[1]);
        } else {
            log.info(g.name + ": Arena-Server startet");
        }
    }

    /** How a game's Java is started: its memory, threads kept in bounds (a panel counts them), the Fabric jar. */
    List<String> command(GameServer.Kind kind) {
        int mem = kind == GameServer.Kind.LEAGUE ? cfg.gameMemoryMb : cfg.arenaMemoryMb;
        // (A container may show the host's cores: the threads a Java makes for them are capped at eight's worth.)
        int cores = Math.min(Host.cores(), 8);
        List<String> c = new ArrayList<>(List.of(javaCommand, "-Xms" + Math.min(1024, mem) + "M", "-Xmx" + mem + "M", "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=200", "-XX:ParallelGCThreads=" + cores, "-XX:ConcGCThreads=" + Math.max(1, cores / 4),
                "-Dmax.bg.threads=" + Math.max(2, cores - 1), "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"));
        if (cfg.tuned) {
            // (Round 3: the usual flags for a Minecraft server's G1 - short pauses, a young
            // generation large enough for a chunk-generating server's garbage.)
            c.addAll(List.of("-XX:+ParallelRefProcEnabled", "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC",
                    "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M", "-XX:G1ReservePercent=20",
                    "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4", "-XX:InitiatingHeapOccupancyPercent=15",
                    "-XX:G1MixedGCLiveThresholdPercent=90", "-XX:SurvivorRatio=32", "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1"));
        }
        c.addAll(cfg.extraFlags());
        Path mc = minecraftJar();
        // (Fabric's downloading launcher: Minecraft's jar the one for all games - see GameServer.prepare.)
        if (mc != null) c.add("-Dfabric.installer.server.gameJar=" + mc.toAbsolutePath());
        c.add("-jar");
        c.add(layout.fabricJar.getFileName().toString());
        c.add("nogui");
        return c;
    }

    /** What the mod reads from its environment: what the laptop trainer gives its servers too. */
    Map<String, String> environment(GameServer g) {
        Map<String, String> e = new LinkedHashMap<>();
        // (Self-play at the machine's pace: Bob's searches count game ticks, not the clock.)
        e.put("CHALLENGECRAFT_BOT_SIM", "1");
        e.put("CHALLENGECRAFT_BOT_SEARCH_THREADS", Integer.toString(cfg.searchThreads));
        e.put("BOB_JOURNAL", journal.toAbsolutePath().toString());
        e.put("BOB_MODELS", models.toAbsolutePath().toString());
        e.put("BOB_STATUS_DIR", g.dir.toAbsolutePath().toString());
        // (The server ends itself when the autopilot is gone - nothing is left running.)
        e.put("BOB_PARENT_PID", Long.toString(ProcessHandle.current().pid()));
        if (g.kind == GameServer.Kind.LEAGUE) {
            // (Lockout on from the world's start, and played to its full time: the endgame is learnt from too.)
            e.put("CHALLENGECRAFT_TEST_CHALLENGES", "40");
            e.put("CHALLENGECRAFT_FULL_BOARD", "1");
        }
        return e;
    }

    // ---- a game's end -----------------------------------------------------------------------------

    private void finishGame(Slot s, long now) {
        Map<String, Object> result;
        try {
            result = Json.map(Json.parse(s.server.result()));
        } catch (RuntimeException e) {
            failure(s, "Ergebnis nicht lesbar: " + e.getMessage());
            return;
        }
        League.Outcome outcome = league.record(s.game, result);
        gamesDone++;
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("time", now / 1000.0);
        line.put("machine", cfg.machine);
        line.put("slot", s.name());
        line.put("game", s.gameNo);
        line.put("code", code());
        line.put("champion", s.game.champion());
        line.put("challenger", s.game.challenger());
        line.put("counted", outcome.counted());
        line.put("real_secs", Math.round((now - s.since) / 1000.0));
        line.put("result", result);
        appendLine(leagueDir.resolve("results.jsonl"), line);
        Map<String, Object> summary = summary(s, result, now);
        lastGames.addFirst(summary);
        while (lastGames.size() > 12) lastGames.removeLast();
        event(s.name() + ": Spiel " + s.gameNo + " fertig - " + summary.get("text"));
        if (outcome.verdict() != null) onVerdict(outcome.verdict());
        saveLeague();
    }

    /** One game in a line: the champion's and the challenger's tiles and deaths, and its first tile. */
    private Map<String, Object> summary(Slot s, Map<String, Object> result, long now) {
        Map<String, Object> champ = null, chall = null;
        for (Object o : Json.list(result.get("bots"))) {
            Map<String, Object> b = Json.map(o);
            String p = Json.str(b.get("params"), "");
            if (champ == null && Knobs.same(p, s.game.champion())) champ = b;
            else if (chall == null && Knobs.same(p, s.game.challenger())) chall = b;
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("time", now / 1000.0);
        o.put("slot", s.name());
        o.put("game", s.gameNo);
        o.put("seed", result.get("seed"));
        o.put("secs", result.get("secs"));
        o.put("real_min", Math.round((now - s.since) / 6000.0) / 10.0);
        String text;
        if (champ != null && chall != null) {
            o.put("champion", List.of(champ.get("tiles"), champ.get("deaths"), champ.get("first")));
            o.put("challenger", List.of(chall.get("tiles"), chall.get("deaths"), chall.get("first")));
            text = String.format(Locale.ROOT, "Champion %s, %s; Herausforderer %s, %s (%.0f echte Minuten)",
                    count(champ.get("tiles"), "Feld", "Felder"), count(champ.get("deaths"), "Tod", "Tode"),
                    count(chall.get("tiles"), "Feld", "Felder"), count(chall.get("deaths"), "Tod", "Tode"), (now - s.since) / 60000.0);
        } else {
            text = "Ergebnis ohne passende Knoepfe";
        }
        o.put("text", text);
        return o;
    }

    /** "1 Feld", "3 Felder". */
    private static String count(Object n, String one, String many) {
        double v = Json.num(n, 0);
        return Knobs.format(v) + " " + (v == 1 ? one : many);
    }

    private void onVerdict(Map<String, Object> v) {
        if ("promoted".equals(v.get("verdict"))) {
            Map<String, Object> champ = new LinkedHashMap<>();
            champ.put("params", v.get("challenger"));
            champ.put("version", LocalDateTime.now().format(STAMP));
            champ.put("games", v.get("games"));
            champ.put("mean_diff", v.get("mean_diff"));
            champ.put("machine", cfg.machine);
            champ.put("history", league.history.subList(Math.max(0, league.history.size() - 20), league.history.size()));
            try {
                writeAtomically(leagueDir.resolve("champion.json"), Json.pretty(champ));
            } catch (IOException e) {
                log.warn("champion.json nicht geschrieben: " + e.getMessage());
            }
            event(String.format(Locale.ROOT, "Liga: neuer Champion %s (%+.1f Felder je Spiel ueber %s Spiele)",
                    v.get("challenger"), Json.num(v.get("mean_diff"), 0), v.get("games")));
        } else {
            event(String.format(Locale.ROOT, "Liga: Herausforderer %s abgelehnt (%+.1f ueber %s Spiele)",
                    v.get("challenger"), Json.num(v.get("mean_diff"), 0), v.get("games")));
        }
    }

    Map<String, Object> code() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("jar", layout.modJar.getFileName().toString());
        c.put("version", layout.modVersion);
        c.put("commit", layout.modCommit);
        c.put("sha", layout.modSha);
        return c;
    }

    /** Something went wrong in a slot: written down, and after five in half an hour a pause. */
    private void failure(Slot s, String reason) {
        long now = System.currentTimeMillis();
        if (s.server.kind == GameServer.Kind.LEAGUE && s.game != null) gamesFailed++;
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("time", now / 1000.0);
        o.put("machine", cfg.machine);
        o.put("slot", s.name());
        o.put("state", s.state.name());
        if (s.game != null) o.put("seed", s.game.seed());
        o.put("reason", reason);
        o.put("code", code());
        appendLine(leagueDir.resolve("failures.jsonl"), o);
        log.warn(s.name() + ": " + reason);
        event(s.name() + ": " + reason);
        s.retryAt = now + 60_000;
        recentFailures.addLast(now);
        while (!recentFailures.isEmpty() && now - recentFailures.peekFirst() > 30 * 60_000L) recentFailures.removeFirst();
        if (recentFailures.size() >= 5 && pausedUntil < now) {
            pausedUntil = now + 15 * 60_000L;
            event("Fuenf Fehler in 30 Minuten: 15 Minuten Pause. Die Logs liegen in bob-autopilot/games.");
            recentFailures.clear();
        }
    }

    /** Ends the slot's server off the loop; its log is kept (league games, and any server that failed). */
    private void beginStop(Slot s) {
        if (s.state == State.STOPPING) return;
        boolean keepLog = s.server.kind == GameServer.Kind.LEAGUE || s.server.hint() != null;
        // (A game that ended without its result - a stop, a crash - says so in its log's name.)
        String base = LocalDateTime.now().format(STAMP) + "_" + s.name() + (s.game != null ? "_spiel" + s.gameNo + "_" + s.game.seed() : "")
                + (s.game != null && s.server.result() == null ? "_abgebrochen" : "");
        Path keepAs = keepLog ? games.resolve(base + ".log.gz") : null;
        GameServer g = s.server;
        s.state = State.STOPPING;
        s.since = System.currentTimeMillis();
        // (Asked to end - the panel's stop button - the servers get less time: their worlds are thrown away anyway.)
        long grace = stopRequested ? 8_000 : 20_000;
        s.stopping = workers.submit(() -> {
            g.stop(grace);
            try {
                g.cleanUp(keepAs);
            } catch (IOException e) {
                log.warn(g.name + ": Aufraeumen unvollstaendig: " + e.getMessage());
            }
        });
    }

    // ---- commands at the console -----------------------------------------------------------------------

    private void handleCommands() {
        String c;
        while ((c = commands.poll()) != null) {
            String cmd = c.trim().toLowerCase(Locale.ROOT);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            switch (cmd) {
                case "" -> {
                }
                case "stop", "end", "exit", "quit" -> {
                    event("Stop: die Server werden beendet (laufende Spiele zaehlen nicht)");
                    stopRequested = true;
                }
                case "finish", "fertig" -> {
                    finishRequested = true;
                    event("Fertig spielen: keine neuen Spiele mehr, danach endet der Autopilot");
                }
                case "status" -> log.plain(Report.text(this));
                case "pause" -> setPaused(true, false);
                case "pausenow", "pause-now", "pause now" -> setPaused(true, true);
                case "resume", "weiter" -> setPaused(false, false);
                case "power low", "power niedrig" -> setPower("niedrig");
                case "power mid", "power mittel" -> setPower("mittel");
                case "power high", "power hoch" -> setPower("hoch");
                case "export" -> startExport("auf Befehl");
                case "help", "hilfe", "?" -> log.plain("""
                        Befehle des Bob-Autopilots:
                          status   der Stand (auch in bob-autopilot/STATUS.txt)
                          export   jetzt ein Paket zum Herunterladen anlegen (bob-autopilot/export)
                          finish   die laufenden Spiele zu Ende spielen, dann aufhoeren
                          stop     sofort aufhoeren (der Stopp-Knopf des Panels tut dasselbe)""");
                default -> log.plain("Unbekannter Befehl: " + c.trim() + " (help zeigt die Befehle)");
            }
        }
    }

    /**
     * Paused: no new games ({@code now}: the running ones stopped at once and thrown away - they
     * count for nothing, everything else stays). Kept across a restart.
     */
    void setPaused(boolean on, boolean now) {
        paused = on;
        try {
            if (on) Files.writeString(home.resolve("paused"), "1", StandardCharsets.UTF_8);
            else Files.deleteIfExists(home.resolve("paused"));
        } catch (IOException e) {
            log.warn("Pause nicht gespeichert: " + e.getMessage());
        }
        if (!on) {
            event("Weiter: neue Spiele starten wieder");
            return;
        }
        if (!now) {
            event("Pause: die laufenden Spiele werden zu Ende gespielt, keine neuen");
            return;
        }
        int stopped = 0;
        for (Slot s : slots) {
            if (s.state == State.STARTING || s.state == State.SPAWNING || s.state == State.PLAYING || s.state == State.ARENA) {
                beginStop(s);
                stopped++;
            }
        }
        event("Sofort pausiert: " + stopped + " laufende(s) Spiel(e) beendet und verworfen");
    }

    /** The app's power setting: how many league games at once (the slots are made for the highest). */
    void setPower(String level) {
        power = level;
        activeLeague = "hoch".equals(level) ? Math.max(1, leagueSlots) : Math.max(1, Math.min(leagueSlots, Autopilot.powerGames(level, cfg)));
        try {
            Files.writeString(home.resolve("power"), level, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Leistung nicht gespeichert: " + e.getMessage());
        }
        event("Leistung " + level + ": " + activeLeague + " Spiel(e) gleichzeitig");
    }

    // ---- status, limits, exports ------------------------------------------------------------------------

    private void housekeeping(long now) {
        if (now - lastStatus >= 15_000) {
            lastStatus = now;
            Report.writeJson(this);
        }
        if (now - lastReport >= 60_000) {
            lastReport = now;
            Report.writeText(this);
        }
        if (now - lastHousekeeping >= 5 * 60_000L) {
            lastHousekeeping = now;
            limits();
        }
        if (now - lastSummary >= 10 * 60_000L) {
            lastSummary = now;
            log.plain(Report.shortText(this));
        }
        if (cfg.exportHours > 0 && now - lastExport >= (long) (cfg.exportHours * 3_600_000L)) startExport("regelmaessig");
    }

    /** The journal, the game logs and the exports kept within their limits. */
    private void limits() {
        List<Path> gone = Disk.prune(journal, p -> p.getFileName().toString().endsWith(".jsonl"), cfg.journalMaxMb * (1L << 20),
                export::wasExported, 10 * 60_000L);
        long lost = gone.stream().filter(p -> !export.wasExported(p)).count();
        if (!gone.isEmpty()) {
            log.info("Journal ueber " + cfg.journalMaxMb + " MB: " + gone.size() + " aelteste Datei(en) geloescht"
                    + (lost > 0 ? ", davon " + lost + " noch nicht heruntergeladen" : ""));
        }
        gone = Disk.prune(games, p -> true, cfg.gameLogsMaxMb * (1L << 20), export::wasExported, 60_000L);
        if (!gone.isEmpty()) log.info("Spiel-Logs ueber " + cfg.gameLogsMaxMb + " MB: " + gone.size() + " aelteste geloescht");
        export.keepNewest(cfg.keepExports);
        workers.submit(() -> diskBytes = Disk.size(home) + (relocated ? Disk.size(work) + Disk.size(minecraftDir) : 0));
    }

    void startExport(String why) {
        lastExport = System.currentTimeMillis();
        if (exporting != null && !exporting.isDone()) {
            log.info("Ein Paket wird schon gepackt");
            return;
        }
        exporting = workers.submit(() -> {
            try {
                Path zip = export.write();
                lastExportFile = zip;
                lastExportAt = System.currentTimeMillis();
                log.info("Paket zum Herunterladen (" + why + "): bob-autopilot/export/" + zip.getFileName() + " (" + Disk.human(Files.size(zip)) + ")");
            } catch (IOException | RuntimeException e) {
                log.warn("Paket nicht angelegt: " + e);
            }
        });
    }

    void event(String text) {
        String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")) + "  " + text;
        events.addFirst(line);
        while (events.size() > 30) events.removeLast();
        log.info(text);
    }

    // ---- the end ----------------------------------------------------------------------------------------

    /** All servers ended (at most about half a minute), the last status written. */
    void shutdown() {
        stopRequested = true;
        List<Future<?>> waits = new ArrayList<>();
        for (Slot s : slots) {
            if (s.state != State.IDLE && s.state != State.STOPPING) beginStop(s);
            if (s.stopping != null) waits.add(s.stopping);
        }
        long until = System.currentTimeMillis() + 40_000;
        for (Future<?> f : waits) {
            try {
                f.get(Math.max(1, until - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // (Ended below at the latest.)
            }
        }
        for (Slot s : slots) {
            s.server.kill();
            s.state = State.IDLE;
        }
        saveLeague();
        Report.writeJson(this);
        Report.writeText(this);
        log.info("Autopilot beendet (" + gamesDone + " Spiele fertig, " + gamesFailed + " abgebrochen)");
        workers.shutdown();
    }

    /** The servers ended at once (the process is being ended: the panel's kill, or the machine going down). */
    void killAll() {
        for (Slot s : slots) s.server.kill();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
