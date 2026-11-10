package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotParams;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.task.ObtainTask;
import net.kasax.challengecraft.bot.task.SequenceTask;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * Stopwatch tests of the kit trip and the food (round 2): each scene played the new way within a
 * time budget and the same scene the old way (its knob off, a wider budget), both logging their
 * ticks ({@code [BOTTEST] ... passed in N ticks}) - the gain stands in every run of the suite, and
 * the old-way tests run on older builds as well (an unknown knob is no knob there).
 *
 * <ul>
 *   <li>{@code vein_sweep}: a vein of eight iron in a rock face and twenty-four cobblestone out of
 *   the same face - the next block in reach broken before the drops are picked up, all of them in
 *   one go after ({@code veinfirst}).</li>
 *   <li>{@code smelt_and_go}: twelve raw iron (the board wants them all), the shield wanted now, then
 *   thirty-two cobblestone and an iron pickaxe - the one ingot for the shield waited for, the rest
 *   left cooking while the stone is mined, the pickaxe's three fetched from the furnace after
 *   ({@code smeltgo}).</li>
 * </ul>
 */
public class BotKitTests {
    private static void veinSweep(GameTestHelper h, String name, String knobs, int budget) {
        BotArena a = BotArena.flat(h, name);
        // A rock face beside the start, a vein of eight iron showing in it (two by two by two).
        a.fill(24, FEET, 12, 29, FEET + 3, 28, Blocks.STONE);
        a.fill(24, FEET, 19, 25, FEET + 1, 20, Blocks.IRON_ORE);
        Bot bot = a.spawn(21, FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE));
        bot.params = BotParams.parse(knobs);
        var body = bot.body();
        a.run(new SequenceTask("iron and stone", List.of(
                        () -> new ObtainTask(Set.of(Items.RAW_IRON), 8),
                        () -> new ObtainTask(Set.of(Items.COBBLESTONE), 24))), budget,
                () -> ObtainPlanner.countAny(body, Set.of(Items.RAW_IRON)) >= 8 && ObtainPlanner.countAny(body, Set.of(Items.COBBLESTONE)) >= 24
                        && bot.current() == null);
    }

    /** Eight iron and twenty-four cobblestone from a face at hand, the drops picked up after each run of blocks in reach. */
    @GameTest(structure = STRUCTURE, maxTicks = 1400, skyAccess = true, padding = 8)
    public void veinSweep(GameTestHelper h) {
        veinSweep(h, "vein_sweep", "-", 1400);
    }

    /** The same the old way (each block's drops picked up before the next block), for the comparison. */
    @GameTest(structure = STRUCTURE, maxTicks = 2000, skyAccess = true, padding = 8)
    public void veinSweepOldWay(GameTestHelper h) {
        veinSweep(h, "vein_sweep_old_way", "veinfirst=0", 2000);
    }

    private static void smeltAndGo(GameTestHelper h, String name, String knobs, int budget) {
        BotArena a = BotArena.flat(h, name);
        // A rock face a dozen blocks from the start: the stone the kit wants.
        a.fill(30, FEET, 12, 33, FEET + 3, 28, Blocks.STONE);
        Bot bot = a.spawn(14, FEET, 20, new ItemStack(Items.RAW_IRON, 12), new ItemStack(Items.COAL, 2), new ItemStack(Items.OAK_PLANKS, 8),
                new ItemStack(Items.FURNACE), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 16));
        bot.params = BotParams.parse(knobs);
        var planner = new ObtainPlanner();
        // (The board wants all twelve: the bucket, the shears, the compass...)
        planner.boardDemand.put(Items.RAW_IRON, 12);
        var body = bot.body();
        a.run(new SequenceTask("shield, stone, pickaxe", List.of(
                        () -> new ObtainTask(Set.of(Items.SHIELD), 1, planner),
                        () -> new ObtainTask(Set.of(Items.COBBLESTONE), 32, planner),
                        () -> new ObtainTask(Set.of(Items.IRON_PICKAXE), 1, planner))), budget,
                () -> ObtainPlanner.countAny(body, Set.of(Items.SHIELD)) > 0 && ObtainPlanner.countAny(body, Set.of(Items.IRON_PICKAXE)) > 0
                        && bot.current() == null);
    }

    /** The shield at once, the stone while the rest of the iron cooks, the pickaxe's ingots fetched after. */
    @GameTest(structure = STRUCTURE, maxTicks = 2000, skyAccess = true, padding = 8)
    public void smeltAndGo(GameTestHelper h) {
        smeltAndGo(h, "smelt_and_go", "-", 2000);
    }

    /** The same the old way (all twelve smelted and waited for before the shield), for the comparison. */
    @GameTest(structure = STRUCTURE, maxTicks = 3000, skyAccess = true, padding = 8)
    public void smeltAndGoOldWay(GameTestHelper h) {
        smeltAndGo(h, "smelt_and_go_old_way", "smeltgo=0", 3000);
    }
}
