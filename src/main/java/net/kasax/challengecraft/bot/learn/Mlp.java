package net.kasax.challengecraft.bot.learn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A small neural network as the trainer exports it (JSON): the inputs standardised (mean and
 * spread from the training data), then dense layers, each {@code out = act(W in + b)} with
 * {@code W} as rows of the layer's outputs. Plain Java, no library: a forward pass is a few
 * thousand multiplications - nothing next to one path search.
 */
public final class Mlp {
    private final double[] mean, std;
    private final double[][][] w;
    private final double[][] b;
    private final boolean[] relu;
    public final int inputs, outputs;

    private Mlp(double[] mean, double[] std, double[][][] w, double[][] b, boolean[] relu) {
        this.mean = mean;
        this.std = std;
        this.w = w;
        this.b = b;
        this.relu = relu;
        this.inputs = mean.length;
        this.outputs = b[b.length - 1].length;
    }

    static double[] vector(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        double[] v = new double[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsDouble();
        return v;
    }

    /** From the trainer's JSON ({@code mean}, {@code std}, {@code layers: [{w, b, act}]}); checks the shapes. */
    public static Mlp parse(JsonObject o) {
        double[] mean = vector(o.get("mean")), std = vector(o.get("std"));
        if (mean.length != std.length) throw new IllegalArgumentException("mean and std differ in length");
        JsonArray layers = o.getAsJsonArray("layers");
        double[][][] w = new double[layers.size()][][];
        double[][] b = new double[layers.size()][];
        boolean[] relu = new boolean[layers.size()];
        int in = mean.length;
        for (int l = 0; l < layers.size(); l++) {
            JsonObject layer = layers.get(l).getAsJsonObject();
            JsonArray rows = layer.getAsJsonArray("w");
            w[l] = new double[rows.size()][];
            for (int r = 0; r < rows.size(); r++) {
                w[l][r] = vector(rows.get(r));
                if (w[l][r].length != in) throw new IllegalArgumentException("layer " + l + " row " + r + ": " + w[l][r].length + " inputs, not " + in);
            }
            b[l] = vector(layer.get("b"));
            if (b[l].length != rows.size()) throw new IllegalArgumentException("layer " + l + ": bias and rows differ");
            relu[l] = "relu".equals(layer.has("act") ? layer.get("act").getAsString() : "linear");
            in = rows.size();
        }
        return new Mlp(mean, std, w, b, relu);
    }

    /** The network's outputs for these (raw, not standardised) inputs. */
    public double[] apply(double[] x) {
        if (x.length != inputs) throw new IllegalArgumentException(x.length + " inputs, the model wants " + inputs);
        double[] v = new double[inputs];
        for (int i = 0; i < inputs; i++) v[i] = (x[i] - mean[i]) / (std[i] > 1e-9 ? std[i] : 1);
        for (int l = 0; l < w.length; l++) {
            double[] out = new double[w[l].length];
            for (int r = 0; r < out.length; r++) {
                double s = b[l][r];
                double[] row = w[l][r];
                for (int i = 0; i < row.length; i++) s += row[i] * v[i];
                out[r] = relu[l] && s < 0 ? 0 : s;
            }
            v = out;
        }
        return v;
    }

    static double sigmoid(double z) {
        return 1 / (1 + Math.exp(-Math.max(-40, Math.min(40, z))));
    }
}
