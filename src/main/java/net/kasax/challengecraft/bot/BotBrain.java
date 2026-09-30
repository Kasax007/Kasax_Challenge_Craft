package net.kasax.challengecraft.bot;

/**
 * Decides what a bot does next. The bot asks when it has nothing to do; the brain pushes tasks
 * ({@link Bot#doNow}, {@link Bot#interject}) and hears how each one ended.
 */
public interface BotBrain {
    void think(Bot bot);

    /** Every tick, before the current task: a chance to drop what the bot is doing when things changed. */
    default void tick(Bot bot) {
    }

    default void finished(Bot bot, BotTask task, boolean success) {
    }

    /** The bot died and stands at its spawn point again, with whatever it kept. */
    default void respawned(Bot bot) {
    }
}
