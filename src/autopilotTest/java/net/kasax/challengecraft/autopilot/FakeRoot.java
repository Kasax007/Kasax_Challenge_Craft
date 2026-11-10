package net.kasax.challengecraft.autopilot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** A server folder as a panel sets it up, in small: the EULA, a server jar, the mods, a config. */
final class FakeRoot {
    private FakeRoot() {
    }

    /** With the stand-in server as {@code fake-server.jar}, Challenge Craft (with Bob) and Fabric API in mods. */
    static Path create(Path root, String config) throws IOException {
        Files.createDirectories(root.resolve("mods"));
        Files.createDirectories(root.resolve("config"));
        Files.createDirectories(root.resolve("libraries/net/example"));
        Files.writeString(root.resolve("libraries/net/example/lib.jar"), "a library");
        Files.writeString(root.resolve("config/some-mod.json"), "{\"a\":1}");
        Files.writeString(root.resolve("eula.txt"), "#By changing the setting below to TRUE you are indicating your agreement\neula=true\n");
        Files.writeString(root.resolve("server.properties"), "level-name=world\nmotd=The player's own server\n");
        Files.createDirectories(root.resolve("world"));
        Files.writeString(root.resolve("world/level.dat"), "the player's world");
        jar(root.resolve("fake-server.jar"), FakeServer.class.getName(), null,
                Map.of("net/kasax/challengecraft/autopilot/FakeServer.class", classBytes(FakeServer.class)));
        jar(root.resolve("mods/challengecraft-1.1.4.jar"), null, "abc1234567", Map.of(
                "fabric.mod.json", "{\"id\":\"challengecraft\",\"version\":\"1.1.4\"}".getBytes(StandardCharsets.UTF_8),
                "net/kasax/challengecraft/bot/BotVersus.class", new byte[]{1, 2, 3},
                "bob-models/state.json", Files.readAllBytes(KnobsTest.repo().resolve("src/main/resources/bob-models/state.json"))));
        jar(root.resolve("mods/fabric-api-0.161.0+26.3.jar"), null, null,
                Map.of("fabric.mod.json", "{\"id\":\"fabric-api\",\"version\":\"0.161.0+26.3\"}".getBytes(StandardCharsets.UTF_8)));
        Files.writeString(root.resolve(Config.FILE), config);
        return root;
    }

    static void jar(Path file, String mainClass, String commit, Map<String, byte[]> entries) throws IOException {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        if (commit != null) mf.getMainAttributes().putValue("Bob-Commit", commit);
        try (OutputStream o = Files.newOutputStream(file); JarOutputStream j = new JarOutputStream(o, mf)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                j.putNextEntry(new JarEntry(e.getKey()));
                j.write(e.getValue());
                j.closeEntry();
            }
        }
    }

    static byte[] classBytes(Class<?> c) throws IOException {
        try (InputStream in = c.getResourceAsStream(c.getSimpleName() + ".class")) {
            return in.readAllBytes();
        }
    }

    static String java() {
        return ProcessHandle.current().info().command().orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }
}
