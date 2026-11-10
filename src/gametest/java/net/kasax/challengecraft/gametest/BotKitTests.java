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
 *   <li>{@code opening_kit_before_kit}: the opening scene of {@code BotOpeningTests#openingKit}
 *   (the four stone tools, a stock of food and the shield on hard, the Lockout brain playing) with
 *   this round's knobs off - against {@code opening_kit}, which has them on.</li>
 *   <li>{@code copper_armour}: on hard with the shield in hand, the Lockout brain's copper armour
 *   - a copper vein in the rock face beside the start: the chestplate's eight ingots waited for,
 *   the helmet's five left cooking while the chestplate is made, then fetched ({@code smeltgo}).</li>
 *   <li>{@code shield_walk_slow}: twenty blocks behind the raised shield - with {@code itemslow} at
 *   the item's use speed (a fifth), as a player's client has it; {@code shield_walk_fast} the
 *   same without (the server never slowed a bot so).</li>
 *   <li>{@code treasure_iron}: on hard, the shield wanted and no iron about, a buried treasure the
 *   world sense knows of thirty blocks off: dug up (a chest under the sand, block nine-nine of its
 *   chunk), its iron made into the shield ({@code treasureiron}).</li>
 *   <li>{@code staircase_pace}: a staircase twenty-six levels down through solid stone to a layer
 *   of iron ore - the pace of the way down to the ore, in ticks a level (logged).</li>
 *   <li>{@code mountain_iron}: on a mountain of solid stone (its top at y 125), iron ore in its rock
 *   a dozen blocks off at the height of the feet and a layer of it at y 14 far below: the
 *   tunnel a few blocks under the feet ({@code mountainiron}), against the staircase down a hundred
 *   levels ({@code mountain_iron_old_way}).</li>
 * </ul>
 */
public class BotKitTests {
    /** This round's knobs off: the kit trip and the food as before. */
    static final String BEFORE_KIT = "veinfirst=0,smeltgo=0,cookalong=0,foodsight=0,mountainiron=0";

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

    /**
     * The Lockout brain on hard, the opening done and the shield in hand (its one tile out of
     * reach): copper armour from a vein in the face beside the start. Done when a chestplate and
     * a helmet are worn.
     */
    private static void copperArmour(GameTestHelper h, String name, String knobs, int budget) {
        BotArena a = BotArena.wide(h, name);
        BotArena.day(h);
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        a.fill(28, BotArena.FEET, 10, 33, BotArena.FEET + 3, 30, Blocks.STONE);
        a.fill(28, BotArena.FEET, 18, 28, BotArena.FEET + 1, 21, Blocks.COPPER_ORE);
        a.fill(29, BotArena.FEET, 19, 29, BotArena.FEET + 1, 20, Blocks.COPPER_ORE);
        Bot bot = a.spawn(22, BotArena.FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD),
                new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.SHIELD), new ItemStack(Items.COOKED_BEEF, 8),
                new ItemStack(Items.COBBLESTONE, 40), new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.COAL, 4),
                new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.FURNACE));
        bot.params = BotParams.parse(knobs);
        String unreachable = "trade_with_cleric";
        net.kasax.challengecraft.challenges.Chal_40_LockoutBingo.startSoloDebugRun(bot.body(), List.of(unreachable));
        bot.setBrain(new net.kasax.challengecraft.bot.lockout.LockoutBrain(net.kasax.challengecraft.bot.lockout.LockoutBrain.Difficulty.HARD)
                .only(Set.of(unreachable)));
        var body = bot.body();
        h.succeedWhen(() -> {
            boolean ok = body.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.COPPER_CHESTPLATE)
                    && body.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.COPPER_HELMET);
            if (h.getTick() % 400 == 0) BotArena.LOG.info("[BOTTEST] {} t={} | {}", name, h.getTick(), bot.status());
            if (!ok && h.getTick() >= budget - 2) {
                BotArena.LOG.info("[BOTTEST] {} FAILED after {} ticks ({})", name, h.getTick(), bot.status());
                endGame(h, bot);
            }
            h.assertTrue(ok, name + ": not yet");
            BotArena.LOG.info("[BOTTEST] {} passed in {} ticks", name, h.getTick());
            endGame(h, bot);
        });
    }

    /** The game over and gone (see BotOpeningTests#end). */
    private static void endGame(GameTestHelper h, Bot bot) {
        if (!net.kasax.challengecraft.bot.BotManager.all().contains(bot)) return;
        bot.setBrain(null);
        var server = h.getLevel().getServer();
        var data = net.kasax.challengecraft.data.LockoutBingoSavedData.get(server.overworld());
        data.resetForRun(data.getRunId());
        net.kasax.challengecraft.challenges.Chal_40_LockoutBingo.setActive(false);
        net.kasax.challengecraft.bot.BotManager.remove(server, bot);
    }

    /**
     * The Lockout brain on hard with its stone tools, no shield and no iron in sight; a buried
     * treasure (three iron ingots in its chest) known to the world sense: dug up for the shield.
     */
    @GameTest(environment = "challengecraft:kit_treasure", structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 24)
    public void treasureIron(GameTestHelper h) {
        String name = "treasure_iron";
        BotArena a = BotArena.wide(h, name);
        BotArena.day(h);
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        // The chest at block nine-nine of its chunk, three under a patch of sand (a beach's).
        var near = a.abs(32, BotArena.GROUND, 20);
        var column = new net.minecraft.core.BlockPos((near.getX() & ~15) + 9, near.getY(), (near.getZ() & ~15) + 9);
        for (var p : net.minecraft.core.BlockPos.betweenClosed(column.offset(-3, -4, -3), column.offset(3, 0, 3))) h.getLevel().setBlock(p, Blocks.SAND.defaultBlockState(), 2);
        var chestAt = column.below(3);
        h.getLevel().setBlock(chestAt, Blocks.CHEST.defaultBlockState(), 2);
        if (h.getLevel().getBlockEntity(chestAt) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
            chest.setItem(1, new ItemStack(Items.HEART_OF_THE_SEA));
        }
        Bot bot = a.spawn(8, BotArena.FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_AXE),
                new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.COOKED_BEEF, 8), new ItemStack(Items.COBBLESTONE, 40),
                new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.CRAFTING_TABLE));
        String unreachable = "trade_with_cleric";
        net.kasax.challengecraft.challenges.Chal_40_LockoutBingo.startSoloDebugRun(bot.body(), List.of(unreachable));
        bot.setBrain(new net.kasax.challengecraft.bot.lockout.LockoutBrain(net.kasax.challengecraft.bot.lockout.LockoutBrain.Difficulty.HARD)
                .only(Set.of(unreachable)));
        bot.senses().know(net.minecraft.resources.Identifier.withDefaultNamespace("buried_treasure"), column.above(), bot.body().blockPosition());
        var body = bot.body();
        h.succeedWhen(() -> {
            boolean ok = ObtainPlanner.countAny(body, Set.of(Items.SHIELD)) > 0 || body.getOffhandItem().is(Items.SHIELD);
            if (h.getTick() % 400 == 0) BotArena.LOG.info("[BOTTEST] {} t={} | {}", name, h.getTick(), bot.status());
            if (!ok && h.getTick() >= 2398) {
                BotArena.LOG.info("[BOTTEST] {} FAILED after {} ticks ({})", name, h.getTick(), bot.status());
                endGame(h, bot);
            }
            h.assertTrue(ok, name + ": not yet");
            BotArena.LOG.info("[BOTTEST] {} passed in {} ticks", name, h.getTick());
            endGame(h, bot);
        });
    }

    /** Down a staircase of twenty-six levels through solid stone to a layer of iron: logs the ticks a level. */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void staircasePace(GameTestHelper h) {
        String name = "staircase_pace";
        BotArena a = BotArena.flat(h, name);
        int top = 30;
        a.fill(0, BotArena.GROUND, 0, BotArena.SIZE - 1, top - 1, BotArena.SIZE - 1, Blocks.STONE);
        a.fill(0, 2, 0, BotArena.SIZE - 1, 2, BotArena.SIZE - 1, Blocks.IRON_ORE);
        Bot bot = a.spawn(20, top, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.BREAD, 8));
        var body = bot.body();
        int depth = a.abs(0, 3, 0).getY();
        long[] start = {-1};
        a.run(new net.kasax.challengecraft.bot.task.MineTask("raw_iron", st -> st.is(Blocks.IRON_ORE), Set.of(Items.RAW_IRON), 1, depth), 3600, () -> {
            if (start[0] < 0) start[0] = h.getTick();
            boolean ok = ObtainPlanner.countAny(body, Set.of(Items.RAW_IRON)) >= 1;
            if (ok) BotArena.LOG.info("[BOTTEST] {} {} levels in {} ticks: {} ticks a level", name, top - 3, h.getTick(), h.getTick() / (top - 3));
            return ok;
        });
    }

    /**
     * A mountain of stone round the start, its top at y 125 (built up from the arena's floor),
     * four little iron veins in its rock twelve blocks off at the height of the feet less four,
     * and a layer of iron at y 14: the first raw iron, by the knobs given.
     */
    private static void mountainIron(GameTestHelper h, String name, String knobs, int budget) {
        BotArena a = BotArena.flat(h, name);
        int base = a.abs(0, 0, 0).getY();
        int top = 125 - base; // (relative: the mountain's top block)
        a.fill(8, BotArena.GROUND, 8, 32, top, 32, Blocks.STONE);
        // The layer at y 14 (or the arena's lowest stone, whichever is higher).
        int layer = Math.max(1, 14 - base);
        a.fill(8, layer, 8, 32, layer, 32, Blocks.IRON_ORE);
        // The veins at the tunnel's height (the feet at top + 1, the tunnel four lower).
        int vein = top + 1 - 4;
        for (int[] d : new int[][]{{12, 0}, {-12, 0}, {0, 12}, {0, -12}}) {
            a.fill(20 + d[0], vein, 20 + d[1], 20 + d[0] + Integer.signum(d[0]), vein + 1, 20 + d[1] + Integer.signum(d[1]), Blocks.IRON_ORE);
        }
        Bot bot = a.spawn(20, top + 1, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.BREAD, 16), new ItemStack(Items.COBBLESTONE, 32));
        bot.params = BotParams.parse(knobs);
        var body = bot.body();
        a.run(new ObtainTask(Set.of(Items.RAW_IRON), 1), budget, () -> ObtainPlanner.countAny(body, Set.of(Items.RAW_IRON)) >= 1);
    }

    /** On the mountain: the iron in its own rock, a short tunnel away. */
    @GameTest(environment = "challengecraft:kit_mountain", structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void mountainIron(GameTestHelper h) {
        mountainIron(h, "mountain_iron", "-", 2400);
    }

    /**
     * The same the old way: the staircase down to y 14, for the comparison. (Optional: its time
     * is the reference; a staircase of a hundred levels may not make it within the budget.)
     */
    @GameTest(environment = "challengecraft:kit_mountain", structure = STRUCTURE, maxTicks = 9000, skyAccess = true, padding = 8, required = false)
    public void mountainIronOldWay(GameTestHelper h) {
        mountainIron(h, "mountain_iron_old_way", "mountainiron=0", 9000);
    }

    /** Copper armour, a piece at a time from one batch: the second piece's ingots cook while the first is made. */
    @GameTest(environment = "challengecraft:kit_copper", structure = STRUCTURE, maxTicks = 3000, skyAccess = true, padding = 24)
    public void copperArmour(GameTestHelper h) {
        copperArmour(h, "copper_armour", "-", 3000);
    }

    /** The same the old way (all thirteen ingots waited for), for the comparison. */
    @GameTest(environment = "challengecraft:kit_copper_before", structure = STRUCTURE, maxTicks = 4000, skyAccess = true, padding = 24)
    public void copperArmourOldWay(GameTestHelper h) {
        copperArmour(h, "copper_armour_old_way", "smeltgo=0", 4000);
    }

    /** Twenty blocks with the shield up; passes on arrival, logging the ticks (and, slowed, failing if it was not slowed). */
    private static void shieldWalk(GameTestHelper h, String name, String knobs, int budget, int atLeast) {
        BotArena a = BotArena.flat(h, name);
        Bot bot = a.spawn(8, FEET, 20, new ItemStack(Items.BREAD, 8));
        var body = bot.body();
        body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        bot.params = BotParams.parse(knobs);
        var go = new net.kasax.challengecraft.bot.task.GoToTask(a.abs(28, FEET, 20), 1);
        long[] start = {-1};
        a.run(new net.kasax.challengecraft.bot.BotTask() {
            @Override
            public Result tick(Bot b) {
                if (start[0] < 0) start[0] = h.getTick();
                if (!body.isUsingItem()) body.gameMode.useItem(body, body.level(), body.getOffhandItem(), net.minecraft.world.InteractionHand.OFF_HAND);
                return go.tick(b);
            }

            @Override
            public String describe() {
                return "walk behind the shield";
            }
        }, budget, () -> {
            if (!a.near(28, FEET, 20, 1.6)) return false;
            h.assertTrue(h.getTick() - start[0] >= atLeast, name + ": not slowed (" + (h.getTick() - start[0]) + " ticks)");
            return true;
        });
    }

    @GameTest(structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void shieldWalkSlow(GameTestHelper h) {
        shieldWalk(h, "shield_walk_slow", "itemslow=1", 900, 300);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void shieldWalkFast(GameTestHelper h) {
        shieldWalk(h, "shield_walk_fast", "itemslow=0", 300, 0);
    }

    /** The whole opening on hard (BotOpeningTests' scene) with this round's knobs off, for the comparison with opening_kit. */
    // (A batch of its own: a Lockout game is on for the whole server while it runs.)
    @GameTest(environment = "challengecraft:kit_opening_before", structure = STRUCTURE, maxTicks = 6000, skyAccess = true, padding = 24)
    public void openingKitBeforeKit(GameTestHelper h) {
        BotOpeningTests.openingKit(h, "opening_kit_before_kit", BEFORE_KIT, 6000);
    }
}
