package net.kasax.challengecraft.autopilot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One dedicated Minecraft server the autopilot runs - a league game or an arena - in a folder of
 * its own under {@code bob-autopilot/work}: the server's jars, libraries and mods linked in, its
 * configs copied, a world of its own. Started with the same Java as the autopilot, told what to do
 * through its console (as a person types at a server console) and watched through its output and
 * Bob's status file: no network involved, the game listens only on 127.0.0.1.
 */
final class GameServer {
    enum Kind { LEAGUE, ARENA }

    final String name;
    final Kind kind;
    final Path dir;
    /** The autopilot's folder: nothing outside it is ever deleted from here. */
    private final Path within;

    private Process process;
    private BufferedWriter stdin;
    private Thread reader;
    private volatile boolean ready;
    private volatile String result;
    private volatile String hint;
    private volatile long lastOutput;
    private volatile long lines;
    private final Map<String, Integer> claims = new LinkedHashMap<>();

    GameServer(String name, Kind kind, Path dir, Path within) {
        this.name = name;
        this.kind = kind;
        this.dir = dir;
        this.within = within;
    }

    // ---- the folder -------------------------------------------------------------------------------

    /**
     * Sets the folder up for a new start: links to the server's jars, libraries and mods (made
     * once, checked each time), the configs and the EULA copied fresh, the last world, logs and
     * output gone, and its own server.properties.
     */
    void prepare(Layout layout, Path ownJar, Map<String, String> properties, Log log) throws IOException {
        Files.createDirectories(dir);
        for (Path entry : layout.linkedEntries(ownJar)) link(dir.resolve(entry.getFileName().toString()), entry, log);
        link(dir.resolve("mods"), layout.mods, log);
        Path dotFabric = dir.resolve(".fabric");
        if (layout.fabricKeepsServerInDotFabric()) {
            // (Fabric's downloading launcher keeps Minecraft and its libraries in .fabric/server: one
            // copy for all games, downloaded by the first that starts. The rest of .fabric - the
            // loader's caches - stays each game's own.)
            Path shared = layout.root.resolve(".fabric").resolve("server");
            Files.createDirectories(shared);
            Files.createDirectories(dotFabric);
            link(dotFabric.resolve("server"), shared, log);
        }
        for (String copied : new String[]{"config", "fabric-server-launcher.properties", "eula.txt"}) {
            Path from = layout.root.resolve(copied), to = dir.resolve(copied);
            Disk.deleteTree(to, within);
            if (Files.exists(from)) Disk.copyTree(from, to);
        }
        for (String old : new String[]{"world", "logs", "crash-reports", "server.out", "bob-status.json", "bob-status.json.tmp"}) {
            Disk.deleteTree(dir.resolve(old), within);
        }
        StringBuilder b = new StringBuilder("# Written by the Bob-Autopilot for each start.\n");
        properties.forEach((k, v) -> b.append(k).append('=').append(v).append('\n'));
        Files.writeString(dir.resolve("server.properties"), b.toString(), StandardCharsets.UTF_8);
    }

    /** A link {@code at} to {@code target} (relative, inside the server's folder); a copy where links cannot be made. */
    private void link(Path at, Path target, Log log) throws IOException {
        Path rel = at.getParent().relativize(target);
        if (Files.isSymbolicLink(at)) {
            if (Files.readSymbolicLink(at).equals(rel)) return;
            Files.delete(at);
        } else if (Files.exists(at, LinkOption.NOFOLLOW_LINKS)) {
            Disk.deleteTree(at, within);
        }
        try {
            Files.createSymbolicLink(at, rel);
        } catch (IOException | UnsupportedOperationException e) {
            log.info(name + ": Verknuepfung nicht moeglich (" + e.getMessage() + "), kopiere " + target.getFileName());
            Disk.copyTree(target, at);
        }
    }

