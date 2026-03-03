package net.kasax.challengecraft.data;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class XpManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-XP");
    private static final Path XP_FILE = FabricLoader.getInstance().getGameDir().resolve("challengecraft_xp.txt");
    private static final Map<UUID, Long> playerXp = new HashMap<>();
    private static boolean loaded = false;

    public static synchronized long getXp(UUID uuid) {
        if (!loaded) {
            load();
        }
        return playerXp.getOrDefault(uuid, 0L);
    }

    public static synchronized void addXp(UUID uuid, long amount) {
        if (!loaded) {
            load();
        }
        long current = playerXp.getOrDefault(uuid, 0L);
        setXp(uuid, current + amount);
    }

    public static synchronized void setXp(UUID uuid, long xp) {
        if (!loaded) {
            load();
        }
        playerXp.put(uuid, xp);
        LOGGER.info("Setting XP for {}. Total: {}", uuid, xp);
        save();
    }
    
    // For legacy support or singleplayer client-side
    public static synchronized long getTotalXp() {
        if (!loaded) {
            load();
        }
        // If we have only one entry (likely singleplayer), return it.
        // Otherwise return 0 or the first one.
        return playerXp.values().stream().findFirst().orElse(0L);
    }

    private static void load() {
        if (Files.exists(XP_FILE)) {
            try {
                List<String> lines = Files.readAllLines(XP_FILE);
                playerXp.clear();
                for (String line : lines) {
                    String[] parts = line.trim().split("=");
                    if (parts.length == 2) {
                        try {
                            playerXp.put(UUID.fromString(parts[0]), Long.parseLong(parts[1]));
                        } catch (Exception e) {
                             // Ignore malformed
                        }
                    } else if (parts.length == 1 && !parts[0].isEmpty()) {
                        // Compatibility with old format (just a number)
                        try {
                             playerXp.put(UUID.fromString("00000000-0000-0000-0000-000000000000"), Long.parseLong(parts[0]));
                        } catch (Exception e) {}
                    }
                }
                LOGGER.info("Loaded {} player XP records", playerXp.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load XP file", e);
            }
        }
        loaded = true;
    }

    public static synchronized void save() {
        try {
            StringBuilder sb = new StringBuilder();
            playerXp.forEach((u, x) -> sb.append(u.toString()).append("=").append(x).append("\n"));
            Files.writeString(XP_FILE, sb.toString());
            LOGGER.info("Saved {} XP records", playerXp.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save XP file", e);
        }
    }
}
