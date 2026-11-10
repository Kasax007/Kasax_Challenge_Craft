package net.kasax.challengecraft.autopilot;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The package to download from the panel and hand to Claude: one zip in {@code bob-autopilot/export}
 * with the league (its results, state and champion), the status, the autopilot's log, and what is
 * new since the last package - the journal files (Bob's decisions, what the models learn from) and
 * the games' logs (what the analysis tools read). Kept under {@code export_max_mb}: what does not
 * fit goes into the next package. A journal file still being written is packed as far as it is,
 * and again, longer, the next time: of the same file name the newest copy counts.
 */
final class Export {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private final Supervisor sup;
    private final Path dir, ledger;
    /** What was packed: path under bob-autopilot (with /) -> "size:modified". */
    private final Map<String, String> packed = new LinkedHashMap<>();

    Export(Supervisor sup) {
        this.sup = sup;
        this.dir = sup.exports;
        this.ledger = dir.resolve("packed.json");
        try {
            if (Files.exists(ledger)) {
                for (Map.Entry<String, Object> e : Json.map(Json.parse(Files.readString(ledger, StandardCharsets.UTF_8))).entrySet()) {
                    packed.put(e.getKey(), Json.str(e.getValue(), ""));
                }
            }
        } catch (IOException | RuntimeException e) {
            sup.log.warn("Liste der gepackten Dateien nicht lesbar: alles kommt ins naechste Paket");
        }
    }

    private String key(Path p) {
        return sup.home.relativize(p).toString().replace('\\', '/');
    }

    private static String stamp(Path p) {
        return Disk.sizeOf(p) + ":" + Disk.modified(p);
    }

    /** Whether this file went into a package as it is now (so it may go first when space is short). */
    synchronized boolean wasExported(Path p) {
        return stamp(p).equals(packed.get(key(p)));
    }

    /** Packs a new zip; returns it. */
    synchronized Path write() throws IOException {
        Files.createDirectories(dir);
        Path zip = dir.resolve("bob-export-" + LocalDateTime.now().format(STAMP) + ".zip");
        for (int n = 2; Files.exists(zip); n++) zip = dir.resolve("bob-export-" + LocalDateTime.now().format(STAMP) + "-" + n + ".zip");
        Path tmp = zip.resolveSibling(zip.getFileName() + ".part");
        long cap = sup.cfg.exportMaxMb * (1L << 20);
        List<String> in = new ArrayList<>(), left = new ArrayList<>();
        Map<String, String> nowPacked = new LinkedHashMap<>();
        try (Counting count = new Counting(Files.newOutputStream(tmp)); ZipOutputStream out = new ZipOutputStream(count)) {
            // The small files, always (the newest state of each).
            for (Path p : List.of(sup.leagueDir.resolve("results.jsonl"), sup.leagueDir.resolve("state.json"),
                    sup.leagueDir.resolve("champion.json"), sup.leagueDir.resolve("failures.jsonl"), sup.home.resolve("status.json"),
                    sup.home.resolve("STATUS.txt"))) {
                if (Files.exists(p)) add(out, key(p), Files.readAllBytes(p), in);
            }
            Path logFile = sup.home.resolve("autopilot.log");
            if (Files.exists(logFile)) add(out, "autopilot.log", tail(Files.readAllBytes(logFile), 2 << 20), in);
            Path config = sup.root.resolve(Config.FILE);
            if (Files.exists(config)) add(out, Config.FILE, Files.readAllBytes(config), in);
            // What is new: the journal (oldest first), then the games' logs (oldest first).
            for (Path p : newFiles(sup.journal)) {
                if (count.n > cap) {
                    left.add(key(p));
                    continue;
                }
                String st = stamp(p);
                add(out, key(p), wholeLines(Files.readAllBytes(p)), in);
                nowPacked.put(key(p), st);
            }
            for (Path p : newFiles(sup.games)) {
                if (count.n > cap) {
                    left.add(key(p));
                    continue;
                }
                String st = stamp(p);
                add(out, key(p), Files.readAllBytes(p), in);
                nowPacked.put(key(p), st);
            }
            add(out, "MANIFEST.txt", manifest(in, left).getBytes(StandardCharsets.UTF_8), in);
        }
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
        packed.putAll(nowPacked);
        // (Files gone from the folder need no entry any more.)
        packed.keySet().removeIf(k -> !Files.exists(sup.home.resolve(k)));
        Supervisor.writeAtomically(ledger, Json.pretty(packed));
        return zip;
    }

    /** The files of {@code folder} not packed as they are now, oldest first. */
    private List<Path> newFiles(Path folder) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) return out;
        try (Stream<Path> s = Files.list(folder)) {
            s.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().endsWith(".part"))
                    .filter(p -> !stamp(p).equals(packed.get(key(p)))).forEach(out::add);
        }
        out.sort(Comparator.comparingLong(Disk::modified));
        return out;
    }

    private static void add(ZipOutputStream out, String name, byte[] data, List<String> in) throws IOException {
        ZipEntry e = new ZipEntry(name);
        if (name.endsWith(".gz")) {
            // (A gzipped log is stored as it is: deflating it again gains nothing.)
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(data);
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            e.setCompressedSize(data.length);
            e.setCrc(crc.getValue());
        }
        out.putNextEntry(e);
        out.write(data);
        out.closeEntry();
        in.add(name + " (" + Disk.mb(data.length) + ")");
    }

    /** Up to the last line break: a line a server is writing right now is left for the next time. */
    static byte[] wholeLines(byte[] data) {
        int end = data.length;
        while (end > 0 && data[end - 1] != '\n') end--;
        return end == data.length ? data : Arrays.copyOf(data, end);
    }

    private static byte[] tail(byte[] data, int max) {
        return data.length <= max ? data : Arrays.copyOfRange(data, data.length - max, data.length);
    }

    private String manifest(List<String> in, List<String> left) {
        StringBuilder b = new StringBuilder();
        b.append("Bob-Autopilot - Paket vom ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append('\n');
        b.append("Rechner: ").append(sup.cfg.machine).append(", Code: ").append(Json.write(sup.code())).append('\n');
        b.append("Liga: ").append(sup.gamesDone).append(" Spiele fertig, ").append(sup.gamesFailed).append(" abgebrochen seit dem Start\n\n");
        b.append("Inhalt:\n");
        for (String s : in) b.append("  ").append(s).append('\n');
        if (!left.isEmpty()) {
            b.append("\nNicht mehr hineingepasst (kommt ins naechste Paket):\n");
            for (String s : left) b.append("  ").append(s).append('\n');
        }
        b.append("\nJournal-Dateien, die noch geschrieben werden, kommen spaeter laenger noch einmal: "
                + "von gleichen Dateinamen gilt die neueste.\n");
        return b.toString();
    }

    /** Leaves only the newest {@code n} packages. */
    synchronized void keepNewest(int n) {
        List<Path> zips = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".zip")).forEach(zips::add);
        } catch (IOException e) {
            return;
        }
        zips.sort(Comparator.comparingLong(Disk::modified).reversed());
        for (int i = n; i < zips.size(); i++) {
            try {
                Files.deleteIfExists(zips.get(i));
            } catch (IOException ignored) {
                // (Next time.)
            }
        }
    }

    /** Counts the bytes written: how big the zip is so far. */
    private static final class Counting extends FilterOutputStream {
        long n;

        Counting(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            n++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            n += len;
        }
    }
}
