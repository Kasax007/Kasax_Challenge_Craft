package net.kasax.challengecraft.autopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskTest {
    @TempDir
    Path tmp;

    /** A game's folder links the server's mods in: deleting the folder must leave the mods where they are. */
    @Test
    void deletingAFolderNeverFollowsItsLinks() throws IOException {
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        Files.writeString(mods.resolve("challengecraft.jar"), "the server's own mod");
        Path jar = Files.writeString(tmp.resolve("server.jar"), "the server's own jar");
        Path home = Files.createDirectories(tmp.resolve("bob-autopilot"));
        Path game = Files.createDirectories(home.resolve("work/liga-1"));
        Files.createSymbolicLink(game.resolve("mods"), game.relativize(mods));
        Files.createSymbolicLink(game.resolve("server.jar"), game.relativize(jar));
        Files.createDirectories(game.resolve("world/region"));
        Files.writeString(game.resolve("world/region/r.0.0.mca"), "chunks");
        // (A link whose target is gone is deleted too.)
        Files.createSymbolicLink(game.resolve("dangling"), game.resolve("nowhere"));

        Disk.deleteTree(game, home);

        assertFalse(Files.exists(game));
        assertTrue(Files.exists(mods.resolve("challengecraft.jar")));
        assertTrue(Files.exists(jar));
    }

    @Test
    void deletesNothingOutsideItsOwnFolder() throws IOException {
        Path home = Files.createDirectories(tmp.resolve("bob-autopilot"));
        Path world = Files.createDirectories(tmp.resolve("world"));
        assertThrows(IOException.class, () -> Disk.deleteTree(world, home));
        assertThrows(IOException.class, () -> Disk.deleteTree(home.resolve("../world"), home));
        assertThrows(IOException.class, () -> Disk.deleteTree(home, home));
        assertTrue(Files.exists(world));
    }

    @Test
    void sizeCountsNoLinkedFiles() throws IOException {
        Path big = Files.write(tmp.resolve("big"), new byte[5000]);
        Path dir = Files.createDirectories(tmp.resolve("d"));
        Files.write(dir.resolve("a"), new byte[100]);
        Files.createSymbolicLink(dir.resolve("link"), big);
        assertEquals(100, Disk.size(dir));
    }

    @Test
    void pruneTakesTheDownloadedAndTheOldestFirstAndLeavesFilesBeingWritten() throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("journal"));
        long now = System.currentTimeMillis();
        Path old = file(dir, "a.jsonl", now - 5_000_000), downloaded = file(dir, "b.jsonl", now - 1_000_000);
        Path recent = file(dir, "c.jsonl", now - 3_000_000), writing = file(dir, "d.jsonl", now);
        // 4 x 1000 bytes, room for 2: the downloaded one goes first, then the oldest.
        List<Path> gone = Disk.prune(dir, p -> true, 2000, p -> p.equals(downloaded), 60_000);
        assertEquals(List.of(downloaded, old), gone);
        assertTrue(Files.exists(recent) && Files.exists(writing));
        // Room for none: the one being written stays all the same.
        Disk.prune(dir, p -> true, 0, p -> false, 60_000);
        assertFalse(Files.exists(recent));
        assertTrue(Files.exists(writing));
    }

    private static Path file(Path dir, String name, long modified) throws IOException {
        Path p = Files.write(dir.resolve(name), new byte[1000]);
        Files.setLastModifiedTime(p, FileTime.fromMillis(modified));
        return p;
    }

    @Test
    void gzipWritesWholeFilesOnly() throws IOException {
        Path src = Files.writeString(tmp.resolve("server.out"), "line\n".repeat(1000));
        Path gz = tmp.resolve("game.log.gz");
        Disk.gzip(src, gz);
        try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(gz))) {
            assertEquals("line\n".repeat(1000), new String(in.readAllBytes()));
        }
        assertFalse(Files.exists(tmp.resolve("game.log.gz.part")));
    }
}
