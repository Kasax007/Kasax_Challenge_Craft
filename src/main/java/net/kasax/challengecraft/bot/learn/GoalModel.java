package net.kasax.challengecraft.bot.learn;

import com.google.gson.JsonObject;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalDifficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * How long a tile really takes, learnt from the journal of many games: from the plan's own
 * estimate and the situation (the tile's kind, the gear, the time of day, how the game has gone),
 * the seconds it is likely to take - and how likely it is not to come off at all. The plan keeps
 * its own figure as the anchor: the model only corrects its habitual misjudgements (a "craft a
 * cauldron" that always takes four times its estimate), within a band the trainer sets.
 *
 * <p>The model is linear in its features (a ridge regression on the logarithm of the seconds,
 * trained in {@code scripts/train}), small enough to read in the JSON.
 */
public final class GoalModel {
    public static final String[] NAMES = {
            "log_estimate", "proxy", "cat_item", "cat_craft", "cat_kill", "cat_exploration", "cat_interaction", "cat_nether",
            "diff_easy", "diff_medium", "diff_hard", "diff_expert", "nether", "night", "minutes", "iron_pickaxe",
            "bucket", "shield", "armour", "hp", "food", "deaths", "tries", "overruns", "experience"};

    public final String version;
    private final double[] mean, std, w, risk;
    private final double b, riskB, minRatio, maxRatio;

    private GoalModel(String version, double[] mean, double[] std, double[] w, double b, double[] risk, double riskB, double minRatio, double maxRatio) {
        this.version = version;
        this.mean = mean;
        this.std = std;
        this.w = w;
        this.b = b;
        this.risk = risk;
        this.riskB = riskB;
        this.minRatio = minRatio;
        this.maxRatio = maxRatio;
    }

    static GoalModel parse(JsonObject o) {
        if (!"goal".equals(o.get("kind").getAsString())) throw new IllegalArgumentException("not a goal model");
        double[] mean = Mlp.vector(o.get("mean")), std = Mlp.vector(o.get("std")), w = Mlp.vector(o.get("w"));
        if (mean.length != NAMES.length || std.length != NAMES.length || w.length != NAMES.length)
            throw new IllegalArgumentException("features: " + w.length + ", the situation has " + NAMES.length);
        double[] risk = o.has("risk_w") ? Mlp.vector(o.get("risk_w")) : new double[NAMES.length];
        if (risk.length != NAMES.length) throw new IllegalArgumentException("risk features: " + risk.length);
        return new GoalModel(o.has("version") ? o.get("version").getAsString() : "?", mean, std, w, o.get("b").getAsDouble(),
                risk, o.has("risk_b") ? o.get("risk_b").getAsDouble() : -10,
                o.has("min_ratio") ? o.get("min_ratio").getAsDouble() : 0.5, o.has("max_ratio") ? o.get("max_ratio").getAsDouble() : 3);
    }

    /** The situation's numbers for a tile (see {@link #NAMES}). */
    public static double[] features(Bot bot, LockoutBingoGoal goal, double estimate, boolean proxy, double minutes, int deaths,
                                     int tries, int overruns, double experience) {
        BotPlayer body = bot.body();
        double[] x = new double[NAMES.length];
        x[0] = Math.log1p(Math.max(0, estimate));
        x[1] = proxy ? 1 : 0;
        LockoutBingoGoalCategory c = goal.category();
        x[2] = c == LockoutBingoGoalCategory.ITEM ? 1 : 0;
        x[3] = c == LockoutBingoGoalCategory.CRAFT ? 1 : 0;
        x[4] = c == LockoutBingoGoalCategory.KILL ? 1 : 0;
        x[5] = c == LockoutBingoGoalCategory.EXPLORATION ? 1 : 0;
        x[6] = c == LockoutBingoGoalCategory.INTERACTION ? 1 : 0;
        x[7] = c == LockoutBingoGoalCategory.NETHER ? 1 : 0;
        LockoutBingoGoalDifficulty d = goal.difficulty();
        x[8] = d == LockoutBingoGoalDifficulty.EASY ? 1 : 0;
        x[9] = d == LockoutBingoGoalDifficulty.MEDIUM ? 1 : 0;
        x[10] = d == LockoutBingoGoalDifficulty.HARD ? 1 : 0;
        x[11] = d == LockoutBingoGoalDifficulty.EXPERT ? 1 : 0;
        Level level = body.level();
        x[12] = level.dimension() == Level.NETHER ? 1 : 0;
        x[13] = level.dimension() == Level.OVERWORLD && level.isDarkOutside() ? 1 : 0;
        x[14] = Math.min(3, minutes / 90);
        // (Counted by item, not by stack: a worn pickaxe is still one.)
        x[15] = has(body, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE) ? 1 : 0;
        x[16] = has(body, Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET) ? 1 : 0;
        x[17] = has(body, Items.SHIELD) ? 1 : 0;
        x[18] = body.getArmorValue() / 20.0;
        x[19] = body.getHealth() / 20;
        x[20] = body.getFoodData().getFoodLevel() / 20.0;
        x[21] = Math.min(5, deaths) / 5.0;
        x[22] = Math.min(3, tries) / 3.0;
        x[23] = Math.min(3, overruns) / 3.0;
        x[24] = experience;
        return x;
    }

    private static boolean has(BotPlayer body, net.minecraft.world.item.Item... items) {
        return net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(body, java.util.Set.of(items)) > 0;
    }

    private double dot(double[] weights, double bias, double[] x) {
        double s = bias;
        for (int i = 0; i < weights.length; i++) s += weights[i] * (x[i] - mean[i]) / (std[i] > 1e-9 ? std[i] : 1);
        return s;
    }

    /** The seconds the tile is likely to take (the estimate corrected, within the band). */
    public double seconds(double estimate, double[] x) {
        double predicted = Math.expm1(dot(w, b, x));
        double base = Math.max(1, estimate);
        return Math.max(minRatio * base, Math.min(maxRatio * base, predicted));
    }

    /** The chance it does not come off (given up, failed, died at it). */
    public double failChance(double[] x) {
        return Mlp.sigmoid(dot(risk, riskB, x));
    }
}
