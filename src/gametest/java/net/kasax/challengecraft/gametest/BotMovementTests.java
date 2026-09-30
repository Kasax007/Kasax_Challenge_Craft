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
}
