package net.kasax.challengecraft.bot.lockout.sim;

import static net.kasax.challengecraft.bot.lockout.sim.Place.*;

/**
 * The economy of the abstract game: how each {@link Ms milestone} is made and where and how fast
 * each {@link Res resource} is gathered. Times are a good player's seconds; a {@link Profile}
 * stretches them per {@link Work kind of work} (Bob is slower at some than at others).
 *
 * <p>Gathering a resource: the first one takes {@code access} seconds on average to find (luck:
 * exponential), each one after that {@code per} seconds while staying where it is (a vein, a
 * flock). Monsters' drops are found at night on the surface, in caves at any time, in the Nether
 * always; by day on the surface they take {@link #DAY_MONSTERS} times longer.
 */
public final class Econ {
    private Econ() {
    }

    /** What a step of work is, for the pace of a {@link Profile}. */
    public enum Work { CRAFT, GATHER, ANIMAL, MONSTER, EXPLORE, NETHER, TRADE, STUNT }

    /** Monsters by day on the surface: this many times the search (waiting for the dark, looking in caves). */
    public static final double DAY_MONSTERS = 3.0;

    // ---- milestones ------------------------------------------------------------------------------
    static final int[] MS_NEEDS = new int[Ms.ALL.length];
    static final int[][] MS_USES = new int[Ms.ALL.length][];
    static final double[] MS_TIME = new double[Ms.ALL.length];
    static final Place[] MS_PLACE = new Place[Ms.ALL.length];
    static final Work[] MS_WORK = new Work[Ms.ALL.length];
    /** All milestones a milestone needs, transitively (itself not included). */
    static final int[] MS_CLOSURE = new int[Ms.ALL.length];

    // ---- resources -------------------------------------------------------------------------------
    static final Place[] RES_PLACE = new Place[Res.N];
    static final double[] RES_ACCESS = new double[Res.N], RES_PER = new double[Res.N];
    static final int[] RES_NEEDS = new int[Res.N];
    static final Work[] RES_WORK = new Work[Res.N];
    /** Found more easily in the dark (monsters' drops). */
    static final boolean[] RES_MONSTER = new boolean[Res.N];
    /**
     * How many more a player takes while at the source, when the board wants more of it later (the
     * rest of the vein, the flock): Bob's MineTask takes "the whole vein when the material is still
     * needed", up to sixteen beyond the need.
     */
    static final int[] RES_TRIP = new int[Res.N];

    private static void ms(Ms m, int needs, Place place, double seconds, Work work, Object... uses) {
        MS_NEEDS[m.ordinal()] = needs;
        MS_PLACE[m.ordinal()] = place;
        MS_TIME[m.ordinal()] = seconds;
        MS_WORK[m.ordinal()] = work;
        int[] u = new int[uses.length];
        for (int i = 0; i < uses.length; i += 2) {
            u[i] = ((Res) uses[i]).ordinal();
            u[i + 1] = (Integer) uses[i + 1];
        }
        MS_USES[m.ordinal()] = u;
    }

    private static void res(Res r, Place place, double access, double per, int needs, Work work, boolean monster, int trip) {
        RES_PLACE[r.ordinal()] = place;
        RES_ACCESS[r.ordinal()] = access;
        RES_PER[r.ordinal()] = per;
        RES_NEEDS[r.ordinal()] = needs;
        RES_WORK[r.ordinal()] = work;
        RES_MONSTER[r.ordinal()] = monster;
        RES_TRIP[r.ordinal()] = trip;
    }

