package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.BotParams;
import net.kasax.challengecraft.bot.lockout.LockoutBrain;
import net.kasax.challengecraft.bot.lockout.LockoutGoals;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.List;
import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * Round 2 of the board brain (docs/bob-plan.md 28): the estimates the leagues found too
 * optimistic (knob {@code estfix}), the leagues' goal priors (knob {@code goalprior}), the look-ahead
 * that decides the chores too ({@code search=2}), and the knowledge base's routes run end to end -
 * a structure's chests looted for a tile (a shipwreck, buried treasure, a village) and a mob hunted
 * at its home (a pillager at its outpost, a witch at her hut). The structures are put down by hand
 * and made known as the world sense would make them known: the routes, not the world generator,
 * are under test.
 */
public class BotBrainTests {
    private static void time(GameTestHelper h, int ticks) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + ticks);
    }

    private static double cost(LockoutGoals.Option o) {
        return o == null ? -1 : o.cost();
    }

    // ---- estimates ------------------------------------------------------------------------------

    /**
     * "3 Sapling Types" with oak trees about and no other kind in sight (leagues 9 and 10: ~49 s
     * planned, 0 got in 5 starts at some 195 s each): with estfix the second and third kinds are
     * found where their trees are, the leaves a step up the crown each - several times the old figure.
     */
    @GameTest(environment = "challengecraft:brain_tests", structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void saplingTypesEstimate(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "sapling_types_estimate");
        for (int[] t : new int[][]{{8, 8}, {30, 10}, {10, 30}}) {
            a.fill(t[0], FEET, t[1], t[0], FEET + 3, t[1], Blocks.OAK_LOG);
            a.fill(t[0] - 2, FEET + 3, t[1] - 2, t[0] + 2, FEET + 5, t[1] + 2, Blocks.OAK_LEAVES);
        }
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.CRAFTING_TABLE));
        var goal = LockoutBingoGoalPool.byId("obtain_3_sapling_types");
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            bot.params = BotParams.parse("estfix=0");
            var old = LockoutGoals.planAny(bot, new ObtainPlanner(), goal);
            bot.params = BotParams.parse("-");
            var fixed = LockoutGoals.planAny(bot, new ObtainPlanner(), goal);
            BotArena.LOG.info("[BOTTEST] sapling_types_estimate: estfix=0 ~{} s, estfix=1 ~{} s", Math.round(cost(old)), Math.round(cost(fixed)));
            h.assertTrue(old != null && fixed != null, "no way to three sapling types");
            h.assertTrue(cost(fixed) >= 2 * cost(old) && cost(fixed) >= 120, "with estfix ~" + Math.round(cost(fixed)) + " s against ~" + Math.round(cost(old)) + " s");
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /**
     * The dripstone caves known fifty blocks down and forty off (as the world sense knows a cave
     * biome: at its depth): with estfix the staircase down into it is in the estimate, not just the
     * walk over the land (leagues 9 and 10: ~12 to 120 s planned, 1 got in 4 starts at ~260 s each).
     */
    @GameTest(environment = "challengecraft:brain_tests", structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void caveBiomeWayDown(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "cave_biome_way_down");
        a.spawn(5, FEET, 5, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.CRAFTING_TABLE));
        var goal = LockoutBingoGoalPool.byId("visit_dripstone_caves");
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            BlockPos below = a.abs(35, FEET - 50, 35);
            bot.senses().knowBiome(Identifier.withDefaultNamespace("dripstone_caves"), below, bot.body().blockPosition());
            bot.params = BotParams.parse("estfix=0");
            var old = LockoutGoals.planAny(bot, new ObtainPlanner(), goal);
            bot.params = BotParams.parse("-");
            var fixed = LockoutGoals.planAny(bot, new ObtainPlanner(), goal);
            BotArena.LOG.info("[BOTTEST] cave_biome_way_down: estfix=0 ~{} s, estfix=1 ~{} s", Math.round(cost(old)), Math.round(cost(fixed)));
            h.assertTrue(old != null && fixed != null, "no way to the dripstone caves known below");
            h.assertTrue(cost(fixed) >= cost(old) + 40, "the way down not in it: ~" + Math.round(cost(fixed)) + " s against ~" + Math.round(cost(old)) + " s");
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /** "Fall 20 Blocks" with the blocks in the pack: a pillar's real pace (leagues: ~20 s planned, 2 got in 8, mostly out of time). */
    @GameTest(environment = "challengecraft:brain_tests", structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void fallEstimate(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "fall_estimate");
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 32));
        var goal = LockoutBingoGoalPool.byId("fall_20_blocks_and_survive");
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            bot.params = BotParams.parse("estfix=0");
            var old = LockoutGoals.planAny(bot, new ObtainPlanner(), goal);
            bot.params = BotParams.parse("-");
            var fixed = LockoutGoals.planAny(bot, new ObtainPlanner(), goal);
            BotArena.LOG.info("[BOTTEST] fall_estimate: estfix=0 ~{} s, estfix=1 ~{} s", Math.round(cost(old)), Math.round(cost(fixed)));
            h.assertTrue(cost(old) > 0 && cost(old) < 30, "the old figure ~" + Math.round(cost(old)) + " s");
            h.assertTrue(cost(fixed) >= 35 && cost(fixed) <= 90, "with estfix ~" + Math.round(cost(fixed)) + " s");
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /** The leagues' goal priors are read: tiles that ran far over their estimates there start believed dearer, the knob turns it off. */
    @GameTest(environment = "challengecraft:brain_tests", structure = STRUCTURE, maxTicks = 20, padding = 8)
    public void goalPriors(GameTestHelper h) {
        double lava = net.kasax.challengecraft.bot.lockout.GoalExperience.priorFactor("craft_lava_bucket");
        double tag = net.kasax.challengecraft.bot.lockout.GoalExperience.priorFactor("obtain_name_tag");
        double bricks = net.kasax.challengecraft.bot.lockout.GoalExperience.priorFactor("craft_bricks");
        double none = net.kasax.challengecraft.bot.lockout.GoalExperience.priorFactor("visit_end_city");
        BotArena.LOG.info("[BOTTEST] goal_priors: lava bucket x{}, name tag x{}, bricks x{}, end city x{}", round(lava), round(tag), round(bricks), round(none));
        h.assertTrue(lava > 2, "lava bucket (2 got in 7 at eight times its estimate a tile): x" + round(lava));
        h.assertTrue(tag > 1.5, "name tag: x" + round(tag));
        h.assertTrue(bricks > 0.8 && bricks < 1.6, "bricks (7 of 7, near the plan): x" + round(bricks));
        h.assertTrue(none == 1, "a goal never started: x" + round(none));
        h.succeed();
    }

    private static double round(double x) {
        return Math.round(x * 100) / 100.0;
    }

    // ---- the look-ahead deciding the chores (search=2) ------------------------------------------
    // (A batch of its own: a Lockout game of its own, not at once with the search=1 test.)

    /**
     * Bob with the look-ahead deciding the chores too ({@code search=2}, quick tiles off): no shield,
     * little food, a board he can do right here - the look-ahead decides (tiles and chores alike) and
     * the first tile comes within two minutes.
     */
    @GameTest(environment = "challengecraft:lockout_search2", structure = STRUCTURE, maxTicks = 2500, skyAccess = true, padding = 8)
    public void searchChoresFirstTile(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "search_chores_first_tile");
        BotArena.day(h);
        var server = h.getLevel().getServer();
        var bob = a.spawn(20, FEET, 20,
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_SHOVEL),
                new ItemStack(Items.BREAD, 3), new ItemStack(Items.COBBLESTONE, 40), new ItemStack(Items.OAK_PLANKS, 32),
                new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.BUNDLE));
        bob.params = BotParams.parse("search=2,quick=0");
        Chal_40_LockoutBingo.startDebugRun(List.of((net.minecraft.server.level.ServerPlayer) bob.body()),
                List.of("craft_bucket", "craft_shears", "craft_furnace", "craft_cauldron"));
        LockoutBrain brain = new LockoutBrain(LockoutBrain.Difficulty.HARD);
        bob.setBrain(brain);
        LockoutBingoTeam team = Chal_40_LockoutBingo.teamOf(server, bob.id);
        boolean[] ended = {false};
        h.succeedWhen(() -> {
            int tiles = Chal_40_LockoutBingo.score(server, team);
            if (h.getTick() >= 2480 && tiles < 1 && !ended[0]) {
                ended[0] = true;
                BotArena.LOG.info("[BOTTEST] search_chores_first_tile FAILED: no tile, look-ahead decisions {} (chores {})",
                        brain.lookAheadDecisions(), brain.lookAheadChores());
                end(server, bob);
            }
            h.assertTrue(tiles >= 1, "no tile yet");
            h.assertTrue(brain.lookAheadDecisions() >= 1, "a tile, but the look-ahead never decided");
            BotArena.LOG.info("[BOTTEST] search_chores_first_tile passed in {} ticks ({} look-ahead decisions, {} of them chores)", h.getTick(),
                    brain.lookAheadDecisions(), brain.lookAheadChores());
            end(server, bob);
        });
    }

    private static void end(net.minecraft.server.MinecraftServer server, net.kasax.challengecraft.bot.Bot bob) {
        bob.setBrain(null);
        Chal_40_LockoutBingo.endDebugRun(server);
        Chal_40_LockoutBingo.setActive(false);
        BotManager.remove(server, bob);
    }

    // ---- routes: a structure's chests ---------------------------------------------------------------

    /** Chests with these loot tables at these (relative) spots, as the structure's. */
    private static void chests(BotArena a, net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table, int[]... at) {
        long seed = 1;
        for (int[] p : at) {
            BlockPos pos = a.abs(p[0], p[1], p[2]);
            a.helper().getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            if (a.helper().getLevel().getBlockEntity(pos) instanceof RandomizableContainerBlockEntity c) {
                c.setLootTable(table);
                c.setLootTableSeed(seed++);
            }
        }
    }

    /** The iron tile with a structure known (as the world sense knows one): the route is to loot it, and it is run till the iron is in hand. */
    private static void lootRoute(GameTestHelper h, BotArena a, String name, String family, BlockPos spot, int maxTicks) {
        var goal = LockoutBingoGoalPool.byId("obtain_iron_ingot");
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            bot.senses().know(Identifier.withDefaultNamespace(family), spot, bot.body().blockPosition());
            var o = LockoutGoals.plan(bot, new ObtainPlanner(), goal);
            String route = o == null ? "none" : o.task().get().describe();
            BotArena.LOG.info("[BOTTEST] {}: the route {} (~{} s)", name, route, Math.round(cost(o)));
            h.assertTrue(route.startsWith("loot the " + family.replace('_', ' ')), "the route: " + route);
            a.run(o.task().get(), maxTicks, () -> ObtainPlanner.countAny(bot.body(), Set.of(Items.IRON_INGOT)) >= 1);
        });
    }

    /** A shipwreck's treasure and supply chests thirty blocks off, known: Bob walks there and loots the iron (94 % a treasure chest). */
    @GameTest(environment = "challengecraft:brain_routes", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void lootShipwreckRoute(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "loot_shipwreck_route");
        a.fill(28, FEET, 28, 34, FEET, 32, Blocks.OAK_PLANKS);
        chests(a, BuiltInLootTables.SHIPWRECK_TREASURE, new int[]{30, FEET + 1, 30});
        chests(a, BuiltInLootTables.SHIPWRECK_SUPPLY, new int[]{33, FEET + 1, 31});
        a.spawn(5, FEET, 5, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.CRAFTING_TABLE));
        lootRoute(h, a, "loot_shipwreck_route", "shipwreck", a.abs(31, FEET + 1, 30), 1500);
    }

    /**
     * Buried treasure, its chest under the sand thirty blocks off, known: the route to it (iron in
     * every buried treasure). Nobody stands inside a buried treasure's one part - its chest - and
     * before round 2 the raid never counted itself there.
     */
    @GameTest(environment = "challengecraft:brain_routes", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void lootBuriedTreasureRoute(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "loot_buried_treasure_route");
        a.fill(27, GROUND - 3, 27, 33, GROUND, 33, Blocks.SAND);
        chests(a, BuiltInLootTables.BURIED_TREASURE, new int[]{30, GROUND - 2, 30});
        a.spawn(5, FEET, 5, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.CRAFTING_TABLE));
        lootRoute(h, a, "loot_buried_treasure_route", "buried_treasure", a.abs(30, FEET, 30), 1500);
    }

    /** A village's smiths' chests, known: the raid there for iron (the weaponsmith's and the toolsmith's chests). */
    @GameTest(environment = "challengecraft:brain_routes", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void lootVillageRoute(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "loot_village_route");
        chests(a, BuiltInLootTables.VILLAGE_WEAPONSMITH, new int[]{30, FEET, 30}, new int[]{33, FEET, 27});
        chests(a, BuiltInLootTables.VILLAGE_TOOLSMITH, new int[]{27, FEET, 33});
        // (No pickaxe: the iron the usual way is a pickaxe first and a staircase - the smiths'
        // chests are the quicker way; with a stone pickaxe in hand mining was, ~143 s against ~150.)
        a.spawn(5, FEET, 5, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.CRAFTING_TABLE));
        lootRoute(h, a, "loot_village_route", "village", a.abs(30, FEET, 30), 1500);
    }

    // ---- routes: a mob at its home -----------------------------------------------------------------

    /**
     * A pillager outpost known thirty blocks off, a pillager there, Bob geared (the shooters' homes
     * want a shield or armour): the route is the hunt at the outpost, run till the pillager is dead.
     */
    @GameTest(environment = "challengecraft:brain_routes", structure = STRUCTURE, maxTicks = 1800, skyAccess = true, padding = 8)
    public void huntPillagerAtOutpost(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "hunt_pillager_at_outpost");
        var bot = a.spawn(5, FEET, 5, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD, 8));
        bot.body().setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        bot.body().setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        huntRoute(h, a, "hunt_pillager_at_outpost", "kill_pillager", "pillager_outpost", a.abs(31, FEET, 31), () -> {
            var pillager = EntityTypes.PILLAGER.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
            pillager.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(32, FEET, 32)));
            pillager.setPersistenceRequired();
            h.getLevel().addFreshEntity(pillager);
            return pillager;
        }, 1700);
    }

    /** A swamp hut known, a witch there, by night, Bob in iron: the hunt at the hut (one witch in three hundred monsters elsewhere). */
    @GameTest(environment = "challengecraft:brain_night", structure = STRUCTURE, maxTicks = 1800, skyAccess = true, padding = 8)
    public void huntWitchAtHut(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "hunt_witch_at_hut");
        time(h, 14000);
        var bot = a.spawn(5, FEET, 5, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD, 8));
        bot.body().setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        bot.body().setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        bot.body().setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        bot.body().setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        a.fill(29, FEET, 29, 33, FEET, 33, Blocks.SPRUCE_PLANKS);
        huntRoute(h, a, "hunt_witch_at_hut", "kill_witch", "swamp_hut", a.abs(31, FEET + 1, 31), () -> {
            var witch = EntityTypes.WITCH.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
            witch.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(31, FEET + 1, 31)));
            witch.setPersistenceRequired();
            h.getLevel().addFreshEntity(witch);
            return witch;
        }, 1700);
    }

    /**
     * The hunt at a mob's home: planned with the home known and no such mob seen (one in sight is
     * simply killed, the route is for one that is not), then the mob put there and the route run
     * till it is dead.
     */
    private static void huntRoute(GameTestHelper h, BotArena a, String name, String goalId, String family, BlockPos spot,
                                  java.util.function.Supplier<net.minecraft.world.entity.LivingEntity> spawnPrey, int maxTicks) {
        var goal = LockoutBingoGoalPool.byId(goalId);
        h.runAfterDelay(20, () -> {
            var bot = a.bot();
            bot.senses().know(Identifier.withDefaultNamespace(family), spot, bot.body().blockPosition());
            var o = LockoutGoals.plan(bot, new ObtainPlanner(), goal);
            String route = o == null ? "none" : o.task().get().describe();
            BotArena.LOG.info("[BOTTEST] {}: the route {} (~{} s)", name, route, Math.round(cost(o)));
            h.assertTrue(route.startsWith("hunt a"), "the route: " + route);
            var prey = spawnPrey.get();
            a.run(o.task().get(), maxTicks, () -> {
                if (!prey.isAlive()) {
                    BotArena.day(h);
                    return true;
                }
                return false;
            });
        });
    }
}
