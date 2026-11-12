package net.kasax.challengecraft.autopilot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
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
import java.util.Locale;
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
        if (junction(t) && removedAsLink(t)) return;
        // (walkFileTree follows no links unless told to: a link is visited as a file, and deleted as one.)
        Files.walkFileTree(t, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // (A Windows junction is a folder to the walk: only the junction goes, not what it points to.)
                if (attrs.isOther() && !dir.equals(t) && removedAsLink(dir)) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                // (Gone meanwhile: nothing to do. A link whose target is gone - a symbolic link, a
                // junction - is still deleted, as the link it is.)
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * A symbolic link, or a Windows junction (a folder that leads to another folder) - and not a
     * real folder that merely carries a sync program's mark, as OneDrive marks the folders it syncs.
     */
    static boolean linkElsewhere(Path p) {
        if (Files.isSymbolicLink(p)) return true;
        if (!junction(p)) return false;
        try {
            Path abs = p.toAbsolutePath().normalize();
            return !abs.toRealPath().equals(abs.getParent().toRealPath().resolve(abs.getFileName()));
        } catch (IOException e) {
            return true; // (a junction whose target is gone)
        }
    }

    /**
     * A folder Windows marks as a reparse point, removed as a link: a junction goes as the link it
     * is (Windows removes a junction whatever its target holds). A folder that is not empty is a
     * real one carrying a mark of its own - OneDrive marks every folder it syncs so - and false
     * says its contents are to go first.
     */
    private static boolean removedAsLink(Path dir) throws IOException {
        try {
            Files.delete(dir);
            return true;
        } catch (DirectoryNotEmptyException e) {
            return false;
        } catch (NoSuchFileException e) {
            return true;
        }
    }

    /**
     * As {@link #deleteTree}, tried again for a few seconds: on Windows a virus scanner, the search
     * index or a sync program holds a file it has just seen for a moment, and the folder cannot go
     * until it lets go.
     */
    static void deleteTreeRetrying(Path target, Path within) throws IOException {
        for (int i = 0; ; i++) {
            try {
                deleteTree(target, within);
                return;
            } catch (IOException e) {
                if (i >= RETRY_WAITS_MS.length || e.getMessage() != null && e.getMessage().startsWith("not deleting ")) throw e;
                pause(RETRY_WAITS_MS[i]);
            }
        }
    }

    private static final long[] RETRY_WAITS_MS = {200, 400, 800, 1200, 1600};

    /** {@code action} tried again for a few seconds when it fails (a file held for a moment); its last failure if it never works. */
    static void retrying(IoAction action) throws IOException {
        for (int i = 0; ; i++) {
            try {
                action.run();
                return;
            } catch (IOException e) {
                if (i >= RETRY_WAITS_MS.length) throw e;
                pause(RETRY_WAITS_MS[i]);
            }
        }
    }

    /** Something done with files that may fail. */
    interface IoAction {
        void run() throws IOException;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A failure with files, said so a person can act on it (the path, and what kept it from working). */
    static String describe(IOException e) {
        String path = e instanceof FileSystemException f && f.getFile() != null ? f.getFile() : null;
        if (e instanceof DirectoryNotEmptyException) {
            return "der Ordner " + path + " laesst sich nicht leeren - ein anderes Programm haelt Dateien darin offen "
                    + "(OneDrive oder ein anderes Sync-Programm, ein Virenscanner, ein noch laufendes Spiel)";
        }
        if (e instanceof AccessDeniedException) {
            return "kein Zugriff auf " + path + " - gesperrt oder schreibgeschuetzt "
                    + "(OneDrive oder ein anderes Sync-Programm, ein Virenscanner, ein noch laufendes Spiel)";
        }
        if (e instanceof FileSystemException f && f.getReason() != null) return path + ": " + f.getReason();
        if (e instanceof NoSuchFileException) return path + " fehlt";
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    /**
     * Whether {@code p} lies in a folder a sync program keeps in step with the cloud (OneDrive,
     * Dropbox, Google Drive, iCloud): such a program opens each new file to upload it and marks
     * its folders, and a game's world - thousands of files written and thrown away - fights it.
     */
    static boolean cloudSynced(Path p) {
        String abs = p.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String var : new String[]{"OneDrive", "OneDriveConsumer", "OneDriveCommercial"}) {
            String v = System.getenv(var);
            if (v == null || v.isBlank()) continue;
            String base = v.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            if (!base.isEmpty() && (abs.equals(base) || abs.startsWith(base + "/"))) return true;
        }
        for (String part : abs.split("/")) {
            if (part.startsWith("onedrive") || part.startsWith("dropbox") || part.startsWith("google drive") || part.equals("googledrive")
                    || part.equals("my drive") || part.equals("meine ablage") || part.equals("shared drives") || part.equals("geteilte ablagen")
                    || part.equals("icloud drive") || part.equals("iclouddrive") || part.equals("mobile documents") || part.equals("cloudstorage")) {
                return true;
            }
        }
        return false;
    }

    /**
     * A folder of this computer's own for the trainer's throwaway files, outside every synced
     * folder: on Windows under %LOCALAPPDATA%, on a Mac in the user's caches, elsewhere in
     * ~/.cache. ({@code bob.localDir}, a system property, puts it elsewhere: the tests.)
     */
    static Path localDataDir() {
        String set = System.getProperty("bob.localDir");
        if (set != null && !set.isBlank()) return Path.of(set).toAbsolutePath().normalize();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            return (local != null && !local.isBlank() ? Path.of(local) : home.resolve("AppData").resolve("Local")).resolve("Bob-Trainer");
        }
        if (os.contains("mac")) return home.resolve("Library").resolve("Caches").resolve("Bob-Trainer");
        return home.resolve(".cache").resolve("bob-trainer");
    }

    /** A Windows directory junction (a reparse point the walk would take for a folder); false elsewhere. */
    static boolean junction(Path p) {
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return a.isDirectory() && a.isOther();
        } catch (IOException e) {
            return false;
        }
    }

    /** The bytes of all files under {@code dir}, links not followed (a link counts nothing). */
    static long size(Path dir) {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return 0;
        long[] total = {0};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    // (A Windows junction: what it points to is counted where it lives. A folder that
                    // only carries a sync program's mark is counted.)
                    return attrs.isOther() && !d.equals(dir) && linkElsewhere(d) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // (A file a sync program marks is "other" to Java: counted all the same; a link is not.)
                    if (!attrs.isDirectory() && !attrs.isSymbolicLink()) total[0] += attrs.size();
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
                // (A file a sync program marks is "other" to Java, and is pruned all the same; links and folders are not.)
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(p) || !which.test(p)) continue;
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
