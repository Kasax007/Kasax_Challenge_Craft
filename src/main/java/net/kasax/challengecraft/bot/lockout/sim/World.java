package net.kasax.challengecraft.bot.lockout.sim;

import java.util.SplittableRandom;

/**
 * One world of the abstract game: where the nearest place of each kind lies (or that there is
 * none within reach). Both players share it. The Overworld's places are measured from the spawn,
 * the Nether's from the portal on the Nether side.
 *
 * <p>What a player knows of it at the start follows Bob's world sense (the {@code worldsense}
 * knob, on by default): structures out to {@link #STRUCTURE_SENSE} blocks, biomes out to
 * {@link #BIOME_SENSE}, lava pools as far as one sees. Everything else is found by walking there
 * (and is then known).
 */
public final class World {
    public static final double STRUCTURE_SENSE = 500, BIOME_SENSE = 800, SIGHT = 160;
    private static final int N = Place.ALL.length;

    public final long seed;
    final boolean[] exists = new boolean[N];
    final double[] x = new double[N], z = new double[N];

    private World(long seed) {
        this.seed = seed;
    }

    /** A fresh world drawn from the odds in {@link Place}. */
    public static World sample(long seed) {
        World w = new World(seed);
        w.draw(new SplittableRandom(seed ^ 0x5DEECE66DL), null, 0);
        return w;
    }

    /**
     * The world as one player may picture it: what it knows is kept, the rest drawn anew from the
     * odds (for planning without peeking at what it has not seen).
     */
    public World guessed(long knownMask, long seed) {
        World w = new World(this.seed);
        w.draw(new SplittableRandom(seed), this, knownMask);
        return w;
    }

    private void draw(SplittableRandom r, World truth, long known) {
        for (Place p : Place.ALL) {
            int i = p.ordinal();
            if (p.kind == Place.Kind.LAYER || p == Place.NETHER) {
                exists[i] = true;
                continue;
            }
            // (Drawn either way, so a world guessed twice with the same seed comes out the same.)
            // (The nearest of a kind: anywhere between its nearest and farthest usual distance.)
            double u = r.nextDouble(), d = p.dMin + (p.dMax - p.dMin) * r.nextDouble(), a = r.nextDouble() * 2 * Math.PI;
            if (truth != null && (known & bit(p)) != 0) {
                exists[i] = truth.exists[i];
                x[i] = truth.x[i];
                z[i] = truth.z[i];
                continue;
            }
            boolean parent = p.in == null || exists[p.in.ordinal()];
            exists[i] = parent && u < p.near;
            double ox = p.in == null ? 0 : x[p.in.ordinal()], oz = p.in == null ? 0 : z[p.in.ordinal()];
            x[i] = ox + Math.cos(a) * d;
            z[i] = oz + Math.sin(a) * d;
        }
    }

    /** Puts a place where it is known to be (or marks that there is none): for a picture of a real game. */
    public void set(Place p, boolean there, double px, double pz) {
        exists[p.ordinal()] = there;
        x[p.ordinal()] = px;
        z[p.ordinal()] = pz;
    }

    public static long bit(Place p) {
        return 1L << p.ordinal();
    }

    public boolean exists(Place p) {
        return exists[p.ordinal()];
    }

    public double x(Place p) {
        return x[p.ordinal()];
    }

    public double z(Place p) {
        return z[p.ordinal()];
    }

    /** Straight-line distance of a place from the spawn (from the portal for the Nether's). */
    public double distance(Place p) {
        return Math.hypot(x[p.ordinal()], z[p.ordinal()]);
    }

    /** The places a player knows at the start of a game (world sense on or off). */
    public long knownAtStart(boolean worldsense) {
        long k = 0;
        for (Place p : Place.ALL) {
            if (p.kind == Place.Kind.LAYER || p == Place.NETHER) {
                k |= bit(p);
                continue;
            }
            if (!exists(p) || p.dim != 0) continue;
            if (distance(p) <= range(p, worldsense)) k |= bit(p);
        }
        return k;
    }

    /** The Nether's places a player knows on arriving there (world sense covers its structures and biomes). */
    public long knownInNether(boolean worldsense) {
        long k = 0;
        for (Place p : Place.ALL) {
            if (p.dim != 1 || p == Place.NETHER || !exists(p)) continue;
            if (distance(p) <= range(p, worldsense)) k |= bit(p);
        }
        return k;
    }

    /** How far a player knows places of this kind from where it stands. */
    static double range(Place p, boolean worldsense) {
        if (p == Place.LAVA_POOL || p.kind == Place.Kind.UNDER && !structure(p)) return SIGHT;
        if (!worldsense) return p.kind == Place.Kind.SURFACE && !structure(p) ? SIGHT * 1.5 : SIGHT * 0.6;
        return structure(p) ? STRUCTURE_SENSE : BIOME_SENSE;
    }

    static boolean structure(Place p) {
        return switch (p) {
            case VILLAGE, CAMP, RUINED_PORTAL, SHIPWRECK, OCEAN_RUIN, MONUMENT, DESERT_PYRAMID, WITCH_HUT, JUNGLE_TEMPLE, IGLOO,
                 OUTPOST, MANSION, STRONGHOLD, MINESHAFT, ANCIENT_CITY, TRIAL_CHAMBERS, FORTRESS, BASTION -> true;
            default -> false;
        };
    }
}
