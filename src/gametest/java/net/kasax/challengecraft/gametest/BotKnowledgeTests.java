package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.lockout.GoalExperience;
import net.kasax.challengecraft.bot.lockout.LockoutGoals;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.plan.SpeedrunKnowledge;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * Bob's knowledge and his time estimates (docs/bob-reference-times.md): the speedrun knowledge
 * base read and its loot chances rolled from the game, estimates of the goals the leagues found
 * worst within a sensible range, batches counted as a player would, the better source taken when
 * a structure is known, and goal experience that can take in the factors the leagues measured.
 */
public class BotKnowledgeTests {
    private static void time(GameTestHelper h, int ticks) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + ticks);
    }

    /** The knowledge base: every goal of the pool has its entry, and the loot is rolled from the game's own tables. */
    @GameTest(structure = STRUCTURE, maxTicks = 20, padding = 8)
    public void knowledgeBase(GameTestHelper h) {
        var kb = SpeedrunKnowledge.get();
        int missing = 0;
        StringBuilder which = new StringBuilder();
        for (var g : LockoutBingoGoalPool.all()) {
            if (!g.isSelectableOnNormalBoard()) continue;
            var entry = kb.goal(g.id());
            if (entry == null || Double.isNaN(entry.expert()) || entry.ways().isEmpty()) {
                missing++;
                which.append(g.id()).append(' ');
            }
        }
        var server = h.getLevel().getServer();
        var iron = kb.loot(server, Items.IRON_INGOT);
        var tag = kb.loot(server, Items.NAME_TAG);
        double shipwreck = iron.stream().filter(l -> l.family().equals("shipwreck")).mapToDouble(SpeedrunKnowledge.Loot::chance).findFirst().orElse(0);
        double treasure = iron.stream().filter(l -> l.family().equals("buried_treasure")).mapToDouble(SpeedrunKnowledge.Loot::chance).findFirst().orElse(0);
        double mineshaft = tag.stream().filter(l -> l.family().equals("mineshaft")).mapToDouble(SpeedrunKnowledge.Loot::chance).findFirst().orElse(0);
        BotArena.LOG.info("[BOTTEST] knowledge_base: {} goals, {} mobs, {} structures; iron from a shipwreck {}, buried treasure {}; a name tag from a mineshaft {}; missing: {}",
                kb.goals().size(), kb.mobs().size(), kb.structures().size(), round(shipwreck), round(treasure), round(mineshaft), which);
        h.assertTrue(missing == 0, "goals without knowledge: " + which);
        h.assertTrue(shipwreck > 0.6, "iron from a shipwreck: " + shipwreck);
        h.assertTrue(treasure > 0.9, "iron from buried treasure: " + treasure);
        h.assertTrue(mineshaft > 0.3, "a name tag from a mineshaft: " + mineshaft);
        h.succeed();
    }

    private static double round(double x) {
        return Math.round(x * 100) / 100.0;
    }

    /**
     * A skeleton tile, none in sight, geared: by night a search and a fight (the leagues' night
     * hunts took 72 s); by day a wait for the night first - not the old "~27 s" either way.
     */
    // (A batch of its own: it sets the clock, the whole server's - tests in one batch run at once.)
    @GameTest(environment = "challengecraft:knowledge_skeleton", structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void skeletonEstimate(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "skeleton_estimate");
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD, 8));
        var goal = LockoutBingoGoalPool.byId("kill_skeleton");
        time(h, 1000);
        h.runAfterDelay(10, () -> {
            var day = LockoutGoals.plan(a.bot(), new ObtainPlanner(), goal);
            time(h, 14000);
            h.runAfterDelay(10, () -> {
                var night = LockoutGoals.plan(a.bot(), new ObtainPlanner(), goal);
                BotArena.day(h);
                double d = day == null ? -1 : day.cost(), n = night == null ? -1 : night.cost();
                BotArena.LOG.info("[BOTTEST] skeleton_estimate: by day ~{} s, by night ~{} s (biome {})", Math.round(d), Math.round(n),
                        h.getLevel().getBiome(a.bot().body().blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
                h.assertTrue(night != null && n >= 40 && n <= 240, "by night ~" + Math.round(n) + " s, not within 40 to 240");
                h.assertTrue(day != null && d >= n + 200 && d <= n + 700, "by day ~" + Math.round(d) + " s: not the wait for the night and the night's search");
                a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
            });
        });
    }

    /**
     * Batches: one arrow is a whole craft (four come out) - so four cost what one does and a fifth
     * a second craft; sixteen logs are one walk to the trees and sixteen chops, not sixteen walks.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void batchEstimates(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "batch_estimates");
        for (int[] t : new int[][]{{8, 8}, {12, 30}, {30, 10}, {32, 32}}) a.fill(t[0], FEET, t[1], t[0], FEET + 4, t[1], Blocks.OAK_LOG);
        a.fill(26, GROUND, 24, 30, GROUND, 28, Blocks.GRAVEL);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.FEATHER, 2), new ItemStack(Items.CRAFTING_TABLE));
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            var p = new ObtainPlanner();
            double a1 = p.estimate(bot, Set.of(Items.ARROW), 1), a4 = p.estimate(bot, Set.of(Items.ARROW), 4), a5 = p.estimate(bot, Set.of(Items.ARROW), 5);
            double l1 = p.estimate(bot, Set.of(Items.OAK_LOG), 1), l16 = p.estimate(bot, Set.of(Items.OAK_LOG), 16);
            BotArena.LOG.info("[BOTTEST] batch_estimates: arrows 1 ~{} s, 4 ~{} s, 5 ~{} s; oak logs 1 ~{} s, 16 ~{} s",
                    Math.round(a1), Math.round(a4), Math.round(a5), Math.round(l1), Math.round(l16));
            h.assertTrue(a1 < 1e8 && a4 <= a1 * 1.05 + 1, "four arrows ~" + Math.round(a4) + " s, one ~" + Math.round(a1) + " s: one craft makes four");
            h.assertTrue(a5 > a4, "a fifth arrow needs a second craft");
            h.assertTrue(l16 < 10 * l1 && l16 > l1, "sixteen logs ~" + Math.round(l16) + " s against one ~" + Math.round(l1) + " s");
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /**
     * The tiles the leagues found worst, from a typical spot (stone tools, a table, sugar cane by
     * the water, no creeper about): a name tag a paper's craft and a nugget's (the leagues: 110 s
     * against "~18 s"); a firework rocket by day waits for a creeper - its gunpowder (the leagues:
     * never got, "~30 s").
     */
    @GameTest(environment = "challengecraft:knowledge_tiles", structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void worstTilesEstimates(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "worst_tiles_estimates");
        a.fill(6, GROUND, 6, 9, GROUND, 9, Blocks.WATER);
        a.fill(10, GROUND, 6, 10, GROUND, 9, Blocks.SAND);
        a.fill(10, FEET, 6, 10, FEET + 1, 9, Blocks.SUGAR_CANE);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD),
                new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.CRAFTING_TABLE));
        time(h, 1000);
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            var tag = LockoutGoals.plan(bot, new ObtainPlanner(), LockoutBingoGoalPool.byId("obtain_name_tag"));
            var rocketDay = LockoutGoals.planAny(bot, new ObtainPlanner(), LockoutBingoGoalPool.byId("craft_firework_rocket"));
            time(h, 14000);
            h.runAfterDelay(10, () -> {
                var rocketNight = LockoutGoals.planAny(bot, new ObtainPlanner(), LockoutBingoGoalPool.byId("craft_firework_rocket"));
                BotArena.day(h);
                double t = tag == null ? -1 : tag.cost(), rd = rocketDay == null ? -1 : rocketDay.cost(), rn = rocketNight == null ? -1 : rocketNight.cost();
                BotArena.LOG.info("[BOTTEST] worst_tiles_estimates: name tag ~{} s; firework rocket by day ~{} s, by night ~{} s", Math.round(t), Math.round(rd), Math.round(rn));
                h.assertTrue(tag != null && t >= 8 && t <= 150, "name tag ~" + Math.round(t) + " s");
                h.assertTrue(rocketNight != null && rn >= 30, "firework rocket by night ~" + Math.round(rn) + " s");
                h.assertTrue(rocketDay == null || rd > rn + 60, "firework rocket by day ~" + Math.round(rd) + " s, not dearer than by night");
                a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
            });
        });
    }

    /**
     * Iron, none in the pack nor in sight: with a shipwreck known forty blocks off (its treasure
     * chest nearly always has iron), the plan is to loot it; with none known, or the one known
     * raided already, the usual way.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void lootKnownShipwreck(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "loot_known_shipwreck");
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.CRAFTING_TABLE));
        var goal = LockoutBingoGoalPool.byId("obtain_iron_ingot");
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            var before = LockoutGoals.plan(bot, new ObtainPlanner(), goal);
            BlockPos wreck = a.abs(20, FEET, 60);
            bot.senses().know(Identifier.withDefaultNamespace("shipwreck"), wreck, bot.body().blockPosition());
            var known = LockoutGoals.plan(bot, new ObtainPlanner(), goal);
            bot.raided.add(net.kasax.challengecraft.bot.Bot.raidKey("shipwreck", wreck));
            var raided = LockoutGoals.plan(bot, new ObtainPlanner(), goal);
            String b = before == null ? "none" : before.task().get().describe(), k = known == null ? "none" : known.task().get().describe(),
                    r = raided == null ? "none" : raided.task().get().describe();
            BotArena.LOG.info("[BOTTEST] loot_known_shipwreck: none known: {} (~{} s); a shipwreck known: {} (~{} s); raided: {}",
                    b, before == null ? -1 : Math.round(before.cost()), k, known == null ? -1 : Math.round(known.cost()), r);
            h.assertTrue(k.startsWith("loot the shipwreck"), "with a shipwreck known: " + k);
            h.assertFalse(b.startsWith("loot"), "with none known: " + b);
            h.assertFalse(r.startsWith("loot the shipwreck"), "raided already: " + r);
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /**
     * A mineshaft known forty blocks off: its chest minecarts hold a name tag nearly three times in
     * four, but the raid opens chest blocks only - so it is no way to one, not even with no other
     * way at all (LockoutGoals.NOT_RAIDED). The control: a shipwreck known as well, iron with no
     * other way - the raid there is the way.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void noRaidOnAMineshaft(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "no_raid_on_a_mineshaft");
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD, 8));
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            var planner = new ObtainPlanner();
            bot.senses().know(Identifier.withDefaultNamespace("mineshaft"), a.abs(20, FEET, 60), bot.body().blockPosition());
            var tag = LockoutGoals.loot(bot, planner, java.util.Set.of(Items.NAME_TAG), 1, 1e9);
            bot.senses().know(Identifier.withDefaultNamespace("shipwreck"), a.abs(60, FEET, 20), bot.body().blockPosition());
            var iron = LockoutGoals.loot(bot, planner, java.util.Set.of(Items.IRON_INGOT), 1, 1e9);
            String t = tag == null ? "none" : tag.task().get().describe(), i = iron == null ? "none" : iron.task().get().describe();
            BotArena.LOG.info("[BOTTEST] no_raid_on_a_mineshaft: a name tag with a mineshaft known: {}; iron with a shipwreck known: {} (~{} s)",
                    t, i, iron == null ? -1 : Math.round(iron.cost()));
            h.assertFalse(t.startsWith("loot the mineshaft"), "a name tag: " + t);
            h.assertTrue(i.startsWith("loot the shipwreck"), "iron: " + i);
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /** A witch, by night, geared: none to be had by looking about (one in three hundred monsters) - at a swamp hut known nearby, yes. */
    @GameTest(environment = "challengecraft:knowledge_witch", structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void witchAtTheSwampHut(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "witch_at_the_swamp_hut");
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD, 8));
        var goal = LockoutBingoGoalPool.byId("kill_witch");
        time(h, 14000);
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            var before = LockoutGoals.plan(bot, new ObtainPlanner(), goal);
            bot.senses().know(Identifier.withDefaultNamespace("swamp_hut"), a.abs(60, FEET, 20), bot.body().blockPosition());
            var known = LockoutGoals.plan(bot, new ObtainPlanner(), goal);
            BotArena.day(h);
            String b = before == null ? "none" : before.task().get().describe() + " ~" + Math.round(before.cost()) + " s";
            String k = known == null ? "none" : known.task().get().describe();
            BotArena.LOG.info("[BOTTEST] witch_at_the_swamp_hut: none known: {}; a hut known: {} (~{} s)", b, k, known == null ? -1 : Math.round(known.cost()));
            h.assertTrue(k.startsWith("hunt a witch at the swamp hut"), "with a hut known: " + k);
            h.assertTrue(before == null || before.cost() > 2 * known.cost(), "without a hut: " + b);
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /** Goal experience can learn the factors the leagues measured (mob tiles four to nine times their estimate), not only up to 2. */
    @GameTest(structure = STRUCTURE, maxTicks = 20, padding = 8)
    public void experienceLearnsBigFactors(GameTestHelper h) {
        double nine = GoalExperience.learnt(9, 9, 9, 9), half = GoalExperience.learnt(0.5, 0.5, 0.5), once = GoalExperience.learnt(9);
        BotArena.LOG.info("[BOTTEST] experience: four games at 9x -> {}, three at 0.5x -> {}, one at 9x -> {}", round(nine), round(half), round(once));
        h.assertTrue(nine > 4, "four games at nine times: " + nine);
        h.assertTrue(half < 0.75, "three games at half: " + half);
        h.assertTrue(once > 1.5 && once < 4, "one game should move it, not decide it: " + once);
        h.succeed();
    }
}
