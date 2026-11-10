package net.kasax.challengecraft.bot.lockout.sim;

/**
 * Milestones: the tools, stations and steps of progress a player keeps once made (a bit each in
 * {@link PlayerState#ms}). How each is made - what it needs first, what it uses up, how long it
 * takes, where - is in {@link Econ}. Most are carried and lost with a death (see {@link #KEPT}).
 */
public enum Ms {
    /** Logs, planks, a crafting table, a wooden pickaxe. */
    WOOD,
    /** Cobblestone and the stone tools (pickaxe, sword, axe): the "Stone Age". */
    STONE,
    FURNACE,
    IRON_PICK,
    BUCKET,
    WATER_BUCKET,
    SHIELD,
    SHEARS,
    FLINT_STEEL,
    BOW,
    ROD,
    BED,
    DIAMOND_PICK,
    /** An iron chestplate worn: every blow and arrow a third less. */
    ARMOR,
    /** Copper armour on hard (Bob's own habit; less than iron). */
    COPPER_ARMOR,
    /** A lit portal to the Nether (cast at a lava pool, or built from obsidian). */
    PORTAL,
    /** Been through the portal at least once ("We Need to Go Deeper"). */
    NETHER_VISITED,
    BREWING_STAND,
    ENCHANTING_TABLE,
    /** A bundle in the pack (Bob's kit: the pack fills up). */
    BUNDLE;

    public final int bit = 1 << ordinal();

    /** What a death does not take away: the portal stays where it was built, the advancement stays. */
    public static final int KEPT = PORTAL.bit | NETHER_VISITED.bit;

    public static final Ms[] ALL = values();

    public static boolean has(int mask, Ms m) {
        return (mask & m.bit) != 0;
    }
}
