package net.kasax.challengecraft.client.tutorial;

import net.fabricmc.loader.api.FabricLoader;
import net.kasax.challengecraft.ChallengeCraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Whether the first-run tutorial has been seen, and how far it got.
 *
 * <p>Stored next to the game directory rather than in a world, like {@code XpManager} and
 * {@code StatsManager}: the tutorial is about learning the mod, not about one save. Someone who
 * skipped it should stay skipped when they make their next world.
 *
 * <p>The trigger is this explicit flag and nothing else. Inferring "first launch" from
 * {@code xp == 0} would re-run the tutorial for a returning player who reinstalled, and would
 * re-run it forever for anyone who never earns XP.
 */
public final class TutorialState {
    private static final String FILE_NAME = "challengecraft_tutorial.properties";
    private static final String KEY_DONE = "completed";
    private static final String KEY_STEP = "step";

    private static boolean loaded = false;
    private static boolean completed = false;
    private static int step = 0;

    private TutorialState() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve(FILE_NAME);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path p = file();
        if (!Files.exists(p)) return;
        Properties props = new Properties();
        try (var in = Files.newInputStream(p)) {
            props.load(in);
            completed = Boolean.parseBoolean(props.getProperty(KEY_DONE, "false"));
            step = Integer.parseInt(props.getProperty(KEY_STEP, "0"));
        } catch (IOException | NumberFormatException e) {
            ChallengeCraft.LOGGER.warn("[Tutorial] Could not read {}: {}", FILE_NAME, e.toString());
        }
    }

    private static void save() {
        Properties props = new Properties();
        props.setProperty(KEY_DONE, Boolean.toString(completed));
        props.setProperty(KEY_STEP, Integer.toString(step));
        try (var out = Files.newOutputStream(file())) {
            props.store(out, "Challenge Craft first-run tutorial progress");
        } catch (IOException e) {
            ChallengeCraft.LOGGER.warn("[Tutorial] Could not write {}: {}", FILE_NAME, e.toString());
        }
    }

    /** True on the very first launch, and on any later launch that was interrupted mid-tutorial. */
    public static boolean shouldRun() {
        load();
        return !completed;
    }

    public static int getStep() {
        load();
        return step;
    }

    public static void setStep(int value) {
        load();
        if (value == step) return;
        step = value;
        save();
    }

    /** Marks it finished — used by both "reached the end" and "pressed skip". */
    public static void complete() {
        load();
        completed = true;
        save();
        ChallengeCraft.LOGGER.info("[Tutorial] Marked as completed");
    }

    /** Replays it from the beginning (the {@code /challengecraft_tutorial} command). */
    public static void reset() {
        load();
        completed = false;
        step = 0;
        save();
        ChallengeCraft.LOGGER.info("[Tutorial] Reset — will run from the first step");
    }
}
