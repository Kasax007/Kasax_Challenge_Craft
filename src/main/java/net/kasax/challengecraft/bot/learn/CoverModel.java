package net.kasax.challengecraft.bot.learn;

import com.google.gson.JsonObject;

import java.util.Set;

/**
 * The learnt cover model: for the situation (see {@link Situation#NAMES}) and each way out, what
 * it is likely to cost - the chance of dying, the hearts lost, the seconds it takes - learnt from
 * the arena's fights and the games. The way taken is the cheapest of those possible there, the
 * costs weighed as the trainer set them (a death against hearts against seconds).
 */
public final class CoverModel {
    public final String version;
    private final Mlp net;
    private final CoverOption[] options;
    private final int heads;
    private final double wDeath, wHp, wSecs;

    private CoverModel(String version, Mlp net, CoverOption[] options, int heads, double wDeath, double wHp, double wSecs) {
        this.version = version;
        this.net = net;
        this.options = options;
        this.heads = heads;
        this.wDeath = wDeath;
        this.wHp = wHp;
        this.wSecs = wSecs;
    }

    static CoverModel parse(JsonObject o) {
        if (!"cover".equals(o.get("kind").getAsString())) throw new IllegalArgumentException("not a cover model");
        Mlp net = Mlp.parse(o);
        if (net.inputs != Situation.NAMES.length) throw new IllegalArgumentException(net.inputs + " inputs, the situation has " + Situation.NAMES.length);
        var opts = o.getAsJsonArray("options");
        CoverOption[] options = new CoverOption[opts.size()];
        for (int i = 0; i < options.length; i++) options[i] = CoverOption.valueOf(opts.get(i).getAsString());
        int heads = o.getAsJsonArray("heads").size();
        if (heads != 3 || net.outputs != options.length * heads) throw new IllegalArgumentException("outputs: " + net.outputs + " for " + options.length + " options x " + heads);
        JsonObject cost = o.getAsJsonObject("cost");
        return new CoverModel(o.has("version") ? o.get("version").getAsString() : "?", net, options,
                heads, cost.get("death").getAsDouble(), cost.get("hp").getAsDouble(), cost.get("secs").getAsDouble());
    }

    /** What it costs, by the model, per option: {death chance, hearts lost, seconds}. */
    public double[][] predict(double[] situation) {
        double[] out = net.apply(situation);
        double[][] p = new double[CoverOption.ALL.length][];
        for (int i = 0; i < options.length; i++) {
            int o = i * heads;
            p[options[i].ordinal()] = new double[]{Mlp.sigmoid(out[o]), Math.max(0, out[o + 1]), Math.max(0, out[o + 2])};
        }
        return p;
    }

    public double cost(double[] prediction) {
        return prediction == null ? Double.MAX_VALUE : wDeath * prediction[0] + wHp * prediction[1] + wSecs * prediction[2];
    }

    /** The cheapest of the possible ways by the model (null: none of them known to it). */
    public CoverOption best(double[][] prediction, Set<CoverOption> possible) {
        CoverOption best = null;
        double c = Double.MAX_VALUE;
        for (CoverOption o : possible) {
            double v = cost(prediction[o.ordinal()]);
            if (v < c) {
                c = v;
                best = o;
            }
        }
        return best;
    }
}
