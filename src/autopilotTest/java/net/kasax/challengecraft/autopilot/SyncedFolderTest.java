package net.kasax.challengecraft.autopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a trainer folder in OneDrive taught (11.11.2026): the games' folders go outside the synced
 * folder, links made once are kept, Minecraft is installed by one launcher alone and then started
 * without it, and a failure with files says what held them.
 */
class SyncedFolderTest {
    @TempDir
    Path tmp;

    @AfterEach
    void clearLocalDir() {
        System.clearProperty("bob.localDir");
    }

    @Test
    void knowsTheSyncedFolders() {
        assertTrue(Disk.cloudSynced(Path.of("/c/Users/lars6/OneDrive/Documents/Bob Trainer/Bob-Trainer/server")));
        assertTrue(Disk.cloudSynced(Path.of("C:\\Users\\x\\OneDrive - Firma\\Bob-Trainer\\server")));
        assertTrue(Disk.cloudSynced(Path.of("/home/x/Dropbox/Bob-Trainer/server")));
        assertTrue(Disk.cloudSynced(Path.of("/Users/x/Library/Mobile Documents/com~apple~CloudDocs/Bob-Trainer/server")));
        assertFalse(Disk.cloudSynced(Path.of("/home/x/Bob-Trainer/server")));
        assertFalse(Disk.cloudSynced(Path.of("C:\\Bob-Trainer\\server")));
    }

    @Test
    void theGamesLeaveASyncedFolder() {
        System.setProperty("bob.localDir", tmp.resolve("local").toString());
        Config cfg = new Config();
        Path synced = tmp.resolve("OneDrive/Documents/Bob Trainer/Bob-Trainer/server");
        Path base = Supervisor.localBase(synced, cfg);
        assertNotNull(base);
        assertTrue(base.startsWith(tmp.resolve("local")), base.toString());
        assertEquals(base, Supervisor.localBase(synced, cfg), "the same folder at every start");
        assertTrue(!base.equals(Supervisor.localBase(tmp.resolve("OneDrive/other copy/server"), cfg)), "another copy, another folder");
        assertNull(Supervisor.localBase(tmp.resolve("Bob-Trainer/server"), cfg));
        cfg.workDir = "D:/Bob-Welten";
        assertTrue(Supervisor.localBase(tmp.resolve("Bob-Trainer/server"), cfg).toString().endsWith("Bob-Welten"));
    }

