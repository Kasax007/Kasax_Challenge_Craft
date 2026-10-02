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

    /** The task this one is carrying out right now on its behalf (a step of it), or null. */
    default BotTask inner() {
        return null;
    }

    /** Down through the tasks carried out on behalf of others, to the one actually at work. */
    static BotTask innermost(BotTask t) {
        for (int i = 0; i < 8 && t != null && t.inner() != null; i++) t = t.inner();
        return t;
    }
}
