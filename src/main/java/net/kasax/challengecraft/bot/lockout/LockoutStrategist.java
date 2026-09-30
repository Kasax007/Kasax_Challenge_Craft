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
                if (kit.name().startsWith("iron")) wants.put(Items.RAW_IRON, net.minecraft.world.level.block.Blocks.IRON_ORE);
                notes.add(kit.name() + " (~" + Math.round(kitCost) + " s, saves ~" + Math.round(savings) + " s on " + helped + " tiles)");
                // The tiles that leave the kit (partly) in hand come first.
                for (LockoutBingoGoal g : open) {
                    Set<Item> targets = LockoutGoals.items(g.targets());
                    boolean onTheWay = targets.stream().anyMatch(kit.items()::contains) || feeds(g, kit);
                    if (onTheWay) add(g.id(), Math.min(300, savings / 2), "on the way to the " + kit.name());
                }
            }
        }

        // The Nether trip: worth more than its own tile when the Nether holds several.
        long nether = open.stream().filter(g -> g.category() == LockoutBingoGoalCategory.NETHER).count();
        if (nether >= 2) {
            for (LockoutBingoGoal g : open) {
                if ("minecraft:the_nether".equals(g.primaryTarget()) || g.id().equals("advancement_we_need_to_go_deeper")) {
                    add(g.id(), Math.min(400, 60 * nether), "opens " + nether + " Nether tiles");
                }
            }
            notes.add("Nether trip (" + nether + " tiles there)");
            wants.put(Items.FLINT, net.minecraft.world.level.block.Blocks.GRAVEL);
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
