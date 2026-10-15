package net.kasax.challengecraft.bot.learn;

/**
 * What Bob can do when a fight is going badly (the guardian's ways out, and standing to fight).
 * The order is the model's output order: never reordered, only added to at the end.
 */
public enum CoverOption {
    /** No cover: the reflexes fight on (hit back, the shield, a step back). */
    FIGHT,
    /** Walled in: a box of blocks round him, a lid on top. */
    BOX,
    /** Up a pillar out of reach, and waiting there. */
    PILLAR,
    /** Up a pillar, and what cannot reach him hit from above as it comes to the foot. */
    PILLAR_FIGHT,
    /** Away from them, while there is room to run. */
    RUN,
    /** Dug into the ground under a lid. */
    DIG,
    /** Dug in for the night (out again in the morning). */
    SHELTER;

    public static final CoverOption[] ALL = values();
}
