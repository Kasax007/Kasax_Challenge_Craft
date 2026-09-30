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
    private BotTask goalTask, sideTask;
    private String sideId;
    private long nextChance;
    private final Set<net.minecraft.core.BlockPos> treasureTried = new java.util.HashSet<>();
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
        // Keep something to eat: a player who is starving loses more time than bread costs.
        if (needsFood(bot)) return;
        // The opening every player plays: wood, a table, then stone tools (pickaxe and axe), before
        // anything else. They make every later goal quicker.
        if (opening(bot)) return;
        List<Choice> choices = choices(bot, -1);
        // Nothing (more) to do down here: back to the Overworld, where most goals are.
        if (choices.isEmpty() && bot.body().level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            bot.say("nothing left for me here, going back");
            bot.doNow(new net.kasax.challengecraft.bot.task.ThroughPortalTask());
            return;
        }
        if (choices.isEmpty()) {
            pause = 200; // nothing it can do now; look again in a while (tiles, time of day change)
            return;
        }
        choices.sort(Comparator.comparingDouble(c -> c.option().cost()));
        Choice pick = difficulty == Difficulty.EASY ? choices.get(0) : lookAhead(bot, choices);
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

    private static final Set<net.minecraft.world.item.Item> FOODS = Set.of(net.minecraft.world.item.Items.BREAD,
            net.minecraft.world.item.Items.COOKED_BEEF, net.minecraft.world.item.Items.COOKED_PORKCHOP,
            net.minecraft.world.item.Items.COOKED_MUTTON, net.minecraft.world.item.Items.COOKED_CHICKEN,
            net.minecraft.world.item.Items.BAKED_POTATO, net.minecraft.world.item.Items.APPLE,
            net.minecraft.world.item.Items.COOKED_COD, net.minecraft.world.item.Items.COOKED_SALMON,
            net.minecraft.world.item.Items.CARROT);
    private long nextFoodCheck;

    /**
     * Low on food: stock up if it is cheap right now (hay bales in a village make bread, animals
     * around make steak). Returns whether it went for food.
     */
    private int openingStep;

    private static final List<Set<net.minecraft.world.item.Item>> OPENING = List.of(
            Set.of(net.minecraft.world.item.Items.STONE_PICKAXE, net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE),
            Set.of(net.minecraft.world.item.Items.STONE_AXE, net.minecraft.world.item.Items.IRON_AXE, net.minecraft.world.item.Items.DIAMOND_AXE));

    /** Works through the opening; returns whether it started a step of it. */
    private boolean opening(Bot bot) {
        if (bot.body().level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        while (openingStep < OPENING.size()) {
            Set<net.minecraft.world.item.Item> want = OPENING.get(openingStep);
            if (ObtainPlanner.countAny(bot.body(), want) > 0) {
                openingStep++;
                continue;
            }
            if (openingTries++ > 2) { // could not (no stone anywhere?): play on without
                openingStep++;
                openingTries = 0;
                continue;
            }
            bot.say("opening: " + ObtainPlanner.names(want));
            bot.doNow(new net.kasax.challengecraft.bot.task.ObtainTask(want, 1, planner));
            return true;
        }
        return false;
    }

    private int openingTries;

    private boolean needsFood(Bot bot) {
        long now = bot.body().level().getGameTime();
        if (now < nextFoodCheck) return false;
        nextFoodCheck = now + 600;
        int points = 0;
        for (var st : bot.body().getInventory().getNonEquipmentItems()) {
            var food = st.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food != null && !st.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) points += food.nutrition() * st.getCount();
        }
        // Nobody hunts for food with a full stomach: only once hunger has started to bite.
        if (points >= 16 || bot.body().getFoodData().getFoodLevel() >= 17) return false;
        int have = ObtainPlanner.countAny(bot.body(), FOODS);
        double cost = planner.estimate(bot, FOODS, have + 4);
        if (cost > 90) return false;
        bot.say("stocking up on food (~" + Math.round(cost) + " s)");
        bot.doNow(new net.kasax.challengecraft.bot.task.ObtainTask(FOODS, have + 4, planner));
        return true;
    }

    private record Choice(Chal_40_LockoutBingo.BoardTile tile, LockoutGoals.Option option) {
    }

    /** Every open tile the bot knows how to do, with its way and effort from here. */
    private List<Choice> choices(Bot bot, int except) {
        MinecraftServer server = bot.server();
        long now = server.overworld().getGameTime();
        List<Choice> out = new ArrayList<>();
        for (Chal_40_LockoutBingo.BoardTile tile : Chal_40_LockoutBingo.board(server)) {
            if (tile.claimedBy() != null || tile.index() == except) continue;
            if (resting(tile.goal().id(), now)) continue;
            if (difficulty == Difficulty.EASY && tile.goal().difficulty() == LockoutBingoGoalDifficulty.HARD) continue;
            LockoutGoals.Option o;
            try {
                o = LockoutGoals.plan(bot, planner, tile.goal());
            } catch (RuntimeException e) {
                o = null;
            }
            if (o != null) out.add(new Choice(tile, o));
        }
        return out;
    }

    /**
     * Routes rather than single tiles: of the quickest few, the one that leaves the next tile
     * quickest too (iron ingot first when the bucket and the shears are also on the board: the
     * furnace and the iron are then already there). Scored as its own time plus half of the
     * cheapest next tile's time, as if it held what this one leaves it.
     */
    private Choice lookAhead(Bot bot, List<Choice> sorted) {
        Choice best = sorted.get(0);
        double bestScore = Double.MAX_VALUE;
        for (Choice c : sorted.subList(0, Math.min(5, sorted.size()))) {
            Set<net.minecraft.world.item.Item> after = c.option().yields().get();
            double next = planner.assuming(bot, after, () -> {
                double min = Double.MAX_VALUE;
                for (Choice o : sorted) {
                    if (o == c) continue;
                    LockoutGoals.Option again = LockoutGoals.plan(bot, planner, o.tile().goal());
                    if (again != null) min = Math.min(min, again.cost());
                }
                return min == Double.MAX_VALUE ? 0 : min;
            });
            double score = c.option().cost() + 0.5 * next;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best != sorted.get(0)) {
            bot.say("route: " + best.tile().goal().id() + " first, it makes the next ones quicker");
        }
        return best;
    }

    @Override
    public void tick(Bot bot) {
        if (++checkTicks % 20 != 0 || targetIndex < 0) return;
        if (checkTicks % 40 == 0 && sideTask == null) takeChances(bot);
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

    /**
     * What a player does on the way: a loot chest in a structure it passes (shipwreck, ruined
     * portal, temple: iron, gold, flint and steel, obsidian, food), or another tile that has become
     * a matter of seconds right here (the cow for "milk a cow" walking by, the sugar cane at the
     * river bank). Done in between, then back to the goal.
     */
    private void takeChances(Bot bot) {
        if (bot.current() instanceof net.kasax.challengecraft.bot.task.EatTask) return;
        java.util.List<net.minecraft.core.BlockPos> chests = bot.senses().lootables();
        if (!chests.isEmpty() && chests.get(0).distSqr(bot.body().blockPosition()) < 32 * 32) {
            sideTask = new net.kasax.challengecraft.bot.task.LootTask(chests.get(0));
            bot.say("on the way: a loot chest");
            bot.interject(sideTask);
            return;
        }
        // A treasure map from a shipwreck: the X is worth a detour when it is not too far.
        net.kasax.challengecraft.bot.task.TreasureTask treasure = net.kasax.challengecraft.bot.task.TreasureTask.of(bot.body());
        if (treasure != null && !treasureTried.contains(treasure.column())
                && treasure.column().distSqr(bot.body().blockPosition().atY(0)) < 250 * 250) {
            treasureTried.add(treasure.column());
            sideTask = treasure;
            bot.say("on the way: the treasure from the map");
            bot.interject(sideTask);
            return;
        }
        if (bot.body().level().getGameTime() < nextChance) return;
        nextChance = bot.body().level().getGameTime() + 200;
        for (Choice c : choices(bot, targetIndex)) {
            if (c.option().cost() < 12) {
                sideTask = c.option().task().get();
                if (sideTask == null) continue;
                sideId = c.tile().goal().id();
                bot.say("on the way: " + c.tile().goal().title().getString() + " (~" + Math.round(c.option().cost()) + " s)");
                bot.interject(sideTask);
                return;
            }
        }
    }

    @Override
    public void finished(Bot bot, BotTask task, boolean success) {
        if (task == sideTask) {
            if (!success && sideId != null) restUntil.put(sideId, bot.body().level().getGameTime() + REST_TICKS);
            sideTask = null;
            sideId = null;
            return;
        }
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
        openingStep = 0; // the tools are gone with the rest
        openingTries = 0;
        targetIndex = -1;
        goalTask = null;
        sideTask = null;
    }

    private boolean resting(String goalId, long now) {
        Long until = restUntil.get(goalId);
        return until != null && until > now;
    }

    private void drop(Bot bot) {
        targetIndex = -1;
        goalTask = null;
        sideTask = null;
        sideId = null;
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
