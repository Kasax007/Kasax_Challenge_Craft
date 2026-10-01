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
    private static final double MIN = 0.6, MAX = 2.0, WEIGHT = 0.25, FORGET = 0.2;
    private static Properties data;
    private static final String VERSION_KEY = "_version", VERSION = "2";

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
            // Forgetting, a little each game: what went wrong long ago (a since mended blunder, a
            // map that happened to be awkward) should not weigh on him for ever.
            // (Learnt before one game counted only a quarter: brought down to that once.)
            boolean old = !VERSION.equals(data.getProperty(VERSION_KEY));
            data.setProperty(VERSION_KEY, VERSION);
            for (String goal : data.stringPropertyNames()) {
                if (goal.equals(VERSION_KEY)) continue;
                String[] parts = data.getProperty(goal).split(",");
                try {
                    double f = Math.max(MIN, Math.min(MAX, Double.parseDouble(parts[0])));
                    int n = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    if (old) f = 1 + (f - 1) * Math.min(1, WEIGHT * n);
                    f = 1 + (f - 1) * (1 - FORGET);
                    data.setProperty(goal, String.format(java.util.Locale.ROOT, "%.3f,%s", f, parts.length > 1 ? parts[1] : "1"));
                } catch (NumberFormatException e) {
                    data.remove(goal);
                }
            }
        }
        return data;
    }

    /** By how much to multiply the estimate for this goal (1 when nothing is known yet). */
    static double factor(String goalId) {
        if (goalId.equals(VERSION_KEY)) return 1;
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
        record(goalId, estimate, actualSeconds, 1);
    }

    /**
     * {@code certainty} below 1 for a lower bound only (given up on: it would have taken at least
     * that long). One game never decides it: the first one moves the factor from 1 only a quarter
     * of the way too.
     */
    static void record(String goalId, double estimate, double actualSeconds, double certainty) {
        if (estimate <= 0) return;
        double ratio = Math.max(MIN, Math.min(MAX, actualSeconds / estimate));
        String old = data().getProperty(goalId);
        double prev = 1;
        int n = 1;
        if (old != null) {
            String[] parts = old.split(",");
            try {
                prev = Math.max(MIN, Math.min(MAX, Double.parseDouble(parts[0])));
                n = parts.length > 1 ? Integer.parseInt(parts[1]) + 1 : 2;
            } catch (NumberFormatException ignored) {
                // start afresh
            }
        }
        double f = prev + WEIGHT * certainty * (ratio - prev);
        data().setProperty(goalId, String.format(java.util.Locale.ROOT, "%.3f,%d", f, n));
        try (Writer w = Files.newBufferedWriter(FILE)) {
            data().store(w, "Bob's experience: per goal, how much longer (or shorter) it takes than estimated, and how often seen");
        } catch (IOException e) {
            BotManager.LOG.warn("[Bot] could not write {}: {}", FILE, e.toString());
        }
    }
}
