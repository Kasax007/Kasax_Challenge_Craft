package net.kasax.challengecraft.bot.lockout;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The game plan: what a strong player sees when the board comes up, and again whenever it
 * changes. Not just "what is quickest now", but
 * <ul>
 * <li><b>investments</b>: a kit (iron tools and a bucket, diamonds) is worth getting early when it
 * makes enough tiles on the board quicker (or possible at all) to pay for itself; the tiles on the
 * way to it come first then,</li>
 * <li><b>the Nether trip</b>: with several Nether tiles on the board, getting in is worth more than
 * its own tile,</li>
 * <li><b>blocking</b>: a tile the opponent is close to (they hold most of what it takes) is worth
 * taking away from them if it is quick for us.</li>
 * </ul>
 * The result is a bonus in seconds per tile that the brain subtracts from its cost when choosing,
 * and a plan written to the log.
 */
final class LockoutStrategist {
    private record Kit(String name, Set<Item> items) {
    }

    private static final List<Kit> KITS = List.of(
            new Kit("iron kit", Set.of(Items.IRON_PICKAXE, Items.IRON_INGOT, Items.BUCKET, Items.FURNACE)),
            new Kit("diamonds", Set.of(Items.DIAMOND_PICKAXE, Items.DIAMOND)));

    /** Re-planned at least this often (ticks), and whenever the board changes. */
    private static final long REPLAN_TICKS = 2400;
    private static final double UNREACHABLE = 1800;

    /** What the plan will need later and is worth picking up in passing: item -> the block it comes from. */
    private final Map<Item, net.minecraft.world.level.block.Block> wants = new HashMap<>();

    Map<Item, net.minecraft.world.level.block.Block> wants() {
        return wants;
    }

    private boolean ironKit;

    /** Whether the plan invests in iron (tools, bucket) early. */
    boolean wantsIron() {
        return ironKit;
    }

    private final Map<String, Double> bonus = new HashMap<>();
    private final Map<String, String> why = new HashMap<>();
    private long plannedAt = Long.MIN_VALUE;
    private int boardState = -1;

    /** The bonus (seconds) for a tile, and a short reason, from the current plan. */
    double bonus(String goalId) {
        return bonus.getOrDefault(goalId, 0.0);
    }

    String why(String goalId) {
        return why.get(goalId);
    }

    /** Plans again if the board changed, the bot died, or it has been a while. */
    void update(Bot bot, ObtainPlanner planner, boolean force) {
        MinecraftServer server = bot.server();
        long now = server.overworld().getGameTime();
        List<Chal_40_LockoutBingo.BoardTile> board = Chal_40_LockoutBingo.board(server);
        int state = 0;
        for (Chal_40_LockoutBingo.BoardTile t : board) if (t.claimedBy() != null) state = state * 31 + t.index() + 1;
        if (!force && state == boardState && now - plannedAt < REPLAN_TICKS) return;
        boardState = state;
        plannedAt = now;
        plan(bot, planner, board);
    }

