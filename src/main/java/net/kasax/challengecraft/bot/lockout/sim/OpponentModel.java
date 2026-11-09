package net.kasax.challengecraft.bot.lockout.sim;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * What a player of a given pace usually holds at a given minute of a game: for every minute, how
 * often it has each milestone and how many of each resource on average - learnt by playing many
 * games of the abstract game (the cheapest-first brain, both sides). The look-ahead draws the
 * opponent from it, then puts in what is known for sure (its tiles, its advancements): a picture
 * that is right on average without seeing the opponent's pack.
 */
public final class OpponentModel {
    private static final int MINUTES = 91;
    private static final Map<String, OpponentModel> CACHE = new HashMap<>();

    /** Per minute: the share of games with each milestone, the mean count of each resource, the share in the Nether / underground. */
    private final double[][] msShare = new double[MINUTES][Ms.ALL.length];
    private final double[][] resMean = new double[MINUTES][Res.N];
    private final double[] nether = new double[MINUTES], under = new double[MINUTES], dist = new double[MINUTES];

    private OpponentModel() {
    }

    /** The model for players of this profile (learnt once, from a few hundred games; about a second). */
    public static synchronized OpponentModel of(Profile p) {
        return CACHE.computeIfAbsent(p.name + "|" + p.hashKey(), k -> learn(p));
    }

    /** The model if it is learnt already; else null, and it is learnt in the background (a daemon thread). */
    public static OpponentModel ifReady(Profile p) {
        String key = p.name + "|" + p.hashKey();
        synchronized (OpponentModel.class) {
            OpponentModel m = CACHE.get(key);
            if (m != null || LEARNING.contains(key)) return m;
            LEARNING.add(key);
        }
        Thread t = new Thread(() -> {
            OpponentModel m = learn(p);
            synchronized (OpponentModel.class) {
                CACHE.put(key, m);
                LEARNING.remove(key);
            }
        }, "Bob opponent model");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return null;
    }

    private static final java.util.Set<String> LEARNING = new java.util.HashSet<>();

    private static OpponentModel learn(Profile prof) {
        OpponentModel m = new OpponentModel();
        int[] n = new int[MINUTES];
        int games = 160;
        for (int g = 0; g < games; g++) {
            long seed = SearchPolicy.mix(0xBEEF + g);
            World w = World.sample(seed);
            Board b = RandomBoards.board(seed);
            Sim sim = new Sim(w, b, prof, prof, new RolloutPolicy(false), new RolloutPolicy(false), 5400, seed);
            for (int minute = 0; minute < MINUTES && !sim.over; minute++) {
                sim.runUntil(minute * 60.0);
                for (PlayerState p : sim.players) {
                    n[minute]++;
                    for (Ms ms : Ms.ALL) if (p.has(ms)) m.msShare[minute][ms.ordinal()]++;
                    for (int r = 0; r < Res.N; r++) m.resMean[minute][r] += p.res[r];
                    if (p.dim == 1) m.nether[minute]++;
                    if (p.layer > 0) m.under[minute]++;
                    m.dist[minute] += p.dim == 0 ? Math.hypot(p.x, p.z) : 0;
                }
            }
        }
        for (int minute = 0; minute < MINUTES; minute++) {
            double k = Math.max(1, n[minute]);
            for (int i = 0; i < Ms.ALL.length; i++) m.msShare[minute][i] /= k;
            for (int r = 0; r < Res.N; r++) m.resMean[minute][r] /= k;
            m.nether[minute] /= k;
            m.under[minute] /= k;
            m.dist[minute] /= k;
            // (Minutes no game reached: as the last one that did.)
            if (n[minute] == 0 && minute > 0) {
                m.msShare[minute] = m.msShare[minute - 1].clone();
                m.resMean[minute] = m.resMean[minute - 1].clone();
                m.nether[minute] = m.nether[minute - 1];
                m.under[minute] = m.under[minute - 1];
                m.dist[minute] = m.dist[minute - 1];
            }
        }
        return m;
    }

    /**
     * The opponent drawn for one game played ahead: milestones and resources as such a player has
     * them at this minute, then what is known for sure ({@code sureMs}: advancements, what its
     * tiles took), in the dimension it is known to be in.
     */
    PlayerState draw(Sim sim, int o, int sureMs, SplittableRandom r) {
        int minute = (int) Math.min(MINUTES - 1, Math.max(0, sim.now / 60));
        PlayerState t = sim.players[o];
        PlayerState v = new PlayerState();
        int ms = sureMs;
        for (Ms m : Ms.ALL) if ((ms & m.bit) == 0 && r.nextDouble() < msShare[minute][m.ordinal()]) ms |= m.bit;
        // (What it has, it has the means to: the milestones before it.)
        for (int x = ms; x != 0; x &= x - 1) ms |= Econ.MS_CLOSURE[Integer.numberOfTrailingZeros(x)];
        v.ms = ms;
        for (int i = 0; i < Res.N; i++) {
            double mean = resMean[minute][i];
            if (mean > 0) v.res[i] = poisson(mean, r);
        }
        v.dim = t.dim;
        if (v.dim == 0) {
            double d = dist[minute] * (0.5 + r.nextDouble());
            double a = r.nextDouble() * 2 * Math.PI;
            v.x = Math.cos(a) * d;
            v.z = Math.sin(a) * d;
            v.layer = r.nextDouble() < under[minute] ? 1 : 0;
        }
        v.known = sim.world.knownAtStart(sim.profiles[o].worldsense) | (t.dim == 1 ? sim.netherKnown : 0);
        v.fedAt = sim.now + 100;
        v.busyUntil = sim.now;
        v.firstTile = t.firstTile;
        v.deaths = t.deaths;
        return v;
    }

    private static int poisson(double mean, SplittableRandom r) {
        if (mean > 30) return (int) Math.max(0, Math.round(mean + Math.sqrt(mean) * r.nextGaussian()));
        double l = Math.exp(-mean), p = 1;
        int k = 0;
        do {
            k++;
            p *= r.nextDouble();
        } while (p > l);
        return k - 1;
    }
}
