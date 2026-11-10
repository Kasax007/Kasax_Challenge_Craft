package net.kasax.challengecraft.autopilot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Stands in for a Minecraft server with Challenge Craft in the tests (run as its own Java, from a
 * jar the test packs): says it is up as a server does, writes Bob's status file, and answers the
 * console as the mod would - a versus game ends at once with a {@code [BOTVS] result} line, the
 * side whose knobs have {@code guardian=1} taking five tiles, the other two. {@code -Dfake.mode=crash}:
 * it breaks down at the start.
 */
public final class FakeServer {
    private FakeServer() {
    }

    public static void main(String[] args) throws Exception {
        if ("crash".equals(System.getProperty("fake.mode"))) {
            System.out.println("Exception in thread \"main\" java.lang.IllegalStateException: boom");
            System.exit(1);
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(Path.of("server.properties"), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        String seed = p.getProperty("level-seed");
        Files.createDirectories(Path.of("world", "region"));
        Files.writeString(Path.of("world", "region", "r.0.0.mca"), "chunks");
        long start = System.currentTimeMillis();
        Thread status = new Thread(() -> {
            while (true) {
                long now = System.currentTimeMillis();
                String st = "{\"wall\":" + now + ",\"tick\":" + (now - start) / 10 + ",\"tps\":100.0,\"mspt\":5.0,\"memory_mb\":900}";
                try {
                    Files.writeString(Path.of("bob-status.json.tmp"), st);
                    Files.move(Path.of("bob-status.json.tmp"), Path.of("bob-status.json"), StandardCopyOption.REPLACE_EXISTING);
                    Thread.sleep(200);
                } catch (Exception e) {
                    return;
                }
            }
        });
        status.setDaemon(true);
        status.start();
        System.out.println("[00:00:00] [Server thread/INFO]: Done (0.123s)! For help, type \"help\"");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println("[00:00:01] [Server thread/INFO]: > " + line);
            if (line.startsWith("challengecraft_bot versus ")) {
                String[] parts = line.split(" ");
                StringBuilder bots = new StringBuilder();
                for (int i = 4; i < parts.length; i++) {
                    String[] np = parts[i].split(":", 2);
                    int tiles = np[1].contains("guardian=1") ? 5 : 2;
                    if (bots.length() > 0) bots.append(',');
                    bots.append("{\"name\":\"").append(np[0]).append("\",\"params\":\"").append(np[1]).append("\",\"tiles\":").append(tiles)
                            .append(",\"deaths\":0,\"hearts\":4.5,\"first\":120,\"m5\":0,\"m10\":1,\"m20\":2,\"m30\":3}");
                    System.out.println("[00:00:02] [Server thread/INFO]: [BOTVS] " + np[0] + " claimed Oak Log at 30 s");
                }
                System.out.println("[00:00:03] [Server thread/INFO]: [BOTVS] result {\"seed\":" + seed + ",\"secs\":" + parts[2]
                        + ",\"decided\":false,\"winner\":\"-\",\"bots\":[" + bots + "],\"sim\":\"" + System.getenv("CHALLENGECRAFT_BOT_SIM")
                        + "\",\"challenges\":\"" + System.getenv("CHALLENGECRAFT_TEST_CHALLENGES") + "\"}");
            } else if (line.equals("stop")) {
                System.out.println("[00:00:04] [Server thread/INFO]: Stopping the server");
                System.exit(0);
            }
        }
    }
}
