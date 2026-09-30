package net.kasax.challengecraft.bot;

/** Something a bot does over many ticks: go somewhere, mine an ore, craft a pickaxe, ... */
public interface BotTask {
    enum Result { RUNNING, DONE, FAILED }

    Result tick(Bot bot);

    /** For chat and logs, e.g. "mine 3 iron ore". */
    String describe();

    /** What it is up to in detail, for the status command. */
    default String status() {
        return describe();
    }
}