    private void plan(Bot bot, ObtainPlanner planner, List<Chal_40_LockoutBingo.BoardTile> board) {
        bonus.clear();
        why.clear();
        wants.clear();
        ironKit = false;
        List<LockoutBingoGoal> open = new ArrayList<>();
        for (Chal_40_LockoutBingo.BoardTile t : board) if (t.claimedBy() == null) open.add(t.goal());
        if (open.isEmpty()) return;
        Map<String, Double> now = costs(bot, planner, open);
        List<String> notes = new ArrayList<>();

        // Investments: kits that pay for themselves.
        for (Kit kit : KITS) {
            if (holds(bot, kit.items())) continue;
            double kitCost = 0;
            for (Item i : kit.items()) kitCost += Math.min(UNREACHABLE, planner.estimate(bot, Set.of(i), 1));
            Map<String, Double> with = planner.assuming(bot, kit.items(), () -> costs(bot, planner, open));
            double savings = 0;
            int helped = 0;
            for (LockoutBingoGoal g : open) {
                double a = now.getOrDefault(g.id(), UNREACHABLE), b = with.getOrDefault(g.id(), UNREACHABLE);
                if (b < a - 5) {
                    savings += a - b;
                    helped++;
                }
            }
            if (helped >= 2 && savings > 1.5 * kitCost) {
                if (kit.name().startsWith("iron")) {
                    wants.put(Items.RAW_IRON, net.minecraft.world.level.block.Blocks.IRON_ORE);
                    ironKit = true;
                }
                notes.add(kit.name() + " (~" + Math.round(kitCost) + " s, saves ~" + Math.round(savings) + " s on " + helped + " tiles)");
                // The tiles that leave the kit (partly) in hand come first.
                for (LockoutBingoGoal g : open) {
                    Set<Item> targets = LockoutGoals.items(g.targets());
                    boolean onTheWay = targets.stream().anyMatch(kit.items()::contains) || feeds(g, kit);
                    if (onTheWay) add(g.id(), Math.min(300, savings / 2), "on the way to the " + kit.name());
                }
            }
        }

        // The shopping list: raw materials all open tiles want together, mined in one trip.
        planner.boardDemand.clear();
        net.kasax.challengecraft.bot.plan.BotKnowledge k = net.kasax.challengecraft.bot.plan.BotKnowledge.get(bot.server());
        for (LockoutBingoGoal g : open) {
            if (!now.containsKey(g.id())) continue;
            Set<Item> targets = LockoutGoals.items(g.targets());
            if (targets.isEmpty() || ObtainPlanner.countAny(bot.body(), targets) > 0) continue;
            Item first = targets.iterator().next();
            int amount = g.type() == LockoutBingoGoalType.ITEM_AMOUNT ? Math.max(1, g.amount()) : 1;
            demand(k, first, amount, 0, planner.boardDemand);
        }
        if (!planner.boardDemand.isEmpty()) notes.add("shopping list " + planner.boardDemand.entrySet().stream()
                .map(e -> e.getValue() + " " + ObtainPlanner.name(e.getKey())).toList());

        // The Nether trip: worth more than its own tile when the Nether holds several.
        long nether = open.stream().filter(g -> g.category() == LockoutBingoGoalCategory.NETHER).count();
        // From up here each Nether tile is planned with the whole way in; the way in is shared by
        // all of them, though: each carries only its part of it.
        if (nether >= 2 && bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            LockoutGoals.Option trip = LockoutGoals.netherForTest(bot, planner);
            if (trip != null) {
                double share = trip.cost() * (1 - 1.0 / nether);
                for (LockoutBingoGoal g : open) {
                    if (g.category() == LockoutBingoGoalCategory.NETHER && g.type() != LockoutBingoGoalType.DIMENSION) add(g.id(), share, "the way in is shared by " + nether + " Nether tiles");
                }
                Double lava = planner.seen(bot, net.minecraft.world.level.block.Blocks.LAVA);
                notes.add("way into the Nether ~" + Math.round(trip.cost()) + " s (lava " + (lava == null ? "-" : Math.round(lava))
                        + ", water bucket " + Math.round(planner.estimate(bot, Set.of(Items.WATER_BUCKET), 1))
                        + ", flint and steel " + Math.round(planner.estimate(bot, Set.of(Items.FLINT_AND_STEEL), 1))
                        + ", obsidian " + Math.round(planner.estimate(bot, Set.of(Items.OBSIDIAN), 10)) + ")");
            } else notes.add("no way into the Nether known");
        }
        if (nether >= 2) {
            for (LockoutBingoGoal g : open) {
                if ("minecraft:the_nether".equals(g.primaryTarget()) || g.id().equals("advancement_we_need_to_go_deeper")) {
                    add(g.id(), Math.min(400, 60 * nether), "opens " + nether + " Nether tiles");
                }
            }
            notes.add("Nether trip (" + nether + " tiles there)");
            wants.put(Items.FLINT, net.minecraft.world.level.block.Blocks.GRAVEL);
        }

        // The desert temple's TNT: nine blocks for the taking once the plate is out of the way.
        for (LockoutBingoGoal g : open) {
            if (g.id().contains("tnt") || g.id().contains("gunpowder")) {
                wants.put(Items.TNT, net.minecraft.world.level.block.Blocks.TNT);
                break;
            }
        }
        // Paper for books, maps and the like: sugar cane at the river bank.
        for (LockoutBingoGoal g : open) {
            String id = g.id();
            if (id.contains("book") || id.contains("map") || id.contains("paper") || id.contains("lectern")) {
                wants.put(Items.SUGAR_CANE, net.minecraft.world.level.block.Blocks.SUGAR_CANE);
                break;
            }
        }
        if (!wants.isEmpty()) notes.add("picking up on the way: " + wants.keySet().stream().map(ObtainPlanner::name).toList());

        // Blocking: what the opponent is close to.
        for (LockoutBingoGoal g : open) {
            double p = opponentProgress(bot, g);
            if (p >= 0.5 && now.getOrDefault(g.id(), UNREACHABLE) < 150) {
                add(g.id(), 60 * p, "the opponent is close to it");
                notes.add("block " + g.id());
            }
        }

        // The plan as a player would say it: the order it means to go in.
        List<LockoutBingoGoal> order = new ArrayList<>(open);
        order.removeIf(g -> !now.containsKey(g.id()));
        order.sort((a, b) -> Double.compare(now.get(a.id()) - bonus(a.id()), now.get(b.id()) - bonus(b.id())));
        StringBuilder sb = new StringBuilder("plan: ");
        for (int i = 0; i < Math.min(6, order.size()); i++) {
            if (i > 0) sb.append(" > ");
            sb.append(order.get(i).id().replace("obtain_", "").replace("advancement_", ""));
        }
        if (!notes.isEmpty()) sb.append(" | ").append(String.join("; ", notes));
        sb.append(" | ").append(order.size()).append(" of ").append(open.size()).append(" open tiles doable");
        bot.say(sb.toString());
    }

