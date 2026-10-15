package net.kasax.challengecraft.bot.learn;

import net.kasax.challengecraft.bot.Bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Which way out the guardian takes when a fight goes badly: by the rules as they are (the order
 * the guardian has always gone by), by the learnt model (the cheapest of the ways possible, by its
 * reckoning), or - in the arena, for the trainer - at random among the possible ones, so that each
 * way is seen in every kind of situation and not only where the rules would take it.
 *
 * <p>The mode: what the arena set for the bot, else the knob {@code cover} (0: rules, 1: the model
 * where there is one - the default, 2: exploring), else the model where there is one.
 */
public final class CoverPolicy {
    public enum Mode { RULE, MODEL, EXPLORE }

    /** What was chosen, how ("rule", "model", "explore"), and what the model made of each way (null: no model). */
    public record Choice(CoverOption option, String how, double[][] predicted) {
    }

    private Mode mode;
    private double epsilon = 1;
    private String modelFile;
    private final Random random = new Random();

    /** Fixes the mode for this bot (the arena): {@code epsilon} the share of random choices when exploring, {@code modelFile} a model other than cover.json. */
    public void set(Mode mode, double epsilon, String modelFile) {
        this.mode = mode;
        this.epsilon = epsilon;
        this.modelFile = modelFile;
    }

    /** Back to the default (the knob, the model where there is one). */
    public void reset() {
        set(null, 1, null);
    }

    public Mode mode(Bot bot) {
        if (mode != null) return mode;
        double knob = bot.params.get("cover", 1);
        return knob == 0 ? Mode.RULE : knob == 2 ? Mode.EXPLORE : Mode.MODEL;
    }

    public CoverModel model() {
        return modelFile != null ? ModelStore.cover(modelFile) : ModelStore.cover();
    }

    /** The way out for this situation, of the {@code possible} ones; {@code rule}: what the rules would do. */
    public Choice choose(Bot bot, double[] situation, Set<CoverOption> possible, CoverOption rule) {
        Mode m = mode(bot);
        CoverModel model = model();
        double[][] predicted = null;
        if (model != null) {
            try {
                predicted = model.predict(situation);
            } catch (RuntimeException e) {
                predicted = null;
            }
        }
        if (m == Mode.EXPLORE && !possible.isEmpty() && random.nextDouble() < epsilon) {
            List<CoverOption> ways = new ArrayList<>(possible);
            return new Choice(ways.get(random.nextInt(ways.size())), "explore", predicted);
        }
        if (m != Mode.RULE && model != null && predicted != null) {
            CoverOption best = model.best(predicted, possible);
            if (best != null) return new Choice(best, "model", predicted);
        }
        return new Choice(rule, "rule", predicted);
    }
}
