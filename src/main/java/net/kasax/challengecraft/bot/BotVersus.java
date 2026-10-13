package net.kasax.challengecraft.bot;

import net.kasax.challengecraft.bot.lockout.LockoutBrain;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.minecraft.stats.Stats;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-play match: two to four bots, each a team of its own, on one Lockout board in one world,
 * each with its own knobs ({@link BotParams}) - variants of Bob against each other, seed after
 * seed. What a match shows: who won, each one's tiles over the game (after 5, 10, 20, 30 minutes),
 * the first tile, the deaths and the hearts lost. At the end one {@code [BOTVS] result} line of
 * JSON, for the league script to add up.
 */
public final class BotVersus {
    private static BotVersus running;

    /** One side of the match and what it did. */
    private static final class Side {
        final Bot bot;
        final LockoutBingoTeam team;
        final int deaths0, damage0;
        int firstTile = -1, tiles;
        final int[] tilesAt = new int[4];
        final List<String> claims = new ArrayList<>();
        /** Ticks spent per root task (what it set out to do), the counts left out. */
        final java.util.Map<String, Integer> timeOn = new java.util.HashMap<>();

        Side(Bot bot, LockoutBingoTeam team) {
            this.bot = bot;
            this.team = team;
            var stats = bot.body().getStats();
            deaths0 = stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
            damage0 = stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_TAKEN));
        }

        int deaths() {
            return bot.body().getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)) - deaths0;
        }

        double hearts() {
            // (Damage taken is counted in tenths of a health point.)
            return (bot.body().getStats().getValue(Stats.CUSTOM.get(Stats.DAMAGE_TAKEN)) - damage0) / 10.0;
        }
    }

    private static final int[] MARKS = {5, 10, 20, 30};
    private static final java.util.regex.Pattern DIGITS = java.util.regex.Pattern.compile("[0-9]+");

    private final List<Side> sides = new ArrayList<>();
    private final int length;
    private final long seed;
    private int ticks;

    private BotVersus(int length, long seed) {
        this.length = length;
        this.seed = seed;
    }

    /** Starts a match of {@code ticks} between these bots (each with its params already set). */
    public static void start(List<Bot> bots, int ticks, LockoutBrain.Difficulty difficulty) {
        var server = bots.get(0).server();
        long seed = server.overworld().getSeed();
        // The same board for the same world: a seed is a match that can be played again.
        List<String> board = net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool.pickBoard(seed)
                .stream().map(net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal::id).toList();
        Chal_40_LockoutBingo.startDebugRun(bots.stream().map(b -> (net.minecraft.server.level.ServerPlayer) b.body()).toList(), board);
        BotVersus v = new BotVersus(ticks, seed);
        for (Bot b : bots) {
            b.idleTicks = 0;
            b.failures = 0;
            b.setBrain(new LockoutBrain(difficulty));
            v.sides.add(new Side(b, Chal_40_LockoutBingo.teamOf(server, b.id)));
            BotManager.LOG.info("[BOTVS] {} plays {} with {}", b.name, Chal_40_LockoutBingo.teamOf(server, b.id), b.params);
        }
        running = v;
        BotManager.LOG.info("[BOTVS] start: seed {}, {} s, {}, world {}", seed, ticks / 20, difficulty, server.overworld().getDifficulty().getSerializedName());
    }

    static void tickAll() {
        if (running != null && running.tick()) running = null;
    }

    /** Returns true when the match is over. */
    private boolean tick() {
        ticks++;
        var server = sides.get(0).bot.server();
        for (Side s : sides) {
            var root = s.bot.root();
            String what = root == null ? "(nothing)" : DIGITS.matcher(root.describe()).replaceAll("#");
            s.timeOn.merge(what.length() > 48 ? what.substring(0, 48) : what, 1, Integer::sum);
        }
        if (ticks % 10 == 0) {
            for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(server)) {
                if (t.claimedBy() == null) continue;
                for (Side s : sides) {
                    if (s.team != t.claimedBy()) continue;
                    String title = t.goal().title().getString();
                    if (s.claims.stream().anyMatch(c -> c.startsWith(title + "@"))) continue;
                    s.claims.add(title + "@" + ticks / 20);
                    if (s.firstTile < 0) s.firstTile = ticks / 20;
                    BotManager.LOG.info("[BOTVS] {} claimed {} at {} s", s.bot.name, title, ticks / 20);
                }
            }
            for (Side s : sides) s.tiles = Chal_40_LockoutBingo.score(server, s.team);
        }
        for (int i = 0; i < MARKS.length; i++) {
            if (ticks == MARKS[i] * 1200) for (Side s : sides) s.tilesAt[i] = s.tiles;
        }
        boolean over = !Chal_40_LockoutBingo.isRunning(server);
        if (ticks < length && !over) return false;
        report(over);
        for (Side s : sides) {
            s.bot.setBrain(null);
            s.bot.clearTasks();
        }
        return true;
    }

    private void report(boolean decided) {
        // Not yet reached when it ended: the tiles at the end stand for the marks still to come.
        for (int i = 0; i < MARKS.length; i++) {
            if (ticks < MARKS[i] * 1200) for (Side s : sides) s.tilesAt[i] = s.tiles;
        }
        Side best = null;
        boolean tie = false;
        for (Side s : sides) {
            if (best == null || s.tiles > best.tiles) {
                best = s;
                tie = false;
            } else if (s.tiles == best.tiles) tie = true;
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"seed\":").append(seed).append(",\"secs\":").append(ticks / 20).append(",\"decided\":").append(decided)
                .append(",\"winner\":\"").append(tie || best == null ? "-" : best.bot.name).append("\",\"bots\":[");
        for (int i = 0; i < sides.size(); i++) {
            Side s = sides.get(i);
            if (i > 0) json.append(',');
            json.append("{\"name\":\"").append(s.bot.name).append("\",\"params\":\"").append(s.bot.params)
                    .append("\",\"tiles\":").append(s.tiles).append(",\"deaths\":").append(s.deaths())
                    .append(",\"hearts\":").append(String.format(java.util.Locale.ROOT, "%.1f", s.hearts()))
                    .append(",\"first\":").append(s.firstTile)
                    .append(",\"m5\":").append(s.tilesAt[0]).append(",\"m10\":").append(s.tilesAt[1])
                    .append(",\"m20\":").append(s.tilesAt[2]).append(",\"m30\":").append(s.tilesAt[3])
                    .append(",\"guardian\":[").append(s.bot.guardian.boxes).append(',').append(s.bot.guardian.pillars).append(',').append(s.bot.guardian.digs).append(',').append(s.bot.guardian.runs).append(']')
                    .append(",\"time\":{");
            int n = 0;
            for (var e : s.timeOn.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(10).toList()) {
                if (n++ > 0) json.append(',');
                json.append('"').append(e.getKey().replace("\"", "'").replace("\\", "/")).append("\":").append(e.getValue() / 20);
            }
            json.append('}')
                    .append(",\"claims\":[");
            for (int c = 0; c < s.claims.size(); c++) {
                if (c > 0) json.append(',');
                json.append('"').append(s.claims.get(c).replace("\"", "'")).append('"');
            }
            json.append("]}");
        }
        json.append("]}");
        BotManager.LOG.info("[BOTVS] result {}", json);
    }
}
