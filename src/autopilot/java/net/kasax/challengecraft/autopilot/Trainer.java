package net.kasax.challengecraft.autopilot;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The Bob-Trainer app (round 3): the autopilot's league on the person's own computer, started by a
 * double-click, with a dashboard in the browser instead of a server panel's console.
 *
 * <pre>
 * Bob-Trainer/            the app's folder
 *   app/bob-trainer.jar   this
 *   server/               the autopilot's root: the Fabric launcher jar, mods/, eula.txt once accepted,
 *                         bob-autopilot.properties, bob-autopilot/ (state, journal, logs, exports, work)
 *   java/                 a Java 25 the start script downloaded, if the computer had none
 * </pre>
 *
 * One instance at a time (a second start opens the running one's dashboard). The games wait until
 * the Minecraft EULA is accepted in the dashboard; then the league runs until paused or ended, and
 * picks up where it was at the next start (the league's state is saved after every game; a game
 * running when the app ends is lost, nothing else). Every game's Java ends with the app
 * (BOB_PARENT_PID, and a shutdown hook).
 */
public final class Trainer {
    private Trainer() {
    }

    public static void main(String[] args) {
        PrintStream console = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        Log log = new Log(console);
        Path app = appDir(args);
        Path root = app.resolve("server"), home = root.resolve("bob-autopilot");
        if (Runtime.version().feature() < 25) {
            log.warn("Minecraft 26.3 braucht Java 25, hier laeuft Java " + Runtime.version().feature() + ". Bitte den Trainer ueber das Startskript starten.");
            System.exit(1);
        }
        FileLock lock;
        try {
            Files.createDirectories(home);
            log.to(home.resolve("autopilot.log"));
            FileChannel ch = FileChannel.open(home.resolve("app.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = ch.tryLock();
        } catch (IOException e) {
            log.warn("Der Ordner " + home + " ist nicht beschreibbar: " + e.getMessage());
            System.exit(1);
            return;
        }
        if (lock == null) {
            // (Already running: its dashboard, and nothing else.)
            try {
                String url = Files.readString(home.resolve("dashboard.url"), StandardCharsets.UTF_8).trim();
                log.info("Der Bob-Trainer laeuft schon: " + url);
                browse(url, log);
            } catch (IOException e) {
                log.info("Der Bob-Trainer laeuft schon.");
            }
            System.exit(0);
            return;
        }
        log.info("Bob-Trainer " + Autopilot.VERSION + " im Ordner " + app);
        Config cfg;
        Dashboard dash;
        try {
            cfg = Config.load(root.resolve(Config.FILE), log::warn, Config.TEMPLATE_APP);
            dash = Dashboard.start(root, home, cfg.dashboardPort);
        } catch (IOException | RuntimeException e) {
            log.warn("Dashboard nicht gestartet: " + e.getMessage());
            System.exit(1);
            return;
        }
        log.info("Dashboard: " + dash.url);
        browse(dash.url, log);
        // The EULA: the person accepts it in the dashboard; until then no Minecraft runs.
        if (!Autopilot.eulaAccepted(root)) {
            dash.phase = "wartet auf die EULA";
            log.info("Bitte im Dashboard die Minecraft-EULA lesen und akzeptieren: " + dash.url);
            while (!Autopilot.eulaAccepted(root)) {
                if (dash.quit) {
                    log.info("Beendet, bevor die EULA akzeptiert war");
                    dash.stop();
                    System.exit(0);
                }
                sleep(500);
            }
            log.info("EULA akzeptiert");
        }
        dash.phase = "startet";
        Supervisor sup;
        try {
            Path ownJar = Autopilot.ownJar();
            Layout layout = Layout.find(root, cfg, ownJar);
            String java = cfg.java.equalsIgnoreCase("auto")
                    ? ProcessHandle.current().info().command().orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString())
                    : cfg.java;
            String level = "mittel";
            try {
                String saved = Files.readString(home.resolve("power"), StandardCharsets.UTF_8).trim();
                if (List.of("niedrig", "mittel", "hoch").contains(saved)) level = saved;
            } catch (IOException ignored) {
                // (First start: mittel.)
            }
            // (league_games as a number: at most that many - on "hoch" exactly that many.)
            Integer fixed = cfg.leagueGames.equals("auto") ? null : Config.parseInt(cfg.leagueGames);
            int slots = fixed != null && fixed > 0 ? fixed : Autopilot.powerGames("hoch", cfg);
            log.info(String.format(Locale.GERMANY, "Rechner: %d Kerne, %.1f GB. Bis zu %d Spiel(e) gleichzeitig (Leistung %s: %d)", Host.cores(),
                    Host.totalMb() / 1024.0, slots, level, Autopilot.powerGames(level, cfg)));
            sup = new Supervisor(root, cfg, layout, ownJar, java, log, slots);
            sup.setPower(level);
        } catch (Layout.Problem | IOException | RuntimeException e) {
            log.warn("Der Trainer kann nicht starten: " + e.getMessage());
            dash.phase = "Fehler: " + e.getMessage();
            // (The page says why; the person ends it with its button.)
            while (!dash.quit) sleep(500);
            dash.stop();
            System.exit(1);
            return;
        }
        dash.sup = sup;
        CountDownLatch done = new CountDownLatch(1);
        Supervisor s = sup;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // (The window closed, the computer going down: the games end with the app.)
            s.stopRequested = true;
            try {
                if (!done.await(45, TimeUnit.SECONDS)) s.killAll();
            } catch (InterruptedException e) {
                s.killAll();
            }
        }, "Bob-Trainer shutdown"));
        try {
            sup.run();
        } finally {
            done.countDown();
            dash.stop();
        }
        System.exit(0);
    }

    /** The app's folder: {@code --app <dir>}, else the folder above the one this jar is in (Bob-Trainer/app/bob-trainer.jar). */
    static Path appDir(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--app")) return Path.of(args[i + 1]).toAbsolutePath().normalize();
        Path jar = Autopilot.ownJar();
        if (jar != null && jar.getParent() != null && jar.getParent().getParent() != null) return jar.getParent().getParent().toAbsolutePath();
        return Path.of("").toAbsolutePath();
    }

    /** Opens the page in the person's browser (nothing when BOB_NO_BROWSER=1, or there is no browser). */
    static void browse(String url, Log log) {
        if ("1".equals(System.getenv("BOB_NO_BROWSER"))) return;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> cmd = os.contains("win") ? List.of("rundll32", "url.dll,FileProtocolHandler", url)
                : os.contains("mac") ? List.of("open", url) : List.of("xdg-open", url);
        try {
            new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            log.info("Bitte im Browser oeffnen: " + url);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