    /** The properties of a game's server: its seed, a free port on 127.0.0.1 only, no RCON, nothing waiting for players. */
    static Map<String, String> properties(Config cfg, Kind kind, long seed, String name) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("level-name", "world");
        p.put("level-seed", Long.toString(seed));
        p.put("online-mode", "false");
        p.put("server-ip", "127.0.0.1");
        p.put("server-port", Integer.toString(freePort()));
        p.put("enable-rcon", "false");
        p.put("enable-query", "false");
        p.put("enable-status", "false");
        p.put("management-server-enabled", "false");
        p.put("difficulty", cfg.difficulty);
        p.put("spawn-protection", "0");
        p.put("max-players", Integer.toString(Math.max(16, cfg.arenaBots + 4)));
        p.put("motd", "Bob-Autopilot " + name);
        boolean arena = kind == Kind.ARENA;
        p.put("view-distance", Integer.toString(arena ? 6 : cfg.viewDistance));
        p.put("simulation-distance", Integer.toString(arena ? 4 : Math.min(cfg.simulationDistance, cfg.viewDistance)));
        p.put("sync-chunk-writes", "false");
        p.put("allow-flight", "true");
        // (No vanilla watchdog: a game sprinting through a heavy tick is not hung; the autopilot watches.)
        p.put("max-tick-time", "-1");
        p.put("pause-when-empty-seconds", "-1");
        p.put("player-idle-timeout", "0");
        p.put("log-ips", "false");
        return p;
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        }
    }

    // ---- the process ------------------------------------------------------------------------------

    void start(List<String> command, Map<String, String> env) throws IOException {
        ready = false;
        result = null;
        hint = null;
        lines = 0;
        synchronized (claims) {
            claims.clear();
        }
        ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        Map<String, String> e = pb.environment();
        // (The test hooks a shell may have set for other runs: only what this game needs.)
        e.keySet().removeIf(k -> k.startsWith("CHALLENGECRAFT_") || k.startsWith("BOB_"));
        e.putAll(env);
        process = pb.start();
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        lastOutput = System.currentTimeMillis();
        Path out = dir.resolve("server.out");
        Process p = process;
        reader = new Thread(() -> read(p, out), "Bob-Autopilot " + name + " output");
        reader.setDaemon(true);
        reader.start();
    }

    private static final Pattern CLAIM = Pattern.compile("\\[BOTVS\\] (\\S+) claimed .+ at \\d+ s");
    /** Lines that say why a server did not come up or broke down (the first one seen is kept). */
    private static final String[] TROUBLE = {"Exception in server tick loop", "Encountered an unexpected exception", "OutOfMemoryError",
            "Failed to start the minecraft server", "Incompatible mods", "Mod resolution", "No such bot", "Exception in thread \"main\"",
            "Error: Could not find or load main class", "Error occurred during initialization of VM", "Could not reserve enough space",
            "UnsupportedClassVersionError", "FAILED TO BIND TO PORT", "Failed to setup fabric server"};

    /** The server's output: into server.out, and read for what the autopilot waits on. */
    private void read(Process p, Path out) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                w.write(line);
                w.write('\n');
                lastOutput = System.currentTimeMillis();
                lines++;
                look(line);
                if (!r.ready()) w.flush();
            }
        } catch (IOException ignored) {
            // (The process is gone; what was read stands.)
        }
    }

    /** One line of output: the server up, the game's result, a tile claimed, or trouble. */
    void look(String line) {
        if (!ready && line.contains("Done (") && line.contains("For help, type")) ready = true;
        int i = line.indexOf("[BOTVS] result ");
        if (i >= 0) result = line.substring(i + "[BOTVS] result ".length()).trim();
        if (line.contains("[BOTVS]") && line.contains(" claimed ")) {
            Matcher m = CLAIM.matcher(line);
            if (m.find()) {
                synchronized (claims) {
                    claims.merge(m.group(1), 1, Integer::sum);
                }
            }
        }
        if (hint == null) {
            for (String t : TROUBLE) {
                if (line.contains(t)) {
                    hint = line.length() > 300 ? line.substring(0, 300) : line;
                    break;
                }
            }
        }
    }

    /** A line at the server's console. */
    synchronized void command(String line) {
        if (stdin == null) return;
        try {
            stdin.write(line);
            stdin.write('\n');
            stdin.flush();
        } catch (IOException ignored) {
            // (The server is gone: its end is seen in alive().)
        }
    }

    boolean alive() {
        return process != null && process.isAlive();
    }

    /** Waits (up to {@code ms}) until the output of a server that has ended is read to its end. */
    void drainOutput(long ms) {
        Thread r = reader;
        if (r == null || alive()) return;
        try {
            r.join(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    boolean ready() {
        return ready;
    }

    String result() {
        return result;
    }

    String hint() {
        return hint;
    }

    long lastOutput() {
        return lastOutput;
    }

    long pid() {
        return process == null ? -1 : process.pid();
    }

    Map<String, Integer> claims() {
        synchronized (claims) {
            return new LinkedHashMap<>(claims);
        }
    }

    /** Bob's status file (written by the mod every five seconds), with its age in seconds; null when none yet. */
    Map<String, Object> status() {
        try {
            Path f = dir.resolve("bob-status.json");
            if (!Files.exists(f)) return null;
            Map<String, Object> st = Json.map(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
            st.put("age", (System.currentTimeMillis() - (long) Json.num(st.get("wall"), 0)) / 1000.0);
            return st;
        } catch (IOException | RuntimeException e) {
            return null; // (Caught half written: the next look reads it whole.)
        }
    }

    /**
     * Stops the server: "stop" at its console, then - the world is thrown away, so no saving needs
     * waiting for - ended after {@code graceMs}, forcibly if need be. Blocks; call it off the loop.
     */
    void stop(long graceMs) {
        Process p = process;
        if (p == null) return;
        if (p.isAlive()) {
            command("stop");
            try {
                if (!p.waitFor(graceMs, TimeUnit.MILLISECONDS)) {
                    p.destroy();
                    if (!p.waitFor(10, TimeUnit.SECONDS)) {
                        p.destroyForcibly();
                        p.waitFor(10, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        try {
            if (reader != null) reader.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            try {
                if (stdin != null) stdin.close();
            } catch (IOException ignored) {
                // (Closed with the process.)
            }
            stdin = null;
        }
    }

    /** At once, without waiting (the autopilot is being ended). */
    void kill() {
        Process p = process;
        if (p != null && p.isAlive()) p.destroyForcibly();
    }

    /**
     * After a game: its output kept (gzipped, as {@code keepAs}), crash reports beside it, the world
     * and the logs gone. Returns the kept log, or null.
     */
    Path cleanUp(Path keepAs) throws IOException {
        Path kept = null;
        Path out = dir.resolve("server.out");
        if (keepAs != null && Files.exists(out) && Files.size(out) > 0) {
            Disk.gzip(out, keepAs);
            kept = keepAs;
        }
        Path crashes = dir.resolve("crash-reports");
        if (keepAs != null && Files.isDirectory(crashes)) {
            List<Path> reports = new ArrayList<>();
            try (var s = Files.list(crashes)) {
                s.forEach(reports::add);
            }
            for (Path r : reports) {
                String base = keepAs.getFileName().toString().replace(".log.gz", "");
                Disk.gzip(r, keepAs.resolveSibling(base + "_" + r.getFileName() + ".gz"));
            }
        }
        for (String old : new String[]{"world", "logs", "crash-reports", "server.out"}) Disk.deleteTree(dir.resolve(old), within);
        return kept;
    }
}
