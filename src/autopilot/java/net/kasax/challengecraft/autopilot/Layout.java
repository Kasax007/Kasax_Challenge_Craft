package net.kasax.challengecraft.autopilot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The server's folder as the panel set it up, and what a game's own folder takes from it: the
 * Fabric server jar (found by what it starts, whatever it is named), the libraries and Minecraft's
 * own jar (linked: read only, large), the mods (linked), the configs (copied: a mod may write its
 * config at the start, and two games writing one file could break it). Nothing else of the
 * server's folder is touched - its world, its server.properties, its player lists stay as they are.
 */
final class Layout {
    final Path root;
    final Path fabricJar;
    final Path mods;
    /** The Challenge Craft jar in the mods folder, and what it says of itself. */
    final Path modJar;
    final String modVersion, modCommit, modSha;

    private Layout(Path root, Path fabricJar, Path mods, Path modJar, String modVersion, String modCommit, String modSha) {
        this.root = root;
        this.fabricJar = fabricJar;
        this.mods = mods;
        this.modJar = modJar;
        this.modVersion = modVersion;
        this.modCommit = modCommit;
        this.modSha = modSha;
    }

    /** What is wrong with the server's folder, said so a person can put it right. */
    static final class Problem extends Exception {
        private static final long serialVersionUID = 1L;

        Problem(String message) {
            super(message);
        }
    }

    static Layout find(Path root, Config cfg, Path ownJar) throws Problem {
        Path fabric = fabricJar(root, cfg.serverJar, ownJar);
        Path mods = root.resolve(cfg.modsFolder).normalize();
        if (!Files.isDirectory(mods)) throw new Problem("Der Mods-Ordner " + cfg.modsFolder + " fehlt.");
        Path mod = null, api = null;
        try (Stream<Path> s = Files.list(mods)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!p.getFileName().toString().endsWith(".jar")) continue;
                String id = modId(p);
                if ("challengecraft".equals(id)) {
                    if (mod != null) throw new Problem("Zwei Challenge-Craft-Jars im Mods-Ordner (" + mod.getFileName() + " und "
                            + p.getFileName() + "): bitte nur eine lassen.");
                    mod = p;
                }
                if ("fabric-api".equals(id)) api = p;
            }
        } catch (IOException e) {
            throw new Problem("Der Mods-Ordner ist nicht lesbar: " + e.getMessage());
        }
        if (mod == null) throw new Problem("Im Mods-Ordner ist keine Challenge-Craft-Jar.");
        if (api == null) throw new Problem("Im Mods-Ordner fehlt Fabric API (fabric-api-...jar).");
        if (!hasEntry(mod, "net/kasax/challengecraft/bot/BotVersus.class")) {
            throw new Problem(mod.getFileName() + " kennt Bob noch nicht: bitte die Challenge-Craft-Jar mit Bob nehmen (aus dem Chat).");
        }
        String version = "?", commit = "-";
        try (JarFile jar = new JarFile(mod.toFile())) {
            Manifest mf = jar.getManifest();
            if (mf != null && mf.getMainAttributes().getValue("Bob-Commit") != null) commit = mf.getMainAttributes().getValue("Bob-Commit");
            ZipEntry e = jar.getEntry("fabric.mod.json");
            if (e != null) {
                try (InputStream in = jar.getInputStream(e)) {
                    version = Json.str(Json.map(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))).get("version"), "?");
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new Problem(mod.getFileName() + " ist keine lesbare Jar: " + e.getMessage());
        }
        return new Layout(root, fabric, mods, mod, version, commit, sha(mod));
    }

    /**
     * The jar that starts Fabric: the one named in the settings, else the root's jar whose manifest
     * starts a Fabric launcher (the panel may have named it anything). The autopilot's own jar and
     * Minecraft's are not it.
     */
    static Path fabricJar(Path root, String setting, Path ownJar) throws Problem {
        if (setting != null && !setting.isBlank() && !setting.equalsIgnoreCase("auto")) {
            Path p = root.resolve(setting).normalize();
            if (!Files.isRegularFile(p)) throw new Problem("server_jar=" + setting + ": die Datei gibt es nicht.");
            return p;
        }
        List<Path> found = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar") || !Files.isRegularFile(p)) continue;
                if (ownJar != null && sameFile(p, ownJar)) continue;
                String main = mainClass(p);
                if (main != null && main.startsWith("net.fabricmc.")) found.add(p);
            }
        } catch (IOException e) {
            throw new Problem("Der Server-Ordner ist nicht lesbar: " + e.getMessage());
        }
        if (found.isEmpty()) {
            throw new Problem("Keine Fabric-Server-Jar im Hauptordner gefunden. Bitte Fabric fuer Minecraft 26.3 installieren "
                    + "(oder in " + Config.FILE + " server_jar=<Name der Jar> eintragen).");
        }
        // (Two of them: the one the Fabric installer names, else the first by name.)
        found.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        for (Path p : found) if (p.getFileName().toString().equals("fabric-server-launch.jar")) return p;
        return found.get(0);
    }

    /**
     * The root's entries a game's folder links to (they are only read): every jar but our own,
     * the libraries and versions Minecraft unpacked, and Fabric's downloaded server files.
     */
    List<Path> linkedEntries(Path ownJar) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String name = p.getFileName().toString();
                boolean jar = name.toLowerCase(Locale.ROOT).endsWith(".jar") && Files.isRegularFile(p);
                if (jar && (ownJar == null || !sameFile(p, ownJar)) || (name.equals("libraries") || name.equals("versions")) && Files.isDirectory(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Whether the Fabric jar is the downloading launcher (fabricmc.net's server jar): it keeps Minecraft in .fabric/server. */
    boolean fabricKeepsServerInDotFabric() {
        return "net.fabricmc.installer.ServerLauncher".equals(mainClass(fabricJar));
    }

    static String mainClass(Path jar) {
        try (JarFile j = new JarFile(jar.toFile())) {
            Manifest mf = j.getManifest();
            return mf == null ? null : mf.getMainAttributes().getValue("Main-Class");
        } catch (IOException e) {
            return null;
        }
    }

    /** A mod jar's id (fabric.mod.json), or null. */
    static String modId(Path jar) {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            ZipEntry e = z.getEntry("fabric.mod.json");
            if (e == null) return null;
            try (InputStream in = z.getInputStream(e)) {
                return Json.str(Json.map(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))).get("id"), null);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** A file in a jar, as text (null when it is not there). */
    static String entry(Path jar, String name) {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            ZipEntry e = z.getEntry(name);
            if (e == null) return null;
            try (InputStream in = z.getInputStream(e)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return null;
        }
    }

    static boolean hasEntry(Path jar, String name) {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            return z.getEntry(name) != null;
        } catch (IOException e) {
            return false;
        }
    }

    static String sha(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest()).substring(0, 12);
        } catch (Exception e) {
            return "-";
        }
    }

    static boolean sameFile(Path a, Path b) {
        try {
            return Files.exists(a, LinkOption.NOFOLLOW_LINKS) && Files.exists(b) && Files.isSameFile(a, b);
        } catch (IOException e) {
            return false;
        }
    }
}
