package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.task.LavaPortalTask;
import net.kasax.challengecraft.bot.task.ObtainTask;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/** What the bot makes of the world: tools, portals. */
public class BotSkillTests {
    /** From nothing to a stone pickaxe: a few trees about, stone under the grass. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void stonePickaxe(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "stone_pickaxe");
        for (int[] t : new int[][]{{10, 10}, {14, 26}, {28, 12}}) {
            a.fill(t[0], FEET, t[1], t[0], FEET + 4, t[1], Blocks.OAK_LOG);
            a.fill(t[0] - 2, FEET + 3, t[1] - 2, t[0] + 2, FEET + 5, t[1] + 2, Blocks.OAK_LEAVES);
            a.fill(t[0], FEET + 3, t[1], t[0], FEET + 4, t[1], Blocks.OAK_LOG);
        }
        a.spawn(20, FEET, 20);
        a.run(new ObtainTask(Set.of(Items.STONE_PICKAXE), 1), 2400,
                () -> ObtainPlanner.countAny(a.bot().body(), Set.of(Items.STONE_PICKAXE)) > 0);
    }

    /**
     * Three ingots wanted now, six on the board: all six smelted in one go with coal (not the
     * planks), and the furnace taken back along.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void batchSmelt(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "batch_smelt");
        a.spawn(20, FEET, 20, new ItemStack(Items.RAW_IRON, 6), new ItemStack(Items.COAL, 2), new ItemStack(Items.OAK_PLANKS, 8),
                new ItemStack(Items.FURNACE), new ItemStack(Items.STONE_PICKAXE));
        var planner = new ObtainPlanner();
        planner.boardDemand.put(Items.IRON_INGOT, 6);
        var body = a.bot().body();
        a.run(new ObtainTask(Set.of(Items.IRON_INGOT), 3, planner), 1600,
                () -> ObtainPlanner.countAny(body, Set.of(Items.IRON_INGOT)) >= 6
                        && ObtainPlanner.countAny(body, Set.of(Items.FURNACE)) > 0
                        && ObtainPlanner.countAny(body, Set.of(Items.OAK_PLANKS)) == 8
                        && a.bot().current() == null);
    }

    /**
     * An iron vein in a rock with one ore showing, a lone ore a little farther on the other side: the
     * whole vein is taken (the ores behind the first come to light), the lone one left.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void wholeVein(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "whole_vein");
        a.fill(24, FEET, 17, 27, FEET + 2, 21, Blocks.STONE);
        a.fill(24, FEET, 19, 24, FEET, 19, Blocks.IRON_ORE);
        a.fill(25, FEET, 19, 25, FEET + 1, 19, Blocks.IRON_ORE);
        a.fill(26, FEET, 19, 26, FEET, 20, Blocks.IRON_ORE);
        a.fill(20, FEET, 25, 20, FEET, 25, Blocks.IRON_ORE);
        a.spawn(20, FEET, 19, new ItemStack(Items.STONE_PICKAXE));
        var body = a.bot().body();
        a.run(new ObtainTask(Set.of(Items.RAW_IRON), 5), 1600,
                () -> ObtainPlanner.countAny(body, Set.of(Items.RAW_IRON)) >= 5 && a.bot().current() == null
                        && h.getBlockState(new net.minecraft.core.BlockPos(20, FEET, 25)).is(Blocks.IRON_ORE));
    }

    /**
     * In a village with chests done: the bell the board wants, a bed to carry, the ripe wheat
     * field cut and baked into bread.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 3000, skyAccess = true, padding = 8)
    public void villageChores(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "village_chores");
        a.fill(10, GROUND, 26, 13, GROUND, 29, Blocks.FARMLAND);
        a.fill(10, FEET, 26, 13, FEET, 29, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        var bed = Blocks.BED.red().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH);
        a.fill(28, FEET, 13, 28, FEET, 13, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        a.fill(28, FEET, 12, 28, FEET, 12, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        a.fill(26, FEET, 28, 26, FEET, 28, Blocks.BELL);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.CRAFTING_TABLE));
        var body = a.bot().body();
        a.run(new net.kasax.challengecraft.bot.task.VillageChoresTask(Set.of(Blocks.BELL)), 3000,
                () -> ObtainPlanner.countAny(body, Set.of(Items.BELL)) > 0 && ObtainPlanner.countAny(body, Set.of(Blocks.BED.red().asItem())) > 0
                        && ObtainPlanner.countAny(body, Set.of(Items.BREAD)) >= 4 && a.bot().current() == null);
    }

    /** Night, a bed in the pack: put down, slept in until morning, taken back. */
    // (A batch of its own: it sets the clock to night, which the villagers next door would mind.)
    @GameTest(environment = "challengecraft:night", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void sleepNight(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "sleep_night");
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "time set 14000");
        var bed = Blocks.BED.red().asItem();
        a.spawn(20, FEET, 20, new ItemStack(bed), new ItemStack(Items.STONE_AXE));
        var body = a.bot().body();
        boolean[] slept = {false};
        a.run(new net.kasax.challengecraft.bot.task.SequenceTask("sleep", java.util.List.of(
                        () -> new net.kasax.challengecraft.bot.task.PlaceAndUseTask(bed, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.CLICK, null, 0),
                        net.kasax.challengecraft.bot.task.SleepTask::new,
                        () -> new net.kasax.challengecraft.bot.task.MineTask("the bed", s -> s.is(net.minecraft.tags.BlockTags.BEDS), Set.of(bed), 1).knownOnly())),
                1200, () -> {
                    if (body.isSleeping()) slept[0] = true;
                    return slept[0] && ObtainPlanner.countAny(body, Set.of(bed)) > 0 && a.bot().current() == null;
                });
    }

    /**
     * Night with another player about: Bob does not lie down while the other is up (the night
     * would not pass), and would once the other is in bed.
     */
    // (A batch of its own: every bot in the world counts as a player who has to sleep.)
    @GameTest(environment = "challengecraft:slumber", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void sleepWaitsForOthers(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "sleep_waits_for_others");
        var level = h.getLevel();
        var server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 14000");
        // (Every one of them asleep is needed: the usual "one in bed" rule would let it through.)
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.PLAYERS_SLEEPING_PERCENTAGE, 100, server);
        var bed = Blocks.BED.red().asItem();
        a.spawn(20, FEET, 20, new ItemStack(bed));
        var other = net.kasax.challengecraft.bot.BotManager.spawn(server, "Other" + System.nanoTime() % 1000, level,
                net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(10, FEET, 10)));
        other.body().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        other.body().getInventory().clearContent();
        other.body().getInventory().add(new ItemStack(bed));
        boolean awake = net.kasax.challengecraft.bot.lockout.LockoutBrain.nightWouldPass(a.bot());
        if (awake) h.fail("would sleep with the other one up");
        other.doNow(new net.kasax.challengecraft.bot.task.SequenceTask("to bed", java.util.List.of(
                () -> new net.kasax.challengecraft.bot.task.PlaceAndUseTask(bed, net.kasax.challengecraft.bot.task.PlaceAndUseTask.Then.CLICK, null, 0),
                net.kasax.challengecraft.bot.task.SleepTask::new)));
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(800), 900, () -> {
            boolean ok = other.body().isSleeping() && net.kasax.challengecraft.bot.lockout.LockoutBrain.nightWouldPass(a.bot());
            if (ok) net.kasax.challengecraft.bot.BotManager.remove(server, other);
            return ok;
        });
    }

    /**
     * Cobblestone on flat grass, no rock showing: a staircase down (not a shaft), and back up it
     * on foot - none of the cobblestone spent on climbing out.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void cobbleStairs(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "cobble_stairs");
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE));
        var body = a.bot().body();
        a.run(new net.kasax.challengecraft.bot.task.SequenceTask("cobble and back", java.util.List.of(
                        () -> new ObtainTask(Set.of(Items.COBBLESTONE), 20),
                        () -> new net.kasax.challengecraft.bot.task.GoToTask(a.abs(20, FEET, 20), 1))), 2400,
                () -> ObtainPlanner.countAny(body, Set.of(Items.COBBLESTONE)) >= 20 && a.near(20, FEET, 20, 1.5) && a.bot().current() == null);
    }

    /** A rock wall at hand: the cobblestone comes out of the wall, the ground it stands on stays whole. */
    @GameTest(structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void cobbleFromWall(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "cobble_from_wall");
        a.fill(23, FEET, 14, 26, FEET + 3, 26, Blocks.STONE);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE));
        var body = a.bot().body();
        a.run(new ObtainTask(Set.of(Items.COBBLESTONE), 12), 1600, () -> {
            if (body.getY() < a.abs(0, FEET, 0).getY() - 0.5) h.fail("dug down instead of into the wall");
            return ObtainPlanner.countAny(body, Set.of(Items.COBBLESTONE)) >= 12 && a.bot().current() == null;
        });
    }

    /** Three iron wanted, a vein of six in the rock: all six taken while there. */
    @GameTest(structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void veinBeyondCount(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "vein_beyond_count");
        a.fill(24, FEET, 17, 27, FEET + 2, 21, Blocks.STONE);
        a.fill(24, FEET, 19, 25, FEET, 19, Blocks.IRON_ORE);
        a.fill(25, FEET + 1, 19, 26, FEET + 1, 19, Blocks.IRON_ORE);
        a.fill(26, FEET, 19, 26, FEET, 20, Blocks.IRON_ORE);
        a.spawn(20, FEET, 19, new ItemStack(Items.STONE_PICKAXE));
        var body = a.bot().body();
        a.run(new ObtainTask(Set.of(Items.RAW_IRON), 3), 1600,
                () -> ObtainPlanner.countAny(body, Set.of(Items.RAW_IRON)) >= 6 && a.bot().current() == null);
    }

    /**
     * Digging down for iron right over a cave: the staircase meets the cave's roof (no floor for
     * the next step) - on along the level or down into the cave, not stuck on the spot; the ore
     * in the cave wall then in sight and taken.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void digIntoCave(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "dig_into_cave");
        a.fill(6, 8, 6, 34, 10, 34, Blocks.AIR);
        // (The ore on the cave floor in the middle: in sight from wherever it comes down.)
        a.fill(20, 8, 20, 20, 8, 20, Blocks.IRON_ORE);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.TORCH, 16));
        var body = a.bot().body();
        int depth = a.abs(0, 4, 0).getY();
        a.run(new net.kasax.challengecraft.bot.task.MineTask("raw_iron", s -> s.is(Blocks.IRON_ORE), Set.of(Items.RAW_IRON), 1, depth), 2400,
                () -> ObtainPlanner.countAny(body, Set.of(Items.RAW_IRON)) >= 1);
    }

    /** Oak trees about: an apple is known to come from their leaves (one break in two hundred). */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void applesFromLeaves(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "apples_from_leaves");
        for (int[] t : new int[][]{{10, 10}, {14, 26}, {28, 12}}) {
            a.fill(t[0] - 2, FEET + 3, t[1] - 2, t[0] + 2, FEET + 5, t[1] + 2, Blocks.OAK_LEAVES);
            a.fill(t[0], FEET, t[1], t[0], FEET + 4, t[1], Blocks.OAK_LOG);
        }
        a.spawn(20, FEET, 20);
        h.runAfterDelay(20, () -> {
            var planner = new ObtainPlanner();
            double cost = planner.estimate(a.bot(), Set.of(Items.APPLE), 1);
            var plan = planner.plan(a.bot(), Set.of(Items.APPLE), 1);
            String first = plan instanceof ObtainPlanner.Step st ? st.task().describe() : plan.toString();
            BotArena.LOG.info("[BOTTEST] apples_from_leaves: ~{} s, first {}", Math.round(cost), first);
            if (cost >= 1e9 || !first.startsWith("mine")) h.fail("no way to an apple: " + first);
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /** A bucket, no water in sight: off to find some, and filled. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void waterUnseen(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "water_unseen");
        a.spawn(20, FEET, 20, new ItemStack(Items.BUCKET));
        h.runAfterDelay(60, () -> a.fill(36, BotArena.GROUND - 1, 36, 38, BotArena.GROUND, 38, Blocks.WATER));
        a.run(new ObtainTask(Set.of(Items.WATER_BUCKET), 1, new ObtainPlanner()), 1200,
                () -> ObtainPlanner.countAny(a.bot().body(), Set.of(Items.WATER_BUCKET)) > 0);
    }

    /**
     * A lake sealed in the rock right under its feet (that it cannot have seen) and a pond on the
     * surface 22 blocks off: the pond, as a player would; no digging down.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void waterNotXray(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "water_not_xray");
        a.fill(16, GROUND - 9, 16, 24, GROUND - 6, 24, Blocks.WATER);
        a.fill(34, GROUND - 1, 34, 36, GROUND, 36, Blocks.WATER);
        a.spawn(20, FEET, 20, new ItemStack(Items.BUCKET));
        int[] lowest = {FEET};
        a.run(new ObtainTask(Set.of(Items.WATER_BUCKET), 1, new ObtainPlanner()), 1200, () -> {
            lowest[0] = Math.min(lowest[0], a.feet().getY());
            h.assertTrue(lowest[0] >= GROUND - 1, "dug down to " + lowest[0]);
            return ObtainPlanner.countAny(a.bot().body(), Set.of(Items.WATER_BUCKET)) > 0;
        });
    }

    /** Not a test of skill: logs which goals of the whole pool Bob knows no way for ([COVERAGE]). */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void coverage(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "coverage");
        a.spawn(20, FEET, 20);
        h.runAfterDelay(20, () -> {
            var planner = new ObtainPlanner();
            java.util.Map<String, java.util.List<String>> missing = new java.util.TreeMap<>();
            int total = 0;
            for (var g : net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool.all()) {
                total++;
                boolean covered;
                try {
                    covered = net.kasax.challengecraft.bot.lockout.LockoutGoals.covers(a.bot(), planner, g);
                } catch (RuntimeException e) {
                    covered = false;
                }
                if (!covered) missing.computeIfAbsent(g.type().name(), k -> new java.util.ArrayList<>())
                        .add(g.id() + (g.isSelectableOnNormalBoard() && g.isImplemented() ? "" : "(off-board)"));
            }
            int n = missing.values().stream().mapToInt(java.util.List::size).sum();
            BotArena.LOG.info("[COVERAGE] {} of {} goals without a way: {}", n, total, missing);
            a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 100, () -> true);
        });
    }

    /** Its things lying where it died, 20 blocks off: all picked up again. */
    @GameTest(structure = STRUCTURE, maxTicks = 800, skyAccess = true, padding = 8)
    public void recoverThings(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "recover");
        var level = h.getLevel();
        var spot = a.abs(30, FEET, 30);
        for (ItemStack st : new ItemStack[]{new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.OAK_PLANKS, 7)}) {
            var e = new net.minecraft.world.entity.item.ItemEntity(level, spot.getX() + level.getRandom().nextDouble() * 3, spot.getY() + 0.5,
                    spot.getZ() + level.getRandom().nextDouble() * 3, st);
            level.addFreshEntity(e);
        }
        a.spawn(8, FEET, 8);
        a.run(new net.kasax.challengecraft.bot.task.RecoverTask(spot), 800, () -> ObtainPlanner.countAny(a.bot().body(), Set.of(Items.STONE_PICKAXE)) > 0
                && ObtainPlanner.countAny(a.bot().body(), Set.of(Items.IRON_INGOT)) >= 3 && ObtainPlanner.countAny(a.bot().body(), Set.of(Items.OAK_PLANKS)) >= 7);
    }

    /** Taiga: a big spruce (2 x 2) and a slim one, leaves down to head height. Six logs. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void spruceLogs(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "spruce_logs");
        spruce(a, 10, 10, 2, 16);
        spruce(a, 26, 24, 1, 10);
        a.spawn(20, FEET, 20);
        a.run(new ObtainTask(Set.of(Items.SPRUCE_LOG), 6), 2400,
                () -> ObtainPlanner.countAny(a.bot().body(), Set.of(Items.SPRUCE_LOG)) >= 6);
    }

    private static void spruce(BotArena a, int x, int z, int width, int height) {
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int y = 2; y <= height; y++) {
            int r = Math.max(1, (height - y) / 3 + 1) + (width - 1);
            if (y % 2 == 1 && y < height - 1) r--;
            a.fill(x - r, FEET + y, z - r, x + width - 1 + r, FEET + y, z + width - 1 + r, leaves);
        }
        a.fill(x, FEET, z, x + width - 1, FEET + height - 2, z + width - 1, Blocks.SPRUCE_LOG);
    }

    /**
     * Two eyes of ender, thrown forty blocks apart, read the way the bot reads them (where each
     * set out, where it is a moment later): the two lines cross at the stronghold, to the block.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void eyeTriangulation(GameTestHelper h) {
        var level = h.getLevel();
        net.minecraft.world.phys.Vec3 stronghold = net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(1730, 0, -1210))).multiply(1, 0, 1).add(0, 32, 0);
        net.minecraft.world.entity.projectile.EyeOfEnder[] eyes = new net.minecraft.world.entity.projectile.EyeOfEnder[2];
        net.minecraft.world.phys.Vec3[] from = new net.minecraft.world.phys.Vec3[2];
        int[][] at = {{4, 20}, {36, 24}};
        for (int i = 0; i < 2; i++) {
            var p = net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(at[i][0], FEET + 1, at[i][1])));
            eyes[i] = new net.minecraft.world.entity.projectile.EyeOfEnder(level, p.x, p.y, p.z);
            eyes[i].signalTo(stronghold);
            level.addFreshEntity(eyes[i]);
            from[i] = eyes[i].position();
        }
        h.runAfterDelay(30, () -> {
            var readings = new net.kasax.challengecraft.bot.task.EyeTrackTask.Reading[2];
            for (int i = 0; i < 2; i++) {
                var now = eyes[i].position();
                double dx = now.x - from[i].x, dz = now.z - from[i].z, m = Math.sqrt(dx * dx + dz * dz);
                readings[i] = new net.kasax.challengecraft.bot.task.EyeTrackTask.Reading(from[i], new net.minecraft.world.phys.Vec3(dx / m, 0, dz / m));
            }
            var cross = net.kasax.challengecraft.bot.task.EyeTrackTask.intersect(readings[0], readings[1]);
            if (cross == null) h.fail("the lines did not cross");
            double off = Math.hypot(cross.x - stronghold.x, cross.z - stronghold.z);
            BotArena.LOG.info("[BOTTEST] eye_triangulation: estimate {}, {} for {}, {} ({} blocks off)", cross.x, cross.z, stronghold.x, stronghold.z, off);
            if (off > 1.5) h.fail("estimate " + Math.round(off) + " blocks off");
            h.succeed();
        });
    }

    /** The portal cast at a lava pool, then through it (see {@link BotPortalTests} for the pool shapes). */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void castPortal(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "cast_portal");
        a.fill(24, GROUND, 4, 27, GROUND, 7, Blocks.LAVA);
        a.spawn(20, FEET, 20, new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.DIRT, 20), new ItemStack(Items.COBBLESTONE, 8));
        a.run(new LavaPortalTask(h.getLevel(), null), 3600, () -> a.bot().body().level().dimension() == Level.NETHER);
    }

    /**
     * The cast cut short halfway (called away), then a new cast: the frame begun is finished,
     * not a second one started beside it.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 4800, skyAccess = true, padding = 8)
    public void castPortalResumed(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "cast_portal_resumed");
        a.fill(24, GROUND, 4, 27, GROUND, 7, Blocks.LAVA);
        a.spawn(20, FEET, 20, new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.DIRT, 20), new ItemStack(Items.COBBLESTONE, 8));
        java.util.function.IntSupplier obsidian = () -> {
            int n = 0;
            for (int x = 0; x < 40; x++) for (int y = GROUND - 2; y < FEET + 8; y++) for (int z = 0; z < 40; z++)
                if (h.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).is(Blocks.OBSIDIAN)) n++;
            return n;
        };
        int[] most = {0};
        a.run(new net.kasax.challengecraft.bot.BotTask() {
            net.kasax.challengecraft.bot.BotTask first = new LavaPortalTask(h.getLevel(), null), second;

            @Override
            public Result tick(net.kasax.challengecraft.bot.Bot bot) {
                if (second == null) {
                    first.tick(bot);
                    if (h.getTick() % 10 == 0 && obsidian.getAsInt() >= 4) {
                        bot.navigator().stop();
                        bot.body().stopInputs();
                        second = new LavaPortalTask(h.getLevel(), null);
                    }
                    return Result.RUNNING;
                }
                return second.tick(bot);
            }

            @Override
            public String describe() {
                return "cast, called away, cast again";
            }
        }, 4800, () -> {
            if (h.getTick() % 20 == 0) most[0] = Math.max(most[0], obsidian.getAsInt());
            if (most[0] > 11) h.fail("a second frame: " + most[0] + " obsidian");
            return a.bot().body().level().dimension() == Level.NETHER;
        });
    }

    /** The pool in a cave, no flat room anywhere near: room dug out, then the portal cast. */
    @GameTest(structure = STRUCTURE, maxTicks = 6000, skyAccess = true, padding = 8)
    public void castPortalDug(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "cast_portal_dug");
        a.fill(0, FEET, 0, 39, FEET + 6, 39, Blocks.STONE);
        a.fill(22, FEET, 2, 29, FEET + 6, 9, Blocks.AIR);
        a.fill(19, FEET, 9, 21, FEET + 2, 21, Blocks.AIR);
        a.fill(24, GROUND, 4, 27, GROUND, 7, Blocks.LAVA);
        a.spawn(20, FEET, 20, new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.DIRT, 16), new ItemStack(Items.COBBLESTONE, 8));
        a.run(new LavaPortalTask(h.getLevel(), null), 6000, () -> a.bot().body().level().dimension() == Level.NETHER);
    }

    /**
     * Through a lit portal, then far off in the Nether (beyond where it would see the portal), and
     * back home through the same portal: it remembers where it came out.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 3000, skyAccess = true, padding = 8)
    public void portalRoundTrip(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "portal_round_trip");
        var level = h.getLevel();
        a.fill(24, FEET, 20, 27, FEET + 4, 20, Blocks.OBSIDIAN);
        a.fill(25, FEET + 1, 20, 26, FEET + 3, 20, Blocks.AIR);
        net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, a.abs(25, FEET + 1, 20), net.minecraft.core.Direction.Axis.X)
                .ifPresent(sh -> sh.createPortalBlocks(level));
        a.spawn(20, FEET, 22, new ItemStack(Items.COBBLESTONE, 32));
        int[] stage = {0};
        long[] arrived = {0};
        a.run(new net.kasax.challengecraft.bot.BotTask() {
            final net.kasax.challengecraft.bot.task.ThroughPortalTask in = new net.kasax.challengecraft.bot.task.ThroughPortalTask(), out = new net.kasax.challengecraft.bot.task.ThroughPortalTask();

            @Override
            public Result tick(net.kasax.challengecraft.bot.Bot bot) {
                var body = bot.body();
                if (stage[0] == 0) {
                    in.tick(bot);
                    if (body.level().dimension() == Level.NETHER) stage[0] = 1;
                    return Result.RUNNING;
                }
                if (stage[0] == 1) {
                    // A corridor ninety blocks long out of the portal, and over to its far end.
                    var nether = (net.minecraft.server.level.ServerLevel) body.level();
                    if (arrived[0] == 0) arrived[0] = nether.getGameTime();
                    if (nether.getGameTime() - arrived[0] < 20) return Result.RUNNING; // (out of the frame first)
                    var start = body.blockPosition();
                    for (int i = -2; i <= 92; i++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            nether.setBlockAndUpdate(start.offset(i, -1, dz), Blocks.OBSIDIAN.defaultBlockState());
                            for (int y = 0; y < 3; y++) {
                                var q = start.offset(i, y, dz);
                                if (!nether.getBlockState(q).is(Blocks.NETHER_PORTAL) && !nether.getBlockState(q).is(Blocks.OBSIDIAN)) nether.setBlockAndUpdate(q, Blocks.AIR.defaultBlockState());
                            }
                            nether.setBlockAndUpdate(start.offset(i, 3, dz), Blocks.OBSIDIAN.defaultBlockState());
                        }
                    }
                    body.teleportTo(start.getX() + 90.5, start.getY(), start.getZ() + 0.5);
                    stage[0] = 2;
                    return Result.RUNNING;
                }
                return out.tick(bot);
            }

            @Override
            public String describe() {
                return "there and back";
            }

            @Override
            public String status() {
                return "there and back (stage " + stage[0] + ") " + (stage[0] == 2 ? out.status() : in.status());
            }
        }, 3000, () -> stage[0] == 2 && a.bot().body().level().dimension() == Level.OVERWORLD);
    }

    /**
     * Dead once before the portal: the respawned body (a new player object) crosses like the first
     * one did, and comes out in the Nether at an eighth of its Overworld coordinates.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void portalAfterDeath(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "portal_after_death");
        var level = h.getLevel();
        a.fill(24, FEET, 20, 27, FEET + 4, 20, Blocks.OBSIDIAN);
        a.fill(25, FEET + 1, 20, 26, FEET + 3, 20, Blocks.AIR);
        net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, a.abs(25, FEET + 1, 20), net.minecraft.core.Direction.Axis.X)
                .ifPresent(sh -> sh.createPortalBlocks(level));
        var bot = a.spawn(20, FEET, 22);
        var first = bot.body();
        var gate = a.abs(25, FEET + 1, 20);
        boolean[] started = {false};
        h.runAfterDelay(5, () -> {
            first.hurtServer(level, level.damageSources().fellOutOfWorld(), 1000f);
            if (!first.isDeadOrDying()) {
                first.setHealth(0f);
                first.die(level.damageSources().fellOutOfWorld());
            }
        });
        h.onEachTick(() -> {
            var body = bot.body();
            if (started[0] || body == first || body.isDeadOrDying()) return;
            started[0] = true;
            // Back from the respawn point to the arena (a teleport: the network handler's), then
            // through the portal.
            var to = a.abs(20, FEET, 22);
            body.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
            a.run(new net.kasax.challengecraft.bot.task.ThroughPortalTask(), 1200, () -> {
                var now = bot.body();
                if (now.level().dimension() != Level.NETHER) return false;
                double dx = now.getX() - gate.getX() / 8.0, dz = now.getZ() - gate.getZ() / 8.0;
                h.assertTrue(dx * dx + dz * dz < 32 * 32, "came out at " + now.blockPosition().toShortString() + ", not near "
                        + gate.getX() / 8 + ", " + gate.getZ() / 8);
                return true;
            });
        });
    }

    /** Where mobs live, from the game's spawn lists: horses in plains not snow, zombies anywhere, drowned in rivers. */
    @GameTest(structure = STRUCTURE, maxTicks = 20, padding = 8)
    public void mobHabitats(GameTestHelper h) {
        var level = h.getLevel();
        var horse = net.kasax.challengecraft.bot.plan.MobHabitats.of(level, net.minecraft.world.entity.EntityTypes.HORSE);
        var zombie = net.kasax.challengecraft.bot.plan.MobHabitats.of(level, net.minecraft.world.entity.EntityTypes.ZOMBIE);
        var drowned = net.kasax.challengecraft.bot.plan.MobHabitats.of(level, net.minecraft.world.entity.EntityTypes.DROWNED);
        org.slf4j.LoggerFactory.getLogger("ChallengeCraft-BotTest").info("[BOTTEST] habitats: horse {}, zombie {}, drowned {}", horse, zombie, drowned);
        h.assertTrue(horse.contains(net.minecraft.resources.Identifier.parse("minecraft:plains")), "horses in plains");
        h.assertFalse(horse.contains(net.minecraft.resources.Identifier.parse("minecraft:snowy_plains")), "no horses in the snow");
        h.assertTrue(zombie.isEmpty(), "zombies anywhere");
        h.assertTrue(drowned.contains(net.minecraft.resources.Identifier.parse("minecraft:river")), "drowned in rivers");
        h.assertTrue(net.kasax.challengecraft.bot.plan.MobHabitats.anywhere(level, net.minecraft.world.entity.EntityTypes.ZOMBIE), "zombies turn up anywhere");
        h.assertFalse(net.kasax.challengecraft.bot.plan.MobHabitats.anywhere(level, net.minecraft.world.entity.EntityTypes.CAVE_SPIDER), "cave spiders only from spawners");
        h.assertFalse(net.kasax.challengecraft.bot.plan.MobHabitats.anywhere(level, net.minecraft.world.entity.EntityTypes.WITCH), "witches are rare");
        h.assertTrue(net.kasax.challengecraft.bot.plan.MobHabitats.anywhere(level, net.minecraft.world.entity.EntityTypes.CREEPER), "creepers anywhere");
        h.succeed();
    }

    /**
     * Water seen down a sinkhole, in a cave twenty-five blocks under the hill it stands on (near
     * over the ground, far straight down): dug down to, bucket filled.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 72)
    public void waterUnderHill(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "water_under_hill");
        a.fill(0, GROUND, 0, 39, GROUND + 25, 39, Blocks.STONE);
        a.fill(24, GROUND + 1, 24, 30, GROUND + 3, 30, Blocks.AIR);
        a.fill(30, GROUND + 4, 30, 30, GROUND + 25, 30, Blocks.AIR); // (the sinkhole it looks down)
        a.fill(27, GROUND + 1, 27, 27, GROUND + 1, 27, Blocks.WATER);
        a.spawn(20, GROUND + 26, 20, new ItemStack(Items.BUCKET), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 16));
        a.run(new net.kasax.challengecraft.bot.task.FillBucketTask(net.minecraft.tags.FluidTags.WATER), 2400,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.WATER_BUCKET)) > 0);
    }

    /** A crossbow and a firework rocket: the rocket loaded into the crossbow (the "firework crossbow" tile). */
    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void fireworkCrossbow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "firework_crossbow");
        a.spawn(20, FEET, 20, new ItemStack(Items.CROSSBOW), new ItemStack(Items.FIREWORK_ROCKET, 2), new ItemStack(Items.BREAD, 2));
        a.run(new net.kasax.challengecraft.bot.task.LoadCrossbowTask(), 300,
                () -> net.kasax.challengecraft.bot.task.LoadCrossbowTask.loaded(a.bot().body()));
    }

    /** A pack full of poppies (nothing the plan wants): some dropped, so the log it chops fits in. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void fullPack(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "full_pack");
        a.fill(24, FEET, 20, 24, FEET + 3, 20, Blocks.OAK_LOG);
        ItemStack[] pack = new ItemStack[36];
        pack[0] = new ItemStack(Items.WOODEN_AXE);
        for (int i = 1; i < 36; i++) pack[i] = new ItemStack(Items.POPPY, 64);
        a.spawn(20, FEET, 20, pack);
        a.run(new net.kasax.challengecraft.bot.task.ObtainTask(java.util.Set.of(Items.OAK_LOG), 1, new net.kasax.challengecraft.bot.plan.ObtainPlanner()), 600,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.OAK_LOG)) > 0);
    }

    /** Ten obsidian and flint and steel: a frame built, lit, gone through; it comes out in a portal on the other side. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void builtPortal(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "built_portal");
        a.spawn(20, FEET, 20, new ItemStack(Items.OBSIDIAN, 10), new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.COBBLESTONE, 16));
        var from = a.abs(20, FEET, 20);
        a.run(new net.kasax.challengecraft.bot.task.PortalTask(h.getLevel()), 2400, () -> {
            var body = a.bot().body();
            if (body.level().dimension() != Level.NETHER) return false;
            var nether = (net.minecraft.server.level.ServerLevel) body.level();
            var portal = net.kasax.challengecraft.bot.BotWorld.nearest(nether, body.blockPosition(), 6, 4,
                    s -> s.is(Blocks.NETHER_PORTAL), false, java.util.Set.of());
            org.slf4j.LoggerFactory.getLogger("ChallengeCraft-BotTest").info("[BOTTEST] built_portal: from {} came out at {}, portal there {}",
                    from.toShortString(), body.blockPosition().toShortString(), portal);
            return portal != null;
        });
    }

    /** Killed (as by a creeper): back on its feet after the respawn, the task stack cleared. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void respawnAfterDeath(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "respawn");
        a.spawn(20, FEET, 20);
        var bot = a.bot();
        boolean[] died = {false};
        h.runAfterDelay(100, () -> bot.body().hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 1000f));
        h.onEachTick(() -> { if (!bot.body().isAlive()) died[0] = true; });
        a.run(new net.kasax.challengecraft.bot.task.GoToTask(a.abs(4, FEET, 4), 1), 400,
                () -> died[0] && bot.body().isAlive() && bot.body().getHealth() >= 20f && bot.current() == null);
    }

    /** A chest behind a pressure plate over TNT (the desert temple's trap): looted, nothing blows up. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void trapChest(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "trap_chest");
        // A corridor one wide, the plate in the middle of it, TNT under the plate.
        a.fill(10, FEET, 19, 24, FEET + 2, 21, Blocks.SANDSTONE);
        a.fill(11, FEET, 20, 23, FEET + 1, 20, Blocks.AIR);
        a.fill(17, GROUND, 20, 17, GROUND, 20, Blocks.TNT);
        a.fill(17, FEET, 20, 17, FEET, 20, Blocks.STONE_PRESSURE_PLATE);
        a.fill(23, FEET, 20, 23, FEET, 20, Blocks.CHEST);
        var chestPos = a.abs(23, FEET, 20);
        if (h.getLevel().getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 2));
        }
        a.spawn(11, FEET, 20, new ItemStack(Items.WOODEN_PICKAXE));
        boolean[] gone = {false};
        h.onEachTick(() -> {
            if (!gone[0] && !h.getLevel().getBlockState(a.abs(17, GROUND, 20)).is(Blocks.TNT)) {
                gone[0] = true;
                BotArena.LOG.info("[BOTTEST] trap_chest: the TNT is gone at tick {}, bot at {}, now {}", h.getTick(), a.feet().toShortString(),
                        h.getLevel().getBlockState(a.abs(17, GROUND, 20)));
            }
        });
        a.run(new net.kasax.challengecraft.bot.task.LootTask(chestPos), 600,
                () -> !gone[0] && ObtainPlanner.countAny(a.bot().body(), Set.of(Items.DIAMOND)) > 0);
    }
}
