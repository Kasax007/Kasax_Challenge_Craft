package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.task.LavaPortalTask;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * A portal cast at a lava pool, every time (no second attempt): pools of all shapes, room dug
 * where there is none, the water never let run into the pool.
 */
public class BotPortalTests {
    private static ItemStack[] kit(ItemStack... more) {
        ItemStack[] base = {new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.FLINT_AND_STEEL),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.DIRT, 24), new ItemStack(Items.COBBLESTONE, 8)};
        ItemStack[] all = java.util.Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return all;
    }

    private static void castAndGo(GameTestHelper h, BotArena a, int maxTicks) {
        a.run(new LavaPortalTask(h.getLevel(), null), maxTicks, () -> a.bot().body().level().dimension() == Level.NETHER);
    }

    /** A pool sunk in a meadow (4 x 4): a frame beside it, the wall behind built up. */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void lavaCastMeadow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lava_cast_meadow");
        a.fill(24, GROUND, 4, 27, GROUND, 7, Blocks.LAVA);
        a.spawn(20, FEET, 20, kit());
        castAndGo(h, a, 3600);
    }

    /** Just enough lava (2 x 5): not one bucket wasted. */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void lavaCastSmall(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lava_cast_small");
        a.fill(18, GROUND, 14, 19, GROUND, 18, Blocks.LAVA);
        a.spawn(20, FEET, 26, kit());
        castAndGo(h, a, 3600);
    }

    /** At the foot of a stone hill: the frame dug into the hill (its back wall for free). */
    @GameTest(structure = STRUCTURE, maxTicks = 4800, skyAccess = true, padding = 8)
    public void lavaCastHill(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lava_cast_hill");
        a.fill(0, FEET, 0, 39, FEET + 7, 11, Blocks.STONE);
        a.fill(14, GROUND, 12, 19, GROUND, 15, Blocks.LAVA);
        a.spawn(20, FEET, 24, kit());
        castAndGo(h, a, 4800);
    }

    /** In a cave, no flat room anywhere near: room dug out of the rock, then the frame. */
    @GameTest(structure = STRUCTURE, maxTicks = 6000, skyAccess = true, padding = 8)
    public void lavaCastCave(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lava_cast_cave");
        a.fill(0, FEET, 0, 39, FEET + 6, 39, Blocks.STONE);
        a.fill(22, FEET, 2, 29, FEET + 3, 9, Blocks.AIR);
        a.fill(19, FEET, 9, 21, FEET + 2, 21, Blocks.AIR);
        a.fill(24, GROUND, 4, 27, GROUND, 7, Blocks.LAVA);
        a.spawn(20, FEET, 20, kit());
        castAndGo(h, a, 6000);
    }

    /**
     * A ragged pool (two deep in the middle, a lava stream running off one side) with a river
     * close by on the other: the frame where no fluid is next to anything dug.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 4800, skyAccess = true, padding = 8)
    public void lavaCastRagged(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lava_cast_ragged");
        a.fill(12, GROUND, 12, 17, GROUND, 16, Blocks.LAVA);
        a.fill(14, GROUND - 1, 13, 15, GROUND - 1, 15, Blocks.LAVA);
        a.fill(13, GROUND, 17, 14, GROUND, 18, Blocks.LAVA);
        a.fill(16, GROUND, 11, 16, GROUND, 11, Blocks.LAVA);
        // The river, three blocks off the pool's east side.
        a.fill(21, GROUND, 0, 22, GROUND, 39, Blocks.WATER);
        // Uneven ground: a few mounds and a step.
        a.fill(8, FEET, 8, 10, FEET + 1, 20, Blocks.DIRT);
        a.fill(12, FEET, 20, 18, FEET, 23, Blocks.GRASS_BLOCK);
        a.spawn(15, FEET + 1, 22, kit());
        castAndGo(h, a, 4800);
    }

    /** Flowing lava all round the pool's edge (a fresh spill): the pool's sources only. */
    @GameTest(structure = STRUCTURE, maxTicks = 4800, skyAccess = true, padding = 8)
    public void lavaCastSpill(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lava_cast_spill");
        a.fill(16, GROUND, 16, 19, GROUND, 19, Blocks.LAVA);
        // Raised a block: the lava runs over the edge onto the grass round it.
        a.fill(15, FEET, 15, 20, FEET, 15, Blocks.STONE);
        a.fill(15, FEET, 20, 20, FEET, 20, Blocks.STONE);
        a.fill(16, FEET, 16, 19, FEET, 19, Blocks.LAVA);
        a.spawn(30, FEET, 30, kit());
        castAndGo(h, a, 4800);
    }

    /** A meadow full of flowers (no block goes in over a flower), crafting tables standing about. */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void lavaCastFlowers(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lava_cast_flowers");
        a.fill(1, FEET, 1, 38, FEET, 38, Blocks.POPPY);
        for (int i = 4; i < 36; i += 5) a.fill(i, FEET, 12, i, FEET, 12, Blocks.CRAFTING_TABLE);
        a.fill(24, GROUND, 4, 27, GROUND, 7, Blocks.LAVA);
        a.fill(24, FEET, 4, 27, FEET, 7, Blocks.AIR);
        a.fill(20, FEET, 20, 20, FEET, 20, Blocks.AIR);
        a.spawn(20, FEET, 20, kit());
        // (The first site holds: no mould given up on over a flower.)
        var task = new LavaPortalTask(h.getLevel(), null);
        a.run(task, 3600, () -> {
            if (task.sitesGivenUp() > 0) h.fail("gave up on a site");
            return a.bot().body().level().dimension() == Level.NETHER;
        });
    }
}
