package net.kasax.challengecraft.bot.lockout.sim;

/**
 * The board brain of one player in the abstract game: which tile next. Everything else (the
 * chores of the {@link Profile}, the quick tiles, giving up on a tile that takes too long) is the
 * same for every policy, so two policies compare on the choice alone.
 */
public interface Policy {
    String name();

    /**
     * An investment of the brain's own before the shield and the armour (the greedy brain's iron
     * kit and Nether phase), or null.
     */
    default Decision invest(Sim sim, int me) {
        return null;
    }

    /**
     * Whether this brain decides the chores too (the stock of food, the shield, the copper armour,
     * the iron kit, the way into the Nether): then the game's own rules for them give way to its
     * {@link #choose} - except where they are about staying alive (starving, the night coming
     * without a shield on hard).
     */
    default boolean decidesChores() {
        return false;
    }

    /** The next tile to go for, or null when there is none (the player waits a little and looks again). */
    Decision choose(Sim sim, int me);

    /** A new instance for another game or a look-ahead copy (nothing of this game remembered). */
    Policy fresh();
}