    /** What a raw material is called once made: the ingot is smelted from the raw ore. */
    private static final Map<Item, Item> RAW = Map.of(Items.IRON_INGOT, Items.RAW_IRON, Items.GOLD_INGOT, Items.RAW_GOLD,
            Items.COPPER_INGOT, Items.RAW_COPPER, Items.RAW_IRON, Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_GOLD,
            Items.RAW_COPPER, Items.RAW_COPPER, Items.DIAMOND, Items.DIAMOND, Items.REDSTONE, Items.REDSTONE,
            Items.LAPIS_LAZULI, Items.LAPIS_LAZULI, Items.COAL, Items.COAL);

    /** Adds the raw materials {@code n} of {@code item} take (along the first recipe, a few levels deep). */
    private static void demand(net.kasax.challengecraft.bot.plan.BotKnowledge k, Item item, int n, int depth, Map<Item, Integer> out) {
        Item raw = RAW.get(item);
        if (raw != null) {
            out.merge(raw, n, Integer::sum);
            return;
        }
        if (depth > 3) return;
        for (var r : k.routesTo(item)) {
            if (!(r instanceof net.kasax.challengecraft.bot.plan.BotKnowledge.CraftRoute c)) continue;
            int crafts = (n + c.yield() - 1) / c.yield();
            for (var ing : c.ingredients()) {
                List<Item> options = net.kasax.challengecraft.bot.plan.BotKnowledge.items(ing);
                Item pick = options.stream().filter(RAW::containsKey).findFirst().orElse(options.isEmpty() ? null : options.get(0));
                if (pick != null && pick != item) demand(k, pick, crafts, depth + 1, out);
            }
            return; // (the first recipe: the usual one)
        }
    }

    /** A kit item is made from this tile's item (the iron ingot for the iron kit, say). */
    private static boolean feeds(LockoutBingoGoal g, Kit kit) {
        Set<Item> t = LockoutGoals.items(g.targets());
        return kit.name().startsWith("iron") && (t.contains(Items.RAW_IRON) || t.contains(Items.IRON_NUGGET) || t.contains(Items.IRON_BLOCK))
                || kit.name().startsWith("diamond") && t.contains(Items.DIAMOND_BLOCK);
    }

    private void add(String id, double seconds, String reason) {
        bonus.merge(id, seconds, Double::sum);
        why.putIfAbsent(id, reason);
    }

    private static Map<String, Double> costs(Bot bot, ObtainPlanner planner, List<LockoutBingoGoal> goals) {
        Map<String, Double> out = new HashMap<>();
        for (LockoutBingoGoal g : goals) {
            try {
                LockoutGoals.Option o = LockoutGoals.plan(bot, planner, g);
                if (o != null) out.put(g.id(), o.cost());
            } catch (RuntimeException ignored) {
                // a goal the planner chokes on: leave it out
            }
        }
        return out;
    }

    private static boolean holds(Bot bot, Set<Item> items) {
        for (Item i : items) if (ObtainPlanner.countAny(bot.body(), Set.of(i)) == 0) return false;
        return true;
    }

    /**
     * How far the best opponent is with a tile that is about holding things: the share of the amount
     * (or of the different kinds) they already carry. 0 when it cannot be told.
     */
    private static double opponentProgress(Bot bot, LockoutBingoGoal g) {
        if (g.type() != LockoutBingoGoalType.ITEM_AMOUNT && g.type() != LockoutBingoGoalType.INVENTORY_SET) return 0;
        MinecraftServer server = bot.server();
        LockoutBingoTeam mine = Chal_40_LockoutBingo.teamOf(server, bot.id);
        Set<Item> targets = LockoutGoals.items(g.targets());
        int need = Math.max(1, g.amount());
        double best = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            LockoutBingoTeam team = Chal_40_LockoutBingo.teamOf(server, p.getUUID());
            if (team == null || team == mine) continue;
            int have;
            if (g.type() == LockoutBingoGoalType.ITEM_AMOUNT) {
                have = 0;
                for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (targets.contains(s.getItem())) have += s.getCount();
            } else {
                Set<Item> kinds = new HashSet<>();
                for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (targets.contains(s.getItem())) kinds.add(s.getItem());
                have = kinds.size();
            }
            best = Math.max(best, Math.min(1.0, have / (double) need));
        }
        return best;
    }
}
