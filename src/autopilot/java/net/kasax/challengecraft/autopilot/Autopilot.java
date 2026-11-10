package net.kasax.challengecraft.autopilot;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The Bob-Autopilot: Bob's self-play league on a rented Minecraft server (a panel such as
 * BisectHosting's: files uploaded in the browser, a server jar chosen, no shell, no Python). The
 * panel starts this jar instead of Fabric's; it starts Fabric servers with Challenge Craft itself,
 * one per game, each with a fresh world, and plays the laptop trainer's league on them
 * ({@code scripts/train}): the champion's knobs against a challenger's, game after game, results,
 * journal and status into {@code bob-autopilot/}. Set up as told in {@code docs/bob-bisect-server.md}.
 *
 * <p>Safe by its make: it opens no connection (the games listen on 127.0.0.1 only, and are driven
 * through their consoles), needs no password, and changes nothing of the server's own files - only
 * its folder {@code bob-autopilot/} and the file {@code bob-autopilot.properties}. A normal server
 * is untouched by it: it runs only where the panel is set to start this jar.
 */
public final class Autopilot {
    static final String VERSION = "1.0";

    private Autopilot() {
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        PrintStream console = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        Log log = new Log(console);
        Path root = Path.of("").toAbsolutePath();
        log.info("Bob-Autopilot " + VERSION + " (Challenge Craft) im Ordner " + root);
        if (Runtime.version().feature() < 25) {
            fail(log, "Minecraft 26.3 braucht Java 25, hier laeuft Java " + Runtime.version().feature()
                    + ". Bitte im Panel (Startup) Java 25 waehlen.");
        }
        Supervisor sup;
        try {
            Path home = root.resolve("bob-autopilot");
            Files.createDirectories(home);
            log.to(home.resolve("autopilot.log"));
            boolean fresh = !Files.exists(root.resolve(Config.FILE));
            Config cfg = Config.load(root.resolve(Config.FILE), log::warn);
            if (fresh) log.info(Config.FILE + " neu angelegt: dort stehen alle Einstellungen mit Erklaerung");
            if (!eulaAccepted(root)) {
                fail(log, "Die Minecraft-EULA ist auf diesem Server noch nicht akzeptiert. Bitte den Server einmal normal (mit der "
                        + "Fabric-Jar) starten und die EULA im Panel bestaetigen, oder in eula.txt eula=true eintragen.");
            }
            if (!cfg.seeds.isBlank() && cfg.seedList().isEmpty()) log.warn("seeds=" + cfg.seeds + " enthaelt keine Zahl: zufaellige Seeds");
            Path ownJar = ownJar();
            Layout layout = Layout.find(root, cfg, ownJar);
            String java = cfg.java.equalsIgnoreCase("auto")
                    ? ProcessHandle.current().info().command().orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString())
                    : cfg.java;
            int games = leagueGames(cfg, log);
            log.info("Fabric: " + layout.fabricJar.getFileName() + ", Mod: " + layout.modJar.getFileName() + " (Version " + layout.modVersion
                    + ", Commit " + layout.modCommit + ")");
            log.info(String.format(Locale.GERMANY, "Rechner: %d Kerne, %.1f GB Arbeitsspeicher. Plan: %d Liga-Spiel(e) zu %d MB, %d Arena(s) zu %d MB",
                    Host.cores(), Host.totalMb() / 1024.0, games, cfg.gameMemoryMb, cfg.arenaServers, cfg.arenaMemoryMb));
            if (games == 0 && cfg.arenaServers == 0) {
                fail(log, "league_games=0 und arena_servers=0: nichts zu tun. Bitte in " + Config.FILE + " einstellen.");
            }
            sup = new Supervisor(root, cfg, layout, ownJar, java, log, games);
        } catch (Layout.Problem e) {
            fail(log, e.getMessage());
            return;
        } catch (IOException e) {
            fail(log, "Kann nicht in den Server-Ordner schreiben: " + e.getMessage());
            return;
        }
        readConsole(sup);
        CountDownLatch done = new CountDownLatch(1);
        Supervisor s = sup;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // (Ended from outside - the panel's stop signal, the machine going down: the loop ends
            // the servers; what is not ended in time goes with this process.)
            s.stopRequested = true;
            try {
                if (!done.await(45, TimeUnit.SECONDS)) s.killAll();
            } catch (InterruptedException e) {
                s.killAll();
            }
        }, "Bob-Autopilot shutdown"));
        // (The line a server panel waits for before it shows the server as running.)
        log.info(String.format(Locale.ROOT, "Done (%.3fs)! For help, type \"help\"", (System.nanoTime() - t0) / 1e9));
        log.info("Stand: bob-autopilot/STATUS.txt. Befehle: status, export, finish, stop (help erklaert sie)");
        try {
            sup.run();
        } finally {
            done.countDown();
        }
        System.exit(0);
    }

    /** How many league games at once: the setting, or what memory and cores allow; never more than fit in memory. */
    static int leagueGames(Config cfg, Log log) {
        long total = Host.totalMb();
        int auto = Host.autoLeagueGames(total, Host.cores(), cfg.gameMemoryMb, cfg.arenaServers, cfg.arenaMemoryMb);
        if (cfg.leagueGames.equals("auto")) return auto;
        int wanted = Config.parseInt(cfg.leagueGames);
        long need = 1536 + (long) wanted * (cfg.gameMemoryMb + Host.OVERHEAD_MB) + (long) cfg.arenaServers * (cfg.arenaMemoryMb + Host.OVERHEAD_MB);
        if (wanted > 0 && need > total) {
            int fit = (int) Math.max(1, (total - 1536 - (long) cfg.arenaServers * (cfg.arenaMemoryMb + Host.OVERHEAD_MB))
                    / (cfg.gameMemoryMb + Host.OVERHEAD_MB));
            log.warn("league_games=" + wanted + " braucht etwa " + need / 1024 + " GB, der Server hat " + total / 1024 + " GB: es laufen " + fit);
            return fit;
        }
        return wanted;
    }

    static boolean eulaAccepted(Path root) {
        try {
            for (String line : Files.readAllLines(root.resolve("eula.txt"), StandardCharsets.UTF_8)) {
                if (line.replace(" ", "").equalsIgnoreCase("eula=true")) return true;
            }
        } catch (IOException ignored) {
            // (No eula.txt: not accepted.)
        }
        return false;
    }

    /** This jar (to leave it out of what the games link to), or null when run from classes. */
    static Path ownJar() {
        try {
            Path p = Path.of(Autopilot.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The panel's console: each line typed there is a command for the loop. */
    private static void readConsole(Supervisor sup) {
        Thread t = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) sup.commands.add(line);
            } catch (IOException ignored) {
                // (No console: the panel's stop still ends the process.)
            }
        }, "Bob-Autopilot console");
        t.setDaemon(true);
        t.start();
    }

    private static void fail(Log log, String why) {
        log.warn(why);
        log.warn("Der Autopilot startet nicht. Anleitung: docs/bob-bisect-server.md (Challenge Craft).");
        System.exit(1);
    }
}
