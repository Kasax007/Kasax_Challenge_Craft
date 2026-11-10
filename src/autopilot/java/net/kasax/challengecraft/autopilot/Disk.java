package net.kasax.challengecraft.autopilot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Files, with care: the autopilot deletes only inside its own folder, and never through a link -
 * the games' folders link the server's mods, libraries and jars in, and a delete that followed
 * those links would take the server's own files with it.
 */
final class Disk {
    private Disk() {
    }

    /**
     * Deletes {@code target} and everything under it, links as links (what they point to stays).
     * Refused for anything outside {@code within}: a guard against a wrong path ever reaching the
     * server's world or mods.
     */
    static void deleteTree(Path target, Path within) throws IOException {
        Path t = target.toAbsolutePath().normalize(), w = within.toAbsolutePath().normalize();
        if (!t.startsWith(w) || t.equals(w)) throw new IOException("not deleting " + t + ": outside " + w);
        if (!Files.exists(t, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(t, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(t);
            return;
        }
        // (walkFileTree follows no links unless told to: a link is visited as a file, and deleted as one.)
        Files.walkFileTree(t, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                if (e instanceof NoSuchFileException) return FileVisitResult.CONTINUE;
                // (A link whose target is gone is still deleted.)
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** The bytes of all files under {@code dir}, links not followed (a link counts nothing). */
    static long size(Path dir) {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return 0;
        long[] total = {0};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) total[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE; // (a world's file deleted while counting)
                }
            });
        } catch (IOException ignored) {
            // (What was counted stands.)
        }
        return total[0];
    }

    /** {@code source} gzipped into {@code target} (written beside it first, then moved: never half a file). */
    static void gzip(Path source, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = Files.newInputStream(source); OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp), 1 << 16)) {
            in.transferTo(out);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Copies {@code source} (a folder: all of it) to {@code target}, replacing what is there. */
    static void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (Stream<Path> all = Files.walk(source)) {
            for (Path p : (Iterable<Path>) all::iterator) {
                Path to = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Keeps the files in {@code dir} matching {@code which} under {@code maxBytes}: the oldest go
     * first, those {@code preferred} (already downloaded) before the others; a file written to in
     * the last {@code quietMs} is left alone (a server may be writing it). Returns what went.
     */
    static List<Path> prune(Path dir, Predicate<Path> which, long maxBytes, Predicate<Path> preferred, long quietMs) {
        List<Path> removed = new ArrayList<>();
        if (!Files.isDirectory(dir)) return removed;
        List<Path> files = new ArrayList<>();
        long total = 0;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || !which.test(p)) continue;
                files.add(p);
                total += sizeOf(p);
            }
        } catch (IOException e) {
            return removed;
        }
        if (total <= maxBytes) return removed;
        long now = System.currentTimeMillis();
        files.sort(Comparator.comparing((Path p) -> !preferred.test(p)).thenComparingLong(Disk::modified));
        for (Path p : files) {
            if (total <= maxBytes) break;
            if (now - modified(p) < quietMs) continue;
            long size = sizeOf(p);
            try {
                Files.deleteIfExists(p);
                total -= size;
                removed.add(p);
            } catch (IOException ignored) {
                // (Tried again next time.)
            }
        }
        return removed;
    }

    static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** A size for people: "12 KB", "3.4 MB", "250 MB". */
    static String human(long bytes) {
        if (bytes < (1L << 20)) return String.format(java.util.Locale.ROOT, "%d KB", (bytes + 1023) >> 10);
        return bytes < (100L << 20) ? String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0)
                : String.format(java.util.Locale.ROOT, "%.0f MB", bytes / 1048576.0);
    }
}
