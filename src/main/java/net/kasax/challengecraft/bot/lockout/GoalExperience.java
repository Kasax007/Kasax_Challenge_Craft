package net.kasax.challengecraft.bot.lockout;

import net.kasax.challengecraft.bot.BotManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What Bob has learnt over many games: for each goal, how long it really took against what he
 * estimated (a running average of the ratio). Kept in the config folder, so the next game starts
 * with estimates that know "a cauldron always takes four times longer than it looks".
 */
final class GoalExperience {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("challengecraft-bob-experience.properties");
    // (Kept to a narrow band: an estimate already knows what Bob holds and knows about the world;
    // experience only corrects its habitual bias, it must not drown it.)
    private static final double MIN = 0.6, MAX = 2.0, WEIGHT = 0.25;
    private static Properties data;

    private GoalExperience() {
    }

    private static Properties data() {
        if (data == null) {
            data = new Properties();
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    data.load(r);
                } catch (IOException e) {
                    BotManager.LOG.warn("[Bot] could not read {}: {}", FILE, e.toString());
                }
            }
        }
        return data;
    }

    /** By how much to multiply the estimate for this goal (1 when nothing is known yet). */
    static double factor(String goalId) {
        String v = data().getProperty(goalId);
        if (v == null) return 1;
        try {
            return Math.max(MIN, Math.min(MAX, Double.parseDouble(v.split(",")[0])));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Records one game's outcome: {@code actualSeconds} for what was estimated at {@code estimate}. */
    static void record(String goalId, double estimate, double actualSeconds) {
        if (estimate <= 0) return;
        double ratio = Math.max(MIN, Math.min(MAX, actualSeconds / estimate));
        String old = data().getProperty(goalId);
        double f = ratio;
        int n = 1;
        if (old != null) {
            String[] parts = old.split(",");
            try {
                double prev = Double.parseDouble(parts[0]);
                n = parts.length > 1 ? Integer.parseInt(parts[1]) + 1 : 2;
                f = prev + WEIGHT * (ratio - prev);
            } catch (NumberFormatException ignored) {
                // start afresh
            }
        }
        data().setProperty(goalId, String.format(java.util.Locale.ROOT, "%.3f,%d", f, n));
        try (Writer w = Files.newBufferedWriter(FILE)) {
            data().store(w, "Bob's experience: per goal, how much longer (or shorter) it takes than estimated, and how often seen");
        } catch (IOException e) {
            BotManager.LOG.warn("[Bot] could not write {}: {}", FILE, e.toString());
        }
    }
}
