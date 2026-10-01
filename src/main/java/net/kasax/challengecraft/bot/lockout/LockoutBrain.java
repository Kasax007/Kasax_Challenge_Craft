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
        // Gold on before the piglins see it (a helmet or boots; gold ore all about down there).
        if (goldGuard(bot)) return;
        // The opening every player plays: wood, a table, then stone tools (pickaxe and axe), before
        // anything else. They make every later goal quicker.
        if (opening(bot)) return;
        // The kit a player never goes without: a pickaxe (a new one before the old one breaks)
        // and a stack of blocks to build with (out of a hole, over a gap, a pillar from mobs).
        if (keepKit(bot)) return;
        // Iron the quick way: a shipwreck's chests (or a village's) when one is near and the plan
        // wants iron. Once per structure.
        // And whatever the plan: a shipwreck (or a ruined portal) not far off is looted the moment
        // it is seen. Iron, gold, emeralds, food, a treasure map: things that make every later
        // tile quicker, wanted now or not.
        if (bot.body().level().dimension() == net.minecraft.world.level.Level.OVERWORLD && bot.body().getHealth() >= 12) {
            var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
            boolean ironWanted = strategist.wantsIron() && ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.IRON_INGOT)) < 3;
            for (String kind : List.of("shipwreck", "ruined_portal", "village")) {
                int radius = switch (kind) {
                    case "shipwreck" -> ironWanted ? 160 : 120;
                    case "ruined_portal" -> 64;
                    default -> ironWanted ? 160 : 0;
                };
                if (radius == 0) continue;
                var seen = net.kasax.challengecraft.bot.task.VisitStructureTask.nearest(bot,
                        net.kasax.challengecraft.bot.task.VisitStructureTask.resolve(level, kind));
                if (seen == null || seen.spot().distSqr(bot.body().blockPosition()) > (long) radius * radius) continue;
                String key = kind + "@" + (seen.spot().getX() >> 6) + "," + (seen.spot().getZ() >> 6);
                if (!raided.add(key)) continue;
                bot.say("loot from the " + kind.replace('_', ' ') + " at " + seen.spot().toShortString());
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
        // What not to throw away when the pack is full: what the board and the plan want.
        bot.keepItems.clear();
        bot.keepItems.addAll(planner.boardDemand.keySet());
        bot.keepItems.addAll(strategist.wants().keySet());
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server()))
            if (t.claimedBy() == null) bot.keepItems.addAll(LockoutGoals.items(t.goal().targets()));
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
                    // (Each failed try waits longer for the next: 3, 6, 12 minutes...)
                    netherRetryAt = bot.body().level().getGameTime() + (3600L << Math.min(3, netherFails++));
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
        // A bastion near in the Nether and gold of use on the board (bartering, gold tiles): its
        // chests and gold blocks, where few piglins watch. Once per bastion.
        if (bastionRaid(bot)) return;
        // Night (or a cave, or the Nether), and several monsters wanted: one hunt for all of them.
        if (huntRound(bot)) return;
        replanNow = false;
        List<Choice> choices = choices(bot, -1);
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
                && LockoutGoals.wayBack(bot) == null && bot.body().level().getGameTime() >= strandedRetryAt) {
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
    private static final Set<net.minecraft.world.item.Item> PICKAXES = Set.of(net.minecraft.world.item.Items.WOODEN_PICKAXE, net.minecraft.world.item.Items.STONE_PICKAXE,
            net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.GOLDEN_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE,
            net.minecraft.world.item.Items.NETHERITE_PICKAXE);
    private static final Set<net.minecraft.world.item.Item> GOOD_PICKAXES = Set.of(net.minecraft.world.item.Items.STONE_PICKAXE,
            net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE, net.minecraft.world.item.Items.NETHERITE_PICKAXE);
    private long kitCheckAt;

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
        Set<net.minecraft.world.entity.EntityType<?>> wanted = wantedMonsters(bot);
        if (wanted.size() < 2) return false;
        huntRetryAt = now + 6000; // (not straight back into it if it found nothing)
        long clock = bot.body().level().getOverworldClockTime() % 24000;
        long nightLeft = clock >= 12000 ? 24000 - clock : 0;
        long budgetTicks = Math.max(2400, Math.min(9000, nightLeft > 0 ? nightLeft : 4800));
        bot.say("hunting time: one round for " + wanted.size() + " kinds of monster");
        start(bot, new net.kasax.challengecraft.bot.task.HuntRoundTask(() -> wantedMonsters(bot), () -> huntingTime(bot)), budgetTicks);
        return true;
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
        if (blocks < 12) {
            var stone = bot.body().level().dimension() == net.minecraft.world.level.Level.NETHER
                    ? Set.of(net.minecraft.world.item.Items.NETHERRACK) : Set.of(net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.COBBLED_DEEPSLATE, net.minecraft.world.item.Items.DIRT);
            bot.say("few blocks left (" + blocks + "): a stack to build with");
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(stone, ObtainPlanner.countAny(body, stone) + 24, planner), 1800);
            return true;
        }
        // The pack filling up and no bundle yet: one (string and leather), for the odds and ends.
        if (net.kasax.challengecraft.bot.BotBundles.bundle(body) == null && net.kasax.challengecraft.bot.BotBundles.freeSlots(body) <= 8
                && now >= bundleRetryAt) {
            bundleRetryAt = now + 6000;
            double cost = planner.estimate(bot, Set.of(net.minecraft.world.item.Items.BUNDLE), 1);
            if (cost < 120) {
                bot.say("the pack fills up: a bundle for the odds and ends (~" + Math.round(cost) + " s)");
                start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(net.minecraft.world.item.Items.BUNDLE), 1, planner), 2400);
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
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.COBBLED_DEEPSLATE),
                    cobble + 24, planner), 1800);
            return true;
        }
        if (overworld && woodPlanks(body) < SPARE_PLANKS && now >= woodRetryAt) {
            woodRetryAt = now + 2400;
            int logs = ObtainPlanner.countAny(body, LOGS);
            bot.say("spare wood (" + woodPlanks(body) + " planks' worth)");
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(LOGS, logs + 6, planner), 1800);
            return true;
        }
        return false;
    }

    private long upgradeRetryAt, bundleRetryAt;

    private boolean upgrade(Bot bot) {
        var body = bot.body();
        long now = body.level().getGameTime();
        if (now < upgradeRetryAt) return false;
        int iron = ObtainPlanner.countAny(body, Set.of(net.minecraft.world.item.Items.IRON_INGOT));
        int spare = iron - planner.boardDemand.getOrDefault(net.minecraft.world.item.Items.IRON_INGOT, 0);
        if (spare < 1) return false;
        record Step(net.minecraft.world.item.Item item, int iron, Set<net.minecraft.world.item.Item> better) {}
        var steps = List.of(
                new Step(net.minecraft.world.item.Items.IRON_PICKAXE, 3, Set.of(net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE, net.minecraft.world.item.Items.NETHERITE_PICKAXE)),
                new Step(net.minecraft.world.item.Items.IRON_SWORD, 2, Set.of(net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.DIAMOND_SWORD, net.minecraft.world.item.Items.NETHERITE_SWORD)),
                new Step(net.minecraft.world.item.Items.SHIELD, 1, Set.of(net.minecraft.world.item.Items.SHIELD)),
                new Step(net.minecraft.world.item.Items.IRON_HELMET, 5, Set.of(net.minecraft.world.item.Items.IRON_HELMET, net.minecraft.world.item.Items.DIAMOND_HELMET)),
                new Step(net.minecraft.world.item.Items.IRON_BOOTS, 4, Set.of(net.minecraft.world.item.Items.IRON_BOOTS, net.minecraft.world.item.Items.DIAMOND_BOOTS)),
                new Step(net.minecraft.world.item.Items.IRON_CHESTPLATE, 8, Set.of(net.minecraft.world.item.Items.IRON_CHESTPLATE, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)),
                new Step(net.minecraft.world.item.Items.IRON_LEGGINGS, 7, Set.of(net.minecraft.world.item.Items.IRON_LEGGINGS, net.minecraft.world.item.Items.DIAMOND_LEGGINGS)));
        for (Step st : steps) {
            boolean has = ObtainPlanner.countAny(body, st.better()) > 0;
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.OFFHAND}) {
                if (st.better().contains(body.getItemBySlot(slot).getItem())) has = true;
            }
            if (has) continue;
            if (spare < st.iron()) return false; // (in this order: not armour before the pickaxe)
            upgradeRetryAt = now + 2400;
            bot.say("iron to spare (" + spare + "): " + ObtainPlanner.name(st.item()));
            start(bot, new net.kasax.challengecraft.bot.task.ObtainTask(Set.of(st.item()), 1, planner), 1200);
            return true;
        }
        return false;
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
            // Ran over its time before: believed somewhat less - unless things have changed since (a
            // new tool, a village found: the fresh estimate is well below the one that failed).
            int over = overruns.getOrDefault(tile.goal().id(), 0);
            Double failedAt = overrunEstimate.get(tile.goal().id());
            if (o != null && over > 0 && failedAt != null && o.cost() < 0.7 * failedAt) {
                overruns.remove(tile.goal().id());
                overrunEstimate.remove(tile.goal().id());
                over = 0;
            }
            if (o != null && over > 0) o = new LockoutGoals.Option(o.cost() * (1 + 0.5 * over) + 20 * over, o.task(), o.yields());
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
        // (Three times a short estimate; a long one is less far off in proportion, and losing
        // three times ten minutes on one tile costs the game.)
        double factor = estimateSeconds > 120 ? 2.2 : 3;
        return Math.max(min, Math.min(max, (long) (estimateSeconds * 20 * factor)));
    }

    private long goalStarted, goalBudget, sideStarted, sideBudget;
    private double goalEstimate, goalFirstEstimate;
    private net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal goalGoal;
    private int extensions;
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
            overrunEstimate.put(targetId, goalFirstEstimate);
            // (For next games too: at least this long, and it was not even done.)
            GoalExperience.record(targetId, goalFirstEstimate, 1.5 * (now - goalStarted) / 20.0, 0.5);
            restUntil.put(targetId, now + REST_TICKS);
            drop(bot);
            return;
        }
        if (goalTask != null && targetId != null) spent.merge(targetId, 1, Integer::sum);
        // In the middle of a long goal (digging for diamonds) the pickaxe wears out too: a spare
        // made before it breaks, without dropping the goal.
        if (checkTicks % 200 == 0 && goalTask != null && !(bot.current() instanceof net.kasax.challengecraft.bot.task.ObtainTask) && pickaxeAlmostGone(bot)) {
            bot.say("the pickaxe is about to break: a spare one first");
            bot.interject(new net.kasax.challengecraft.bot.task.ObtainTask(GOOD_PICKAXES, ObtainPlanner.countAny(bot.body(), GOOD_PICKAXES) + 1, planner));
        }
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
            // A few seconds' work, or a monster wanted for a tile right there in sight (the spider
            // in the cave it is mining in, the creeper met on a night walk): taken along.
            boolean quick = c.option().cost() < 8;
            boolean prey = !quick && c.option().cost() < 25 && c.tile().goal().type() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType.KILL;
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
