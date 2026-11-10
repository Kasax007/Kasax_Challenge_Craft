package net.kasax.challengecraft.bot.lockout.sim;

/**
 * Resources: things counted and used up (an ingot goes into a bucket). Where and how fast each is
 * gathered is in {@link Econ}. Only what several goals share is a resource; what one goal alone
 * wants (thirty-two cobblestone, a flower) is part of that goal's own work.
 */
public enum Res {
    IRON, GOLD, COPPER, COAL, DIAMOND, REDSTONE, LAPIS, EMERALD,
    STRING, BONE, GUNPOWDER, ENDER_PEARL, SLIME,
    LEATHER, WOOL, FEATHER, FOOD, INK,
    PAPER, CLAY, SAND, FLINT, WHEAT,
    OBSIDIAN, BLAZE_ROD, QUARTZ, GLOWSTONE, NETHER_WART, MAGMA_CREAM;

    public static final Res[] ALL = values();
    public static final int N = ALL.length;
}
