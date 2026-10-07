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
    /** The big brain over the deciders below: what each set out to do, failures, loops, the mode. */
    private final Cortex cortex = new Cortex();
    /** The decider now thinking (its intents are named after it), the intent running, and whether the cortex said no. */
    private String stepKey, runningKey;
    private boolean startRefused;
    private boolean joined;

    public LockoutBrain(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    /** Only these tiles count (a field test of one goal at a time); null: the whole board. */
    private Set<String> only;
    /** In a field test: no way known to the tile (see {@link #only}). */
    public boolean noWay;
    /** The dimension it is in, and when it last came through a portal (against going to and fro). */
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension;
    private long switchedAt = -100_000;

    public LockoutBrain only(Set<String> goalIds) {
        this.only = goalIds;
        return this;
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
        Cortex.Mode mode = cortex.mode(bot);
        boolean surviving = mode == Cortex.Mode.SURVIVE;
        if (bot.lastDeath != null) {
            stepKey = "recover";
            Bot.Death d = bot.lastDeath;
            bot.lastDeath = null;
            long age = bot.body().level().getGameTime() - d.time();
            double dist = Math.sqrt(d.pos().distSqr(bot.body().blockPosition()));
            // (Not back into the dark with bare hands, to whatever killed it there - unless close.)
            var lv = bot.body().level();
            boolean night = lv.dimension() == net.minecraft.world.level.Level.OVERWORLD && lv.isDarkOutside();
            // Not bare-handed back to what killed it where it still lurks (in a cave, or at night),
            // and not to a spot deep under ground that takes digging to reach: those cost a second
            // death more often than they bring the things back (0 of 10 on the benchmarks).
            // The things lie there five minutes: the time is that, not more.
            long left = 6000 - age - 200;
            boolean lurking = d.byMob() && (d.underground() || night);
            boolean deep = d.underground() && d.pos().getY() < bot.body().getY() - 12;
            if (d.recoverable() && d.dimension() == bot.body().level().dimension() && left > dist * 6 && dist < (night ? 40 : 250)
                    && !lurking && !deep
                    && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.STONE_PICKAXE, net.minecraft.world.item.Items.IRON_PICKAXE)) == 0) {
                bot.say("back for my things at " + d.pos().toShortString() + " (" + Math.round(dist) + " blocks)");
                start(bot, new net.kasax.challengecraft.bot.task.RecoverTask(d.pos()), Math.min(left, 600 + (long) (dist * 8)));
                return;
            }
            if (d.recoverable()) bot.say("my things at " + d.pos().toShortString() + " left there ("
                    + (lurking ? "what killed me is still about" : deep ? "too deep to get at" : "too far for the time they lie") + ")");
        }
        // In a basalt delta with nothing to do there: out of it first. Magma cubes by the dozen,
        // magma underfoot, lava in every hollow - the Nether's deadliest ground.
        if (step(bot, "deltas", () -> leaveDeltas(bot))) return;
        // Keep something to eat: a player who is starving loses more time than bread costs.
        if (step(bot, "food", () -> needsFood(bot))) return;
        // Few hearts left down in a cave, in the dark or with monsters about: dug in, eaten, and
        // waited for the hearts - not on with the next ore at two hearts (seed 66: six deaths,
        // most of them at one to four hp, still mining "for later").
        if (step(bot, "heal", () -> healUp(bot))) return;
        // The night: geared for it (a weapon, food, armour or a shield, the hearts) it is played;
        // else slept away in a bed (one made before dusk if wool is close), or dug in till
        // morning. Half of all deaths were in the first night, on the surface, bare.
        if (step(bot, "night", () -> nightPlan(bot))) return;
        // Gold on before the piglins see it (a helmet or boots; gold ore all about down there).
        if (step(bot, "gold", () -> goldGuard(bot))) return;
        // The opening every player plays: wood, a table, then stone tools (pickaxe and axe), before
        // anything else. They make every later goal quicker.
        if (step(bot, "opening", () -> opening(bot))) return;
        // The kit a player never goes without: a pickaxe (a new one before the old one breaks)
        // and a stack of blocks to build with (out of a hole, over a gap, a pillar from mobs).
        if (step(bot, "kit", () -> keepKit(bot))) return;
        // Iron the quick way: a shipwreck's chests (or a village's) when one is near and the plan
        // wants iron. Once per structure.
        // And whatever the plan: a shipwreck (or a ruined portal) not far off is looted the moment
        // it is seen. Iron, gold, emeralds, food, a treasure map: things that make every later
        // tile quicker, wanted now or not.
        // (By day: at night a village is full of zombies, and a long walk in the dark is how
        // games are lost.)
        stepKey = "raid";
        if (!surviving && bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD && bot.body().getHealth() >= 12
                && !bot.body().level().isDarkOutside()) {
            var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
            boolean ironWanted = (strategist.wantsIron() || !openingIron) && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.IRON_INGOT)) < 3;
            // Only for what is wanted: iron (the kit, the board), food running low; a ruined
            // portal for its obsidian and gold when the board or the way to the Nether wants them.
            boolean foodLow = bot.body().getFoodData().getFoodLevel() < 12 && net.kasax.challengecraft.bot.task.EatTask.bestFood(bot.body()) < 0;
            boolean portalWanted = planner.demandFor(net.minecraft.world.item.Items.OBSIDIAN) > 0 || planner.demandFor(net.minecraft.world.item.Items.GOLD_INGOT) > 0
                    || planner.demandFor(net.minecraft.world.item.Items.FLINT_AND_STEEL) > 0 || netherTilesOpen(bot);
            for (String kind : List.of("shipwreck", "ruined_portal", "village")) {
                int radius = switch (kind) {
                    case "shipwreck" -> ironWanted ? 160 : foodLow ? 80 : 0;
                    case "ruined_portal" -> portalWanted ? 64 : 0;
                    default -> ironWanted ? 160 : 0;
                };
                if (radius == 0) continue;
                var seen = net.kasax.challengecraft.bot.task.VisitStructureTask.nearest(bot,
                        net.kasax.challengecraft.bot.task.VisitStructureTask.resolve(level, kind));
                if (seen == null || seen.spot().distSqr(bot.body().blockPosition()) > (long) radius * radius) continue;
                String key = kind + "@" + (seen.spot().getX() >> 6) + "," + (seen.spot().getZ() >> 6);
                if (!raided.add(key)) continue;
                bot.say("loot from the " + kind.replace('_', ' ') + " at " + seen.spot().toShortString());
                // What the board wants that may stand about there (the bell, a hay bale).
                Set<net.minecraft.world.level.block.Block> blocks = new java.util.HashSet<>();
                if (kind.equals("village")) {
                    Set<net.minecraft.world.item.Item> items = new java.util.HashSet<>(planner.boardDemand.keySet());
                    for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server()))
                        if (t.claimedBy() == null) items.addAll(LockoutGoals.items(t.goal().targets()));
                    for (var i : items) {
                        if (i instanceof net.minecraft.world.item.BlockItem bi && !net.kasax.challengecraft.bot.BotWorld.COMMON.contains(bi.getBlock())
                                && ObtainPlanner.countAny(bot.body(), Set.of(i)) == 0) blocks.add(bi.getBlock());
                    }
                }
                start(bot, new net.kasax.challengecraft.bot.task.RaidTask(level, kind, blocks), kind.equals("village") ? 6000 : 3600);
                if (startRefused) {
                    startRefused = false;
                    continue;
                }
                return;
            }
        }
        // Leaving: the table it put down comes along.
        if (step(bot, "table", () -> {
            if (!net.kasax.challengecraft.bot.task.PackTableTask.worth(bot)) return false;
            start(bot, new net.kasax.challengecraft.bot.task.PackTableTask(bot.ownTable), 400);
            return true;
        })) return;
        strategist.update(bot, planner, replanNow);
        // What not to throw away when the pack is full: what the board and the plan want.
        bot.keepItems.clear();
        bot.keepItems.addAll(planner.boardDemand.keySet());
        bot.keepItems.addAll(strategist.wants().keySet());
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server()))
            if (t.claimedBy() == null) bot.keepItems.addAll(LockoutGoals.items(t.goal().targets()));
        // The Nether phase: with the kit in hand and several Nether tiles open, a player goes now
        // rather than one more Overworld tile at a time (each looks a bit cheaper on its own, and
        // the trip never happens). A couple of tries per life.
        stepKey = "nether";
        if (!surviving && bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD && bot.body().level().getGameTime() >= netherRetryAt
                && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.BUCKET, net.minecraft.world.item.Items.WATER_BUCKET)) > 0) {
            int netherTiles = 0;
            for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
                if (t.claimedBy() == null && t.goal().category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER) netherTiles++;
            }
            if (netherTiles >= 2) {
                LockoutGoals.Option trip = LockoutGoals.netherForTest(bot, planner);
                if (trip != null && trip.cost() < 120 * netherTiles) {
                    // (Again in three minutes if this one does not get there.)
                    // (Each failed try waits longer for the next: 3, 6, 12 minutes...)
                    netherRetryAt = bot.body().level().getGameTime() + (3600L << Math.min(3, netherFails++));
                    bot.say("Nether phase: " + netherTiles + " tiles there, the way in ~" + Math.round(trip.cost()) + " s");
                    start(bot, trip.task().get(), budget(trip.cost(), 2400, 9000));
                    if (!startRefused) return;
                    startRefused = false;
                }
            }
        }
        // The investment the plan has decided on: iron tools and a bucket now, before the cheap
        // tiles (they pay for themselves on the tiles after). A few tries, then without.
        stepKey = "invest";
        if (strategist.wantsIron() && bot.body().level().getGameTime() >= kitRetryAt && bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            for (var item : List.of(net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.BUCKET)) {
                if (ObtainPlanner.countAny(bot.body(), Set.of(item)) > 0) continue;
                // (Not again at once if this fails, and each failure waits longer; four, and the
                // kit is left to the tiles that need it.)
                if (kitTries >= 4) break;
                kitRetryAt = bot.body().level().getGameTime() + (1200L << Math.min(3, kitTries++));
                bot.say("investing: " + ObtainPlanner.name(item) + " (the plan wants the iron kit)");
                start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(item), 1, planner), 4800);
                if (!startRefused) return;
                startRefused = false;
            }
        }
        // A bastion near in the Nether and gold of use on the board (bartering, gold tiles): its
        // chests and gold blocks, where few piglins watch. Once per bastion.
        if (!surviving && step(bot, "bastion", () -> bastionRaid(bot))) return;
        // Night (or a cave, or the Nether), and several monsters wanted: one hunt for all of them.
        if (!surviving && step(bot, "hunt", () -> huntRound(bot))) return;
        // Night with nothing to hunt: the night slept away in the bed it carries (fewer monsters
        // on the way, the spawn set here), and the bed taken along again.
        if (step(bot, "sleep", () -> sleep(bot))) return;
        stepKey = null;
        replanNow = false;
        List<Choice> choices = choices(bot, -1);
        // Only dear tiles left (over half an hour, by the estimate) or ones that ran over a while
        // ago: the cheapest of them all the same - standing about never fills the board.
        if (choices.isEmpty()) {
            choices = choices(bot, -1, true);
            if (!choices.isEmpty()) bot.say("only hard tiles left: trying the cheapest of them");
        }
        // A field test of one tile: no way to it is the answer, nothing else to do.
        if (only != null) {
            noWay = choices.isEmpty();
            if (noWay) {
                pause = 200;
                return;
            }
        }
        // Nothing (more) to do down here: back to the Overworld, where most goals are.
        if (choices.isEmpty() && bot.body().level().dimension() != net.minecraft.world.level.Level.OVERWORLD
                && LockoutGoals.wayBack(bot) != null) {
            bot.say("nothing left for me here, going back");
            bot.doNow(new net.kasax.challengecraft.bot.task.ThroughPortalTask());
            return;
        }
        // Down in the Nether with no way back known: obsidian for a frame of its own (a ruined
        // portal's, mined with a diamond pickaxe), rather than standing about.
        if (choices.isEmpty() && bot.body().level().dimension() == net.minecraft.world.level.Level.NETHER
                && LockoutGoals.wayBack(bot) == null && bot.senses().knownPortal(bot.body().blockPosition()) == null
                && bot.body().level().getGameTime() >= strandedRetryAt) {
            strandedRetryAt = bot.body().level().getGameTime() + 6000;
            bot.say("no way back known: obsidian for a portal of my own");
            start(bot, new net.kasax.challengecraft.bot.task.SequenceTask("a way back home", List.of(
                    () -> new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(net.minecraft.world.item.Items.OBSIDIAN), 10, planner),
                    () -> new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(net.minecraft.world.item.Items.FLINT_AND_STEEL), 1, planner)
                            .keeping(Set.of(net.minecraft.world.item.Items.OBSIDIAN)),
                    net.kasax.challengecraft.bot.task.ThroughPortalTask::new)), 9000);
            return;
        }
        if (choices.isEmpty()) {
            pause = 200; // nothing it can do now; look again in a while (tiles, time of day change)
            return;
        }
        // By the plan: the effort, less what a tile is worth beyond itself (see LockoutStrategist).
        choices.sort(Comparator.comparingDouble(c -> c.option().cost() - strategist.bonus(c.tile().goal().id())));
        Choice pick = difficulty == Difficulty.EASY ? choices.get(0) : route(bot, choices);
        if (difficulty.mistakes > 0 && choices.size() > 1 && bot.body().getRandom().nextDouble() < difficulty.mistakes) {
            pick = choices.get(1 + bot.body().getRandom().nextInt(Math.min(3, choices.size() - 1)));
        }
        // Off to the Nether: packed for it first - blocks to bridge and climb with (the way
        // home up to a portal on a ledge), and food (no animals worth the name down there).
        if (netherBound(bot, pick) && packForNether(bot)) return;
        targetIndex = pick.tile().index();
        targetId = pick.tile().goal().id();
        goalTask = pick.option().task().get();
        starts.merge(pick.tile().goal().id(), 1, Integer::sum);
        goalStarted = bot.body().level().getGameTime();
        goalPaused = 0;
        goalBudget = budget(pick.option().cost(), 1200, 9000);
        goalEstimate = pick.option().cost();
        // (The plain estimate: before what experience and overruns added, else each overrun
        // would be measured against a figure that already had the last one in it.)
        goalFirstEstimate = pick.plain();
        goalProxy = pick.option().proxy();
        goalGoal = pick.tile().goal();
        extensions = 0;
        stalls = 0;
        String reason = strategist.why(pick.tile().goal().id());
        Odds po = odds(bot, pick, hazard(bot));
        bot.say("goal: " + pick.tile().goal().title().getString() + " (~" + Math.round(pick.option().cost()) + " s, of that ~"
                + Math.round(po.luck()) + " s luck, " + Math.round(100 * po.within(goalBudget / 20.0)) + "% within " + goalBudget / 20 + " s, "
                + choices.size() + " doable" + (reason == null ? "" : ", " + reason) + ")");
        start(bot, goalTask, goalBudget);
        if (startRefused) {
            // (A loop on this tile: the cortex set it aside; something else next time round.)
            startRefused = false;
            targetIndex = -1;
            goalTask = null;
        }
    }

    private static boolean deltas(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos p) {
        return level.getBiome(p).is(net.minecraft.world.level.biome.Biomes.BASALT_DELTAS);
    }

    /** Out of a basalt delta (unless an open tile wants something from there): to the nearest other ground seen. */
    private boolean leaveDeltas(Bot bot) {
        var level = bot.body().level();
        net.minecraft.core.BlockPos feet = bot.body().blockPosition();
        if (level.dimension() != net.minecraft.world.level.Level.NETHER || !deltas(level, feet)) return false;
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
            if (t.claimedBy() != null || only != null && !only.contains(t.goal().id())) continue;
            String id = t.goal().id();
            if (id.contains("basalt") || id.contains("delta") || id.contains("magma") || id.contains("blackstone")) return false;
        }
        // The nearest point of other ground, from rings of looks round (the biome map, as a player
        // sees the grey give way to red or blue).
        net.minecraft.core.BlockPos out = null;
        for (int r = 16; r <= 128 && out == null; r += 16) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12;
                net.minecraft.core.BlockPos q = feet.offset((int) (Math.cos(a) * r), 0, (int) (Math.sin(a) * r));
                if (!level.isLoaded(q) || deltas(level, q)) continue;
                out = q;
                break;
            }
        }
        if (out == null) return false;
        net.minecraft.core.BlockPos steer = out;
        bot.say("in a basalt delta with nothing to do here: out of it, towards " + out.toShortString());
        start(bot, new net.kasax.challengecraft.bot.task.NavGoalTask("out of the basalt deltas",
                (lv, p) -> !deltas(lv, p) && lv.getBlockState(p).getCollisionShape(lv, p).isEmpty() && lv.getFluidState(p).isEmpty()
                        && !lv.getBlockState(p.below()).getCollisionShape(lv, p.below()).isEmpty() && lv.getFluidState(p.below()).isEmpty(),
                b -> steer, 2400), 2400);
        return true;
    }

    /** One decider, under the cortex: returns whether it started something (a refused start is not that). */
    private boolean step(Bot bot, String key, java.util.function.BooleanSupplier decider) {
        stepKey = key;
        startRefused = false;
        boolean r = decider.getAsBoolean();
        stepKey = null;
        if (startRefused) {
            startRefused = false;
            return false;
        }
        return r;
    }

    /** The intent a task stands for: the decider and what the task does, without the counts. */
    private String intent(BotTask task) {
        if (task == goalTask && targetId != null) return "goal:" + targetId;
        return (stepKey == null ? "other" : stepKey) + ":" + plain(task);
    }

    /** What a task does, without the counts (one intent whatever the numbers). */
    private static String plain(BotTask task) {
        String what = task.describe().replaceAll("[0-9]+", "").replaceAll("\\s+", " ").trim();
        return what.length() > 40 ? what.substring(0, 40) : what;
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
            // The full kit a player keeps: a sword for what comes at night (right after the
            // pickaxe: no weapon at nightfall on seed 55), an axe, a shovel for sand, gravel and
            // snow (each two or three cobblestone and a stick at the same table).
            Set.of(net.minecraft.world.item.Items.STONE_SWORD, net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.DIAMOND_SWORD),
            Set.of(net.minecraft.world.item.Items.STONE_AXE, net.minecraft.world.item.Items.IRON_AXE, net.minecraft.world.item.Items.DIAMOND_AXE),
            Set.of(net.minecraft.world.item.Items.STONE_SHOVEL, net.minecraft.world.item.Items.IRON_SHOVEL, net.minecraft.world.item.Items.DIAMOND_SHOVEL));

    /** Works through the opening; returns whether it started a step of it. */
    private static final Set<net.minecraft.world.item.Item> PICKAXES = Set.of(net.minecraft.world.item.Items.WOODEN_PICKAXE, net.minecraft.world.item.Items.STONE_PICKAXE,
            net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.GOLDEN_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE,
            net.minecraft.world.item.Items.NETHERITE_PICKAXE);
    private static final Set<net.minecraft.world.item.Item> GOOD_PICKAXES = Set.of(net.minecraft.world.item.Items.STONE_PICKAXE,
            net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE, net.minecraft.world.item.Items.NETHERITE_PICKAXE);
    private long kitCheckAt;
    private int ironSeen;

    private long huntRetryAt, strandedRetryAt;

    /** The kinds of monster wanted for open kill tiles that can be met where Bob is now. */
    private Set<net.minecraft.world.entity.EntityType<?>> wantedMonsters(Bot bot) {
        Set<net.minecraft.world.entity.EntityType<?>> out = new java.util.HashSet<>();
        boolean nether = bot.body().level().dimension() == net.minecraft.world.level.Level.NETHER;
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
            if (t.claimedBy() != null || t.goal().type() != net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.KILL) continue;
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.tryParse(t.goal().primaryTarget())).orElse(null);
            if (type == null || type.getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) continue;
            if (type == net.minecraft.world.entity.EntityTypes.ENDER_DRAGON || type == net.minecraft.world.entity.EntityTypes.WITHER
                    || type == net.minecraft.world.entity.EntityTypes.WARDEN || type == net.minecraft.world.entity.EntityTypes.ELDER_GUARDIAN) continue;
            if (net.kasax.challengecraft.bot.plan.BotKnowledge.NETHER_MOBS.contains(type) != nether) continue;
            // Only what may turn up here: the common night monsters, or those whose home this is
            // (a slime in the swamp it stands in); not cave spiders far from any mineshaft.
            var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
            if (!nether && !net.kasax.challengecraft.bot.plan.MobHabitats.anywhere(level, type)
                    && (net.kasax.challengecraft.bot.plan.MobHabitats.of(level, type).isEmpty() || !net.kasax.challengecraft.bot.plan.MobHabitats.inHabitat(bot, type))) continue;
            out.add(type);
        }
        return out;
    }

    /** Whether it is hunting time here: night on the surface, any time in a cave or the Nether. */
    private static boolean huntingTime(Bot bot) {
        var level = bot.body().level();
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) return true;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        return level.isDarkOutside() || net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body());
    }

    private boolean huntRound(Bot bot) {
        long now = bot.body().level().getGameTime();
        if (now < huntRetryAt || !huntingTime(bot) || bot.body().getHealth() < 14) return false;
        // (Not out at night after monsters ungeared: that is how the first nights were lost.)
        if (nightComing(bot, DUSK) && !nightReady(bot)) return false;
        Set<net.minecraft.world.entity.EntityType<?>> wanted = wantedMonsters(bot);
        // At night on the surface even a single one: they come to it, the hunt is cheap now and
        // dear by day. (In a cave at any time only for several: it is there to mine.)
        var lv = bot.body().level();
        boolean night = lv.dimension() == net.minecraft.world.level.Level.OVERWORLD && lv.isDarkOutside()
                && !net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body());
        if (wanted.size() < (night ? 1 : 2)) return false;
        huntRetryAt = now + 6000; // (not straight back into it if it found nothing)
        long clock = bot.body().level().getOverworldClockTime() % 24000;
        long nightLeft = clock >= 12000 ? 24000 - clock : 0;
        long budgetTicks = Math.max(2400, Math.min(9000, nightLeft > 0 ? nightLeft : 4800));
        bot.say("hunting time: one round for " + wanted.size() + (wanted.size() == 1 ? " kind" : " kinds") + " of monster");
        start(bot, new net.kasax.challengecraft.bot.task.HuntRoundTask(() -> wantedMonsters(bot), () -> huntingTime(bot)), budgetTicks);
        return true;
    }

    private long sleepRetryAt;

    // ---- the night plan --------------------------------------------------------------------

    /** Dusk: from here on, ready for the night or making ready (monsters come out from about 13000). */
    static final long DUSK = 10000, SHELTER_FROM = 12600, DAWN = 23200;
    private long bedTriedDay = -1, foodTriedDay = -1, shelterRetryAt;

    private static long clock(Bot bot) {
        return bot.body().level().getOverworldClockTime() % 24000;
    }

    /** Whether it is the night (or close to it) up in the Overworld. */
    private static boolean nightComing(Bot bot, long from) {
        long c = clock(bot);
        return bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD && c >= from && c < DAWN;
    }

    /** Ready for a night out on the surface, as a player would be: a weapon, food, armour or a shield, the hearts. */
    public static boolean nightReady(Bot bot) {
        var body = bot.body();
        boolean armed = false, shield = body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD);
        int food = 0;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) armed = true;
            if (st.is(net.minecraft.world.item.Items.SHIELD)) shield = true;
            var f = st.get(net.minecraft.core.component.DataComponents.FOOD);
            if (f != null && !st.is(net.minecraft.world.item.Items.ROTTEN_FLESH) && !st.is(net.minecraft.world.item.Items.SPIDER_EYE)) food += f.nutrition() * st.getCount();
        }
        return armed && food >= 8 && (body.getArmorValue() >= 6 || shield && body.getArmorValue() >= 2) && body.getHealth() >= 12;
    }

    private static net.minecraft.world.item.Item bedInPack(Bot bot) {
        for (var st : bot.body().getInventory().getNonEquipmentItems()) if (st.is(net.minecraft.tags.ItemTags.BEDS)) return st.getItem();
        return null;
    }

    private static final Set<net.minecraft.world.item.Item> BEDS = tagged(net.minecraft.tags.ItemTags.BEDS);

    /**
     * Not ready for the night: before dusk a bed if one is quickly made (wool from sheep in sight);
     * at nightfall slept away in it, or dug in until the morning (a hole with a lid: nothing gets
     * at it, the hearts come back). Underground already (a mine of its own), it works on.
     */
    private boolean nightPlan(Bot bot) {
        if (!nightComing(bot, DUSK) || nightReady(bot)) return false;
        var level = bot.body().level();
        long now = level.getGameTime(), c = clock(bot);
        long day = level.getOverworldClockTime() / 24000;
        net.minecraft.world.item.Item bed = bedInPack(bot);
        // Before dusk proper: a bed, if it comes cheap (the sheep are in sight, wool in the pack).
        if (bed == null && c < SHELTER_FROM - 300 && bedTriedDay != day) {
            bedTriedDay = day;
            double est = planner.estimate(bot, BEDS, 1);
            if (est <= 150) {
                bot.say("dusk soon, not geared for the night: a bed first (~" + Math.round(est) + " s)");
                nightPrep = new net.kasax.challengecraft.bot.task.ObtainTask(BEDS, 1, planner);
                start(bot, nightPrep, 3000);
                if (!startRefused) return true;
                startRefused = false;
            }
        }
        // And something to eat for the night (in the hole the hearts only come back with food).
        if (c < SHELTER_FROM - 300 && foodTriedDay != day) {
            int points = 0;
            for (var st : bot.body().getInventory().getNonEquipmentItems()) {
                var f = st.get(net.minecraft.core.component.DataComponents.FOOD);
                if (f != null && !st.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) points += f.nutrition() * st.getCount();
            }
            if (points < 8) {
                foodTriedDay = day;
                int have = ObtainPlanner.countAny(bot.body(), FOODS);
                double est = planner.estimate(bot, FOODS, have + 4);
                if (est <= 180) {
                    bot.say("dusk soon and little to eat (" + points + "): food for the night (~" + Math.round(est) + " s)");
                    nextErrand = "food";
                    nightPrep = new net.kasax.challengecraft.bot.task.ObtainTask(FOODS, have + 4, planner);
                    start(bot, nightPrep, 3000);
                    if (!startRefused) return true;
                    startRefused = false;
                }
            }
        }
        if (c < SHELTER_FROM) return false;
        if (net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body())) return false;
        // A bed: the night slept away (once the game lets one lie down), if that ends it.
        if (bed != null && c >= 12542 && now >= sleepRetryAt && nightWouldPass(bot)) {
            sleepRetryAt = now + Math.max(600, 24000 - c);
            Set<net.minecraft.world.item.Item> beds = Set.of(bed);
            bot.say("night, not geared for it: sleeping it away");
            net.minecraft.world.item.Item placed = bed;
            start(bot, new net.kasax.challengecraft.bot.task.SequenceTask("sleep the night away", List.of(
                    () -> new net.kasax.challengecraft.bot.task.PlaceAndUseTask(placed, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.CLICK, null, 0),
                    net.kasax.challengecraft.bot.task.SleepTask::new,
                    () -> new net.kasax.challengecraft.bot.task.MineTask("the bed", s -> s.is(net.minecraft.tags.BlockTags.BEDS), beds,
                            ObtainPlanner.countAny(bot.body(), beds) + 1).knownOnly())), 1200);
            if (!startRefused) return true;
            startRefused = false;
        }
        // Else dug in till the morning - when the night is dangerous here and now: monsters about
        // or coming, no weapon, few hearts, nothing to eat. A quiet night is played on, carefully
        // (measured on fixed boards: a hole every night cost half the tiles and saved no lives).
        if (!nightThreat(bot)) return false;
        if (now < shelterRetryAt) return false;
        // (Mid-jump or mid-step: on the ground first, then looked at - not walked off for that.)
        if (!bot.body().onGround() && !bot.body().isInWater()) {
            start(bot, new net.kasax.challengecraft.bot.task.WaitTask(10), 40);
            if (!startRefused) return true;
            startRefused = false;
            return false;
        }
        shelterRetryAt = now + 400;
        if (!net.kasax.challengecraft.bot.task.HideTask.possible(bot) && !net.kasax.challengecraft.bot.task.HideTask.shelterPossible(bot)) {
            // (Not here - water, sand, a berry bush: a few steps off and again.)
            var r = bot.body().getRandom();
            var off = bot.body().blockPosition().offset(r.nextInt(9) - 4, 0, r.nextInt(9) - 4);
            var spot = bot.body().level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, off);
            bot.say("no ground to dig in here (" + net.kasax.challengecraft.bot.task.HideTask.shelterProblem(bot) + "): a few steps off");
            start(bot, new net.kasax.challengecraft.bot.task.GoToTask(spot, 1), 300);
            if (!startRefused) return true;
            startRefused = false;
            return false;
        }
        // With a pickaxe and something to eat: the night worked, not waited - dug in, the hole
        // shut, and a tunnel of its own down to the iron (none of it open to a cave), as players
        // spend a first night. The morning brings the shield and the armour with it.
        if (NIGHT_MINE && ObtainPlanner.countAny(bot.body(), GOOD_PICKAXES) > 0 && net.kasax.challengecraft.bot.task.HideTask.possible(bot)) {
            int iron = ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.RAW_IRON, net.minecraft.world.item.Items.IRON_INGOT));
            int want = Math.min(16, Math.max(iron + 4, planner.demandFor(net.minecraft.world.item.Items.RAW_IRON) + 1 + 8));
            bot.say("night, not geared for it (" + nightGaps(bot) + "): dug in, and a tunnel of my own for iron till morning");
            start(bot, new net.kasax.challengecraft.bot.task.SequenceTask("the night's mine", List.of(
                    net.kasax.challengecraft.bot.task.HideTask::new,
                    () -> new net.kasax.challengecraft.bot.task.MineTask("raw_iron", s -> s.is(net.minecraft.world.level.block.Blocks.IRON_ORE)
                            || s.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE), Set.of(net.minecraft.world.item.Items.RAW_IRON),
                            ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.RAW_IRON)) + want - iron, 16).sealed())), 13000);
            if (!startRefused) return true;
            startRefused = false;
        }
        bot.say("night, not geared for it (" + nightGaps(bot) + "): dug in till morning");
        start(bot, net.kasax.challengecraft.bot.task.HideTask.shelter(), 13000);
        if (!startRefused) return true;
        startRefused = false;
        return false;
    }

    /**
     * Whether this night is dangerous where it stands: a monster out for it or two about within
     * twenty-four blocks, no weapon, under twelve hearts, or nothing to eat.
     */
    private static boolean nightThreat(Bot bot) {
        var body = bot.body();
        boolean armed = false;
        int food = 0;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) armed = true;
            var f = st.get(net.minecraft.core.component.DataComponents.FOOD);
            if (f != null && !st.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) food += f.nutrition() * st.getCount();
        }
        if (!armed || body.getHealth() < 12 || food == 0 && body.getFoodData().getFoodLevel() < 14) return true;
        var about = body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, body.getBoundingBox().inflate(24),
                m -> m.isAlive() && m.getType() != net.minecraft.world.entity.EntityTypes.ENDERMAN);
        if (about.size() >= 2) return true;
        for (var m : about) if (m.getTarget() == body || m.distanceTo(body) < 12) return true;
        return false;
    }

    /** What is missing for the night, for the log. */
    private static String nightGaps(Bot bot) {
        var body = bot.body();
        List<String> out = new ArrayList<>();
        boolean armed = false, shield = body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD);
        int food = 0;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) armed = true;
            if (st.is(net.minecraft.world.item.Items.SHIELD)) shield = true;
            var f = st.get(net.minecraft.core.component.DataComponents.FOOD);
            if (f != null && !st.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) food += f.nutrition() * st.getCount();
        }
        if (!armed) out.add("no weapon");
        if (food < 8) out.add("food " + food);
        if (body.getArmorValue() < 6 && !(shield && body.getArmorValue() >= 2)) out.add("armour " + body.getArmorValue() + (shield ? "+shield" : ""));
        if (body.getHealth() < 12) out.add("hp " + Math.round(body.getHealth()));
        return String.join(", ", out);
    }

    private boolean sleep(Bot bot) {
        var level = bot.body().level();
        long now = level.getGameTime();
        if (now < sleepRetryAt || level.dimension() != net.minecraft.world.level.Level.OVERWORLD || !level.isDarkOutside()
                || net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body())) return false;
        net.minecraft.world.item.Item bed = null;
        for (var st : bot.body().getInventory().getNonEquipmentItems()) if (st.is(net.minecraft.tags.ItemTags.BEDS)) bed = st.getItem();
        if (bed == null) return false;
        // Only when the night is no use: no open tile wants what comes out in the dark.
        String wanted = nightTile(bot);
        if (wanted != null) {
            sleepRetryAt = now + 1200;
            return false;
        }
        // And with others about, only if lying down really ends the night: enough of them in
        // bed already (the game's "players sleeping percentage", with it counted).
        if (!nightWouldPass(bot)) {
            sleepRetryAt = now + 200;
            return false;
        }
        // (Once a night: if the others stay up, it does not pass.)
        long clock = level.getOverworldClockTime() % 24000;
        sleepRetryAt = now + Math.max(600, 24000 - clock);
        net.minecraft.world.item.Item placed = bed;
        Set<net.minecraft.world.item.Item> beds = Set.of(placed);
        bot.say("night, and nothing on the board wants it: sleeping");
        start(bot, new net.kasax.challengecraft.bot.task.SequenceTask("sleep the night away", List.of(
                () -> new net.kasax.challengecraft.bot.task.PlaceAndUseTask(placed, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.CLICK, null, 0),
                net.kasax.challengecraft.bot.task.SleepTask::new,
                () -> new net.kasax.challengecraft.bot.task.MineTask("the bed", s -> s.is(net.minecraft.tags.BlockTags.BEDS), beds,
                        ObtainPlanner.countAny(bot.body(), beds) + 1).knownOnly())), 1200);
        return true;
    }

    private static final Set<String> HEAVY = Set.of("enderman", "witch", "vindicator", "evoker", "ravager", "piglin_brute", "warden", "ghast", "blaze", "wither_skeleton");

    private static boolean heavy(String entityId) {
        try {
            return HEAVY.contains(net.minecraft.resources.Identifier.parse(entityId).getPath());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Fit for a hard fight: a weapon, armour (six points, or a shield and some), the hearts. */
    static boolean geared(Bot bot) {
        var body = bot.body();
        boolean armed = false, shield = body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD);
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) armed = true;
            if (st.is(net.minecraft.world.item.Items.SHIELD)) shield = true;
        }
        return armed && (body.getArmorValue() >= 6 || shield && body.getArmorValue() >= 2) && body.getHealth() >= 16;
    }

    /** An open tile that is only to be had in the Nether. */
    private static boolean hostile(String entityId) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(entityId));
        return type != null && type.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
    }

    private static boolean netherTilesOpen(Bot bot) {
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
            if (t.claimedBy() == null && t.goal().category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER) return true;
        }
        return false;
    }

    private static final Set<String> NIGHT_MOBS = Set.of("zombie", "skeleton", "spider", "creeper", "enderman", "witch", "phantom",
            "drowned", "stray", "husk", "zombie_villager", "slime", "bogged");
    private static final Set<String> NIGHT_DROPS = Set.of("gunpowder", "bone", "bone_meal", "bone_block", "string", "ender_pearl", "ender_eye",
            "spider_eye", "fermented_spider_eye", "rotten_flesh", "arrow", "slime_ball", "slime_block", "phantom_membrane", "glowstone_dust",
            "tipped_arrow");

    /** An open tile that the night serves (its monsters, what they drop, the phantoms): its id, or null. */
    private String nightTile(Bot bot) {
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
            if (t.claimedBy() != null) continue;
            var g = t.goal();
            String id = g.id();
            String target;
            try {
                target = net.minecraft.resources.Identifier.parse(g.primaryTarget()).getPath();
            } catch (RuntimeException e) {
                target = "";
            }
            if (g.type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.KILL && NIGHT_MOBS.contains(target)) return id;
            if (NIGHT_DROPS.contains(target)) return id;
            if (id.contains("phantom") || id.contains("skeleton") || id.contains("shield") || id.contains("arrow") || id.contains("slime")
                    || id.contains("sniper")) return id;
        }
        return null;
    }

    /**
     * Whether its lying down would end the night: alone, yes; with others in the Overworld, only
     * if enough of them (by the game rule) are in bed already, it counted in.
     */
    public static boolean nightWouldPass(Bot bot) {
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        int players = 0, asleep = 0;
        for (var p : level.players()) {
            if (p.isSpectator()) continue;
            players++;
            if (p.isSleeping() && p != bot.body()) asleep++;
        }
        int pct = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        int needed = Math.max(1, (int) Math.ceil(players * pct / 100.0));
        return asleep + 1 >= needed;
    }

    /** Every pickaxe it has nearly used up (under a tenth left). */
    private static boolean pickaxeAlmostGone(Bot bot) {
        boolean any = false;
        for (var st : bot.body().getInventory().getNonEquipmentItems()) {
            if (!PICKAXES.contains(st.getItem())) continue;
            any = true;
            if (!st.isDamageableItem() || st.getDamageValue() < st.getMaxDamage() * 0.9) return false;
        }
        return any;
    }

    private long bastionCheckAt;

    private boolean bastionRaid(Bot bot) {
        var body = bot.body();
        long now = body.level().getGameTime();
        if (now < bastionCheckAt || body.level().dimension() != net.minecraft.world.level.Level.NETHER || body.getHealth() < 16) return false;
        bastionCheckAt = now + 1200;
        var level = (net.minecraft.server.level.ServerLevel) body.level();
        var seen = net.kasax.challengecraft.bot.task.VisitStructureTask.nearest(bot,
                net.kasax.challengecraft.bot.task.VisitStructureTask.resolve(level, "bastion_remnant"));
        if (seen == null || seen.spot().distSqr(body.blockPosition()) > 128 * 128) return false;
        String key = "bastion@" + (seen.spot().getX() >> 6) + "," + (seen.spot().getZ() >> 6);
        if (raided.contains(key)) return false;
        // Worth it: something on the board wants gold, or what piglins give for it.
        boolean wanted = false;
        for (var item : bot.keepItems) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
            if (id.contains("gold") || ObtainPlanner.BARTER_GOLD.containsKey(item)) wanted = true;
        }
        if (!wanted) return false;
        raided.add(key);
        bot.say("the bastion at " + seen.spot().toShortString() + ": its gold");
        start(bot, new net.kasax.challengecraft.bot.task.BastionLootTask(level), 9600);
        return true;
    }

    private long netherPackAt;

    private static boolean netherBound(Bot bot, Choice pick) {
        return bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                && pick.tile().goal().category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER;
    }

    /** Blocks and food for a Nether trip; returns whether it went for some (once in a while). */
    private boolean packForNether(Bot bot) {
        var body = bot.body();
        long now = body.level().getGameTime();
        if (now < netherPackAt) return false;
        netherPackAt = now + 3600;
        int blocks = net.kasax.challengecraft.bot.BotActions.buildingBlocks(body);
        if (blocks < 48) {
            var stone = net.kasax.challengecraft.bot.BotActions.buildingBlocks(body.level());
            bot.say("for the Nether: blocks to build with (" + blocks + ")");
            nextErrand = "blocks";
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(stone, ObtainPlanner.countAny(body, stone) + 64 - blocks, planner), 1800);
            return true;
        }
        int points = 0;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            var food = st.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food != null && !st.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) points += food.nutrition() * st.getCount();
        }
        if (points < 24) {
            double cost = planner.estimate(bot, FOODS, ObtainPlanner.countAny(body, FOODS) + 6);
            if (cost > 240) return false;
            bot.say("for the Nether: food (~" + Math.round(cost) + " s)");
            nextErrand = "food";
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(FOODS, ObtainPlanner.countAny(body, FOODS) + 6, planner), budget(cost, 600, 2400));
            return true;
        }
        return false;
    }

    private long goldCheckAt;
    private static final Set<net.minecraft.world.item.Item> GOLD_PIECES = Set.of(net.minecraft.world.item.Items.GOLDEN_HELMET,
            net.minecraft.world.item.Items.GOLDEN_BOOTS, net.minecraft.world.item.Items.GOLDEN_CHESTPLATE, net.minecraft.world.item.Items.GOLDEN_LEGGINGS);

    /**
     * Piglins attack whoever wears no gold. In the Nether without a gold piece: one made (the
     * cheapest, a helmet or boots); up here with the gold for one and Nether tiles open: made
     * before going. Returns whether it started on that.
     */
    private boolean goldGuard(Bot bot) {
        var body = bot.body();
        long now = body.level().getGameTime();
        if (now < goldCheckAt) return false;
        goldCheckAt = now + 600;
        if (net.kasax.challengecraft.bot.BotArmor.wearsGold(body) || ObtainPlanner.countAny(body, GOLD_PIECES) > 0) return false;
        boolean nether = body.level().dimension() == net.minecraft.world.level.Level.NETHER;
        Set<net.minecraft.world.item.Item> piece = Set.of(net.minecraft.world.item.Items.GOLDEN_HELMET, net.minecraft.world.item.Items.GOLDEN_BOOTS);
        if (nether) {
            double cost = planner.estimate(bot, piece, 1);
            if (cost > 400) return false;
            goldCheckAt = now + 3600; // (once in a while if it does not work out)
            bot.say("no gold on in the Nether: a gold piece first (~" + Math.round(cost) + " s)");
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(piece, 1, planner), 4800);
            return true;
        }
        if (body.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        if (ObtainPlanner.countAny(body, Set.of(net.minecraft.world.item.Items.GOLD_INGOT)) < 4) return false;
        boolean netherTiles = false;
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
            if (t.claimedBy() == null && t.goal().category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER) netherTiles = true;
        }
        if (!netherTiles) return false;
        bot.say("gold boots for the Nether, from the gold I carry");
        start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(piece, 1, planner), 1200);
        return true;
    }

    private boolean keepKit(Bot bot) {
        long now = bot.body().level().getGameTime();
        // Iron come by (a vein on the way, a chest): looked at now, not in half a minute.
        int ironNow = ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.RAW_IRON));
        if (ironNow > ironSeen) {
            kitCheckAt = 0;
            upgradeRetryAt = 0;
        }
        ironSeen = ironNow;
        if (now < kitCheckAt) return false;
        kitCheckAt = now + 600; // (a try now and then, not again at once if it fails)
        var body = bot.body();
        int picks = 0, worn = 0;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (!PICKAXES.contains(st.getItem())) continue;
            picks++;
            if (st.isDamageableItem() && st.getDamageValue() > st.getMaxDamage() * 0.85) worn++;
        }
        if (picks == 0 || picks == worn) {
            int have = ObtainPlanner.countAny(body, GOOD_PICKAXES);
            bot.say(picks == 0 ? "no pickaxe: making one first" : "the pickaxe is nearly worn out: a spare one");
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(GOOD_PICKAXES, have + 1, planner), 2400);
            return true;
        }
        int blocks = 0;
        for (var st : body.getInventory().getNonEquipmentItems()) if (net.kasax.challengecraft.bot.BotActions.THROWAWAY.contains(st.getItem())) blocks += st.getCount();
        if (blocks < 12 && now >= blocksRetryAt) {
            var stone = net.kasax.challengecraft.bot.BotActions.buildingBlocks(bot.body().level());
            bot.say("few blocks left (" + blocks + "): a stack to build with");
            nextErrand = "blocks";
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(stone, ObtainPlanner.countAny(body, stone) + 24, planner), 1800);
            return true;
        }
        // The pack filling up and no bundle yet: one (string and leather), for the odds and ends.
        if (net.kasax.challengecraft.bot.BotBundles.bundle(body) == null && net.kasax.challengecraft.bot.BotBundles.freeSlots(body) <= 8
                && now >= bundleRetryAt) {
            bundleRetryAt = now + 6000;
            var bundleTask = new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(net.minecraft.world.item.Items.BUNDLE), 1, planner);
            double cost = cortex.believe("kit:" + plain(bundleTask), planner.estimate(bot, Set.of(net.minecraft.world.item.Items.BUNDLE), 1));
            if (cost < 120) {
                bot.say("the pack fills up: a bundle for the odds and ends (~" + Math.round(cost) + " s)");
                start(bot, bundleTask, budget(cost, 600, 2400));
                return true;
            }
        }
        // Iron to spare (a shipwreck's, beyond what the board wants): better tools first, then a
        // sword, a shield and armour; each makes every later tile quicker or safer.
        if (upgrade(bot)) return true;
        // Spare cobblestone (a furnace, stone tools, a wall against a creeper or a ghast) and spare
        // wood (a crafting table, sticks, a boat, a chest) on the way, as a player keeps them.
        boolean overworld = body.level().dimension() == net.minecraft.world.level.Level.OVERWORLD;
        int cobble = ObtainPlanner.countAny(body, CRAFTING_STONE);
        if (overworld && cobble < SPARE_COBBLE && now >= spareRetryAt) {
            spareRetryAt = now + 2400;
            bot.say("spare cobblestone (" + cobble + ")");
            nextErrand = "cobble";
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.COBBLED_DEEPSLATE),
                    cobble + 24, planner), 1800);
            return true;
        }
        if (overworld && woodPlanks(body) < SPARE_PLANKS && now >= woodRetryAt) {
            woodRetryAt = now + 2400;
            int logs = ObtainPlanner.countAny(body, LOGS);
            bot.say("spare wood (" + woodPlanks(body) + " planks' worth)");
            nextErrand = "wood";
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(LOGS, logs + 8, planner), 1800);
            return true;
        }
        return false;
    }

    private long upgradeRetryAt, bundleRetryAt;

    private record Gear(net.minecraft.world.item.Item item, int cost, Set<net.minecraft.world.item.Item> better) {
    }

    /** Better than iron, then the same in diamond: what counts as having a piece already. */
    private static Set<net.minecraft.world.item.Item> orBetter(String piece, boolean diamond) {
        Set<net.minecraft.world.item.Item> out = new java.util.HashSet<>();
        for (String mat : diamond ? new String[]{"diamond", "netherite"} : new String[]{"iron", "diamond", "netherite"}) {
            out.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(mat + "_" + piece)));
        }
        return out;
    }

    /** The order gear is made in: the pickaxe, the sword, then armour by protection per ingot (chest, legs, boots, helmet). */
    private static List<Gear> gear(boolean diamond) {
        String mat = diamond ? "diamond" : "iron";
        List<Gear> out = new ArrayList<>();
        int[] costs = {3, 2, 8, 7, 4, 5};
        String[] pieces = {"pickaxe", "sword", "chestplate", "leggings", "boots", "helmet"};
        for (int i = 0; i < pieces.length; i++) {
            out.add(new Gear(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(mat + "_" + pieces[i])),
                    costs[i], orBetter(pieces[i], diamond)));
        }
        return out;
    }

    private static boolean has(net.minecraft.world.entity.player.Player body, Set<net.minecraft.world.item.Item> any) {
        if (ObtainPlanner.countAny(body, any) > 0) return true;
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) if (any.contains(body.getItemBySlot(slot).getItem())) return true;
        return false;
    }

    /**
     * Iron or diamonds come by beyond what the board will want: made into gear, as a player does
     * - the shield first (one ingot, always worth it), then the pickaxe, the sword and the armour
     * piece by piece, whatever is affordable; the same again in diamond.
     */
    private boolean upgrade(Bot bot) {
        var body = bot.body();
        long now = body.level().getGameTime();
        if (now < upgradeRetryAt) return false;
        // Ingots and raw ore alike: the ore is smelted on the way to whatever it becomes.
        int iron = ObtainPlanner.countAny(body, Set.of(net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.RAW_IRON));
        // The shield whenever there is an ingot about: the arrows and the creepers' blasts end in it.
        if (iron >= 1 && !has(body, Set.of(net.minecraft.world.item.Items.SHIELD))) return make(bot, net.minecraft.world.item.Items.SHIELD, "a shield");
        // On hard, the chestplate next, iron for the board or not: most deaths are arrows,
        // blasts and zombie blows, and a death costs minutes - more than mining eight iron again.
        if (body.level().getDifficulty() == net.minecraft.world.Difficulty.HARD && iron >= 8
                && !has(body, Set.of(net.minecraft.world.item.Items.IRON_CHESTPLATE, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,
                net.minecraft.world.item.Items.NETHERITE_CHESTPLATE))) return make(bot, net.minecraft.world.item.Items.IRON_CHESTPLATE, "armour first on hard");
        for (boolean diamond : new boolean[]{false, true}) {
            int stock = diamond ? ObtainPlanner.countAny(body, Set.of(net.minecraft.world.item.Items.DIAMOND)) : iron;
            int spare = stock - planner.demandFor(diamond ? net.minecraft.world.item.Items.DIAMOND : net.minecraft.world.item.Items.IRON_INGOT);
            if (spare < 1) continue;
            for (Gear g : gear(diamond)) {
                if (has(body, g.better())) continue;
                // (Not enough for this one: the next that is affordable - boots now, the chest
                // later - except that nothing comes before the pickaxe.)
                if (spare < g.cost()) {
                    if (g.item() == net.minecraft.world.item.Items.IRON_PICKAXE) break;
                    continue;
                }
                return make(bot, g.item(), (diamond ? "diamonds" : "iron") + " to spare (" + spare + ")");
            }
        }
        return false;
    }

    private boolean make(Bot bot, net.minecraft.world.item.Item item, String why) {
        upgradeRetryAt = bot.body().level().getGameTime() + 2400;
        bot.say(why + ": " + ObtainPlanner.name(item));
        start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(item), 1, planner), 1200);
        return true;
    }

    /** At least this much cobblestone and this much wood (in planks) on hand, always. */
    private static final int SPARE_COBBLE = 16, SPARE_PLANKS = 12;
    private static final Set<net.minecraft.world.item.Item> CRAFTING_STONE = Set.of(net.minecraft.world.item.Items.COBBLESTONE,
            net.minecraft.world.item.Items.COBBLED_DEEPSLATE, net.minecraft.world.item.Items.BLACKSTONE);
    private static final Set<net.minecraft.world.item.Item> LOGS = tagged(net.minecraft.tags.ItemTags.LOGS);
    private static final Set<net.minecraft.world.item.Item> PLANKS = tagged(net.minecraft.tags.ItemTags.PLANKS);
    private long spareRetryAt, woodRetryAt;

    private static Set<net.minecraft.world.item.Item> tagged(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        Set<net.minecraft.world.item.Item> out = new java.util.HashSet<>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) if (new net.minecraft.world.item.ItemStack(item).is(tag)) out.add(item);
        return out;
    }

    /** The wood on hand, counted in planks (a log makes four). */
    static int woodPlanks(net.kasax.challengecraft.bot.BotPlayer body) {
        return ObtainPlanner.countAny(body, LOGS) * 4 + ObtainPlanner.countAny(body, PLANKS);
    }

    private boolean opening(Bot bot) {
        if (bot.body().level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        // Wood first, enough for the whole opening and some (table, sticks, the four tools, a
        // chest or a boat): one stop at the trees instead of a walk back for every few logs.
        if (openingStep == 0 && !openingWood) {
            openingWood = true;
            // (The opening's tools take about twelve planks; the spare stock wants twelve after.)
            if (woodPlanks(bot.body()) < 28) {
                int logs = ObtainPlanner.countAny(bot.body(), LOGS);
                int more = (28 - woodPlanks(bot.body()) + 3) / 4;
                bot.say("opening: wood for everything (" + (logs + more) + " logs)");
                nextErrand = "wood";
                start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(LOGS, logs + more, planner), 1800);
                return true;
            }
        }
        while (openingStep < OPENING.size()) {
            Set<net.minecraft.world.item.Item> want = OPENING.get(openingStep);
            if (ObtainPlanner.countAny(bot.body(), want) > 0) {
                openingStep++;
                openingTries = 0; // (each tool its own tries)
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
        // On hard, until the shield is made, iron is wanted from a shipwreck's or a village's
        // chests (the raid step, by day) - as speedrunners get it. Not dug for in the caves with
        // stone tools: that was measured (round 18) to cost lives - seven deaths on one seed, the
        // iron no sooner than without it.
        if (bot.body().level().getDifficulty() == net.minecraft.world.Difficulty.HARD && !openingIron
                && has(bot.body(), Set.of(net.minecraft.world.item.Items.SHIELD))) openingIron = true;
        // Iron in sight close to the surface - in a cliff, a cave mouth, a ravine's upper walls,
        // at most ten blocks down and fifty off: taken now for the shield (and the pickaxe), a
        // short way back up if anything goes wrong. Never a dig down to it.
        if (!openingIron && bot.body().level().getDifficulty() == net.minecraft.world.Difficulty.HARD
                && bot.body().getHealth() >= 12 && bot.body().level().getGameTime() >= shallowIronAt) {
            shallowIronAt = bot.body().level().getGameTime() + 600;
            var body = bot.body();
            int iron = ObtainPlanner.countAny(body, Set.of(net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.RAW_IRON));
            int need = 1 + (has(body, Set.of(net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE,
                    net.minecraft.world.item.Items.NETHERITE_PICKAXE)) ? 0 : 3);
            var ore = shallowIron(bot, shallowTried);
            if (iron < need && ore != null) {
                shallowTried.add(ore.immutable()); // (one go at each: no way there, another next time)
                bot.say("opening: iron near the surface at " + ore.toShortString() + " (for the shield)");
                nextErrand = "iron";
                start(bot, new net.kasax.challengecraft.bot.task.MineTask("raw_iron", SHALLOW_IRON, Set.of(net.minecraft.world.item.Items.RAW_IRON),
                        ObtainPlanner.countAny(body, Set.of(net.minecraft.world.item.Items.RAW_IRON)) + need - iron, null, ore).knownOnly(), 1800);
                return true;
            }
        }
        return false;
    }

    private boolean openingIron;
    private long nightDropAt, foodInterjectAt;
    /**
     * The night spent in a sealed tunnel of its own after iron instead of waiting in the hole.
     * Off: measured (round 19 C) it brought little iron - on hills the tunnel kept meeting the
     * open air and turned - and the round had more deaths. Kept for another try.
     */
    static final boolean NIGHT_MINE = false;

    /** The bed or the food being got for the night (not put aside at nightfall: it is the plan). */
    private BotTask nightPrep;

    /** Already seeing to the night: in a hole, in bed, or on the way to one. */
    private boolean sheltering(Bot bot) {
        for (BotTask t : bot.tasks()) {
            if (t == nightPrep && clock(bot) < 13500) return true;
            BotTask in = BotTask.innermost(t);
            if (t instanceof net.kasax.challengecraft.bot.task.HideTask || in instanceof net.kasax.challengecraft.bot.task.HideTask
                    || in instanceof net.kasax.challengecraft.bot.task.SleepTask || t.describe().startsWith("sleep")) return true;
        }
        return false;
    }
    private long shallowIronAt;
    private static final java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> SHALLOW_IRON =
            st -> st.is(net.minecraft.world.level.block.Blocks.IRON_ORE) || st.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE);

    /** Iron ore in sight within fifty blocks, at most ten below the surface over it; the nearest. */
    private final Set<net.minecraft.core.BlockPos> shallowTried = new java.util.HashSet<>();

    private static net.minecraft.core.BlockPos shallowIron(Bot bot, Set<net.minecraft.core.BlockPos> tried) {
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        var at = bot.body().blockPosition();
        for (var p : net.kasax.challengecraft.bot.BotWorld.nearestN(level, at, 48, 24, SHALLOW_IRON, true, bot.unreachable(), 16)) {
            int surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p).getY();
            if (p.getY() >= surface - 10 && tried.stream().noneMatch(t -> t.distSqr(p) < 9)) return p;
        }
        return null;
    }
    private long healRetryAt;

    private boolean healUp(Bot bot) {
        var body = bot.body();
        long now = body.level().getGameTime();
        boolean hard = body.level().getDifficulty() == net.minecraft.world.Difficulty.HARD;
        if (now < healRetryAt || body.getHealth() >= (hard ? 10 : 8)) return false;
        // (Hearts only come back with a stomach of eighteen: something to eat, or already full.)
        boolean canHeal = net.kasax.challengecraft.bot.task.EatTask.bestFood(body) >= 0 || body.getFoodData().getFoodLevel() >= 18;
        if (!canHeal) return false;
        boolean risky = net.kasax.challengecraft.bot.task.SurfaceTask.underground(body) || body.level().isDarkOutside()
                || !body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, body.getBoundingBox().inflate(16),
                net.minecraft.world.entity.LivingEntity::isAlive).isEmpty();
        if (!risky || !net.kasax.challengecraft.bot.task.HideTask.possible(bot)) return false;
        // (Not with one at arm's length after it: digging the hole takes seconds it hits through
        // - the zombie beside it killed it in its half-dug hole on seed 77. That one first: the
        // reflexes fight it or get away, and the hole comes after.)
        if (!body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, body.getBoundingBox().inflate(5),
                m -> m.isAlive() && m.getTarget() == body && !(m instanceof net.minecraft.world.entity.monster.RangedAttackMob)).isEmpty()) return false;
        healRetryAt = now + 600;
        bot.say("few hearts left (" + Math.round(body.getHealth()) + "): dug in to eat and heal");
        start(bot, new net.kasax.challengecraft.bot.task.HideTask(), 1800);
        return true;
    }

    private int openingTries;
    private boolean openingWood;
    private long netherRetryAt, kitRetryAt;
    private int kitTries;
    private int netherFails;

    private boolean needsFood(Bot bot) {
        long now = bot.body().level().getGameTime();
        if (now < nextFoodCheck) return false;
        nextFoodCheck = now + 600;
        int points = 0;
        for (var st : bot.body().getInventory().getNonEquipmentItems()) {
            var food = st.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food != null && !st.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) points += food.nutrition() * st.getCount();
        }
        // Nobody hunts for food with a full stomach and some in the pack: only once hunger has
        // started to bite - or with next to nothing left to eat (on hard the hearts only come
        // back with a full stomach: without food every fight is one hurt more till the last).
        int level = bot.body().getFoodData().getFoodLevel();
        boolean bare = points < 8;
        // Hurt and nothing to eat: on hard the hearts only come back with food, and the next fight
        // starts half dead (seed live: half health, no food, a creeper hunt in a cave - killed).
        boolean hurt = bot.body().getHealth() < 12 && bare;
        if (points >= 16 || level >= 17 && !bare) return false;
        int have = ObtainPlanner.countAny(bot.body(), FOODS);
        double cost = cortex.believe("food:" + plain(new net.kasax.challengecraft.bot.task.ObtainTask(FOODS, have + 4, planner)), planner.estimate(bot, FOODS, have + 4));
        // Food right here (a cow next to it, bread in a chest) is taken while a little hungry; a
        // search only once hunger bites (below six shanks: soon no sprinting), and not a long one.
        // Starving (six shanks and less: no sprinting, and on hard the hunger eats the hearts
        // away - a fall then kills): food first, whatever it costs.
        // (A stock while not hungry yet only if it is near - a cow in sight -, not a hunt across
        // the country: the estimates of those run long.)
        // (Hurt and nothing to eat: food first, whatever it costs. Not hurt: as before - three
        // minutes for apples at the start delayed the tools into the night on seed 77.)
        // (Once the stone tools are made: a stock is worth a couple of minutes - the first night
        // is not to be had without one, and on hard no heart comes back without food; 30 of 54
        // deaths had nothing to eat in the pack.)
        boolean opened = ObtainPlanner.countAny(bot.body(), GOOD_PICKAXES) > 0;
        double stockCost = bare ? (opened ? 150 : 60) : 25;
        if (!hurt && (level >= 12 && cost > stockCost || cost > (level <= 6 ? 900 : bare ? Math.max(120, stockCost) : 90))) return false;
        if (hurt && cost > 900) return false;
        // At night on the surface a hunt across the fields is how a game is lost (and the cows
        // are hard to see): only food close by, unless the hunger is getting serious.
        var lv = bot.body().level();
        boolean night = lv.dimension() == net.minecraft.world.level.Level.OVERWORLD && lv.isDarkOutside()
                && !net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body());
        if (night && cost > 30 && level > 6 && !hurt) return false;
        // (No weapon yet, and one a few seconds off - the stone is there: that first. The hunt
        // goes quicker with it, and the night may come before the hunt is done.)
        if (!hurt && level > 6 && ObtainPlanner.countAny(bot.body(), OPENING.get(1)) == 0) {
            double sword = planner.estimate(bot, OPENING.get(1), 1);
            if (sword <= 30) {
                bot.say("a sword first, then food (~" + Math.round(sword) + " s)");
                nextFoodCheck = now;
                startRefused = false;
                start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(OPENING.get(1), 1, planner), budget(sword, 300, 900));
                if (!startRefused) return true;
            }
        }
        bot.say("stocking up on food (~" + Math.round(cost) + " s)");
        nextErrand = "food";
        start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(FOODS, have + 4, planner), budget(cost, 600, 2400));
        return true;
    }

    /** A tile it could go for: the way and its effort from here, and the plain estimate (before experience). */
    private record Choice(Chal_40_LockoutBingo.BoardTile tile, LockoutGoals.Option option, double plain) {
    }

    /** Every open tile the bot knows how to do, with its way and effort from here. */
    private List<Choice> choices(Bot bot, int except) {
        return choices(bot, except, false);
    }

    /**
     * {@code last}: when nothing else is left - tiles resting after an overrun count again, and
     * so do ones dearer than the usual limit (only those done but never counted stay out).
     */
    private List<Choice> choices(Bot bot, int except, boolean last) {
        MinecraftServer server = bot.server();
        long now = server.overworld().getGameTime();
        List<Choice> out = new ArrayList<>();
        for (Chal_40_LockoutBingo.BoardTile tile : Chal_40_LockoutBingo.board(server)) {
            if (tile.claimedBy() != null || tile.index() == except) continue;
            if (only != null && !only.contains(tile.goal().id())) continue;
            // Hurt: no going after monsters until the hearts are back (eaten, rested).
            if (bot.body().getHealth() < 12 && tile.goal().type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.KILL
                    && hostile(tile.goal().primaryTarget())) continue;
            // The heavy hitters (an enderman: over ten hearts' worth a blow on hard; a witch's
            // potions; a vindicator's axe): only geared - armour, a weapon, the hearts.
            if (tile.goal().type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.KILL && heavy(tile.goal().primaryTarget()) && !geared(bot)) continue;
            if (last ? tries.getOrDefault(tile.goal().id(), 0) > MAX_TRIES : resting(tile.goal().id(), now)) continue;
            if (difficulty == Difficulty.EASY && tile.goal().difficulty() == LockoutBingoGoalDifficulty.HARD) continue;
            LockoutGoals.Option o;
            try {
                o = last ? LockoutGoals.planAny(bot, planner, tile.goal()) : LockoutGoals.plan(bot, planner, tile.goal());
            } catch (RuntimeException e) {
                o = null;
            }
            double plain = o == null ? 0 : o.cost();
            // Harder than it looked last time (it ran over its time): believed less now.
            // What earlier games taught about this tile (it always takes three times as long...).
            if (o != null) {
                double f = GoalExperience.factor(tile.goal().id());
                if (f != 1) o = o.costing(o.cost() * f);
            }
            // Ran over its time before: believed somewhat less - unless things have changed since (a
            // new tool, a village found: the fresh estimate is well below the one that failed).
            int over = overruns.getOrDefault(tile.goal().id(), 0);
            Double failedAt = overrunEstimate.get(tile.goal().id());
            if (o != null && over > 0 && failedAt != null && o.cost() < 0.7 * failedAt) {
                overruns.remove(tile.goal().id());
                overrunEstimate.remove(tile.goal().id());
                over = 0;
            }
            if (o != null && over > 0) o = o.costing(o.cost() * (1 + 0.5 * over) + 20 * over);
            // (What this game has shown of it: failed at, or slower than thought - believed so.)
            if (o != null) o = o.costing(cortex.believe("goal:" + tile.goal().id(), o.cost()));
            // Through the portal again soon after coming through it (a tile up there looked cheap
            // from down here, one down here from up there): the way back is dearer than the
            // estimate says - a few minutes' resistance against going to and fro.
            if (o != null && o.proxy() && now - switchedAt < 6000) {
                boolean nether = bot.body().level().dimension() == net.minecraft.world.level.Level.NETHER;
                boolean netherTile = tile.goal().category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER;
                if (nether != netherTile) o = o.costing(o.cost() + 240);
            }
            if (o != null) out.add(new Choice(tile, o, plain));
        }
        return out;
    }

    // ---- odds and routes -------------------------------------------------------------------------

    /** What a death costs: the respawn, the walk back for the things, the half-done work. */
    private static final double DEATH_SECONDS = 150;

    /**
     * How a tile looks from here, as odds rather than one number: the expected seconds, the part
     * of them that is luck (searching for what is not seen yet - it may come at once or late:
     * exponential), where the work is (null: here, anywhere), and the risk of dying on the way
     * (per minute).
     */
    record Odds(double mean, double luck, net.minecraft.core.BlockPos at, double hazard) {
        /** The chance to be done within {@code t} seconds: the known part for sure, the rest luck. */
        double within(double t) {
            double sure = mean - luck;
            if (t < sure) return 0;
            if (luck < 1) return 1;
            return 1 - Math.exp(-(t - sure) / luck);
        }

        /** The seconds spent on average when it is given up after {@code t}. */
        double spent(double t) {
            double sure = mean - luck;
            if (t <= sure) return t;
            return luck < 1 ? sure : sure + luck * within(t);
        }

        /** The chance of dying while at it for {@code t} seconds. */
        double death(double t) {
            return 1 - Math.exp(-hazard * t / 60);
        }
    }

    /**
     * Its odds from what is around it: whether the things for it are in sight (a walk) or must be
     * looked for (luck), the biome (known for them, or not), the time of day for monsters (they
     * only come out at night on the surface), what it learnt in earlier games (a tile that always
     * takes longer than planned: the extra is luck too), and the danger where the work is.
     */
    private Odds odds(Bot bot, Choice c, double hazard) {
        var g = c.tile().goal();
        double mean = c.option().cost(), plain = Math.max(1, c.plain());
        double luck;
        net.minecraft.core.BlockPos at = null;
        Set<net.minecraft.world.item.Item> items = LockoutGoals.items(g.targets());
        var type = g.type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.KILL
                ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.tryParse(g.primaryTarget())).orElse(null) : null;
        if (type != null) {
            luck = Math.min(plain, planner.mobSearch(bot, type, 180));
            at = planner.mobAnchor(bot, type, 180);
        } else if (!items.isEmpty()) {
            int n = g.type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.ITEM_AMOUNT ? Math.max(1, g.amount()) : 1;
            double est = planner.estimate(bot, items, n);
            double sp = planner.searchPart(bot, items, n);
            luck = est > 0 && est < 1e8 ? plain * Math.min(1, sp / est) : 0.3 * plain;
            at = planner.anchor(bot, items);
        } else if (g.type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.STRUCTURE
                || g.type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.BIOME) {
            luck = 0.6 * plain; // (finding a place not yet seen)
        } else {
            luck = 0.3 * plain;
        }
        // Longer than planned in earlier games or earlier tries: the extra is luck as well.
        luck = Math.min(mean, luck + Math.max(0, mean - plain));
        double h = hazard;
        // Night on the surface: a tile done down below (ore, a cave's things) is out of it - the
        // night's monsters are up here. So a player mines through the first nights.
        var lv = bot.body().level();
        if (at != null && lv.dimension() == net.minecraft.world.level.Level.OVERWORLD && lv.isDarkOutside()
                && at.getY() < lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 8) {
            h = Math.max(0.004, h - nightSurface(bot));
        }
        if (type != null) h *= 1.5; // (a fight)
        if (g.category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER
                && bot.body().level().dimension() != net.minecraft.world.level.Level.NETHER) h += 0.03;
        return new Odds(mean, luck, at, h);
    }

    /**
     * Deaths per minute where it is now, roughly: a little anywhere (falls, lava), more at night on
     * the surface, in the Nether, with monsters about; less in armour; much more on few hearts.
     */
    private double hazard(Bot bot) {
        var body = bot.body();
        var level = body.level();
        boolean underground = net.kasax.challengecraft.bot.task.SurfaceTask.underground(body);
        double h = 0.004;
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) h += 0.03;
        else if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.isDarkOutside() && !underground) h += nightSurface(bot);
        if (underground) h += 0.006;
        int monsters = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, body.getBoundingBox().inflate(16),
                net.minecraft.world.entity.LivingEntity::isAlive).size();
        h += 0.008 * monsters;
        h *= 1 - Math.min(0.5, body.getArmorValue() / 40.0);
        if (body.getHealth() < 10) h *= 1.8;
        return h;
    }

    /** The night's extra deaths per minute on the surface: twice as many without armour on hard. */
    private static double nightSurface(Bot bot) {
        var body = bot.body();
        boolean bare = body.getArmorValue() < 8 && body.level().getDifficulty() == net.minecraft.world.Difficulty.HARD;
        return bare ? 0.03 : 0.015;
    }

    /** Seconds to get from one place to another: walking, a staircase down, stairs up. */
    private static double travel(Bot bot, net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b) {
        if (a == null || b == null) return 0;
        int dy = b.getY() - a.getY();
        // (From where it stands: over the land as it lies; between later stops, the straight line.)
        return net.kasax.challengecraft.bot.BotTerrain.walkSeconds(bot, a, b) + (dy < 0 ? -dy * 1.2 : dy * 0.8);
    }

    /** One way through the next few tiles: in order, where it ends, the tiles expected, the seconds. */
    private record Route(List<Choice> legs, net.minecraft.core.BlockPos at, double tiles, double seconds) {
        double rate() {
            return seconds <= 0 ? 0 : tiles / seconds;
        }
    }

    private static final int ROUTE_CANDIDATES = 10, ROUTE_DEPTH = 4, BEAM = 10;

    /**
     * The next tile as the first of the best route through the next few: tiles that are worked on
     * in the same place go together (the bell, the bread and the librarian while in the village;
     * the redstone and the diamonds on the one trip down), and what one tile leaves in hand makes
     * the next quicker (the iron for the bucket and the shears). A beam search over orders of the
     * quickest ten, each leg the walk from where the last one ended plus its own work, counted
     * as odds: the tiles expected (its chance to be done before it would be given up, and not
     * dying) per second expected. The best tiles per second wins.
     */
    private Choice route(Bot bot, List<Choice> sorted) {
        List<Choice> cand = new ArrayList<>(sorted.subList(0, Math.min(ROUTE_CANDIDATES, sorted.size())));
        double hazard = hazard(bot);
        Map<Choice, Odds> odds = new HashMap<>();
        for (Choice c : cand) odds.put(c, odds(bot, c, hazard));
        net.minecraft.core.BlockPos here = bot.body().blockPosition();
        // What the first few leave in hand for the rest (the iron pickaxe for the redstone): the
        // others planned again as if it held that.
        Map<Choice, Map<String, Double>> after = new HashMap<>();
        for (Choice f : cand.subList(0, Math.min(4, cand.size()))) {
            Set<net.minecraft.world.item.Item> yields = f.option().yields().get();
            if (yields.isEmpty()) continue;
            after.put(f, planner.assuming(bot, yields, () -> {
                Map<String, Double> m = new HashMap<>();
                for (Choice o : cand) {
                    if (o == f) continue;
                    LockoutGoals.Option again = LockoutGoals.plan(bot, planner, o.tile().goal());
                    if (again != null) m.put(o.tile().goal().id(), again.cost() * GoalExperience.factor(o.tile().goal().id()));
                }
                return m;
            }));
        }
        List<Route> beam = List.of(new Route(List.of(), here, 0, 0));
        Route best = null;
        for (int depth = 0; depth < ROUTE_DEPTH; depth++) {
            List<Route> next = new ArrayList<>();
            for (Route r : beam) {
                for (Choice c : cand) {
                    if (r.legs().contains(c)) continue;
                    Odds o = odds.get(c);
                    double mean = o.mean(), luck = o.luck();
                    if (!r.legs().isEmpty()) {
                        Double a = after.getOrDefault(r.legs().get(0), Map.of()).get(c.tile().goal().id());
                        if (a != null && a < mean) {
                            luck *= a / mean;
                            mean = a;
                        }
                    }
                    // Its own work (its estimate is from here, the walk there included), and the
                    // walk from where the route stands.
                    double work = Math.max(0.3 * mean, mean - travel(bot, here, o.at()));
                    double walk = travel(bot, r.at(), o.at());
                    Odds leg = new Odds(work + walk, Math.min(luck, work), o.at(), o.hazard());
                    double horizon = budget(leg.mean(), 600, 9000) / 20.0;
                    double secs = leg.spent(horizon);
                    double dies = leg.death(secs);
                    double tiles = leg.within(horizon) * (1 - dies);
                    // (What the plan says a tile is worth beyond itself: seconds it saves later.)
                    secs = Math.max(0.3 * secs, secs + dies * DEATH_SECONDS - strategist.bonus(c.tile().goal().id()));
                    List<Choice> legs = new ArrayList<>(r.legs());
                    legs.add(c);
                    Route nr = new Route(legs, o.at() != null ? o.at() : r.at(), r.tiles() + tiles, r.seconds() + secs);
                    next.add(nr);
                    if (best == null || nr.rate() > best.rate()) best = nr;
                }
            }
            next.sort(Comparator.comparingDouble(Route::rate).reversed());
            beam = next.subList(0, Math.min(BEAM, next.size()));
        }
        if (best == null || best.legs().isEmpty()) return sorted.get(0);
        Choice first = best.legs().get(0);
        Odds fo = odds.get(first);
        StringBuilder sb = new StringBuilder("route:");
        for (Choice c : best.legs()) sb.append(c == first ? " " : " > ").append(c.tile().goal().id()).append(" [").append(where(bot, odds.get(c).at())).append("]");
        double horizon = budget(fo.mean(), 600, 9000) / 20.0;
        sb.append(String.format(" (%.1f tiles/min; %s %d%% within %d s)", best.rate() * 60, first.tile().goal().id(),
                Math.round(100 * fo.within(horizon)), Math.round(horizon)));
        bot.say(sb.toString());
        return first;
    }

    /** A place in words for the log: here, the village, down at some height, or how far. */
    private String where(Bot bot, net.minecraft.core.BlockPos at) {
        if (at == null) return "here";
        net.minecraft.core.BlockPos here = bot.body().blockPosition();
        double dx = at.getX() - here.getX(), dz = at.getZ() - here.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        net.minecraft.core.BlockPos village = planner.village(bot);
        if (village != null && village.distSqr(at) < 48 * 48) return "village";
        if (at.getY() < here.getY() - 8 && d < 24) return "down to y " + at.getY();
        if (d < 24) return "here";
        return Math.round(d) + " m";
    }

    /**
     * How long a goal may take before it is given up for now: three times the estimate, at least
     * {@code min} ticks, at most {@code max}. A player notices when something is not working.
     */
    private static long budget(double estimateSeconds, long min, long max) {
        // (Three times a short estimate; a long one is less far off in proportion, and losing
        // three times ten minutes on one tile costs the game.)
        double factor = estimateSeconds > 120 ? 2.2 : 3;
        return Math.max(min, Math.min(max, (long) (estimateSeconds * 20 * factor)));
    }

    private long goalStarted, goalBudget, sideStarted, sideBudget;
    /** Ticks of the goal's time spent on side trips (chances on the way): not counted as the goal's. */
    private long goalPaused;
    /** The chest of the side trip on, if it is one. */
    private net.minecraft.core.BlockPos sideChest;

    /** Ticks the goal itself has taken so far. */
    private long goalElapsed(long now) {
        return now - goalStarted - goalPaused;
    }

    private double goalSeconds(long now) {
        return goalElapsed(now) / 20.0;
    }
    private double goalEstimate, goalFirstEstimate;
    /** The goal's task is only the way to the tile (see {@link LockoutGoals.Option#proxy}): nothing learnt from it. */
    private boolean goalProxy;
    private net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal goalGoal;
    private int extensions, stalls;
    /** How often each goal ran over its time: its estimates are trusted that much less. */
    private final java.util.Map<String, Integer> overruns = new java.util.HashMap<>();
    /** The (plain) estimate a goal had when it ran over: a much lower one later means things changed. */
    private final java.util.Map<String, Double> overrunEstimate = new java.util.HashMap<>();

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

    /**
     * The kind of errand running (spare wood, food, ...), or null: one that ran out of time is
     * left alone for longer each time (no wood near here: no wood near here a minute later either).
     */
    private String errand, nextErrand;
    private final Map<String, Integer> errandOverruns = new HashMap<>();
    private long blocksRetryAt;

    /** What is left of the running errand, in seconds, when it was started or last given more time. */
    private double runningLeft = Double.MAX_VALUE;

    /** What is left of the running errand now (for getting things: the planner's estimate), or null if it cannot be told. */
    private Double errandLeft(Bot bot) {
        if (!(running instanceof net.kasax.challengecraft.bot.task.ObtainTask o)) return null;
        double left = planner.estimate(bot, o.items(), o.count());
        return left >= 1e8 ? null : left;
    }

    /** The errand has come on: clearly less left than before (or, for one that cannot say, a good way walked). */
    private boolean errandProgress(Bot bot) {
        Double left = errandLeft(bot);
        if (left == null) return runningFrom != null && horizontal(runningFrom, bot.body().blockPosition()) > 60;
        boolean progress = left < runningLeft * 0.85;
        runningLeft = left;
        return progress;
    }

    private void start(Bot bot, BotTask task, long budgetTicks) {
        String key = intent(task);
        if (!cortex.start(bot, key, bot.body().level().getGameTime())) {
            startRefused = true;
            nextErrand = null;
            return;
        }
        // (Whatever ran before and was left unfinished: not a failure of its own, it was put aside.)
        runningKey = key;
        errand = nextErrand;
        nextErrand = null;
        running = task;
        runningSince = bot.body().level().getGameTime();
        runningBudget = budgetTicks;
        runningFrom = bot.body().blockPosition();
        runningExtensions = 0;
        runningLeft = Double.MAX_VALUE;
        Double left = errandLeft(bot);
        if (left != null) runningLeft = left;
        bot.doNow(task);
    }

    @Override
    public void tick(Bot bot) {
        long now = bot.body().level().getGameTime();
        var dim = bot.body().level().dimension();
        if (dim != lastDimension) {
            if (lastDimension != null) switchedAt = now;
            lastDimension = dim;
        }
        if (sideTask != null && now - sideStarted > sideBudget) {
            bot.say("that takes too long, back to the goal");
            if (sideId != null) restUntil.put(sideId, now + REST_TICKS);
            if (sideWant != null) wantRest.put(sideWant, now + 6000);
            if (sideChest != null) lootedIn.put("chest@" + sideChest.toShortString(), 99);
            // (Only the side trip ends: the goal underneath goes on, its clock having stood still.)
            if (goalTask != null) goalPaused += now - sideStarted;
            BotTask side = sideTask;
            sideTask = null;
            sideWant = null;
            sideId = null;
            sideChest = null;
            bot.cancel(side);
            return;
        }
        if (running != null && running != goalTask && now - runningSince > runningBudget && bot.current() != null
                && runningExtensions < 2 && errandProgress(bot)) {
            // Getting there (what is left of it clearly less than before): more time. Walking
            // about without getting nearer is not that.
            runningExtensions++;
            runningSince = now;
            runningFrom = bot.body().blockPosition();
            return;
        }
        if (running != null && running != goalTask && now - runningSince > runningBudget && bot.current() != null) {
            bot.say(running.describe() + " takes too long, something else");
            cortex.ended(bot, runningKey, now, false, false);
            runningKey = null;
            running = null;
            nextFoodCheck = now + 2400; // (if it was food: not the same way again right away)
            if (errand != null) {
                int n = errandOverruns.merge(errand, 1, Integer::sum);
                long later = now + (3600L << Math.min(3, n)); // 6, 12, 24, 48 minutes... of game time
                switch (errand) {
                    case "wood" -> woodRetryAt = later;
                    case "cobble" -> spareRetryAt = later;
                    case "blocks" -> blocksRetryAt = now + (1200L << Math.min(3, n));
                    case "food" -> nextFoodCheck = Math.max(nextFoodCheck, now + (2400L << Math.min(2, n - 1)));
                    default -> { }
                }
                errand = null;
            }
            drop(bot);
            pause = 100;
            return;
        }
        if (goalTask != null && targetId != null && goalElapsed(now) > goalBudget) {
            // Well on the way (half way down to the ore, say): what is left is worth finishing.
            // By the situation, not the clock: what is left of this one against the best other
            // thing to do from here (deep down next to where redstone lies, a walk back up to the
            // village is never the better deal). Each round without visible progress counts the
            // estimate as less believable, so a goal that only claims to be close loses out in the end.
            double left = remaining(bot);
            boolean progress = left < goalEstimate * 0.85;
            if (!progress) stalls++;
            // (Without progress, what has taken long already is likely to take long still: at
            // least half the time spent so far is believed left, however small the estimate.)
            double spentSeconds = goalSeconds(now);
            double believed = (stalls > 0 ? Math.max(left, 0.5 * spentSeconds) : left) * (1 + 0.6 * stalls);
            double other = Double.MAX_VALUE;
            if (extensions < 8 && left < Double.MAX_VALUE / 4) {
                for (Choice c : choices(bot, targetIndex)) other = Math.min(other, c.option().cost() - strategist.bonus(c.tile().goal().id()));
            }
            if (extensions < 8 && left < Double.MAX_VALUE / 4 && (progress && stalls < 3 || believed + 15 < other)) {
                extensions++;
                goalEstimate = left;
                goalBudget = goalElapsed(now) + budget(left, 600, 6000);
                bot.say("still on " + targetId + ", ~" + Math.round(left) + " s left"
                        + (other < Double.MAX_VALUE ? " (next best ~" + Math.round(other) + " s)" : ""));
                return;
            }
            bot.say(targetId + " takes too long, something else first");
            if (runningKey != null && runningKey.equals("goal:" + targetId)) {
                cortex.ended(bot, runningKey, now, false, false);
                runningKey = null;
            }
            overruns.merge(targetId, 1, Integer::sum);
            overrunEstimate.put(targetId, goalFirstEstimate);
            // (For next games too: at least this long, and it was not even done.)
            if (!goalProxy) GoalExperience.record(targetId, goalFirstEstimate, 1.5 * goalSeconds(now), 0.5);
            restUntil.put(targetId, now + REST_TICKS);
            drop(bot);
            return;
        }
        if (goalTask != null && targetId != null) spent.merge(targetId, 1, Integer::sum);
        // Nightfall while out on the surface, not geared for it: whatever it is doing is put
        // aside, and the night plan decides (a bed, or a hole with a lid) - not on with a log hunt
        // into the dark.
        if (checkTicks % 40 == 0 && nightComing(bot, SHELTER_FROM) && now >= nightDropAt && !nightReady(bot) && nightThreat(bot)
                && !net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body()) && !sheltering(bot)) {
            nightDropAt = now + 1200;
            bot.say("nightfall, not geared for it: " + nightGaps(bot));
            if (runningKey != null) {
                cortex.ended(bot, runningKey, now, false, false);
                runningKey = null;
            }
            running = null;
            drop(bot);
            return;
        }
        // In the middle of a long goal (digging for diamonds) the pickaxe wears out too: a spare
        // made before it breaks, without dropping the goal.
        if (checkTicks % 200 == 0 && goalTask != null && !(bot.current() instanceof net.kasax.challengecraft.bot.task.ObtainTask) && pickaxeAlmostGone(bot)) {
            bot.say("the pickaxe is about to break: a spare one first");
            bot.interject(new net.kasax.challengecraft.bot.task.ObtainTask(GOOD_PICKAXES, ObtainPlanner.countAny(bot.body(), GOOD_PICKAXES) + 1, planner));
        }
        // Nothing left to eat in the middle of a long errand, and hungry or hurt: food now, the
        // errand after (on hard the hearts only come back with a full stomach).
        // (Not at night out on the surface: the hole comes first - a sheep hunt in the dark for
        // something to eat was a death on seed 55.)
        if (checkTicks % 300 == 0 && now >= foodInterjectAt && !(BotTask.innermost(bot.current()) instanceof net.kasax.challengecraft.bot.task.KillTask)
                && (goalTask != null || running != null) && !bot.targeted(10) && !sheltering(bot)
                && !(nightComing(bot, SHELTER_FROM) && !net.kasax.challengecraft.bot.task.SurfaceTask.underground(bot.body()))) {
            int points = 0;
            for (var st : bot.body().getInventory().getNonEquipmentItems()) {
                var f = st.get(net.minecraft.core.component.DataComponents.FOOD);
                if (f != null && !st.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) points += f.nutrition() * st.getCount();
            }
            int level = bot.body().getFoodData().getFoodLevel();
            boolean hard = bot.body().level().getDifficulty() == net.minecraft.world.Difficulty.HARD;
            // (Not before the stone tools - and a sword - unless the hunger is serious: a hunt
            // across the fields bare-handed at the start ran into a drowned on seed 88.)
            boolean opened = ObtainPlanner.countAny(bot.body(), GOOD_PICKAXES) > 0;
            if (points < 4 && (level <= 6 || opened && (level <= 10 || hard && bot.body().getHealth() < 14))) {
                foodInterjectAt = now + 3600;
                int have = ObtainPlanner.countAny(bot.body(), FOODS);
                double cost = planner.estimate(bot, FOODS, have + 4);
                if (cost <= (level <= 6 ? 600 : 200)) {
                    bot.say("nothing left to eat (hunger " + level + ", hp " + Math.round(bot.body().getHealth()) + "): food first (~" + Math.round(cost) + " s)");
                    bot.interject(new net.kasax.challengecraft.bot.task.ObtainTask(FOODS, have + 4, planner));
                }
            }
        }
        if (checkTicks % 100 == 0) {
            boolean open = false;
            for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
                if (t.claimedBy() == null && t.goal().id().equals("survive_explosion")) open = true;
            }
            bot.welcomeExplosion = open;
        }
        if (checkTicks % 6000 == 0) net.kasax.challengecraft.bot.BotManager.LOG.info("[Cortex] {}: {}", bot.name, cortex.summary());
        if (++checkTicks % 20 != 0) return;
        // Chances on the way (a chest, flowers the board wants) on errands too, not only on goals.
        // (Not while seeing to the night - dug in, in bed, on the way to either: the coal for
        // later lured it out of its hole into a parched's arrows on seed 55.)
        if (checkTicks % 40 == 0 && sideTask == null && (targetIndex >= 0 || running != null && bot.current() != null) && !sheltering(bot)) takeChances(bot);
        if (targetIndex < 0) return;
        MinecraftServer server = bot.server();
        if (!Chal_40_LockoutBingo.isRunning(server)) {
            drop(bot);
            return;
        }
        // The tile went to someone (maybe us): whatever we were doing for it is pointless now.
        for (Chal_40_LockoutBingo.BoardTile tile : Chal_40_LockoutBingo.board(server)) {
            if (tile.index() == targetIndex && tile.claimedBy() != null) {
                boolean ours = tile.claimedBy() == Chal_40_LockoutBingo.teamOf(server, bot.id);
                // Ours, and in the middle of an ore vein (the diamonds: the tile wanted one, the
                // tools and the armour want the rest): the vein first, as an errand of its own.
                if (ours && BotTask.innermost(bot.current()) instanceof net.kasax.challengecraft.bot.task.MineTask m && m.veinOpen(bot)) {
                    bot.say("got " + targetId + "; the rest of the vein too");
                    targetIndex = -1;
                    targetId = null;
                    goalTask = null;
                    running = bot.current();
                    runningSince = bot.body().level().getGameTime();
                    runningBudget = 1200;
                    runningExtensions = 2; // (no more time than that)
                    return;
                }
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
    /** One more of the kinds an "N different kinds" tile wants, if one is a few steps away. */
    private boolean collectDistinctNearby(Bot bot) {
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
            if (t.claimedBy() != null || t.goal().type() != net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.INVENTORY_SET) continue;
            Set<net.minecraft.world.item.Item> kinds = LockoutGoals.items(t.goal().targets());
            Set<net.minecraft.world.item.Item> held = new java.util.HashSet<>();
            for (var st : bot.body().getInventory().getNonEquipmentItems()) if (kinds.contains(st.getItem())) held.add(st.getItem());
            if (held.size() >= Math.max(1, t.goal().amount())) continue;
            net.minecraft.core.BlockPos at = net.kasax.challengecraft.bot.BotWorld.nearest(level, bot.body().blockPosition(), 8, 4,
                    st -> kinds.contains(st.getBlock().asItem()) && !held.contains(st.getBlock().asItem()), true, Set.of());
            if (at == null) continue;
            net.minecraft.world.item.Item item = level.getBlockState(at).getBlock().asItem();
            sideTask = new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(item), 1, planner);
            sideId = null;
            sideWant = item;
            sideStarted = bot.body().level().getGameTime();
            sideBudget = 300;
            bot.say("on the way: " + ObtainPlanner.name(item) + " for " + t.goal().title().getString());
            bot.interject(sideTask);
            return true;
        }
        return false;
    }

    private void takeChances(Bot bot) {
        if (bot.current() instanceof net.kasax.challengecraft.bot.task.EatTask) return;
        // (Fetching its things: against the clock, no detours.)
        if (running instanceof net.kasax.challengecraft.bot.task.RecoverTask) return;
        // Chests worth a detour: where the good loot is (iron in a shipwreck, flint and steel and
        // gold at a ruined portal, the temples' treasure), a few per structure, not every chest in
        // a mineshaft or a trial chamber full of spiders and silverfish.
        net.minecraft.core.BlockPos chest = null;
        for (net.minecraft.core.BlockPos c : bot.senses().lootables()) {
            if (c.distSqr(bot.body().blockPosition()) > 32 * 32) break;
            net.minecraft.resources.Identifier in = bot.senses().structureAt(c);
            if (in == null || !WORTH_LOOTING.stream().anyMatch(w -> in.getPath().startsWith(w))) continue;
            if (lootedIn.merge(in.getPath() + "@" + (c.getX() >> 6) + "," + (c.getZ() >> 6), 0, Integer::sum) >= 3) continue;
            // (One it could not get at before: not again.)
            if (lootedIn.getOrDefault("chest@" + c.toShortString(), 0) > 0) continue;
            // In a bastion: only where no brute or crowd of piglins watches (opening it angers
            // them all). In the other guarded places: only at good health, nothing about.
            var lv = (net.minecraft.server.level.ServerLevel) bot.body().level();
            if (in.getPath().startsWith("bastion") && !net.kasax.challengecraft.bot.task.BastionLootTask.safe(lv, bot.body(), c)) continue;
            if ((in.getPath().startsWith("fortress") || in.getPath().startsWith("pillager_outpost") || in.getPath().startsWith("end_city"))
                    && (bot.body().getHealth() < 16 || !lv.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(c).inflate(12),
                            m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy).isEmpty())) continue;
            chest = c;
            lootedIn.merge(in.getPath() + "@" + (c.getX() >> 6) + "," + (c.getZ() >> 6), 1, Integer::sum);
            break;
        }
        if (chest != null) {
            sideChest = chest;
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
            // (Until the board's whole demand is in the pack: twenty-four raw iron wanted, the one
            // ore right here is taken though one is held already.)
            if (ObtainPlanner.countAny(bot.body(), Set.of(item)) >= Math.max(1, planner.boardDemand.getOrDefault(item, 1))) continue;
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
        // A tile that wants several different things (three kinds of flower): one more kind right
        // beside the way is a few seconds, and a third of that tile done.
        if (collectDistinctNearby(bot)) return;
        // (Tiles on the way only when fit for them: hurt, or bare in the dark, a quick tile is
        // often a monster - an arrow from a skeleton at night killed the bot on seed 77.)
        var me = bot.body();
        if (me.getHealth() < 14 || cortex.mode(bot) == Cortex.Mode.SURVIVE) return;
        boolean armed = ObtainPlanner.countAny(me, Set.of(net.minecraft.world.item.Items.STONE_SWORD, net.minecraft.world.item.Items.IRON_SWORD,
                net.minecraft.world.item.Items.DIAMOND_SWORD, net.minecraft.world.item.Items.NETHERITE_SWORD, net.minecraft.world.item.Items.STONE_AXE,
                net.minecraft.world.item.Items.IRON_AXE, net.minecraft.world.item.Items.DIAMOND_AXE)) > 0;
        for (Choice c : choices(bot, targetIndex)) {
            // A few seconds' work, or a monster wanted for a tile right there in sight (the spider
            // in the cave it is mining in, the creeper met on a night walk): taken along.
            boolean quick = c.option().cost() < 8;
            boolean prey = !quick && c.option().cost() < 25 && c.tile().goal().type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.KILL;
            // (A monster on the way: armed for it, or not at all.)
            if (prey && !armed) continue;
            // (Nor down into the dark after it, bare: a zombie followed into a cave at minute
            // four brought a skeleton's arrows with it on seed 11. In the open, one at a time.)
            if (prey && me.getArmorValue() < 6 && !openPrey(me)) continue;
            if (quick || prey) {
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

    /** The monsters about all out in the open at its own level, and not more than one of them. */
    private static boolean openPrey(net.kasax.challengecraft.bot.BotPlayer me) {
        var near = me.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, me.getBoundingBox().inflate(20, 12, 20),
                m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy);
        if (near.size() > 1) return false;
        for (var m : near) {
            if (Math.abs(m.getY() - me.getY()) > 4 || !me.level().canSeeSky(m.blockPosition().above())) return false;
        }
        return true;
    }

    @Override
    public void finished(Bot bot, BotTask task, boolean success) {
        if (task == running && runningKey != null) {
            cortex.ended(bot, runningKey, bot.body().level().getGameTime(), success, false);
            runningKey = null;
        }
        if (task == sideTask) {
            // (The goal's clock stood still meanwhile: the side trip is not the goal's time.)
            if (goalTask != null) goalPaused += bot.body().level().getGameTime() - sideStarted;
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
            if (targetId != null && !goalProxy && tries.getOrDefault(targetId, 0) == 0) GoalExperience.record(targetId, goalFirstEstimate, goalSeconds(now));
            // The game counts it within a second (it checks inventories once a second): wait for
            // that instead of starting on the same tile again.
            // If it never counts (the goal wants something else than the bot thought), give up on it.
            // (A tile done in parts - ten hearts in a few falls - is not "done but not counted".)
            boolean again = targetId.equals("have_10_hearts_missing") || tries.merge(targetId, 1, Integer::sum) < MAX_TRIES + 1;
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
        // Died at it: that intent waits longer before the next try.
        if (runningKey != null) cortex.ended(bot, runningKey, bot.body().level().getGameTime(), false, true);
        runningKey = null;
        replanNow = true;
        openingStep = 0; // the tools are gone with the rest
        openingWood = false;
        openingIron = false;
        shallowIronAt = 0;
        kitRetryAt = 0;
        kitTries = 0;
        openingTries = 0;
        targetIndex = -1;
        goalTask = null;
        sideTask = null;
    }

    private boolean resting(String goalId, long now) {
        Long until = restUntil.get(goalId);
        return until != null && until > now || !cortex.allowed("goal:" + goalId, now);
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
        LockoutBingoTeam mine = Chal_40_LockoutBingo.teamOf(server, bot.id);
        Set<LockoutBingoTeam> used = EnumSet.noneOf(LockoutBingoTeam.class);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == bot.body()) continue;
            LockoutBingoTeam t = Chal_40_LockoutBingo.teamOf(server, p.getUUID());
            if (t != null) used.add(t);
        }
        // Stays put unless someone joined its team (it plays against them, not with them).
        if (joined && mine != null && !used.contains(mine)) return;
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
