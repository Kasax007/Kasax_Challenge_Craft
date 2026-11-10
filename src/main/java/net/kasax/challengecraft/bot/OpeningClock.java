package net.kasax.challengecraft.bot;

import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A stopwatch over a bot's opening in a Lockout game (benchmarks and self-play matches): the game
 * second each milestone a good player hits early was first reached (the first log, the table,
 * the wooden and the four stone tools, a stock of food, the shield, the iron pickaxe, the first
 * tile), and where the time went - every tick put down to one activity (walking, searching,
 * digging, crafting, smelting, hunting, eating, a reflex, waiting...) under the decider that
 * set the errand going (food, opening, kit, invest, goal...).
 *
 * <p>Everything counts in game ticks, so a match at {@code tick sprint} measures the same as one
 * at the real pace. The result is one JSON object ({@link #json()}): {@code ms} the milestones in
 * seconds, {@code act} the seconds per activity over the window, {@code pre} the same up to the
 * first tile, {@code by} the seconds per decider and activity, {@code speed} how fast it covered
 * ground while walking (blocks a second), {@code reflexes} how often a reflex took over.
 */
public final class OpeningClock {
    /** How long the activities are counted (the milestones are looked for the whole game). */
    public static final int WINDOW_TICKS = 600 * 20;

    private record Milestone(String name, Set<Item> any) {
    }

    private static final Set<Item> LOGS = tagged(net.minecraft.tags.ItemTags.LOGS);
    private static final List<Milestone> MILESTONES = List.of(
            new Milestone("log", LOGS),
            new Milestone("table", Set.of(Items.CRAFTING_TABLE)),
            new Milestone("wooden_pickaxe", Set.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)),
            new Milestone("stone_pickaxe", Set.of(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)),
            new Milestone("stone_sword", Set.of(Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD)),
            new Milestone("stone_axe", Set.of(Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE)),
            new Milestone("stone_shovel", Set.of(Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL)),
            new Milestone("furnace", Set.of(Items.FURNACE)),
            new Milestone("shield", Set.of(Items.SHIELD)),
            new Milestone("iron_ingot", Set.of(Items.IRON_INGOT)),
            new Milestone("iron_pickaxe", Set.of(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)),
            new Milestone("bucket", Set.of(Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET)));
    /** A stock of food: what a night or a fight wants in the pack (the brain's own "bare" line is 8). */
    private static final int FOOD_STOCK = 8;

    private final Bot bot;
    private int ticks;
    private final Map<String, Integer> reached = new LinkedHashMap<>();
    private final Map<String, Integer> act = new TreeMap<>(), pre = new TreeMap<>();
    private final Map<String, Map<String, Integer>> by = new TreeMap<>();
    /** Ground covered (flat blocks) and ticks, while walking or searching. */
    private double walked;
    private int walkTicks;
    private double lastX, lastZ;
    private int reflexes, lastNavWait;
    private boolean wasReflex, firstTile;

    public OpeningClock(Bot bot) {
        this.bot = bot;
        lastX = bot.body().getX();
        lastZ = bot.body().getZ();
        lastNavWait = bot.navigator().waitTicks;
    }

    /** One game tick; {@code tilesNow}: the bot's side holds at least one tile. */
    public void tick(boolean tilesNow) {
        ticks++;
        var body = bot.body();
        if (tilesNow && !firstTile) {
            firstTile = true;
            reached.put("first_tile", ticks);
        }
        if (ticks % 10 == 0 && body.isAlive()) {
            for (Milestone m : MILESTONES) {
                if (!reached.containsKey(m.name()) && (ObtainPlanner.countAny(body, m.any()) > 0 || worn(body, m.any()))) reached.put(m.name(), ticks);
            }
            if (!reached.containsKey("table") && bot.ownTable != null) reached.put("table", ticks);
            if (!reached.containsKey("food_stock") && net.kasax.challengecraft.bot.task.EatTask.foodPoints(body) >= FOOD_STOCK) reached.put("food_stock", ticks);
            if (!reached.containsKey("stone_tools") && List.of("stone_pickaxe", "stone_sword", "stone_axe", "stone_shovel").stream().allMatch(reached::containsKey))
                reached.put("stone_tools", ticks);
        }
        if (ticks > WINDOW_TICKS) return;
        String what = activity();
        act.merge(what, 1, Integer::sum);
        if (!firstTile) pre.merge(what, 1, Integer::sum);
        by.computeIfAbsent(decider(), k -> new TreeMap<>()).merge(what, 1, Integer::sum);
        boolean reflex = bot.inReflex();
        if (reflex && !wasReflex) reflexes++;
        wasReflex = reflex;
        double dx = body.getX() - lastX, dz = body.getZ() - lastZ;
        if ((what.equals("walk") || what.equals("search")) && dx * dx + dz * dz < 4) {
            walked += Math.sqrt(dx * dx + dz * dz);
            walkTicks++;
        }
        lastX = body.getX();
        lastZ = body.getZ();
    }

    private static boolean worn(BotPlayer body, Set<Item> any) {
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) if (any.contains(body.getItemBySlot(slot).getItem())) return true;
        return false;
    }

    /** The decider behind what it is doing (the brain's word for its errand), or "-". */
    private String decider() {
        if (!bot.body().isAlive()) return "dead";
        if (bot.brain() instanceof net.kasax.challengecraft.bot.lockout.LockoutBrain brain) return brain.doing(bot);
        return "-";
    }

    /** What this tick went on, the first that fits (the order matters: a walk to a table is a walk). */
    private String activity() {
        var body = bot.body();
        if (!body.isAlive()) return "dead";
        BotTask top = bot.current();
        BotTask in = BotTask.innermost(top);
        if (in instanceof net.kasax.challengecraft.bot.task.EatTask || body.isUsingItem() && body.getUseItem().get(net.minecraft.core.component.DataComponents.FOOD) != null)
            return "eat";
        // (A path being searched for: stood still, waiting for the way.)
        int navWait = bot.navigator().waitTicks;
        boolean planning = navWait != lastNavWait;
        lastNavWait = navWait;
        if (bot.inReflex()) return "reflex";
        if (top == null) return "idle";
        if (bot.actions().brokeThisTick()) return "dig";
        if (planning) return "path";
        boolean moving = bot.navigator().status() == BotNavigator.Status.MOVING && bot.navigator().tickedAt == body.tickCount;
        if (moving && searching(top)) return "search";
        if (in instanceof net.kasax.challengecraft.bot.task.KillTask || in instanceof net.kasax.challengecraft.bot.task.HuntRoundTask) return "hunt";
        if (moving) return "walk";
        if (in instanceof net.kasax.challengecraft.bot.task.SmeltTask) return "smelt";
        if (in instanceof net.kasax.challengecraft.bot.task.CraftTask || in instanceof net.kasax.challengecraft.bot.task.UseStationTask
                || in instanceof net.kasax.challengecraft.bot.task.PackTableTask) return "craft";
        return "wait";
    }

    /** Out looking for what is not in sight (a tree, a stone face, an animal): anywhere down the stack. */
    private static boolean searching(BotTask top) {
        BotTask t = top;
        for (int i = 0; i < 8 && t != null; i++, t = t.inner()) {
            if (t instanceof net.kasax.challengecraft.bot.task.MineTask m && m.searching()) return true;
            if (t instanceof net.kasax.challengecraft.bot.task.KillTask k && k.searching()) return true;
        }
        return false;
    }

    /** The milestones reached so far (name and game second). */
    public Map<String, Integer> milestones() {
        Map<String, Integer> out = new LinkedHashMap<>();
        reached.forEach((k, v) -> out.put(k, v / 20));
        return out;
    }

    public String json() {
        StringBuilder sb = new StringBuilder("{\"ms\":").append(seconds(milestones(), false));
        sb.append(",\"act\":").append(seconds(act, true)).append(",\"pre\":").append(seconds(pre, true)).append(",\"by\":{");
        int n = 0;
        for (var e : by.entrySet()) {
            if (n++ > 0) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":").append(seconds(e.getValue(), true));
        }
        sb.append("},\"speed\":").append(String.format(java.util.Locale.ROOT, "%.2f", walkTicks == 0 ? 0 : walked / walkTicks * 20))
                .append(",\"reflexes\":").append(reflexes).append(",\"window\":").append(Math.min(ticks, WINDOW_TICKS) / 20).append('}');
        return sb.toString();
    }

    /** A map as JSON, its values in seconds (ticks / 20, one decimal) or as they are. */
    private static String seconds(Map<String, Integer> m, boolean ticks) {
        StringBuilder sb = new StringBuilder("{");
        int n = 0;
        for (var e : m.entrySet()) {
            if (n++ > 0) sb.append(',');
            sb.append('"').append(e.getKey().replace("\"", "'")).append("\":");
            if (ticks) sb.append(String.format(java.util.Locale.ROOT, "%.1f", e.getValue() / 20.0));
            else sb.append(e.getValue());
        }
        return sb.append('}').toString();
    }

    private static Set<Item> tagged(net.minecraft.tags.TagKey<Item> tag) {
        Set<Item> out = new java.util.HashSet<>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) if (new net.minecraft.world.item.ItemStack(item).is(tag)) out.add(item);
        return out;
    }
}
