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

    /** Drops everything the bot is doing (the brain decides anew on the next tick). */
    public void clearTasks() {
        tasks.clear();
        navigator.stop();
        actions.reset();
        body.stopInputs();
    }

    /** Runs {@code task} first, then returns to what was going on. */
    public void interject(BotTask task) {
        navigator.stop();
        tasks.push(task);
    }

    /** The task stack, top first, with details (for testing). */
    public String status() {
        StringBuilder sb = new StringBuilder();
        for (BotTask t : tasks) sb.append(" > ").append(t.status());
        return sb.length() == 0 ? " idle" : sb.toString();
    }

    public BotTask current() {
        return tasks.peek();
    }

    void tick() {
        if (!body.isAlive()) return;
        reflexes();
        if (brain != null) brain.tick(this);
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
            say((r == BotTask.Result.FAILED ? "x " : "done: ") + task.describe());
            if (brain != null) brain.finished(this, task, r == BotTask.Result.DONE);
        }
    }

    private int reflexCooldown;

    /**
     * Things a player does without thinking about them, whatever the plan: hit back at a monster
     * that attacks, eat when hungry.
     */
    private void reflexes() {
        if (reflexCooldown-- > 0) return;
        reflexCooldown = 10;
        tidyInventory();
        BotTask top = tasks.peek();
        if (top instanceof net.kasax.challengecraft.bot.task.EatTask || top instanceof net.kasax.challengecraft.bot.task.KillTask) return;
        net.minecraft.world.entity.LivingEntity attacker = body.getLastHurtByMob();
        if (attacker instanceof net.minecraft.world.entity.monster.Enemy && attacker.isAlive()
                && body.tickCount - body.getLastHurtByMobTimestamp() < 60 && attacker.distanceTo(body) < 8) {
            actions.reset();
            interject(new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(attacker.getType()), java.util.Set.of(), 0, 1));
            return;
        }
        int food = body.getFoodData().getFoodLevel();
        boolean hurt = body.getHealth() < body.getMaxHealth() * 0.6f && food < 20;
        if ((food <= 14 || hurt) && net.kasax.challengecraft.bot.task.EatTask.bestFood(body) >= 0) {
            actions.reset();
            interject(new net.kasax.challengecraft.bot.task.EatTask());
        }
    }

    /** What a player throws away when the inventory fills up (keeping one stack of building blocks). */
    private static final java.util.Set<net.minecraft.world.item.Item> JUNK = java.util.Set.of(
            net.minecraft.world.item.Items.DIRT, net.minecraft.world.item.Items.GRAVEL, net.minecraft.world.item.Items.DIORITE,
            net.minecraft.world.item.Items.ANDESITE, net.minecraft.world.item.Items.GRANITE, net.minecraft.world.item.Items.TUFF,
            net.minecraft.world.item.Items.COBBLED_DEEPSLATE, net.minecraft.world.item.Items.COBBLESTONE,
            net.minecraft.world.item.Items.NETHERRACK, net.minecraft.world.item.Items.CALCITE);

    /** Frees slots when the inventory is nearly full: junk first, all but 64 building blocks. */
    private void tidyInventory() {
        var inv = body.getInventory().getNonEquipmentItems();
        int free = 0;
        for (var s : inv) if (s.isEmpty()) free++;
        if (free >= 3) return;
        int keep = 64;
        for (int i = 0; i < inv.size(); i++) {
            var s = inv.get(i);
            if (s.isEmpty() || !JUNK.contains(s.getItem())) {
                // Anything that is not junk stays; junk with a use (cobblestone) is kept up to a stack.
                continue;
            }
            boolean building = BotActions.THROWAWAY.contains(s.getItem());
            if (building && keep > 0) {
                keep -= s.getCount();
                continue;
            }
            inv.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            body.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        body.getInventory().setChanged();
    }

    public void say(String text) {
        if (verbose) BotManager.debug(body, text);
    }
}
