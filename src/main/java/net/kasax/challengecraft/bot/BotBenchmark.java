package net.kasax.challengecraft.bot;

import net.kasax.challengecraft.bot.lockout.LockoutBrain;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A measured Lockout game for one bot alone: the milestones a good player hits early (table,
 * pickaxes, iron, bucket, Nether) with the second each was first reached, the tiles claimed, the
 * time it stood idle and how many tasks failed. The report goes to the log as {@code [BOTBENCH]}
 * lines. Run it on a server with {@code /tick sprint} to get ten minutes of play in a few.
 */
public final class BotBenchmark {
    private record Milestone(String name, Item item) {
    }

    private static final List<Milestone> MILESTONES = List.of(
            new Milestone("crafting table", Items.CRAFTING_TABLE), new Milestone("wooden pickaxe", Items.WOODEN_PICKAXE),
            new Milestone("stone pickaxe", Items.STONE_PICKAXE), new Milestone("iron ingot", Items.IRON_INGOT),
            new Milestone("bucket", Items.BUCKET), new Milestone("iron pickaxe", Items.IRON_PICKAXE),
            new Milestone("diamond", Items.DIAMOND));

    private static final List<BotBenchmark> RUNNING = new ArrayList<>();

    private final Bot bot;
    private final int length;
    private int ticks, claimed, deaths;
    private final Map<String, Integer> reached = new LinkedHashMap<>();
    private final List<String> claims = new ArrayList<>();
    private boolean wasAlive = true;

    private BotBenchmark(Bot bot, int length) {
        this.bot = bot;
        this.length = length;
    }

    public static void start(Bot bot, int ticks, LockoutBrain.Difficulty difficulty) {
        RUNNING.removeIf(b -> b.bot == bot);
        bot.idleTicks = 0;
        bot.failures = 0;
        Chal_40_LockoutBingo.startSoloDebugRun(bot.body());
        bot.setBrain(new LockoutBrain(difficulty));
        RUNNING.add(new BotBenchmark(bot, ticks));
        BotManager.LOG.info("[BOTBENCH] {} start at {} ({} s, {})", bot.name, bot.body().blockPosition().toShortString(), ticks / 20, difficulty);
    }

    static void tickAll() {
        RUNNING.removeIf(BotBenchmark::tick);
    }

    /** Returns true when done. */
    private boolean tick() {
        ticks++;
        var body = bot.body();
        if (!body.isAlive() && wasAlive) deaths++;
        wasAlive = body.isAlive();
        if (ticks % 10 == 0) {
            for (Milestone m : MILESTONES) {
                if (!reached.containsKey(m.name()) && net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(body, java.util.Set.of(m.item())) > 0) {
                    reached.put(m.name(), ticks);
                    BotManager.LOG.info("[BOTBENCH] {} {} at {} s", bot.name, m.name(), ticks / 20);
                }
            }
            if (!reached.containsKey("nether") && body.level().dimension() == Level.NETHER) {
                reached.put("nether", ticks);
                BotManager.LOG.info("[BOTBENCH] {} nether at {} s", bot.name, ticks / 20);
            }
            int now = 0;
            for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
                if (t.claimedBy() != null) {
                    now++;
                    String title = t.goal().title().getString();
                    if (!claims.contains(title)) {
                        claims.add(title);
                        BotManager.LOG.info("[BOTBENCH] {} claimed {} at {} s", bot.name, title, ticks / 20);
                    }
                }
            }
            claimed = now;
        }
        if (ticks % 1200 == 0) {
            BotManager.LOG.info("[BOTBENCH] {} minute {}: {} tiles, at {}, doing{}", bot.name, ticks / 1200, claimed,
                    body.blockPosition().toShortString(), bot.status());
        }
        if (ticks < length) return false;
        report();
        bot.setBrain(null);
        bot.clearTasks();
        return true;
    }

    private void report() {
        String name = bot.name;
        BotManager.LOG.info("[BOTBENCH] ===== {}: {} s of Lockout =====", name, length / 20);
        BotManager.LOG.info("[BOTBENCH] tiles claimed: {} ({})", claimed, String.join(", ", claims));
        for (Milestone m : MILESTONES) {
            Integer t = reached.get(m.name());
            BotManager.LOG.info("[BOTBENCH] {}: {}", m.name(), t == null ? "-" : t / 20 + " s");
        }
        Integer nether = reached.get("nether");
        BotManager.LOG.info("[BOTBENCH] nether: {}", nether == null ? "-" : nether / 20 + " s");
        BotManager.LOG.info("[BOTBENCH] idle: {} s, failed tasks: {}, deaths: {}", bot.idleTicks / 20, bot.failures, deaths);
    }
}
