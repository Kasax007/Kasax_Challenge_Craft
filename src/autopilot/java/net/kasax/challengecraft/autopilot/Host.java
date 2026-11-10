package net.kasax.challengecraft.autopilot;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The machine as the autopilot sees it: its cores and memory - those of the container a game
 * server panel gives it (Java reads the container's limits), not the whole host's.
 */
final class Host {
    private Host() {
    }

    static int cores() {
        return Runtime.getRuntime().availableProcessors();
    }

    /** All the memory this container may use, in MB. */
    static long totalMb() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getTotalMemorySize() >> 20;
        }
        return Runtime.getRuntime().maxMemory() >> 20;
    }

    /** What of it is free now, in MB (-1: not known). */
    static long freeMb() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getFreeMemorySize() >> 20;
        }
        return -1;
    }

    /** What a process holds in memory (its resident set), in MB; -1 where /proc is not there. */
    static long residentMb(long pid) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc", Long.toString(pid), "status"), StandardCharsets.UTF_8)) {
                if (line.startsWith("VmRSS:")) return Long.parseLong(line.replaceAll("[^0-9]", "")) >> 10;
            }
        } catch (Exception ignored) {
            // (Not Linux, or the process just ended.)
        }
        return -1;
    }

    /** The threads a process runs (-1 where /proc is not there): a panel limits them per server. */
    static int threads(long pid) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc", Long.toString(pid), "status"), StandardCharsets.UTF_8)) {
                if (line.startsWith("Threads:")) return Integer.parseInt(line.replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) {
            // (As above.)
        }
        return -1;
    }

    /**
     * How many league games fit at once, given the arena servers beside them: by memory (each game
     * its heap and some 1.3 GB beyond it, 1.5 GB kept free) and by cores (about two each when the
     * game runs as fast as it can) - the smaller, at least one.
     */
    static int autoLeagueGames(long totalMb, int cores, int gameMb, int arenaServers, int arenaMb) {
        long free = totalMb - 1536 - (long) arenaServers * (arenaMb + OVERHEAD_MB);
        int byMemory = (int) Math.max(0, free / (gameMb + OVERHEAD_MB));
        int byCores = Math.max(1, (cores - 2 * arenaServers) / 2);
        return Math.max(1, Math.min(8, Math.min(byMemory, byCores)));
    }

    /** What a server's Java holds beyond its heap (classes, code, threads, buffers), in MB. */
    static final int OVERHEAD_MB = 1300;
}
