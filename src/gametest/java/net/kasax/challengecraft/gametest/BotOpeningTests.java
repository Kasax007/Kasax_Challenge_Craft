package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.BotParams;
import net.kasax.challengecraft.bot.OpeningClock;
import net.kasax.challengecraft.bot.lockout.LockoutBrain;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.task.ObtainTask;
import net.kasax.challengecraft.bot.task.SequenceTask;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * Stopwatch tests of the opening: each scene is played the new way within a time budget, and the
 * same scene the old way (the knob off) with a budget wide enough for it - both log their ticks
 * ({@code [BOTTEST] ... passed in N ticks}), so the gain stands in every run of the suite. The
 * old-way tests run on older builds as well (an unknown knob is no knob there): the same file
 * measures a build from before these changes.
 *
 * <ul>
 *   <li>{@code hillside_stone}: the first stone from rock showing twenty blocks off, not from a
 *   staircase through nine levels of soil ({@code rocksight}).</li>
 *   <li>{@code cook_and_mine}: four beef cooked and sixteen cobblestone - the stone mined beside
 *   the furnace while it cooks ({@code meanwhile}).</li>
 *   <li>{@code opening_kit}: the whole opening of the Lockout brain on hard, from nothing: the four
 *   stone tools, a stock of food and the shield ({@code fastopen}, {@code meanwhile}, {@code rocksight}).</li>
 * </ul>
 */
public class BotOpeningTests {
    /** The knobs of the old way, for the reference runs. */
    private static final String OLD_WAY = "fastopen=0,meanwhile=0,rocksight=0,interleave=0";

    /** An oak with a trunk of five and a crown. */
    private static void tree(BotArena a, int x, int z) {
        a.fill(x - 2, FEET + 3, z - 2, x + 2, FEET + 5, z + 2, Blocks.OAK_LEAVES);
        a.fill(x, FEET, z, x, FEET + 4, z, Blocks.OAK_LOG);
    }

    /**
     * The flat arena with nine levels of soil over the rock (a plain, a savanna), a few trees by
     * the start and a bare stone knoll some twenty blocks off, out of reach of a look round from
     * the trees - a player sees the grey from where he stands.
     */
    private static BotArena soilAndKnoll(GameTestHelper h, String name) {
        BotArena a = BotArena.flat(h, name);
        a.fill(0, GROUND - 9, 0, BotArena.SIZE - 1, GROUND - 1, BotArena.SIZE - 1, Blocks.DIRT);
        a.fill(31, FEET, 16, 36, FEET + 2, 24, Blocks.STONE);
        tree(a, 6, 16);
        tree(a, 7, 25);
        return a;
    }

    private static void hillsideStone(GameTestHelper h, String name, String knobs, int budget) {
        BotArena a = soilAndKnoll(h, name);
        Bot bot = a.spawn(9, FEET, 20);
        bot.params = BotParams.parse(knobs);
        a.run(new ObtainTask(Set.of(Items.STONE_PICKAXE), 1), budget,
                () -> ObtainPlanner.countAny(bot.body(), Set.of(Items.STONE_PICKAXE)) > 0);
    }

    /** From nothing to a stone pickaxe, the stone from the knoll in sight: under 40 s (measured 25 s; the old way 49 s). */
    @GameTest(structure = STRUCTURE, maxTicks = 800, skyAccess = true, padding = 8)
    public void hillsideStone(GameTestHelper h) {
        hillsideStone(h, "hillside_stone", "-", 800);
    }

    /** The same the old way (a staircase down through the soil), for the comparison. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void hillsideStoneOldWay(GameTestHelper h) {
        hillsideStone(h, "hillside_stone_old_way", OLD_WAY, 2400);
    }

    private static void cookAndMine(GameTestHelper h, String name, String knobs, int budget) {
        BotArena a = BotArena.flat(h, name);
        // A rock face beside the start: the stone the kit wants is a few steps from the furnace.
        a.fill(23, FEET, 14, 26, FEET + 2, 26, Blocks.STONE);
        Bot bot = a.spawn(20, FEET, 20, new ItemStack(Items.BEEF, 4), new ItemStack(Items.COAL, 2), new ItemStack(Items.FURNACE),
                new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.STONE_PICKAXE));
        bot.params = BotParams.parse(knobs);
        var body = bot.body();
        a.run(new SequenceTask("cook and mine", List.of(
                        () -> new ObtainTask(Set.of(Items.COOKED_BEEF), 4),
                        () -> new ObtainTask(Set.of(Items.COBBLESTONE), 16))), budget,
                () -> ObtainPlanner.countAny(body, Set.of(Items.COOKED_BEEF)) >= 4 && ObtainPlanner.countAny(body, Set.of(Items.COBBLESTONE)) >= 16
                        && bot.current() == null);
    }

    /**
     * Four beef in the furnace and sixteen cobblestone wanted: mined while it cooks, done in about
     * the cooking time (measured 43 s; the old way 63 s).
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1050, skyAccess = true, padding = 8)
    public void cookAndMine(GameTestHelper h) {
        cookAndMine(h, "cook_and_mine", "-", 1050);
    }

    /** The same the old way (stood at the furnace, then the stone), for the comparison. */
    @GameTest(structure = STRUCTURE, maxTicks = 1800, skyAccess = true, padding = 8)
    public void cookAndMineOldWay(GameTestHelper h) {
        cookAndMine(h, "cook_and_mine_old_way", OLD_WAY, 1800);
    }

