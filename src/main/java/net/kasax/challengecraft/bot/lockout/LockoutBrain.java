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
    /** Per goal, for the report: how often it was set out on, how often that failed, ticks spent. */
    private final Map<String, Integer> starts = new HashMap<>(), fails = new HashMap<>(), spent = new HashMap<>();

    /** One line per open tile: why it is still open (no way known, or tried and how). */
    public List<String> openTileReport(Bot bot) {
        List<String> out = new ArrayList<>();
        for (Chal_40_LockoutBingo.BoardTile tile : Chal_40_LockoutBingo.board(bot.server())) {
            if (tile.claimedBy() != null) continue;
            String id = tile.goal().id();
            LockoutGoals.Option o;
            try {
                o = LockoutGoals.plan(bot, planner, tile.goal());
            } catch (RuntimeException e) {
                o = null;
            }
            out.add(id + ": " + (o == null ? "NO WAY KNOWN" : "plan ~" + Math.round(o.cost()) + " s")
                    + ", started " + starts.getOrDefault(id, 0) + ", failed " + fails.getOrDefault(id, 0)
                    + ", overran " + overruns.getOrDefault(id, 0) + ", spent " + spent.getOrDefault(id, 0) / 20 + " s");
        }
        return out;
    }
    private int targetIndex = -1;
    private String targetId;
    private BotTask goalTask, sideTask;
    private String sideId;
    /** The picked-up-on-the-way item a side task is for, and when each may be tried again. */
    private net.minecraft.world.item.Item sideWant;
    private final java.util.Map<net.minecraft.world.item.Item, Long> wantRest = new java.util.HashMap<>();
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
        // Died: the things are still lying there for a few minutes. Fetching them beats making
        // everything again (if it is not too far and they did not burn).
        if (bot.lastDeath != null) {
            Bot.Death d = bot.lastDeath;
            bot.lastDeath = null;
            long age = bot.body().level().getGameTime() - d.time();
            double dist = Math.sqrt(d.pos().distSqr(bot.body().blockPosition()));
            if (d.recoverable() && d.dimension() == bot.body().level().dimension() && age < 3600 && dist < 350
                    && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.STONE_PICKAXE, net.minecraft.world.item.Items.IRON_PICKAXE)) == 0) {
                bot.say("back for my things at " + d.pos().toShortString() + " (" + Math.round(dist) + " blocks)");
                start(bot, new net.kasax.challengecraft.bot.task.RecoverTask(d.pos()), 600 + (long) (dist * 8));
                return;
            }
        }
        // Keep something to eat: a player who is starving loses more time than bread costs.
        if (needsFood(bot)) return;
        // The opening every player plays: wood, a table, then stone tools (pickaxe and axe), before
        // anything else. They make every later goal quicker.
        if (opening(bot)) return;
        // Iron the quick way: a shipwreck's chests (or a village's) when one is near and the plan
        // wants iron. Once per structure.
        if (strategist.wantsIron() && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.IRON_INGOT)) < 3
                && bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
            for (String kind : List.of("shipwreck", "village")) {
                var seen = net.kasax.challengecraft.bot.task.VisitStructureTask.nearest(bot,
                        net.kasax.challengecraft.bot.task.VisitStructureTask.resolve(level, kind));
                if (seen == null || seen.spot().distSqr(bot.body().blockPosition()) > 160 * 160) continue;
                String key = kind + "@" + (seen.spot().getX() >> 6) + "," + (seen.spot().getZ() >> 6);
                if (!raided.add(key)) continue;
                bot.say("iron from the " + kind + " at " + seen.spot().toShortString());
                start(bot, new net.kasax.challengecraft.bot.task.RaidTask(level, kind), 3600);
                return;
            }
        }
        // Leaving: the table it put down comes along.
        if (net.kasax.challengecraft.bot.task.PackTableTask.worth(bot)) {
            start(bot, new net.kasax.challengecraft.bot.task.PackTableTask(bot.ownTable), 400);
            return;
        }
        strategist.update(bot, planner, replanNow);
        // The Nether phase: with the kit in hand and several Nether tiles open, a player goes now
        // rather than one more Overworld tile at a time (each looks a bit cheaper on its own, and
        // the trip never happens). A couple of tries per life.
        if (bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD && bot.body().level().getGameTime() >= netherRetryAt
                && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.BUCKET, net.minecraft.world.item.Items.WATER_BUCKET)) > 0) {
            int netherTiles = 0;
            for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
                if (t.claimedBy() == null && t.goal().category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER) netherTiles++;
            }
            if (netherTiles >= 2) {
                LockoutGoals.Option trip = LockoutGoals.netherForTest(bot, planner);
                if (trip != null && trip.cost() < 120 * netherTiles) {
                    // (Again in three minutes if this one does not get there.)
                    netherRetryAt = bot.body().level().getGameTime() + 3600;
                    bot.say("Nether phase: " + netherTiles + " tiles there, the way in ~" + Math.round(trip.cost()) + " s");
                    start(bot, trip.task().get(), budget(trip.cost(), 2400, 9000));
                    return;
                }
            }
        }
        // The investment the plan has decided on: iron tools and a bucket now, before the cheap
        // tiles (they pay for themselves on the tiles after). A few tries, then without.
        if (strategist.wantsIron() && bot.body().level().getGameTime() >= kitRetryAt && bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            for (var item : List.of(net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.BUCKET)) {
                if (ObtainPlanner.countAny(bot.body(), Set.of(item)) > 0) continue;
                kitRetryAt = bot.body().level().getGameTime() + 1200; // (not again at once if this fails)
                bot.say("investing: " + ObtainPlanner.name(item) + " (the plan wants the iron kit)");
                start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(item), 1, planner), 4800);
                return;
            }
        }
        replanNow = false;
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
        // By the plan: the effort, less what a tile is worth beyond itself (see LockoutStrategist).
        choices.sort(Comparator.comparingDouble(c -> c.option().cost() - strategist.bonus(c.tile().goal().id())));
        Choice pick = difficulty == Difficulty.EASY ? choices.get(0) : lookAhead(bot, choices);
        if (difficulty.mistakes > 0 && choices.size() > 1 && bot.body().getRandom().nextDouble() < difficulty.mistakes) {
            pick = choices.get(1 + bot.body().getRandom().nextInt(Math.min(3, choices.size() - 1)));
        }
        targetIndex = pick.tile().index();
        targetId = pick.tile().goal().id();
        goalTask = pick.option().task().get();
        starts.merge(pick.tile().goal().id(), 1, Integer::sum);
        goalStarted = bot.body().level().getGameTime();
        goalBudget = budget(pick.option().cost(), 1200, 9000);
        goalEstimate = pick.option().cost();
        goalFirstEstimate = pick.option().cost() / GoalExperience.factor(pick.tile().goal().id());
        goalGoal = pick.tile().goal();
        extensions = 0;
        String reason = strategist.why(pick.tile().goal().id());
        bot.say("goal: " + pick.tile().goal().title().getString() + " (~" + Math.round(pick.option().cost()) + " s, "
                + choices.size() + " doable" + (reason == null ? "" : ", " + reason) + ")");
        start(bot, goalTask, goalBudget);
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
    private final LockoutStrategist strategist = new LockoutStrategist();
    private static final List<String> WORTH_LOOTING = List.of("shipwreck", "ruined_portal", "desert_pyramid", "jungle_pyramid",
            "village", "buried_treasure", "igloo", "pillager_outpost", "bastion", "fortress", "end_city");
    private final Map<String, Integer> lootedIn = new HashMap<>();
    private final Set<String> raided = new java.util.HashSet<>();
    private boolean replanNow = true;
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
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(want, 1, planner), 2400);
            return true;
        }
        return false;
    }

    private int openingTries;
    private long netherRetryAt, kitRetryAt;

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
        int level = bot.body().getFoodData().getFoodLevel();
        if (points >= 16 || level >= 17) return false;
        int have = ObtainPlanner.countAny(bot.body(), FOODS);
        double cost = planner.estimate(bot, FOODS, have + 4);
        // Food right here (a cow next to it, bread in a chest) is taken while a little hungry; a
        // search only once hunger bites (below six shanks: soon no sprinting), and not a long one.
        if (level >= 12 && cost > 25 || cost > 90) return false;
        bot.say("stocking up on food (~" + Math.round(cost) + " s)");
        start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(FOODS, have + 4, planner), budget(cost, 600, 2400));
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
            // Harder than it looked last time (it ran over its time): believed less now.
            // What earlier games taught about this tile (it always takes three times as long...).
            if (o != null) {
                double f = GoalExperience.factor(tile.goal().id());
                if (f != 1) o = new LockoutGoals.Option(o.cost() * f, o.task(), o.yields());
            }
            int over = overruns.getOrDefault(tile.goal().id(), 0);
            if (o != null && over > 0) o = new LockoutGoals.Option(o.cost() * (1 + over) + 30 * over, o.task(), o.yields());
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

    /**
     * How long a goal may take before it is given up for now: three times the estimate, at least
     * {@code min} ticks, at most {@code max}. A player notices when something is not working.
     */
    private static long budget(double estimateSeconds, long min, long max) {
        return Math.max(min, Math.min(max, (long) (estimateSeconds * 20 * 3)));
    }

    private long goalStarted, goalBudget, sideStarted, sideBudget;
    private double goalEstimate, goalFirstEstimate;
    private net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal goalGoal;
    private int extensions;
    /** How often each goal ran over its time: its estimates are trusted that much less. */
    private final java.util.Map<String, Integer> overruns = new java.util.HashMap<>();

    /** What the current goal would still take from here (seconds), or infinity if it cannot be told. */
    private double remaining(Bot bot) {
        if (goalGoal == null) return Double.MAX_VALUE;
        try {
            LockoutGoals.Option o = LockoutGoals.plan(bot, planner, goalGoal);
            return o == null ? Double.MAX_VALUE : o.cost();
        } catch (RuntimeException e) {
            return Double.MAX_VALUE;
        }
    }
    /** Whatever the brain set going last (a goal, food, the opening), and its time allowance. */
    private BotTask running;
    private long runningSince, runningBudget;
    private net.minecraft.core.BlockPos runningFrom;
    private int runningExtensions;

    private static double horizontal(net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void start(Bot bot, BotTask task, long budgetTicks) {
        running = task;
        runningSince = bot.body().level().getGameTime();
        runningBudget = budgetTicks;
        runningFrom = bot.body().blockPosition();
        runningExtensions = 0;
        bot.doNow(task);
    }

    @Override
    public void tick(Bot bot) {
        long now = bot.body().level().getGameTime();
        if (sideTask != null && now - sideStarted > sideBudget) {
            bot.say("that takes too long, back to the goal");
            if (sideId != null) restUntil.put(sideId, now + REST_TICKS);
            if (sideWant != null) wantRest.put(sideWant, now + 6000);
            drop(bot);
            return;
        }
        if (running != null && running != goalTask && now - runningSince > runningBudget && bot.current() != null
                && runningExtensions < 4 && runningFrom != null && horizontal(runningFrom, bot.body().blockPosition()) > 60) {
            // Still on its way somewhere (the forest on the horizon): not stuck, more time.
            runningExtensions++;
            runningSince = now;
            runningFrom = bot.body().blockPosition();
            return;
        }
        if (running != null && running != goalTask && now - runningSince > runningBudget && bot.current() != null) {
            bot.say(running.describe() + " takes too long, something else");
            running = null;
            nextFoodCheck = now + 2400; // (if it was food: not the same way again right away)
            drop(bot);
            pause = 100;
            return;
        }
        if (goalTask != null && targetId != null && now - goalStarted > goalBudget) {
            // Well on the way (half way down to the ore, say): what is left is worth finishing.
            double left = remaining(bot);
            if (extensions < 2 && left < goalEstimate * 0.7) {
                extensions++;
                goalEstimate = left;
                goalBudget = now - goalStarted + budget(left, 600, 6000);
                bot.say("still on " + targetId + ", ~" + Math.round(left) + " s left");
                return;
            }
            bot.say(targetId + " takes too long, something else first");
            overruns.merge(targetId, 1, Integer::sum);
            // (For next games too: at least this long, and it was not even done.)
            GoalExperience.record(targetId, goalFirstEstimate, 1.5 * (now - goalStarted) / 20.0);
            restUntil.put(targetId, now + REST_TICKS);
            drop(bot);
            return;
        }
        if (goalTask != null && targetId != null) spent.merge(targetId, 1, Integer::sum);
        if (checkTicks % 100 == 0) {
            boolean open = false;
            for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
                if (t.claimedBy() == null && t.goal().id().equals("survive_explosion")) open = true;
            }
            bot.welcomeExplosion = open;
        }
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
        // Chests worth a detour: where the good loot is (iron in a shipwreck, flint and steel and
        // gold at a ruined portal, the temples' treasure), a few per structure, not every chest in
        // a mineshaft or a trial chamber full of spiders and silverfish.
        net.minecraft.core.BlockPos chest = null;
        for (net.minecraft.core.BlockPos c : bot.senses().lootables()) {
            if (c.distSqr(bot.body().blockPosition()) > 32 * 32) break;
            net.minecraft.resources.Identifier in = bot.senses().structureAt(c);
            if (in == null || !WORTH_LOOTING.stream().anyMatch(w -> in.getPath().startsWith(w))) continue;
            if (lootedIn.merge(in.getPath() + "@" + (c.getX() >> 6) + "," + (c.getZ() >> 6), 0, Integer::sum) >= 3) continue;
            chest = c;
            lootedIn.merge(in.getPath() + "@" + (c.getX() >> 6) + "," + (c.getZ() >> 6), 1, Integer::sum);
            break;
        }
        if (chest != null) {
            sideTask = new net.kasax.challengecraft.bot.task.LootTask(chest);
            sideStarted = bot.body().level().getGameTime();
            sideBudget = 900;
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
            sideStarted = bot.body().level().getGameTime();
            sideBudget = 4800;
            bot.say("on the way: the treasure from the map");
            bot.interject(sideTask);
            return;
        }
        if (bot.body().level().getGameTime() < nextChance) return;
        nextChance = bot.body().level().getGameTime() + 200;
        // What the plan needs later, lying right here: take it now (flint from the gravel at the
        // river on the way, the iron ore in the cave wall).
        for (var want : strategist.wants().entrySet()) {
            net.minecraft.world.item.Item item = want.getKey();
            if (ObtainPlanner.countAny(bot.body(), Set.of(item)) > 0) continue;
            if (wantRest.getOrDefault(item, 0L) > bot.body().level().getGameTime()) continue;
            if (item == net.minecraft.world.item.Items.FLINT && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.FLINT_AND_STEEL)) > 0) continue;
            net.minecraft.world.level.block.Block source = want.getValue();
            net.minecraft.core.BlockPos at = bot.memory().nearest((net.minecraft.server.level.ServerLevel) bot.body().level(),
                    bot.body().blockPosition(), st -> st.is(source), Set.of());
            if (at == null || at.distSqr(bot.body().blockPosition()) > 10 * 10) continue;
            sideTask = new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(item), item == net.minecraft.world.item.Items.SUGAR_CANE ? 3 : 1, planner);
            sideId = null;
            sideWant = item;
            sideStarted = bot.body().level().getGameTime();
            sideBudget = 600;
            bot.say("on the way: " + ObtainPlanner.name(item) + " for later");
            bot.interject(sideTask);
            return;
        }
        for (Choice c : choices(bot, targetIndex)) {
            if (c.option().cost() < 8) {
                sideTask = c.option().task().get();
                if (sideTask == null) continue;
                sideId = c.tile().goal().id();
                sideWant = null;
                sideStarted = bot.body().level().getGameTime();
                sideBudget = budget(c.option().cost(), 400, 1200);
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
            if (!success && sideWant != null) wantRest.put(sideWant, bot.body().level().getGameTime() + 6000);
            sideTask = null;
            sideWant = null;
            sideId = null;
            return;
        }
        if (task != goalTask) return;
        long now = bot.body().level().getGameTime();
        if (success) {
            // Learnt for next time: how long it really took against the plain estimate.
            if (targetId != null && tries.getOrDefault(targetId, 0) == 0) GoalExperience.record(targetId, goalFirstEstimate, (now - goalStarted) / 20.0);
            // The game counts it within a second (it checks inventories once a second): wait for
            // that instead of starting on the same tile again.
            // If it never counts (the goal wants something else than the bot thought), give up on it.
            boolean again = tries.merge(targetId, 1, Integer::sum) < MAX_TRIES + 1;
            restUntil.put(targetId, now + (again ? 40 : REST_TICKS));
            targetIndex = -1;
            goalTask = null;
            return;
        }
        fails.merge(targetId, 1, Integer::sum);
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
        replanNow = true;
        openingStep = 0; // the tools are gone with the rest
        kitRetryAt = 0;
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
        sideWant = null;
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
