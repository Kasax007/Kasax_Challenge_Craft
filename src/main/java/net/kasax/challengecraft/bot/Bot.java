package net.kasax.challengecraft.bot;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * One bot: its current body (a new one after every respawn), the tools that act through that
 * body, and what it is doing — a stack of {@link BotTask}s, the top one running. A brain (see
 * {@link BotBrain}) decides what to push when the stack runs empty.
 */
public final class Bot {
    public final UUID id;
    public final String name;
    private BotPlayer body;
    private BotTools tools;
    private BotActions actions;
    private BotNavigator navigator;
    private final Deque<BotTask> tasks = new ArrayDeque<>();
    private BotBrain brain;
    /** Chat what it is doing (for testing). */
    public boolean verbose = true;

    Bot(BotPlayer body) {
        this.id = body.getUUID();
        this.name = body.getGameProfile().name();
        attach(body);
    }

    void attach(BotPlayer body) {
        this.body = body;
        this.tools = new BotTools(body);
        this.actions = new BotActions(body, tools);
        this.navigator = new BotNavigator(body, tools, actions);
        // Whatever was going on was about the old body's position and inventory.
        tasks.clear();
    }

    public BotPlayer body() {
        return body;
    }

    public BotTools tools() {
        return tools;
    }

    public BotActions actions() {
        return actions;
    }

    public BotNavigator navigator() {
        return navigator;
    }

    public MinecraftServer server() {
        return body.level().getServer();
    }

    public BotBrain brain() {
        return brain;
    }

    public void setBrain(BotBrain brain) {
        this.brain = brain;
        tasks.clear();
        navigator.stop();
    }

    /** Replaces whatever the bot is doing with {@code task}. */
    public void doNow(BotTask task) {
        tasks.clear();
        navigator.stop();
        tasks.push(task);
        say("-> " + task.describe());
    }

    /** Runs {@code task} first, then returns to what was going on. */
    public void interject(BotTask task) {
        navigator.stop();
        tasks.push(task);
    }

    public BotTask current() {
        return tasks.peek();
    }

    void tick() {
        if (!body.isAlive()) return;
        BotTask task = tasks.peek();
        if (task == null) {
            if (brain != null) brain.think(this);
            task = tasks.peek();
            if (task == null) {
                body.stopInputs();
                return;
            }
        }
        BotTask.Result r;
        try {
            r = task.tick(this);
        } catch (RuntimeException e) {
            BotManager.LOG.warn("[Bot] {} task {} crashed", name, task.describe(), e);
            r = BotTask.Result.FAILED;
        }
        if (r != BotTask.Result.RUNNING) {
            tasks.remove(task);
            navigator.stop();
            if (r == BotTask.Result.FAILED) say("x " + task.describe());
            if (brain != null) brain.finished(this, task, r == BotTask.Result.DONE);
        }
    }

    public void say(String text) {
        if (verbose) BotManager.debug(body, text);
    }
}
