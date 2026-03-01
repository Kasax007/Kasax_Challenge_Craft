package net.kasax.challengecraft.data;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class XpManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-XP");
    private static final Path XP_FILE = FabricLoader.getInstance().getGameDir().resolve("challengecraft_xp.txt");
    private static long totalXp = 0;
    private static boolean loaded = false;

    public static synchronized long getTotalXp() {
        if (!loaded) {
            load();
        }
        return totalXp;
    }

    public static synchronized void addXp(long amount) {
        if (!loaded) {
            load();
        }
        totalXp += amount;
        LOGGER.info("Adding {} XP. New total: {}", amount, totalXp);
        save();
    }

    private static void load() {
        if (Files.exists(XP_FILE)) {
            try {
                String content = Files.readString(XP_FILE).trim();
                totalXp = Long.parseLong(content);
                LOGGER.info("Loaded total XP: {}", totalXp);
            } catch (Exception e) {
                LOGGER.error("Failed to load XP file", e);
                totalXp = 0;
            }
        } else {
            totalXp = 0;
            save();
        }
        loaded = true;
    }

    private static void save() {
        try {
            Files.writeString(XP_FILE, String.valueOf(totalXp));
            LOGGER.info("Saved total XP: {}", totalXp);
        } catch (IOException e) {
            LOGGER.error("Failed to save XP file", e);
        }
    }
}
