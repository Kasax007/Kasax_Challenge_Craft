package net.kasax.challengecraft.bot.lockout.sim;

/**
 * Where a step of the abstract game happens. Three kinds:
 * <ul>
 * <li><b>layers</b> right below or beside the player ({@link #HERE}, {@link #CAVE}, {@link #DEEP},
 * {@link #WATER}): no walk, at most a way down or up;</li>
 * <li><b>places</b> of the Overworld at some distance (a village, a desert, a lava pool): one of each
 * per world, drawn by {@link World} from the odds below (some worlds have none within reach);</li>
 * <li><b>Nether places</b>, measured from the portal on the Nether side.</li>
 * </ul>
 * The odds are a player's experience of fresh worlds, not measured: {@code near} is the chance that
 * one lies within reach of a game (some 1500 blocks), {@code dMin..dMax} the distance of the nearest
 * one then. {@code in} puts a structure inside its biome (a desert temple in the desert).
 */
public enum Place {
    // ---- layers: wherever the player stands ----
    HERE(Kind.LAYER, 0, 1, 0, 0, null, 0),
    CAVE(Kind.LAYER, 1, 1, 0, 0, null, 0),
    DEEP(Kind.LAYER, 2, 1, 0, 0, null, 0),
    /** A river or lake: a short walk from almost anywhere. */
    WATER(Kind.LAYER, 0, 1, 0, 0, null, 0),

    // ---- the Overworld's places (surface) ----
    LAVA_POOL(Kind.SURFACE, 0, 0.95, 40, 260, null, 0),
    VILLAGE(Kind.SURFACE, 0, 0.92, 60, 480, null, 0),
    CAMP(Kind.SURFACE, 0, 0.85, 50, 360, null, 0),
    RUINED_PORTAL(Kind.SURFACE, 0, 0.95, 60, 380, null, 0),
    OCEAN(Kind.SURFACE, 0, 0.88, 60, 700, null, 0),
    WARM_OCEAN(Kind.SURFACE, 0, 0.35, 300, 1200, null, 0),
    SHIPWRECK(Kind.SURFACE, 0, 0.8, 0, 220, OCEAN, 0),
    OCEAN_RUIN(Kind.SURFACE, 0, 0.75, 0, 260, OCEAN, 0),
    MONUMENT(Kind.SURFACE, 0, 0.45, 100, 600, OCEAN, 0),
    DESERT(Kind.SURFACE, 0, 0.6, 80, 900, null, 0),
    DESERT_PYRAMID(Kind.SURFACE, 0, 0.5, 0, 300, DESERT, 0),
    SAVANNA(Kind.SURFACE, 0, 0.55, 80, 900, null, 0),
    SWAMP(Kind.SURFACE, 0, 0.55, 80, 900, null, 0),
    WITCH_HUT(Kind.SURFACE, 0, 0.35, 0, 300, SWAMP, 0),
    MANGROVE(Kind.SURFACE, 0, 0.2, 200, 1200, null, 0),
    JUNGLE(Kind.SURFACE, 0, 0.35, 150, 1100, null, 0),
    JUNGLE_TEMPLE(Kind.SURFACE, 0, 0.5, 0, 350, JUNGLE, 0),
    BAMBOO(Kind.SURFACE, 0, 0.5, 0, 250, JUNGLE, 0),
    SNOWY(Kind.SURFACE, 0, 0.55, 100, 1000, null, 0),
    IGLOO(Kind.SURFACE, 0, 0.3, 0, 300, SNOWY, 0),
    BADLANDS(Kind.SURFACE, 0, 0.15, 300, 1400, null, 0),
    MUSHROOM(Kind.SURFACE, 0, 0.06, 500, 1500, null, 0),
    CHERRY(Kind.SURFACE, 0, 0.35, 150, 1100, null, 0),
    DARK_FOREST(Kind.SURFACE, 0, 0.55, 80, 900, null, 0),
    DAPPLED(Kind.SURFACE, 0, 0.35, 150, 1100, null, 0),
    MOUNTAIN(Kind.SURFACE, 0, 0.6, 100, 900, null, 0),
    OUTPOST(Kind.SURFACE, 0, 0.55, 250, 1200, null, 0),
    MANSION(Kind.SURFACE, 0, 0.04, 1200, 1500, DARK_FOREST, 0),
    STRONGHOLD(Kind.SURFACE, 0, 0.0, 1300, 1500, null, 0),

    // ---- below a spot of the Overworld ----
    DRIPSTONE(Kind.UNDER, 1, 0.6, 60, 700, null, 0),
    LUSH(Kind.UNDER, 1, 0.55, 60, 700, null, 0),
    MINESHAFT(Kind.UNDER, 1, 0.75, 40, 450, null, 0),
    DEEP_DARK(Kind.UNDER, 2, 0.45, 150, 1000, null, 0),
    ANCIENT_CITY(Kind.UNDER, 2, 0.6, 0, 250, DEEP_DARK, 0),
    TRIAL_CHAMBERS(Kind.UNDER, 2, 0.5, 200, 900, null, 0),

    // ---- the Nether, measured from the portal there ----
    NETHER(Kind.NETHER, 0, 1, 0, 0, null, 1),
    WASTES(Kind.NETHER, 0, 0.9, 0, 160, null, 1),
    CRIMSON(Kind.NETHER, 0, 0.75, 40, 300, null, 1),
    WARPED(Kind.NETHER, 0, 0.6, 60, 320, null, 1),
    SOUL_VALLEY(Kind.NETHER, 0, 0.65, 60, 340, null, 1),
    DELTAS(Kind.NETHER, 0, 0.45, 100, 400, null, 1),
    FORTRESS(Kind.NETHER, 0, 0.95, 90, 420, null, 1),
    BASTION(Kind.NETHER, 0, 0.85, 80, 400, null, 1),

    /** The End: out of reach in a Lockout game here (no eyes, no dragon fight). */
    END(Kind.SURFACE, 0, 0.0, 0, 0, null, 0);

    public enum Kind { LAYER, SURFACE, UNDER, NETHER }

    public final Kind kind;
    /** The layer the work is on: 0 surface, 1 caves (stone), 2 deep (deepslate). */
    public final int layer;
    public final double near, dMin, dMax;
    public final Place in;
    /** 0 Overworld, 1 Nether. */
    public final int dim;

    Place(Kind kind, int layer, double near, double dMin, double dMax, Place in, int dim) {
        this.kind = kind;
        this.layer = layer;
        this.near = near;
        this.dMin = dMin;
        this.dMax = dMax;
        this.in = in;
        this.dim = dim;
    }

    /** A place with a position of its own (not a layer under the player). */
    public boolean located() {
        return kind != Kind.LAYER && this != NETHER;
    }

    /** Distance a player would guess for the nearest one, not knowing where it is. */
    public double guess() {
        double d = 0.5 * (dMin + dMax);
        return in == null ? d : in.guess() + d * 0.5;
    }

    public static final Place[] ALL = values();
}
