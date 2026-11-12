package net.kasax.challengecraft.autopilot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LayoutTest {
    @TempDir
    Path tmp;

    /** The panel may have named the Fabric jar anything: it is found by what it starts. */
    @Test
    void findsFabricsJarByWhatItStarts() throws Exception {
        Path root = FakeRoot.create(tmp, "");
        FakeRoot.jar(root.resolve("server.jar"), "net.minecraft.bundler.Main", null, Map.of());
        FakeRoot.jar(root.resolve("fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar"), "net.fabricmc.installer.ServerLauncher", null, Map.of());
        Path own = root.resolve("bob-autopilot.jar");
        FakeRoot.jar(own, "net.kasax.challengecraft.autopilot.Autopilot", null, Map.of());
        Config cfg = new Config();
        Layout l = Layout.find(root, cfg, own);
        assertEquals("fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar", l.fabricJar.getFileName().toString());
        assertTrue(l.fabricKeepsServerInDotFabric());
        assertEquals("1.1.4", l.modVersion);
        assertEquals("abc1234567", l.modCommit);
        assertEquals("challengecraft-1.1.4.jar", l.modJar.getFileName().toString());
        // Everything to link, but not our own jar.
        assertTrue(l.linkedEntries(own).stream().noneMatch(p -> p.equals(own)));
        // (With Fabric's downloading launcher each game installs its own libraries: shared, every new
        // game's install would write files the running games hold - on Windows impossible.)
        assertFalse(l.linkedEntries(own).contains(root.resolve("libraries")));
        // (The installer's own name wins when there are two.)
        FakeRoot.jar(root.resolve("fabric-server-launch.jar"), "net.fabricmc.loader.impl.launch.server.FabricServerLauncher", null, Map.of());
        Layout installed = Layout.find(root, cfg, own);
        assertEquals("fabric-server-launch.jar", installed.fabricJar.getFileName().toString());
        // (A server Fabric's installer set up: its libraries, unpacked once, are linked into the games.)
        assertTrue(installed.linkedEntries(own).contains(root.resolve("libraries")));
    }

    @Test
    void saysWhatIsMissing() throws Exception {
        Path root = FakeRoot.create(tmp, "");
        Config cfg = new Config();
        Layout.Problem p = assertThrows(Layout.Problem.class, () -> Layout.find(root, cfg, null));
        assertTrue(p.getMessage().contains("Fabric"), p.getMessage());
        cfg.serverJar = "fake-server.jar";
        Layout.find(root, cfg, null);
        Files.delete(root.resolve("mods/fabric-api-0.161.0+26.3.jar"));
        assertTrue(assertThrows(Layout.Problem.class, () -> Layout.find(root, cfg, null)).getMessage().contains("Fabric API"));
        // A Challenge Craft without Bob (the public release before him) is refused with a reason.
        FakeRoot.jar(root.resolve("mods/fabric-api.jar"), null, null, Map.of("fabric.mod.json", "{\"id\":\"fabric-api\"}".getBytes(StandardCharsets.UTF_8)));
        FakeRoot.jar(root.resolve("mods/challengecraft-1.1.4.jar"), null, null,
                Map.of("fabric.mod.json", "{\"id\":\"challengecraft\",\"version\":\"1.1.4\"}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(assertThrows(Layout.Problem.class, () -> Layout.find(root, cfg, null)).getMessage().contains("Bob"));
    }

    @Test
    void readsModIds() throws IOException {
        Path jar = tmp.resolve("x.jar");
        FakeRoot.jar(jar, null, null, Map.of("fabric.mod.json", "{\"id\":\"lithium\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals("lithium", Layout.modId(jar));
        Files.writeString(tmp.resolve("broken.jar"), "not a zip");
        assertNull(Layout.modId(tmp.resolve("broken.jar")));
    }

    /** What the autopilot reads from a server's output. */
    @Test
    void readsTheServersOutput() {
        GameServer g = new GameServer("liga-1", GameServer.Kind.LEAGUE, tmp, tmp);
        g.look("[12:00:00] [Server thread/INFO]: Preparing spawn area");
        assertTrue(!g.ready() && g.result() == null && g.hint() == null);
        g.look("[12:00:01] [Server thread/INFO]: Done (8.123s)! For help, type \"help\"");
        assertTrue(g.ready());
        g.look("[12:01:00] [Server thread/INFO]: [BOTVS] BobA claimed Oak Log at 31 s");
        g.look("[12:01:10] [Server thread/INFO]: [BOTVS] BobA claimed Bread at 95 s");
        g.look("[12:01:20] [Server thread/INFO]: [BOTVS] BobB claimed Shield at 140 s");
        assertEquals(Map.of("BobA", 2, "BobB", 1), g.claims());
        g.look("[12:02:00] [Server thread/INFO]: [BOTVS] result {\"seed\":1,\"bots\":[]}");
        assertEquals("{\"seed\":1,\"bots\":[]}", g.result());
        g.look("java.lang.OutOfMemoryError: Java heap space");
        g.look("Exception in server tick loop");
        assertTrue(g.hint().contains("OutOfMemoryError"), "the first trouble is kept");
    }

    @Test
    void aGameServerListensOnlyOnThisMachine() throws IOException {
        Map<String, String> p = GameServer.properties(new Config(), GameServer.Kind.LEAGUE, -7L, "liga-1");
        assertEquals("127.0.0.1", p.get("server-ip"));
        assertEquals("false", p.get("enable-rcon"));
        assertEquals("false", p.get("enable-query"));
        assertEquals("-7", p.get("level-seed"));
        assertEquals("12", p.get("view-distance"));
        assertEquals("8", p.get("simulation-distance"));
        assertEquals("-1", p.get("pause-when-empty-seconds"));
        assertTrue(Integer.parseInt(p.get("server-port")) > 0);
        Map<String, String> arena = GameServer.properties(new Config(), GameServer.Kind.ARENA, 1L, "arena-1");
        assertEquals("6", arena.get("view-distance"));
    }

    @Test
    void packsOnlyWholeLines() {
        assertArrayEquals("a\nb\n".getBytes(), Export.wholeLines("a\nb\nhalf".getBytes()));
        assertArrayEquals("a\n".getBytes(), Export.wholeLines("a\n".getBytes()));
        assertArrayEquals(new byte[0], Export.wholeLines("half".getBytes()));
    }
}
