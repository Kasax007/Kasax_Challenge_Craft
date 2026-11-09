package net.kasax.challengecraft.bot.lockout;

import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * What Bob has learnt over many games: for each goal, how long it really took against what he
 * estimated. Kept in the config folder, so the next game starts with estimates that know "a
 * cauldron always takes four times longer than it looks".
 *
 * <p>Learnt as the logarithm of the ratio (a tile twice as quick and one twice as slow weigh the
 * same), with a little doubt while there are few games behind it: the factor is
 * {@code exp(mean · n / (n + 1.5))} - one game moves it 40 % of the way, three 67 %. It may go as
 * far as {@link #MAX} (the leagues measured mob tiles four to nine times their estimate; the old
 * band of 0.6 to 2 could never take that in). Forgetting lowers how many games it counts (10 % a
 * game), not what it learnt: a factor that keeps being confirmed stays.
 *
 * <p>What was learnt belongs to the estimates it was learnt against: when the planner's way of
 * estimating changes ({@link ObtainPlanner#ESTIMATES}), it starts afresh.
 */
public final class GoalExperience {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("challengecraft-bob-experience.properties");
    /** The band a factor stays in, and what one game's ratio is clamped to. */
    static final double MIN = 0.25, MAX = 8.0;
    /** Games' worth of doubt: with n games behind it, n / (n + PRIOR) of the learnt mean counts. */
    private static final double PRIOR = 1.5;
    /** Of the games counted, this share is forgotten each game. */
    private static final double FORGET = 0.1;
    private static Properties data;
    private static final String VERSION_KEY = "_version", ESTIMATES_KEY = "_estimates", VERSION = "3";

    private GoalExperience() {
    }

    /** One goal's record: the mean log ratio and the games (fractional: forgetting, lower bounds) behind it. */
    private record Entry(double logMean, double games) {
        double factor() {
            return clamp(Math.exp(logMean * games / (games + PRIOR)));
        }

        String text() {
            return String.format(Locale.ROOT, "%.4f,%.2f", logMean, games);
        }

        static Entry parse(String s) {
            String[] parts = s.split(",");
            return new Entry(Double.parseDouble(parts[0]), parts.length > 1 ? Double.parseDouble(parts[1]) : 1);
        }
    }

    private static double clamp(double f) {
        return Math.max(MIN, Math.min(MAX, f));
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
            String estimates = String.valueOf(ObtainPlanner.ESTIMATES);
            // (Learnt against another way of estimating, or in the old format: starts afresh -
            // a factor of 4 on the old "a skeleton, ~27 s" would make the new 250 s a thousand.)
            if (!VERSION.equals(data.getProperty(VERSION_KEY)) || !estimates.equals(data.getProperty(ESTIMATES_KEY))) {
                if (!data.isEmpty()) BotManager.LOG.info("[Bot] goal experience learnt against older estimates: starting afresh");
                data.clear();
            }
            data.setProperty(VERSION_KEY, VERSION);
            data.setProperty(ESTIMATES_KEY, estimates);
            // Forgetting, a little each game: what went wrong long ago (a since mended blunder, a
            // map that happened to be awkward) weighs less and less - unless it happens again.
            for (String goal : data.stringPropertyNames()) {
                if (goal.startsWith("_")) continue;
                try {
                    Entry e = Entry.parse(data.getProperty(goal));
                    data.setProperty(goal, new Entry(e.logMean(), e.games() * (1 - FORGET)).text());
                } catch (RuntimeException ex) {
                    data.remove(goal);
                }
            }
        }
        return data;
    }

    /**
     * The factor after these games' ratios (real / estimate), one game after the other from
     * nothing, forgetting as between games - the learning rule on its own, without the file (for
     * the tests: four games of "nine times the estimate" must leave far more than the old cap of 2).
     */
    public static double learnt(double... ratios) {
        Entry e = new Entry(0, 0);
        for (double r : ratios) {
            e = new Entry(e.logMean(), e.games() * (1 - FORGET));
            double games = e.games() + 1;
            e = new Entry(e.logMean() + (Math.log(clamp(r)) - e.logMean()) / games, games);
        }
        return e.factor();
    }

    /** By how much to multiply the estimate for this goal (1 when nothing is known yet). */
    static double factor(String goalId) {
        if (goalId.startsWith("_")) return 1;
        String v = data().getProperty(goalId);
        if (v == null) return 1;
        try {
            return Entry.parse(v).factor();
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** Records one game's outcome: {@code actualSeconds} for what was estimated at {@code estimate}. */
    static void record(String goalId, double estimate, double actualSeconds) {
        record(goalId, estimate, actualSeconds, 1);
    }

    /**
     * {@code certainty} below 1 for a lower bound only (given up on: it would have taken at least
     * that long) - it counts as that share of a game, and only ever pushes the factor up.
     */
    static void record(String goalId, double estimate, double actualSeconds, double certainty) {
        if (estimate <= 0 || goalId.startsWith("_")) return;
        double ratio = Math.log(clamp(actualSeconds / estimate));
        Entry old;
        try {
            old = data().containsKey(goalId) ? Entry.parse(data().getProperty(goalId)) : new Entry(0, 0);
        } catch (RuntimeException e) {
            old = new Entry(0, 0);
        }
        // (A lower bound below what is believed already says nothing new.)
        if (certainty < 1 && ratio <= old.logMean()) return;
        double games = old.games() + certainty;
        double mean = old.logMean() + certainty * (ratio - old.logMean()) / games;
        data().setProperty(goalId, new Entry(mean, games).text());
        try (Writer w = Files.newBufferedWriter(FILE)) {
            data().store(w, "Bob's experience: per goal, the log of how much longer (or shorter) it takes than estimated, and the games behind it");
        } catch (IOException e) {
            BotManager.LOG.warn("[Bot] could not write {}: {}", FILE, e.toString());
        }
    }
}
