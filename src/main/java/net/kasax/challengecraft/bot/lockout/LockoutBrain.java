package net.kasax.challengecraft.bot.lockout;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotBrain;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalDifficulty;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays Lockout Bingo. The rules as the bot understands them: every tile goes to whoever does its
 * goal first and is then gone for everyone else, and the team with more tiles wins (a team is
 * through as soon as the others cannot catch up). So every tile is worth the same, taking one also
 * takes it from the opponent, and the fastest way to win is to always go for the tile that is
 * quickest to get right now.
 *
 * <p>Each time it is free, the brain looks at the open tiles, works out for each what it would
 * take from here ({@link LockoutGoals}: items to gather and craft, mobs to hunt, blocks to use),
 * and goes for the cheapest. It drops a goal the moment the tile is claimed (by anyone), and
 * gives up on goals that keep failing. Goals it does not understand yet it simply leaves.
 *
 * <p>Difficulty changes how it plays, not what it knows: an easy bot thinks for a while between
 * goals, walks, skips the hard goals and sometimes picks a worse tile; a hard bot sprints and
 * never hesitates.
 */
public final class LockoutBrain implements BotBrain {
    public enum Difficulty {
        EASY(300, false, 0.35), NORMAL(80, false, 0.0), HARD(0, true, 0.0);

        final int pauseTicks;
        final boolean sprint;
        final double mistakes;

        Difficulty(int pauseTicks, boolean sprint, double mistakes) {
            this.pauseTicks = pauseTicks;
            this.sprint = sprint;
            this.mistakes = mistakes;
        }
    }

    private static final int MAX_TRIES = 2, REST_TICKS = 3600;

    private final Difficulty difficulty;
    private final ObtainPlanner planner = new ObtainPlanner();
    private final Map<String, Integer> tries = new HashMap<>();
    private final Map<String, Long> restUntil = new HashMap<>();
    private int targetIndex = -1;
    private String targetId;
    private BotTask goalTask;
    private int pause, checkTicks;
    private boolean joined;

    public LockoutBrain(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    @Override
    public void think(Bot bot) {
        MinecraftServer server = bot.server();
        bot.body().sprintWhenRunning = difficulty.sprint;
        if (!Chal_40_LockoutBingo.isActive()) return;
        if (!Chal_40_LockoutBingo.isRunning(server)) {
            joinLobby(bot, server);
            return;
        }
        if (pause-- > 0) return;

        LockoutBingoTeam team = Chal_40_LockoutBingo.teamOf(server, bot.id);
        if (team == null) return;
        record Choice(Chal_40_LockoutBingo.BoardTile tile, LockoutGoals.Option option) {
        }
        List<Choice> choices = new ArrayList<>();
        for (Chal_40_LockoutBingo.BoardTile tile : Chal_40_LockoutBingo.board(server)) {
            if (tile.claimedBy() != null) continue;
            if (resting(tile.goal().id(), server.overworld().getGameTime())) continue;
            if (difficulty == Difficulty.EASY && tile.goal().difficulty() == LockoutBingoGoalDifficulty.HARD) continue;
            LockoutGoals.Option o;
            try {
                o = LockoutGoals.plan(bot, planner, tile.goal());
            } catch (RuntimeException e) {
                o = null;
            }
            if (o != null) choices.add(new Choice(tile, o));
        }
        if (choices.isEmpty()) {
            pause = 200; // nothing it can do now; look again in a while (tiles, time of day change)
            return;
        }
        choices.sort(Comparator.comparingDouble(c -> c.option().cost()));
        Choice pick = choices.get(0);
        if (difficulty.mistakes > 0 && choices.size() > 1 && bot.body().getRandom().nextDouble() < difficulty.mistakes) {
            pick = choices.get(1 + bot.body().getRandom().nextInt(Math.min(3, choices.size() - 1)));
        }
        targetIndex = pick.tile().index();
        targetId = pick.tile().goal().id();
        goalTask = pick.option().task().get();
        bot.say("goal: " + pick.tile().goal().title().getString() + " (~" + Math.round(pick.option().cost()) + " s, "
                + choices.size() + " doable)");
        bot.doNow(goalTask);
    }

    @Override
    public void tick(Bot bot) {
        if (++checkTicks % 20 != 0 || targetIndex < 0) return;
        MinecraftServer server = bot.server();
        if (!Chal_40_LockoutBingo.isRunning(server)) {
            drop(bot);
            return;
        }
        // The tile went to someone (maybe us): whatever we were doing for it is pointless now.
        for (Chal_40_LockoutBingo.BoardTile tile : Chal_40_LockoutBingo.board(server)) {
            if (tile.index() == targetIndex && tile.claimedBy() != null) {
                boolean ours = tile.claimedBy() == Chal_40_LockoutBingo.teamOf(server, bot.id);
                bot.say(ours ? "got " + targetId : targetId + " was taken, moving on");
                drop(bot);
                pause = difficulty.pauseTicks;
                return;
            }
        }
    }

    @Override
    public void finished(Bot bot, BotTask task, boolean success) {
        if (task != goalTask) return;
        long now = bot.body().level().getGameTime();
        if (success) {
            // The game counts it within a second (it checks inventories once a second): wait for
            // that instead of starting on the same tile again.
            // If it never counts (the goal wants something else than the bot thought), give up on it.
            boolean again = tries.merge(targetId, 1, Integer::sum) < MAX_TRIES + 1;
            restUntil.put(targetId, now + (again ? 40 : REST_TICKS));
            targetIndex = -1;
            goalTask = null;
            return;
        }
        // Failed: try it again later, but not forever.
        if (tries.merge(targetId, 1, Integer::sum) >= MAX_TRIES) {
            // Leave it alone for a while: things change (night brings mobs, the world is explored further).
            restUntil.put(targetId, now + REST_TICKS);
            tries.remove(targetId);
        }
        targetIndex = -1;
        goalTask = null;
        pause = difficulty.pauseTicks / 2;
    }

    @Override
    public void respawned(Bot bot) {
        targetIndex = -1;
        goalTask = null;
    }

    private boolean resting(String goalId, long now) {
        Long until = restUntil.get(goalId);
        return until != null && until > now;
    }

    private void drop(Bot bot) {
        targetIndex = -1;
        goalTask = null;
        bot.clearTasks();
    }

    /** Before the game: take a team nobody else is on and say ready. */
    private void joinLobby(Bot bot, MinecraftServer server) {
        if (joined && Chal_40_LockoutBingo.teamOf(server, bot.id) != null) return;
        Set<LockoutBingoTeam> used = EnumSet.noneOf(LockoutBingoTeam.class);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == bot.body()) continue;
            LockoutBingoTeam t = Chal_40_LockoutBingo.teamOf(server, p.getUUID());
            if (t != null) used.add(t);
        }
        for (LockoutBingoTeam t : LockoutBingoTeam.values()) {
            if (!used.contains(t)) {
                Chal_40_LockoutBingo.joinAndReady(bot.body(), t);
                joined = true;
                bot.say("joined team " + t.name().toLowerCase() + ", ready");
                return;
            }
        }
    }
}
