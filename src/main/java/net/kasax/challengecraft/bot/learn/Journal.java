package net.kasax.challengecraft.bot.learn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.kasax.challengecraft.bot.BotManager;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bob's decision journal - the data bridge to the trainer ({@code scripts/train}): every decision
 * he takes, with the situation it was taken in and how it ended, one line of JSON each. A cover
 * taken when a fight was going badly (what was after him, his hearts and gear, what could be done
 * there, what he did, whether he lived and how many hearts it cost), a tile set out for (the plan's
 * estimate, how long it really took, done or not), a death (what killed him, doing what), a game
 * (the knobs, the tiles, the deaths).
 *
 * <p>Files under {@code bob-journal/} in the server's folder (or where {@code BOB_JOURNAL} points;
 * {@code BOB_JOURNAL=off}: none): a new one each server start and every 16 MB. Written on a thread
 * of its own, so the game never waits for the disk.
 */
public final class Journal {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeSpecialFloatingPointValues().create();
    private static final long ROTATE_BYTES = 16L << 20;
    private static volatile Journal instance;

    private final Path dir;
    private final String session;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(200_000);
    private final AtomicLong ids = new AtomicLong();
    private final Thread writer;
    private volatile boolean closing;
    private long written, dropped;
    private int part;

    private Journal(Path dir) {
        this.dir = dir;
        this.session = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-"
                + Long.toHexString(Double.doubleToLongBits(Math.random()) & 0xffffff);
        writer = new Thread(this::drain, "Bob-Journal");
        writer.setDaemon(true);
        writer.start();
    }

    /** Where the journal goes: {@code BOB_JOURNAL} (env or system property), else {@code bob-journal}; null when off. */
    static Path directory() {
        String v = System.getProperty("bob.journal", System.getenv("BOB_JOURNAL"));
        if (v != null && (v.equalsIgnoreCase("off") || v.equals("0"))) return null;
        return Path.of(v == null || v.isBlank() ? "bob-journal" : v).toAbsolutePath();
    }

    /** Opens the journal for this server run (at the server's start). */
    public static synchronized void open() {
        if (instance != null) return;
        Path d = directory();
        if (d == null) return;
        try {
            Files.createDirectories(d);
        } catch (IOException e) {
            BotManager.LOG.warn("[Journal] cannot write to {}: {}", d, e.toString());
            return;
        }
        instance = new Journal(d);
        BotManager.LOG.info("[Journal] writing Bob's decisions to {}", d);
    }

    /** Closes it (at the server's stop): whatever is still queued is written first. */
    public static synchronized void close() {
        Journal j = instance;
        if (j == null) return;
        instance = null;
        j.closing = true;
        try {
            j.writer.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        BotManager.LOG.info("[Journal] closed: {} records written, {} dropped", j.written, j.dropped);
    }

    public static boolean on() {
        return instance != null;
    }

    /** A new id for a decision whose outcome is written later (unique over all servers' journals). */
    public static String newId() {
        Journal j = instance;
        return j == null ? "-" : j.session + "-" + j.ids.incrementAndGet();
    }

    /** Writes one record ({@code t}: its kind). */
    public static void write(String type, JsonObject record) {
        Journal j = instance;
        if (j == null) return;
        record.addProperty("t", type);
        record.addProperty("session", j.session);
        record.addProperty("wall", System.currentTimeMillis());
        if (!j.queue.offer(GSON.toJson(record))) j.dropped++;
    }

    private void drain() {
        BufferedWriter out = null;
        long bytes = 0;
        try {
            while (!closing || !queue.isEmpty()) {
                String line = queue.poll(500, TimeUnit.MILLISECONDS);
                if (line == null) {
                    if (out != null) out.flush();
                    continue;
                }
                if (out == null || bytes > ROTATE_BYTES) {
                    if (out != null) out.close();
                    Path f = dir.resolve("journal-" + session + "-" + (part++) + ".jsonl");
                    out = Files.newBufferedWriter(f, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    bytes = 0;
                }
                out.write(line);
                out.write('\n');
                bytes += line.length() + 1;
                written++;
            }
        } catch (IOException e) {
            BotManager.LOG.warn("[Journal] write failed: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // nothing more to do
                }
            }
        }
    }

    // ---- small helpers for the records ---------------------------------------------------------

    public static com.google.gson.JsonArray array(double[] xs) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (double x : xs) a.add(Math.round(x * 10000) / 10000.0);
        return a;
    }

    public static com.google.gson.JsonArray array(Iterable<String> xs) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (String x : xs) a.add(x);
        return a;
    }
}