    static {
        int wood = Ms.WOOD.bit, stone = Ms.STONE.bit;
        ms(Ms.WOOD, 0, HERE, 30, Work.GATHER);
        ms(Ms.STONE, wood, HERE, 12, Work.GATHER);
        ms(Ms.FURNACE, stone, HERE, 3, Work.CRAFT);
        ms(Ms.IRON_PICK, stone, HERE, 2, Work.CRAFT, Res.IRON, 3);
        ms(Ms.BUCKET, wood, HERE, 2, Work.CRAFT, Res.IRON, 3);
        ms(Ms.WATER_BUCKET, Ms.BUCKET.bit, WATER, 4, Work.GATHER);
        ms(Ms.SHIELD, wood, HERE, 2, Work.CRAFT, Res.IRON, 1);
        ms(Ms.SHEARS, wood, HERE, 2, Work.CRAFT, Res.IRON, 2);
        ms(Ms.FLINT_STEEL, wood, HERE, 2, Work.CRAFT, Res.IRON, 1, Res.FLINT, 1);
        ms(Ms.BOW, wood, HERE, 2, Work.CRAFT, Res.STRING, 3);
        ms(Ms.ROD, wood, HERE, 2, Work.CRAFT, Res.STRING, 2);
        ms(Ms.BED, wood, HERE, 2, Work.CRAFT, Res.WOOL, 3);
        ms(Ms.DIAMOND_PICK, Ms.IRON_PICK.bit, HERE, 2, Work.CRAFT, Res.DIAMOND, 3);
        ms(Ms.ARMOR, Ms.FURNACE.bit, HERE, 3, Work.CRAFT, Res.IRON, 8);
        ms(Ms.COPPER_ARMOR, Ms.FURNACE.bit, HERE, 4, Work.CRAFT, Res.COPPER, 10);
        // The speedrunners' way in: water poured over a lava pool into a mould, lit. The second
        // bucket carries the lava.
        ms(Ms.PORTAL, Ms.WATER_BUCKET.bit | Ms.FLINT_STEEL.bit, LAVA_POOL, 90, Work.NETHER, Res.IRON, 3);
        ms(Ms.NETHER_VISITED, Ms.PORTAL.bit, NETHER, 5, Work.NETHER);
        ms(Ms.BREWING_STAND, stone, HERE, 3, Work.CRAFT, Res.BLAZE_ROD, 1);
        ms(Ms.ENCHANTING_TABLE, Ms.DIAMOND_PICK.bit, HERE, 6, Work.CRAFT, Res.OBSIDIAN, 4, Res.DIAMOND, 2, Res.LEATHER, 1, Res.PAPER, 3);
        ms(Ms.BUNDLE, wood, HERE, 2, Work.CRAFT, Res.STRING, 1, Res.LEATHER, 1);

        int smelt = stone | Ms.FURNACE.bit;
        res(Res.IRON, CAVE, 40, 6, smelt, Work.GATHER, false, 5);
        res(Res.GOLD, DEEP, 70, 7, Ms.IRON_PICK.bit | Ms.FURNACE.bit, Work.GATHER, false, 5);
        res(Res.COPPER, CAVE, 25, 3, smelt, Work.GATHER, false, 8);
        res(Res.COAL, CAVE, 12, 2, wood, Work.GATHER, false, 8);
        res(Res.DIAMOND, DEEP, 200, 10, Ms.IRON_PICK.bit, Work.GATHER, false, 3);
        res(Res.REDSTONE, DEEP, 70, 1, Ms.IRON_PICK.bit, Work.GATHER, false, 8);
        res(Res.LAPIS, DEEP, 80, 1, stone, Work.GATHER, false, 8);
        // (The chests of a village, a trade or two.)
        res(Res.EMERALD, VILLAGE, 40, 20, 0, Work.TRADE, false, 2);
        res(Res.STRING, HERE, 45, 12, 0, Work.MONSTER, true, 2);
        res(Res.BONE, HERE, 40, 15, 0, Work.MONSTER, true, 2);
        res(Res.GUNPOWDER, HERE, 60, 25, 0, Work.MONSTER, true, 1);
        res(Res.ENDER_PEARL, HERE, 150, 40, 0, Work.MONSTER, true, 1);
        res(Res.SLIME, SWAMP, 150, 10, 0, Work.MONSTER, true, 3);
        res(Res.LEATHER, HERE, 25, 12, 0, Work.ANIMAL, false, 2);
        res(Res.WOOL, HERE, 25, 6, 0, Work.ANIMAL, false, 3);
        res(Res.FEATHER, HERE, 25, 12, 0, Work.ANIMAL, false, 2);
        res(Res.FOOD, HERE, 15, 6, 0, Work.ANIMAL, false, 3);
        res(Res.INK, WATER, 40, 15, 0, Work.ANIMAL, false, 2);
        res(Res.PAPER, WATER, 20, 2, 0, Work.GATHER, false, 9);
        res(Res.CLAY, WATER, 40, 1.5, 0, Work.GATHER, false, 16);
        res(Res.SAND, WATER, 15, 1, 0, Work.GATHER, false, 16);
        res(Res.FLINT, HERE, 15, 6, wood, Work.GATHER, false, 1);
        res(Res.WHEAT, VILLAGE, 20, 1, 0, Work.GATHER, false, 16);
        res(Res.OBSIDIAN, LAVA_POOL, 20, 10, Ms.DIAMOND_PICK.bit | Ms.WATER_BUCKET.bit, Work.GATHER, false, 4);
        res(Res.BLAZE_ROD, FORTRESS, 60, 20, stone, Work.NETHER, true, 2);
        res(Res.QUARTZ, NETHER, 10, 2, wood, Work.NETHER, false, 8);
        res(Res.GLOWSTONE, NETHER, 60, 4, 0, Work.NETHER, false, 8);
        res(Res.NETHER_WART, FORTRESS, 40, 2, 0, Work.NETHER, false, 6);
        res(Res.MAGMA_CREAM, DELTAS, 40, 25, stone, Work.NETHER, true, 1);

        for (Ms m : Ms.ALL) MS_CLOSURE[m.ordinal()] = closure(m.ordinal(), 0);
    }

    private static int closure(int m, int depth) {
        if (depth > 12) throw new IllegalStateException("milestone cycle at " + Ms.ALL[m]);
        int out = 0;
        int needs = MS_NEEDS[m];
        for (Ms n : Ms.ALL) {
            if ((needs & n.bit) == 0) continue;
            out |= n.bit | closure(n.ordinal(), depth + 1);
        }
        // What its resources need, too (gold wants the iron pickaxe).
        int[] uses = MS_USES[m];
        for (int i = 0; i < uses.length; i += 2) {
            int rn = RES_NEEDS[uses[i]];
            for (Ms n : Ms.ALL) if ((rn & n.bit) != 0) out |= n.bit | closure(n.ordinal(), depth + 1);
        }
        return out;
    }
}