    /** On Windows a hard link cannot be deleted while another game runs from the file it shares: made once, kept. */
    @Test
    void aLinkMadeBeforeIsKept() throws IOException {
        Path target = Files.writeString(tmp.resolve("fabric-launcher.jar"), "the launcher");
        Path game = Files.createDirectories(tmp.resolve("work/liga-1"));
        GameServer g = new GameServer("liga-1", GameServer.Kind.LEAGUE, game, tmp.resolve("work"));
        Log log = new Log(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        Path at = game.resolve("fabric-launcher.jar");
        Files.createLink(at, target);
        Object inode = Files.getAttribute(at, "unix:ino");
        g.link(at, target, log);
        assertFalse(Files.isSymbolicLink(at), "the hard link is kept, not made again");
        assertEquals(inode, Files.getAttribute(at, "unix:ino"));
        // A copy is no link: it is replaced by one.
        Files.delete(at);
        Files.writeString(at, "the launcher");
        g.link(at, target, log);
        assertTrue(Files.isSameFile(at, target));
    }

    @Test
    void minecraftsJarIsNamedAsTheLauncherNamesIt() throws IOException {
        Layout layout = fabricRoot(Files.createDirectories(tmp.resolve("server")));
        assertTrue(layout.fabricKeepsServerInDotFabric());
        assertEquals(tmp.resolve("mc/26.3-server.jar"), layout.minecraftJar(tmp.resolve("mc")));
    }

    /**
     * Each game its own Fabric (the launch jar in .fabric/server, made by its own launcher): shared,
     * every new game's launcher made the launch jar again, and on Windows that fails while another
     * game runs from it (liga-7 and liga-8, 11.11.). A link an earlier version left gives way.
     */
    @Test
    void eachGameHasItsOwnFabricAndSharesOnlyMinecraft() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("server"));
        Layout layout = fabricRoot(root);
        Files.writeString(root.resolve("eula.txt"), "eula=true\n");
        Files.writeString(root.resolve(Config.FILE), "league_games=1\nexport_hours=0\n");
        Log log = new Log(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        Config cfg = Config.load(root.resolve(Config.FILE), log::warn);
        Supervisor sup = new Supervisor(root, cfg, layout, null, "java", log, 1);
        Path mc = root.resolve(".fabric/server/26.3-server.jar");
        assertEquals(mc, sup.minecraftJar());
        List<String> c = sup.command(GameServer.Kind.LEAGUE);
        assertTrue(c.contains("-Dfabric.installer.server.gameJar=" + mc.toAbsolutePath()), c.toString());
        assertEquals(List.of("-jar", "fabric-server-mc.26.3-loader.0.19.5-launcher.1.1.2.jar", "nogui"), c.subList(c.size() - 3, c.size()));
        assertFalse(sup.minecraftReady(), "nothing downloaded yet");

        // A game folder as the earlier version left it: .fabric/server a link to the shared folder.
        Path shared = Files.createDirectories(root.resolve(".fabric/server"));
        Files.writeString(shared.resolve("fabric-loader-server-0.19.5-minecraft-26.3.jar"), "the old shared launch jar");
        Path game = Files.createDirectories(sup.work.resolve("liga-1/.fabric"));
        Files.createSymbolicLink(game.resolve("server"), game.relativize(shared));
        GameServer g = sup.slots.get(0).server;
        g.prepare(layout, null, Map.of("level-name", "world"), log, sup.minecraftDir);
        Path own = g.dir.resolve(".fabric/server");
        assertTrue(Files.isDirectory(own, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(own), "a folder of its own");
        assertTrue(Files.exists(shared.resolve("fabric-loader-server-0.19.5-minecraft-26.3.jar")), "the shared folder untouched");
        assertTrue(Files.isSymbolicLink(g.dir.resolve("mods")));
        // Prepared again (the next game): the game's own Fabric stays.
        Files.writeString(own.resolve("fabric-loader-server-0.19.5-minecraft-26.3.jar"), "this game's launch jar");
        g.prepare(layout, null, Map.of("level-name", "world"), log, sup.minecraftDir);
        assertEquals("this game's launch jar", Files.readString(own.resolve("fabric-loader-server-0.19.5-minecraft-26.3.jar")));
        Files.write(mc, new byte[11_000_000]);
        assertTrue(sup.minecraftReady());
    }

    @Test
    void aFailureWithFilesSaysWhatHeldThem() {
        String full = Disk.describe(new DirectoryNotEmptyException("C:\\Bob\\work\\liga-5\\config"));
        assertTrue(full.contains("liga-5\\config") && full.contains("OneDrive"), full);
        assertTrue(Disk.describe(new AccessDeniedException("C:\\x")).contains("gesperrt"));
        assertEquals("C:\\x.jar: used by another process", Disk.describe(new FileSystemException("C:\\x.jar", null, "used by another process")));
        assertEquals("kaputt", Disk.describe(new IOException("kaputt")));
    }

    private static Layout fabricRoot(Path root) throws IOException {
        Files.createDirectories(root.resolve("mods"));
        FakeRoot.jar(root.resolve("fabric-server-mc.26.3-loader.0.19.5-launcher.1.1.2.jar"), "net.fabricmc.installer.ServerLauncher", null,
                Map.of("install.properties", "fabric-loader-version=0.19.5\ngame-version=26.3".getBytes(StandardCharsets.UTF_8)));
        FakeRoot.jar(root.resolve("mods/challengecraft-1.1.4.jar"), null, null, Map.of(
                "fabric.mod.json", "{\"id\":\"challengecraft\",\"version\":\"1.1.4\"}".getBytes(StandardCharsets.UTF_8),
                "net/kasax/challengecraft/bot/BotVersus.class", new byte[]{1}));
        FakeRoot.jar(root.resolve("mods/fabric-api.jar"), null, null,
                Map.of("fabric.mod.json", "{\"id\":\"fabric-api\"}".getBytes(StandardCharsets.UTF_8)));
        try {
            return Layout.find(root, new Config(), null);
        } catch (Layout.Problem e) {
            throw new IOException(e);
        }
    }
}
