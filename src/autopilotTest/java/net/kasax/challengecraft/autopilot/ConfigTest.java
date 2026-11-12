package net.kasax.challengecraft.autopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
    @TempDir
    Path tmp;

    @Test
    void writesTheExplainedDefaultsWhenThereIsNoFile() throws IOException {
        Path f = tmp.resolve(Config.FILE);
        List<String> warnings = new ArrayList<>();
        Config c = Config.load(f, warnings::add);
        assertTrue(Files.exists(f));
        assertTrue(warnings.isEmpty(), warnings.toString());
        Config d = new Config();
        // (The template says what the code's defaults are.)
        assertEquals(d.leagueGames, c.leagueGames);
        assertEquals(d.arenaServers, c.arenaServers);
        assertEquals(d.gameMinutes, c.gameMinutes);
        assertEquals(d.gameMemoryMb, c.gameMemoryMb);
        assertEquals(d.arenaMemoryMb, c.arenaMemoryMb);
        assertEquals(d.journalMaxMb, c.journalMaxMb);
        assertEquals(d.gameLogsMaxMb, c.gameLogsMaxMb);
        assertEquals(d.exportHours, c.exportHours);
        assertEquals(d.arenaBots, c.arenaBots);
        assertEquals(d.viewDistance, c.viewDistance);
        assertEquals(d.simulationDistance, c.simulationDistance);
        assertEquals(d.searchThreads, c.searchThreads);
        assertEquals(d.exportMaxMb, c.exportMaxMb);
        assertEquals(d.keepExports, c.keepExports);
        assertEquals(d.machine, c.machine);
    }

    @Test
    void aWrongValueKeepsItsDefaultAndSaysSo() throws IOException {
        Path f = Files.writeString(tmp.resolve(Config.FILE), """
                league_games=drei
                game_minutes=0
                game_memory_mb=6144
                difficulty=Peaceful
                export_hours=1,5
                seeds=1, -2 x 3
                colour=blue
                """);
        List<String> warnings = new ArrayList<>();
        Config c = Config.load(f, warnings::add);
        assertEquals("auto", c.leagueGames);
        assertEquals(90, c.gameMinutes);
        assertEquals(6144, c.gameMemoryMb);
        assertEquals("hard", c.difficulty);
        assertEquals(1.5, c.exportHours);
        assertEquals(List.of(1L, -2L, 3L), c.seedList());
        assertEquals(4, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("colour")));
    }

    @Test
    void extraFlagsAreSplitOnSpaces() {
        Config c = new Config();
        c.extraJvmFlags = "  -XX:+AlwaysPreTouch   -Dx=1 ";
        assertEquals(List.of("-XX:+AlwaysPreTouch", "-Dx=1"), c.extraFlags());
    }

    @Test
    void autoFitsMemoryAndCores() {
        // A 22 GB server: three games of 4 GB fit (5.3 GB each with what Java needs beyond the heap).
        assertEquals(3, Host.autoLeagueGames(22528, 16, 4096, 0, 3072));
        // ... two beside two arenas.
        assertEquals(2, Host.autoLeagueGames(22528, 16, 4096, 2, 3072));
        // Four cores carry two games running flat out.
        assertEquals(2, Host.autoLeagueGames(22528, 4, 4096, 0, 3072));
        // At least one, however small.
        assertEquals(1, Host.autoLeagueGames(4096, 1, 4096, 0, 3072));
    }

    /** Windows paths as people write them: backslashes are separators, not escapes (C:\\users would be a broken \\u escape). */
    @Test
    void windowsPathsAreTakenAsWritten() throws IOException {
        Path f = tmp.resolve(Config.FILE);
        Files.writeString(f, "work_dir=D:\\Bob-Welten\njava = \"C:\\users\\lars\\Java\\bin\\java.exe\"\nserver_jar=fabric\\\\server.jar\n"
                + "# work_dir=C:\\nicht\\das\nmods_folder=//nas/share/mods\n");
        List<String> warnings = new ArrayList<>();
        Config c = Config.load(f, warnings::add);
        assertEquals("D:/Bob-Welten", c.workDir);
        assertEquals("C:/users/lars/Java/bin/java.exe", c.java);
        assertEquals("fabric/server.jar", c.serverJar);
        assertEquals("//nas/share/mods", c.modsFolder);
        assertTrue(warnings.isEmpty(), warnings.toString());
        // A broken escape elsewhere: a readable failure, no crash.
        Files.writeString(f, "machine=lap\\uZZZZ\n");
        IOException e = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> Config.load(f, warnings::add));
        assertTrue(e.getMessage().contains(Config.FILE), e.getMessage());
    }
}
