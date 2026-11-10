package net.kasax.challengecraft.autopilot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The autopilot's state for people: {@code bob-autopilot/STATUS.txt} (German, every minute), the
 * same as JSON in {@code status.json} (every fifteen seconds), and a few lines on the console every
 * ten minutes.
 */
final class Report {
    private Report() {
    }

    /** The status as JSON (also what Claude reads from an export). */
    static Map<String, Object> json(Supervisor s) {
        long now = System.currentTimeMillis();
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        o.put("uptime_min", Math.round((now - s.startedAt) / 6000.0) / 10.0);
        o.put("autopilot", Autopilot.VERSION);
        o.put("machine", s.cfg.machine);
        o.put("code", s.code());
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("cores", (long) Host.cores());
        host.put("ram_mb", Host.totalMb());
        host.put("free_mb", Host.freeMb());
        o.put("host", host);
        o.put("disk_mb", s.diskBytes < 0 ? null : Math.round(s.diskBytes / 104857.6) / 10.0);
        Map<String, Object> league = new LinkedHashMap<>();
        league.put("games", s.gamesDone);
        league.put("failed", s.gamesFailed);
        league.put("champion", Knobs.text(s.league.champion));
        league.put("challenger", s.league.challenger == null ? null : Knobs.text(s.league.challenger));
        league.put("duel", s.league.duel());
        List<Map<String, Object>> h = s.league.history;
        league.put("history", new ArrayList<>(h.subList(Math.max(0, h.size() - 5), h.size())));
        o.put("league", league);
        o.put("paused_min", s.pausedUntil > now ? Math.round((s.pausedUntil - now) / 6000.0) / 10.0 : null);
        List<Object> servers = new ArrayList<>();
        for (Supervisor.Slot slot : s.slots) servers.add(server(s, slot, now));
        o.put("servers", servers);
        o.put("last_games", new ArrayList<>(s.lastGames));
        o.put("events", new ArrayList<>(s.events));
        return o;
    }

    private static Map<String, Object> server(Supervisor s, Supervisor.Slot slot, long now) {
        GameServer g = slot.server;
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("name", g.name);
        o.put("kind", g.kind.name().toLowerCase(Locale.ROOT));
        o.put("state", slot.state.name().toLowerCase(Locale.ROOT));
        o.put("for_min", Math.round((now - slot.since) / 6000.0) / 10.0);
        if (slot.game != null) {
            o.put("game", slot.gameNo);
            o.put("seed", slot.game.seed());
            o.put("sides", slot.game.sides());
        }
        Map<String, Object> st = slot.state == Supervisor.State.IDLE ? null : g.status();
        if (st != null) {
            o.put("tps", st.get("tps"));
            o.put("mspt", st.get("mspt"));
            o.put("speed", Math.round(Json.num(st.get("tps"), 0) / 2.0) / 10.0);
            o.put("heap_mb", st.get("memory_mb"));
            if (slot.state == Supervisor.State.PLAYING && slot.tickAtStart >= 0) {
                o.put("game_secs", (long) ((Json.num(st.get("tick"), 0) - slot.tickAtStart) / 20));
                o.put("of_secs", (long) s.cfg.gameMinutes * 60);
            }
            if (g.kind == GameServer.Kind.ARENA) o.put("arena", st.get("arena"));
        }
        if (slot.state == Supervisor.State.PLAYING) o.put("tiles", g.claims());
        long pid = g.alive() ? g.pid() : -1;
        if (pid > 0) {
            o.put("rss_mb", Host.residentMb(pid));
            o.put("threads", (long) Host.threads(pid));
        }
        return o;
    }

    static void writeJson(Supervisor s) {
        try {
            Supervisor.writeAtomically(s.home.resolve("status.json"), Json.pretty(json(s)));
        } catch (IOException | RuntimeException e) {
            s.log.warn("status.json nicht geschrieben: " + e.getMessage());
        }
    }

    static void writeText(Supervisor s) {
        try {
            Supervisor.writeAtomically(s.home.resolve("STATUS.txt"), text(s));
        } catch (IOException | RuntimeException e) {
            s.log.warn("STATUS.txt nicht geschrieben: " + e.getMessage());
        }
    }