    /**
     * The opening of a Lockout game on hard, the brain playing (its one tile is out of reach, so
     * only the opening counts): trees by the start, soil over the rock, a knoll with iron and coal
     * showing in its face, cows and pigs about. Done when the four stone tools, a stock of food
     * and the shield are in the pack; the stopwatch's milestones go to the log.
     */
    private static void openingKit(GameTestHelper h, String name, String knobs, int budget) {
        BotArena a = BotArena.wide(h, name);
        BotArena.day(h);
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        // (Rock under the soil out in the padding too: a staircase that leaves the arena still ends in stone.)
        a.fill(-20, GROUND - 14, -20, BotArena.SIZE + 19, GROUND - 10, BotArena.SIZE + 19, Blocks.STONE);
        a.fill(-20, GROUND - 9, -20, BotArena.SIZE + 19, GROUND - 1, BotArena.SIZE + 19, Blocks.DIRT);
        a.fill(31, FEET, 12, 37, FEET + 3, 28, Blocks.STONE);
        a.fill(31, FEET + 1, 18, 31, FEET + 1, 20, Blocks.IRON_ORE);
        a.fill(31, FEET, 23, 31, FEET + 1, 24, Blocks.COAL_ORE);
        tree(a, 6, 14);
        tree(a, 6, 22);
        tree(a, 10, 29);
        for (int i = 0; i < 5; i++) {
            var animal = (i < 3 ? EntityTypes.COW : EntityTypes.PIG).create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
            animal.setPos(Vec3.atBottomCenterOf(a.abs(14 + 3 * i, FEET, 6 + (i % 2) * 28)));
            animal.setPersistenceRequired();
            h.getLevel().addFreshEntity(animal);
        }
        Bot bot = a.spawn(12, FEET, 20);
        bot.params = BotParams.parse(knobs);
        // A game of its own, its one tile out of reach here (no village): the opening is all there is to do.
        String unreachable = "trade_with_cleric";
        Chal_40_LockoutBingo.startSoloDebugRun(bot.body(), List.of(unreachable));
        bot.setBrain(new LockoutBrain(LockoutBrain.Difficulty.HARD).only(Set.of(unreachable)));
        OpeningClock clock = new OpeningClock(bot);
        var body = bot.body();
        Set<net.minecraft.world.item.Item> tools = Set.of(Items.STONE_PICKAXE, Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_SHOVEL);
        h.succeedWhen(() -> {
            // (The check runs every tick until it passes: the stopwatch ticks with it.)
            if (BotManager.all().contains(bot)) clock.tick(false);
            if (h.getTick() % 600 == 0) BotArena.LOG.info("[BOTTEST] {} t={} milestones {} | {}", name, h.getTick(), clock.milestones(), bot.status());
            boolean ok = tools.stream().allMatch(t -> ObtainPlanner.countAny(body, Set.of(t)) > 0)
                    && (ObtainPlanner.countAny(body, Set.of(Items.SHIELD)) > 0 || body.getOffhandItem().is(Items.SHIELD))
                    && net.kasax.challengecraft.bot.task.EatTask.foodPoints(body) >= 8;
            if (!ok && h.getTick() >= budget - 2) {
                BotArena.LOG.info("[BOTTEST] {} FAILED after {} ticks: milestones {} ({})", name, h.getTick(), clock.milestones(), bot.status());
                end(h, bot);
            }
            h.assertTrue(ok, name + ": not yet " + clock.milestones());
            BotArena.LOG.info("[BOTTEST] {} passed in {} ticks: milestones {} | stopwatch {}", name, h.getTick(), clock.milestones(), clock.json());
            end(h, bot);
        });
    }

    /**
     * The scene's game over and gone: the brain off, the board cleared (a game left "started"
     * refuses the lobby's next bot - the lobby test after this batch), Lockout off, the bot out.
     */
    private static void end(GameTestHelper h, Bot bot) {
        if (!BotManager.all().contains(bot)) return;
        bot.setBrain(null);
        var server = h.getLevel().getServer();
        var data = net.kasax.challengecraft.data.LockoutBingoSavedData.get(server.overworld());
        data.resetForRun(data.getRunId());
        Chal_40_LockoutBingo.setActive(false);
        BotManager.remove(server, bot);
    }

    /**
     * The whole opening on hard, the new way: within 200 s (measured 146 s; the old way took 353 s,
     * the stone pickaxe at 320 s against 77 s).
     */
    // (A batch of its own: a Lockout game is on for the whole server while it runs.)
    @GameTest(environment = "challengecraft:opening", structure = STRUCTURE, maxTicks = 4000, skyAccess = true, padding = 24)
    public void openingKit(GameTestHelper h) {
        openingKit(h, "opening_kit", "-", 4000);
    }

    /** The same the old way, for the comparison. */
    @GameTest(environment = "challengecraft:opening_old", structure = STRUCTURE, maxTicks = 9600, skyAccess = true, padding = 24)
    public void openingKitOldWay(GameTestHelper h) {
        openingKit(h, "opening_kit_old_way", OLD_WAY, 9600);
    }
}
