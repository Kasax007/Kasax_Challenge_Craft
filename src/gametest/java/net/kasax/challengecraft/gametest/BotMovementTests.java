package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.task.GoToTask;
import net.kasax.challengecraft.bot.task.SurfaceTask;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/** Getting about: the ground every other skill stands on. */
public class BotMovementTests {
    /** Across flat ground: the baseline (about 30 blocks, diagonal). */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void walkFlat(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "walk_flat");
        a.spawn(4, FEET, 4);
        a.run(new GoToTask(a.abs(34, FEET, 30), 1.5), 400, () -> a.near(34, FEET, 30, 1.6));
    }

    /** Out of a pool two deep whose bank is a full block above the water. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void poolExit(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "pool_exit");
        a.fill(10, GROUND - 2, 10, 14, GROUND, 14, Blocks.AIR);
        a.fill(10, GROUND - 2, 10, 14, GROUND - 1, 14, Blocks.WATER);
        a.spawn(12, GROUND - 1, 12);
        a.run(new GoToTask(a.abs(26, FEET, 12), 1.5), 600, () -> a.near(26, FEET, 12, 1.6));
    }

    /** Out of a deep lake with steep banks two blocks above the water: no way out without work. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void lakeSteepBanks(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lake_steep");
        a.fill(8, GROUND - 6, 8, 16, GROUND, 16, Blocks.AIR);
        a.fill(8, GROUND - 6, 8, 16, GROUND - 2, 16, Blocks.WATER);
        a.spawn(12, GROUND - 2, 12, new ItemStack(Items.WOODEN_PICKAXE));
        a.run(new GoToTask(a.abs(28, FEET, 12), 1.5), 1200, () -> a.near(28, FEET, 12, 1.6));
    }

    /** The same lake with no tool but blocks: build a step out. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void lakeSteepBanksBlocks(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lake_steep_blocks");
        a.fill(8, GROUND - 6, 8, 16, GROUND, 16, Blocks.AIR);
        a.fill(8, GROUND - 6, 8, 16, GROUND - 2, 16, Blocks.WATER);
        a.spawn(12, GROUND - 2, 12, new ItemStack(Items.COBBLESTONE, 16));
        a.run(new GoToTask(a.abs(28, FEET, 12), 1.5), 1200, () -> a.near(28, FEET, 12, 1.6));
    }

    /** Across a river five wide and two deep, banks one above the water. */
    @GameTest(structure = STRUCTURE, maxTicks = 800, skyAccess = true, padding = 8)
    public void riverCross(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "river_cross");
        a.fill(16, GROUND - 2, 0, 20, GROUND, 39, Blocks.AIR);
        a.fill(16, GROUND - 2, 0, 20, GROUND - 1, 39, Blocks.WATER);
        a.spawn(6, FEET, 20);
        a.run(new GoToTask(a.abs(32, FEET, 20), 1.5), 800, () -> a.near(32, FEET, 20, 1.6));
    }

    /** Up from the bottom of a one-wide shaft fourteen deep with a wooden pickaxe: a staircase. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void shaftClimbStairs(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "shaft_stairs");
        a.fill(0, 0, 0, 39, 0, 39, Blocks.BEDROCK); // (the world's floor, as in a real world: nothing to dig out under)
        a.fill(20, 1, 20, 20, GROUND, 20, Blocks.AIR);
        a.spawn(20, 1, 20, new ItemStack(Items.WOODEN_PICKAXE));
        a.run(new SurfaceTask(), 2400, () -> a.feet().getY() >= FEET - 1 && a.bot().body().onGround());
    }

    /** The same shaft with blocks at hand: pillar straight up. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void shaftClimbPillar(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "shaft_pillar");
        a.fill(20, 1, 20, 20, GROUND, 20, Blocks.AIR);
        a.spawn(20, 1, 20, new ItemStack(Items.COBBLESTONE, 32));
        a.run(new SurfaceTask(), 600, () -> a.feet().getY() >= FEET - 1 && a.bot().body().onGround());
    }

    /** Up a shaft whose bottom is flooded four deep (a flooded mine): blocks at hand, out of the water and up. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void floodedShaft(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "flooded_shaft");
        a.fill(19, 1, 19, 21, GROUND, 21, Blocks.STONE);
        a.fill(20, 1, 20, 20, GROUND, 20, Blocks.AIR);
        a.fill(19, 1, 19, 21, 4, 21, Blocks.AIR);
        a.fill(19, 1, 19, 21, 4, 21, Blocks.WATER);
        a.spawn(20, 3, 20, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.WOODEN_PICKAXE));
        a.run(new SurfaceTask(), 1200, () -> a.feet().getY() >= FEET - 1 && a.bot().body().onGround());
    }

    /** A one-wide shaft with water three deep at the bottom (dug into an aquifer): up and out. */
    @GameTest(structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void floodedNarrowShaft(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "flooded_narrow");
        a.fill(20, 1, 20, 20, GROUND, 20, Blocks.AIR);
        a.fill(20, 1, 20, 20, 3, 20, Blocks.WATER);
        a.spawn(20, 1, 20, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.WOODEN_PICKAXE));
        a.run(new SurfaceTask(), 900, () -> a.feet().getY() >= FEET - 1 && a.bot().body().onGround());
    }

    /** Out of a closed cave fifty-five blocks under solid stone (deep in the deepslate, say), in stages. */
    @GameTest(structure = STRUCTURE, maxTicks = 7200, skyAccess = true, padding = 8)
    public void deepClimb(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "deep_climb");
        a.fill(0, GROUND, 0, 39, GROUND + 40, 39, Blocks.STONE);
        a.fill(0, GROUND + 41, 0, 39, GROUND + 41, 39, Blocks.GRASS_BLOCK);
        a.fill(18, 2, 18, 22, 4, 22, Blocks.AIR);
        a.spawn(20, 2, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 16));
        a.run(new SurfaceTask(), 7200, () -> a.feet().getY() >= GROUND + 40 && a.bot().body().onGround());
    }

    /**
     * Down a winding tunnel (thirty-six blocks of stairs, then along) with no pickaxe at all, and
     * back out the way it came: it retraces its steps.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void retraceTunnel(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "retrace_tunnel");
        a.fill(0, GROUND, 0, 39, GROUND + 40, 39, Blocks.STONE);
        a.fill(0, GROUND + 41, 0, 39, GROUND + 41, 39, Blocks.GRASS_BLOCK);
        for (int i = 0; i < 36; i++) a.fill(2 + i, GROUND + 41 - i, 20, 2 + i, GROUND + 44 - i, 20, Blocks.AIR);
        a.fill(37, GROUND + 6, 4, 37, GROUND + 8, 20, Blocks.AIR);
        a.spawn(1, GROUND + 42, 20);
        int[] stage = {0};
        a.run(new net.kasax.challengecraft.bot.BotTask() {
            final net.kasax.challengecraft.bot.task.GoToTask down = new net.kasax.challengecraft.bot.task.GoToTask(a.abs(37, GROUND + 6, 5), 1.5);
            final SurfaceTask up = new SurfaceTask();

            @Override
            public Result tick(net.kasax.challengecraft.bot.Bot bot) {
                if (stage[0] == 0) {
                    Result r = down.tick(bot);
                    if (r == Result.FAILED) return r;
                    if (r == Result.DONE) stage[0] = 1;
                    return Result.RUNNING;
                }
                return up.tick(bot);
            }

            @Override
            public String describe() {
                return stage[0] == 0 ? "down the tunnel" : "back out";
            }
        }, 2400, () -> stage[0] == 1 && a.feet().getY() >= GROUND + 41 && a.bot().body().onGround());
    }

    /** Out of a closed cave twenty below the grass (gravel and dirt on the way), wooden pickaxe only. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void caveEscape(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "cave_escape");
        a.fill(0, GROUND, 0, 39, 24, 39, Blocks.STONE);
        a.fill(0, 17, 0, 39, 18, 39, Blocks.GRAVEL);
        a.fill(0, 21, 0, 39, 24, 39, Blocks.DIRT);
        a.fill(0, 25, 0, 39, 25, 39, Blocks.GRASS_BLOCK);
        a.fill(16, 3, 16, 22, 5, 22, Blocks.AIR);
        a.fill(19, 6, 19, 19, 6, 19, Blocks.WATER); // a leak in the roof
        a.spawn(17, 3, 17, new ItemStack(Items.WOODEN_PICKAXE));
        a.run(new SurfaceTask(), 2400, () -> a.feet().getY() >= 25 && a.bot().body().onGround());
    }

    /** Over a trench two wide and ten deep without a single block: jump it (or go down and up). */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void trenchGap(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "trench_gap");
        a.fill(18, GROUND - 9, 0, 19, GROUND, 39, Blocks.AIR);
        a.spawn(8, FEET, 20);
        a.run(new GoToTask(a.abs(30, FEET, 20), 1.5), 1200, () -> a.near(30, FEET, 20, 1.6));
    }

    /** Over a trench with blocks at hand: bridge it. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void trenchBridge(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "trench_bridge");
        a.fill(16, 1, 0, 21, GROUND, 39, Blocks.AIR);
        a.fill(16, 0, 0, 21, 0, 39, Blocks.LAVA);
        a.spawn(8, FEET, 20, new ItemStack(Items.COBBLESTONE, 32));
        a.run(new GoToTask(a.abs(30, FEET, 20), 1.5), 600, () -> a.near(30, FEET, 20, 1.6));
    }

    /** Walking, interrupted by something in between (a reflex): the walk goes on afterwards. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void walkResumes(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "walk_resumes");
        a.spawn(4, FEET, 4);
        h.runAfterDelay(30, () -> a.bot().interject(new net.kasax.challengecraft.bot.task.WaitTask(1)));
        a.run(new net.kasax.challengecraft.bot.task.GoToTask(a.abs(34, FEET, 34), 1), 400, () -> a.near(34, FEET, 34, 1.6));
    }

    /** A hundred and eighty blocks off over land never loaded before, round a wall and over a channel, and back: in legs. */
    @GameTest(structure = STRUCTURE, maxTicks = 6000, skyAccess = true, padding = 200)
    public void farWalk(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "far_walk");
        // A causeway nine wide out of the arena (the test world is empty round it), with a wall
        // to get round and a channel to swim on the way.
        a.fill(40, GROUND - 3, 16, 215, GROUND, 24, Blocks.STONE);
        a.fill(40, GROUND, 16, 215, GROUND, 24, Blocks.GRASS_BLOCK);
        a.fill(40, FEET, 16, 40, FEET + 24, 24, Blocks.AIR); // (the test's barrier wall: a door in it)
        a.fill(120, FEET, 16, 121, FEET + 3, 22, Blocks.STONE);
        a.fill(160, GROUND - 1, 16, 161, GROUND, 24, Blocks.WATER);
        a.spawn(20, FEET, 20, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.STONE_PICKAXE));
        var home = a.abs(20, FEET, 20);
        var away = a.abs(200, FEET, 20);
        boolean[] there = {false};
        a.run(new net.kasax.challengecraft.bot.BotTask() {
            final net.kasax.challengecraft.bot.task.FarWalk walk = new net.kasax.challengecraft.bot.task.FarWalk();

            @Override
            public Result tick(net.kasax.challengecraft.bot.Bot bot) {
                var s = walk.tick(bot, there[0] ? home : away, 6);
                if (s == net.kasax.challengecraft.bot.task.FarWalk.Status.FAILED) return Result.FAILED;
                if (s == net.kasax.challengecraft.bot.task.FarWalk.Status.ARRIVED) {
                    if (there[0]) return Result.DONE;
                    there[0] = true;
                }
                return Result.RUNNING;
            }

            @Override
            public String describe() {
                return "far and back" + (there[0] ? " (on the way back)" : "");
            }
        }, 6000, () -> there[0] && a.bot().body().blockPosition().closerThan(home, 8));
    }

    /** Under water in a shaft with a lid of dirt (swimming up gets nowhere): dug out, alive. */
    @GameTest(structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void drowningUnderLid(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "drowning_under_lid");
        a.fill(19, FEET, 19, 21, FEET, 21, Blocks.STONE);
        a.fill(19, FEET + 1, 19, 21, FEET + 2, 21, Blocks.DIRT);
        a.fill(20, GROUND, 20, 20, FEET, 20, Blocks.WATER);
        a.spawn(20, GROUND, 20);
        var body = a.bot().body();
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 900, () -> {
            // (The same body all along: drowned and respawned does not count.)
            if (a.bot().body() != body || body.getHealth() <= 10) h.fail("drowned or nearly: " + body.getHealth());
            return h.getTick() > 600 && !body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        });
    }
}