    /** The status in German, for STATUS.txt and the console's {@code status}. */
    static String text(Supervisor s) {
        long now = System.currentTimeMillis();
        Map<String, Object> j = json(s);
        StringBuilder b = new StringBuilder();
        b.append("Bob-Autopilot - Stand ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                .append(" (laeuft seit ").append(duration(now - s.startedAt)).append(")\n");
        b.append("Code: ").append(s.layout.modJar.getFileName()).append(" (Version ").append(s.layout.modVersion)
                .append(", Commit ").append(s.layout.modCommit).append(", Pruefsumme ").append(s.layout.modSha).append(")\n");
        b.append(String.format(Locale.GERMANY, "Rechner: %d Kerne, %.1f GB Arbeitsspeicher (frei %.1f GB)", Host.cores(), Host.totalMb() / 1024.0,
                Math.max(0, Host.freeMb()) / 1024.0));
        if (s.diskBytes >= 0) b.append(String.format(Locale.GERMANY, "; der Autopilot belegt %.2f GB auf der Platte", s.diskBytes / 1073741824.0));
        b.append("\n\n");
        b.append("Liga: ").append(s.gamesDone).append(" Spiele fertig, ").append(s.gamesFailed).append(" abgebrochen\n");
        b.append("  Champion:       ").append(Knobs.text(s.league.champion)).append('\n');
        if (s.league.challenger != null) {
            Map<String, Object> d = s.league.duel();
            b.append("  Herausforderer: ").append(Knobs.text(s.league.challenger)).append('\n');
            long games = (long) Json.num(d.get("games"), 0), wins = (long) Json.num(d.get("wins"), 0), losses = (long) Json.num(d.get("losses"), 0);
            b.append(String.format(Locale.GERMANY, "  Duell: %d %s, der Herausforderer %+.2f Felder je Spiel (Tode als halbes Feld), %d %s, %d %s%n",
                    games, games == 1 ? "Spiel" : "Spiele", Json.num(d.get("mean_diff"), 0), wins, wins == 1 ? "Sieg" : "Siege",
                    losses, losses == 1 ? "Niederlage" : "Niederlagen"));
        }
        if (s.pausedUntil > now) {
            b.append("  PAUSE nach mehreren Fehlern, noch ").append(duration(s.pausedUntil - now)).append(" (siehe Ereignisse)\n");
        }
        b.append("\nServer:\n");
        for (Object o : Json.list(j.get("servers"))) b.append("  ").append(serverLine(Json.map(o))).append('\n');
        if (!s.lastGames.isEmpty()) {
            b.append("\nLetzte Spiele:\n");
            for (Map<String, Object> g : s.lastGames) {
                LocalDateTime when = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli((long) (Json.num(g.get("time"), 0) * 1000)),
                        java.time.ZoneId.systemDefault());
                b.append("  ").append(when.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))).append("  ").append(g.get("slot"))
                        .append(" Spiel ").append(g.get("game")).append(": ").append(g.get("text")).append('\n');
            }
        }
        if (!s.events.isEmpty()) {
            b.append("\nEreignisse (neueste oben):\n");
            int n = 0;
            for (String e : s.events) {
                if (n++ >= 12) break;
                b.append("  ").append(e).append('\n');
            }
        }
        Path latest = latestExport(s);
        b.append("\nHerunterladen: ");
        if (latest != null) b.append("bob-autopilot/export/").append(latest.getFileName()).append(" (").append(Disk.human(Disk.sizeOf(latest))).append(")");
        else b.append("noch kein Paket");
        b.append(" - ein neues legt der Konsolenbefehl export an.\n");
        return b.toString();
    }

    private static String serverLine(Map<String, Object> o) {
        StringBuilder b = new StringBuilder(String.format("%-9s", Json.str(o.get("name"), "?")));
        String state = Json.str(o.get("state"), "?");
        switch (state) {
            case "idle" -> b.append("wartet");
            case "starting" -> b.append("startet").append(o.get("game") != null ? " (Spiel " + o.get("game") + ")" : "");
            case "spawning" -> b.append("Bots kommen ins Spiel ").append(o.get("game"));
            case "stopping" -> b.append("wird beendet und aufgeraeumt");
            case "arena" -> {
                Map<String, Object> a = Json.map(o.get("arena"));
                b.append("Arena: ").append(Knobs.format(Json.num(a.get("fights"), 0))).append(" Kaempfe");
            }
            case "playing" -> {
                b.append("spielt Spiel ").append(o.get("game")).append(" (Seed ").append(o.get("seed")).append(")");
                if (o.get("game_secs") != null) {
                    b.append(String.format(Locale.GERMANY, ": Minute %d von %d", (long) Json.num(o.get("game_secs"), 0) / 60,
                            (long) Json.num(o.get("of_secs"), 0) / 60));
                }
                Map<String, Object> tiles = Json.map(o.get("tiles"));
                if (!tiles.isEmpty()) {
                    b.append(", Felder");
                    tiles.forEach((k, v) -> b.append(' ').append(k).append(' ').append(v));
                }
            }
            default -> b.append(state);
        }
        if (o.get("speed") != null && !state.equals("idle")) b.append(String.format(Locale.GERMANY, ", Tempo %.1fx", Json.num(o.get("speed"), 0)));
        // (The memory the server's Java holds, once it is up.)
        if (Json.num(o.get("rss_mb"), -1) >= 512) b.append(String.format(Locale.GERMANY, ", %.1f GB", Json.num(o.get("rss_mb"), 0) / 1024.0));
        return b.toString();
    }

    /** A few lines for the console. */
    static String shortText(Supervisor s) {
        StringBuilder b = new StringBuilder("[Bob-Autopilot] Stand: ").append(s.gamesDone).append(" Spiele fertig, ").append(s.gamesFailed)
                .append(" abgebrochen; Champion ").append(Knobs.text(s.league.champion));
        for (Object o : Json.list(json(s).get("servers"))) b.append("\n[Bob-Autopilot]   ").append(serverLine(Json.map(o)));
        return b.toString();
    }

    private static Path latestExport(Supervisor s) {
        try (Stream<Path> st = Files.list(s.exports)) {
            return st.filter(p -> p.getFileName().toString().endsWith(".zip")).max(java.util.Comparator.comparingLong(Disk::modified)).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    static String duration(long ms) {
        long min = ms / 60_000;
        return min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
    }
}
