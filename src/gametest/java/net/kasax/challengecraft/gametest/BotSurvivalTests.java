package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.learn.CoverPolicy;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.task.MineTask;
import net.kasax.challengecraft.bot.task.ObtainTask;
import net.kasax.challengecraft.bot.task.WaitTask;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * The deaths of the opening, the kit trip and the first night (round 2 of the league work), each
 * as a scene built after the self-play games it happened in, on hard:
 *
 * <ul>
 *   <li>{@code drown_flooded_stairs}, {@code drown_lake_stone}: under water with a roof over the
 *   head (a staircase dug from a lake's bottom, flooded) and the first stone wanted from a lake -
 *   five drownings in forty bot-games of the new code, none in forty of the old.</li>
 *   <li>{@code unarmed_zombies_night}: two zombies at night and no weapon yet, a few blocks in the
 *   pack - the pillar ran out of blocks, the hole was dug too slowly.</li>
 *   <li>{@code skeletons_down_the_stairs}: the staircase down to the shield's iron opens onto two
 *   skeletons in the dark, no shield yet.</li>
 *   <li>{@code zombie_trio_model}, {@code spider_pack_model}: the learnt cover model choosing for
 *   the guardian - it ran from three zombies the rules fight (10 to 17 hearts lost against 4.5),
 *   and it took to a pillar or a run with a spider at it (four of four such choices died).</li>
 * </ul>
 */
public class BotSurvivalTests {

    private static void command(GameTestHelper h, String cmd) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    /** A monster at this spot after the bot, with a helmet (no burning in the test's daylight) and kept. */
    private static <T extends Mob> T mob(GameTestHelper h, BotArena a, EntityType<T> type, int x, int y, int z) {
        T m = type.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        m.setPos(Vec3.atBottomCenterOf(a.abs(x, y, z)));
        m.setPersistenceRequired();
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        if (m instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        h.getLevel().addFreshEntity(m);
        return m;
    }

    /**
     * Builds a scene as the drowning log prints it (see AirTask.scene): {@code layers} from the
     * top down, each its rows north to south separated by '|', each row west to east; the middle
     * of the middle row is {@code (cx, cz)}, the first layer at {@code top}. '#' and 'W' stone,
     * '~' still water, ',' and '.' and 'F'/'H' air (flowing water comes back by itself from the
     * still water), 'o' air too.
     */
    static void scene(BotArena a, int cx, int top, int cz, String... layers) {
        for (int i = 0; i < layers.length; i++) {
            String[] rows = layers[i].split("\\|");
            int r = rows.length / 2;
            for (int dz = -r; dz <= r; dz++) {
                String row = rows[dz + r];
                for (int dx = -r; dx <= r; dx++) {
                    char c = row.charAt(dx + r);
                    var state = c == '#' || c == 'W' ? Blocks.STONE.defaultBlockState()
                            : c == '~' ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    a.fill(cx + dx, top - i, cz + dz, cx + dx, top - i, cz + dz, state);
                }
            }
        }
    }

    // ---- under water ------------------------------------------------------------------------------

    /**
     * A lake (water y 10 to 15 over x 6..17) and its east bank of soil and stone, and a staircase
     * dug from the lake's bottom into the bank, five steps down, flooded: the bot at its foot with
     * five seconds of air left - the way out is back up the steps and up through the lake; the roof
     * over the head is eight blocks of the bank. (League 9 and the knowledge league: "up for air:
     * dig instead" under such a roof, the spot unchanged, drowned.)
     */
    @GameTest(environment = "challengecraft:survival_water", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void drownFloodedStairs(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "drown_flooded_stairs");
        command(h, "difficulty hard");
        a.fill(0, GROUND - 6, 0, BotArena.SIZE - 1, GROUND - 1, BotArena.SIZE - 1, Blocks.DIRT);
        a.fill(6, 10, 10, 17, GROUND, 24, Blocks.WATER);
        a.fill(6, 9, 10, 17, 9, 24, Blocks.STONE);
        // The staircase: from feet (16, 10) on the lake's floor, step k at x 16+k, y 10-k .. 12-k.
        for (int k = 1; k <= 5; k++) a.fill(16 + k, 10 - k, 17, 16 + k, 12 - k, 17, Blocks.WATER);
        Bot bot = a.spawn(21, 5, 17, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 12));
        var body = bot.body();
        body.setAirSupply(100);
        boolean[] under = {false};
        a.run(new WaitTask(700), 880, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "drown_flooded_stairs: drowned");
            if (h.getTick() % 40 == 0) BotArena.LOG.info("[BOTTEST] drown_flooded_stairs air {} hp {} at {} ({})", body.getAirSupply(), body.getHealth(), a.feet().toShortString(), bot.status());
            // (Not before the first tick has seen it under: the eye's fluid is only known from then.)
            boolean eyeIn = body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
            under[0] |= eyeIn;
            boolean out = under[0] && !eyeIn;
            if (out) BotArena.LOG.info("[BOTTEST] drown_flooded_stairs breathing at {} after {} ticks, hp {}", a.feet().toShortString(), h.getTick(), body.getHealth());
            return out;
        });
    }

    /**
     * The drowning of seed -3882... as the log printed it (AirTask.scene, the repro of league 9 on
     * the base): seven deep in a lake by its bank, the bank's top block reaching one block out over
     * the head at the water's surface, the open lake one block east; a few seconds of air left.
     * Over to the open water and up - not digging at the ledge afloat (five times slower under
     * water, five again off the ground), the counter of a dig that gets nowhere reset by every bob
     * up and down: so it drowned three times in one league game, and in the repro again.
     */
    @GameTest(environment = "challengecraft:survival_water", structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void drownUnderTheLedge(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "drown_under_the_ledge");
        command(h, "difficulty hard");
        // y 65 .. 55 of the scene at y 18 .. 8 of the arena: the water's surface (y 62) at the grass's height.
        scene(a, 20, 18, 20,
                "#######....|######.....|######.....|#####....##|#####....##|####.....##|####.....##|###o......#|######.....|######.....|######.....",
                "########...|#######....|#######....|######.....|######.....|#####......|#####......|####o......|####.......|####.......|####.......",
                "########...|########...|#######....|######.....|######.....|#####......|#####......|#####......|####.......|####.......|####......#",
                "########~~~|########~~~|#######~~~~|######~~~~~|######~~~~~|######~~~~~|#####~~~~~~|#####~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~#",
                "########~~~|#######~~~~|#######~~~~|######~~~~~|######~~~~~|#####~~~~~~|#####~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~",
                "########~~~|#######~~~~|#######~~~~|######~~~~~|######~~~~~|#####~~~~~~|#####~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~",
                "########~~~|#######~~~~|#######~~~~|######~~~~~|######~~~~~|#####~~~~~~|#####~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~",
                "########~~~|########~~~|#######~~~~|######~~~~~|######~~~~~|#####~~~~~~|#####~~~~~~|#####~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~",
                "########~~~|########~~~|#######~~~~|######~~~~~|######~~~~~|#####~~~~~~|#####~~~~~~|#####~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~",
                "###########|###########|###########|#######~~~#|######~~~~~|#####~~~~~~|#####~~~~~~|#####~~~~~~|####~~~~~~~|####~~~~~~~|####~~~~~~~",
                "###########|###########|###########|###########|###########|######~~~~#|#####~~~~~#|#####~~~~~#|####~~~~~~~|####~~~~~~~|####~~~~~~~");
        Bot bot = a.spawn(20, 11, 20, new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.OAK_PLANKS, 6));
        var body = bot.body();
        body.setAirSupply(160);
        boolean[] under = {false};
        a.run(new WaitTask(500), 580, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "drown_under_the_ledge: drowned");
            if (h.getTick() % 20 == 0) BotArena.LOG.info("[BOTTEST] drown_under_the_ledge air {} hp {} at {} ({})", body.getAirSupply(), body.getHealth(), a.feet().toShortString(), bot.status());
            boolean eyeIn = body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
            under[0] |= eyeIn;
            boolean out = under[0] && !eyeIn;
            if (out) BotArena.LOG.info("[BOTTEST] drown_under_the_ledge breathing at {} after {} ticks, hp {}", a.feet().toShortString(), h.getTick(), body.getHealth());
            return out;
        });
    }

    /**
     * The same staircase under the lake, dug dry and then filled from the lake as it is in a game -
     * flowing water down the steps, not still water - with the bot at its foot when the water comes:
     * the air runs out under eight blocks of bank with no way back it knows of (it was dry when it
     * came down). Up the steps and the lake, or the water walled off round its two blocks so that it
     * runs off and leaves air; alive and breathing.
     */
    @GameTest(environment = "challengecraft:survival_water", structure = STRUCTURE, maxTicks = 1000, skyAccess = true, padding = 8)
    public void drownFlowingStairs(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "drown_flowing_stairs");
        command(h, "difficulty hard");
        a.fill(0, GROUND - 6, 0, BotArena.SIZE - 1, GROUND - 1, BotArena.SIZE - 1, Blocks.DIRT);
        a.fill(6, 10, 10, 17, GROUND, 24, Blocks.WATER);
        a.fill(6, 9, 10, 17, 9, 24, Blocks.STONE);
        // The staircase as dug: from feet (16, 10) on the lake's floor, step k at x 16+k, y 10-k ..
        // 12-k - carved dry, the lake's water runs down it.
        for (int k = 1; k <= 5; k++) a.fill(16 + k, 10 - k, 17, 16 + k, 12 - k, 17, Blocks.AIR);
        Bot bot = a.spawn(21, 5, 17, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 12));
        var body = bot.body();
        boolean[] under = {false};
        a.run(new WaitTask(900), 980, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "drown_flowing_stairs: drowned");
            boolean eyeIn = body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
            if (eyeIn && !under[0]) BotArena.LOG.info("[BOTTEST] drown_flowing_stairs under water at tick {}", h.getTick());
            under[0] |= eyeIn;
            if (h.getTick() % 40 == 0) BotArena.LOG.info("[BOTTEST] drown_flowing_stairs air {} hp {} at {} ({})", body.getAirSupply(), body.getHealth(), a.feet().toShortString(), bot.status());
            // Done once it was under and breathes again, with the air back to half.
            boolean ok = under[0] && !eyeIn && body.getAirSupply() >= body.getMaxAirSupply() / 2;
            if (ok) BotArena.LOG.info("[BOTTEST] drown_flowing_stairs breathing at {} after {} ticks, hp {}", a.feet().toShortString(), h.getTick(), body.getHealth());
            return ok;
        });
    }

    /**
     * Stone wanted (a wooden pickaxe in hand, sticks and planks), the bot afloat in the middle of a
     * lake six deep whose banks are soil all round: the stone dug from dry ground, never a staircase
     * from the water (league 9: "no rock at hand: a staircase down" out of a lake on seed -3882...,
     * drowned there three times in one game). Passes with a stone pickaxe, never having been down
     * to a third of its air.
     */
    @GameTest(environment = "challengecraft:survival_water", structure = STRUCTURE, maxTicks = 3000, skyAccess = true, padding = 8)
    public void drownLakeStone(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "drown_lake_stone");
        command(h, "difficulty hard");
        a.fill(0, GROUND - 9, 0, BotArena.SIZE - 1, GROUND - 1, BotArena.SIZE - 1, Blocks.DIRT);
        a.fill(12, GROUND - 6, 12, 27, GROUND, 27, Blocks.WATER);
        a.fill(12, GROUND - 7, 12, 27, GROUND - 7, 27, Blocks.STONE);
        Bot bot = a.spawn(20, GROUND, 20, new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.OAK_PLANKS, 8), new ItemStack(Items.STICK, 4),
                new ItemStack(Items.CRAFTING_TABLE));
        var body = bot.body();
        int[] lowest = {body.getMaxAirSupply()};
        a.run(new ObtainTask(Set.of(Items.STONE_PICKAXE), 1, new ObtainPlanner()), 2980, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "drown_lake_stone: drowned");
            lowest[0] = Math.min(lowest[0], body.getAirSupply());
            if (h.getTick() % 100 == 0) BotArena.LOG.info("[BOTTEST] drown_lake_stone air {} (lowest {}) hp {} at {}", body.getAirSupply(), lowest[0], body.getHealth(), a.feet().toShortString());
            h.assertTrue(lowest[0] >= body.getMaxAirSupply() / 3, "drown_lake_stone: down to " + lowest[0] + " air");
            return ObtainPlanner.countAny(body, Set.of(Items.STONE_PICKAXE)) > 0;
        });
    }

    /**
     * At nightfall in a wide river (four deep, the near bank four blocks north), seven hearts and
     * nothing in hand, a drowned at him from the west: ashore and away on land, not "away" east
     * through seventeen blocks of water where the drowned swims faster than he does (a solo opening
     * bench of the base on seed 2582...: three such runs through the river, struck down by it).
     */
    @GameTest(environment = "challengecraft:survival_river", structure = STRUCTURE, maxTicks = 800, skyAccess = true, padding = 44)
    public void drownedInTheRiver(GameTestHelper h) {
        BotArena a = BotArena.wide(h, "drowned_in_the_river");
        command(h, "difficulty hard");
        command(h, "time set 13000");
        a.fill(-20, GROUND - 4, 14, BotArena.SIZE + 19, GROUND - 4, BotArena.SIZE + 19, Blocks.STONE);
        a.fill(-20, GROUND - 3, 14, BotArena.SIZE + 19, GROUND, BotArena.SIZE + 19, Blocks.WATER);
        Bot bot = a.spawn(20, GROUND - 1, 18);
        var body = bot.body();
        body.setHealth(7);
        var drowned = mob(h, a, EntityTypes.DROWNED, 14, GROUND - 2, 18);
        a.run(new WaitTask(700), 780, () -> {
            if (drowned.isAlive() && drowned.getTarget() != body) drowned.setTarget(body);
            h.assertTrue(a.bot().body() == body && body.isAlive(), "drowned_in_the_river: killed");
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] drowned_in_the_river hp {} in water {} at {}", body.getHealth(), body.isInWater(), a.feet().toShortString());
            if (h.getTick() < 700) return false;
            BotArena.LOG.info("[BOTTEST] drowned_in_the_river alive with {} health, in the water {}", body.getHealth(), body.isInWater());
            BotArena.day(h);
            return true;
        });
    }

    /**
     * Stone wanted at the bottom of a lake nine deep, soil all round and under it (no rock within
     * reach of a look): out and up onto the bank for the staircase - not the staircase begun down
     * there, nor "a few blocks away" to a spot inside the lake's floor, tunnelled to under the water
     * (seed -3882..., league 9, the knowledge league and a solo bench of the base: the same three
     * steps every time - "a staircase down", "can't dig on here", "go to" four blocks below the
     * feet - and drowned under the tunnel's roof). Passes with three cobblestone, alive, never
     * down to a quarter of its air.
     */
    @GameTest(environment = "challengecraft:survival_water", structure = STRUCTURE, maxTicks = 3000, skyAccess = true, padding = 8)
    public void drownLakeBottomStairs(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "drown_lake_bottom_stairs");
        command(h, "difficulty hard");
        a.fill(0, GROUND - 11, 0, BotArena.SIZE - 1, GROUND - 1, BotArena.SIZE - 1, Blocks.DIRT);
        a.fill(14, GROUND - 8, 14, 25, GROUND, 25, Blocks.WATER);
        Bot bot = a.spawn(20, GROUND - 8, 20, new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.OAK_PLANKS, 8), new ItemStack(Items.STICK, 4));
        var body = bot.body();
        int[] lowest = {body.getMaxAirSupply()};
        var rock = net.minecraft.world.level.block.Blocks.STONE;
        a.run(new MineTask("cobblestone", s -> s.is(rock), Set.of(Items.COBBLESTONE), 3, 0), 2980, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "drown_lake_bottom_stairs: drowned");
            lowest[0] = Math.min(lowest[0], body.getAirSupply());
            if (h.getTick() % 100 == 0) BotArena.LOG.info("[BOTTEST] drown_lake_bottom_stairs air {} (lowest {}) hp {} at {} ({})", body.getAirSupply(), lowest[0],
                    body.getHealth(), a.feet().toShortString(), bot.status());
            h.assertTrue(lowest[0] >= body.getMaxAirSupply() / 4, "drown_lake_bottom_stairs: down to " + lowest[0] + " air");
            boolean done = ObtainPlanner.countAny(body, Set.of(Items.COBBLESTONE)) >= 3;
            if (done) BotArena.LOG.info("[BOTTEST] drown_lake_bottom_stairs cobblestone after {} ticks, lowest air {}", h.getTick(), lowest[0]);
            return done;
        });
    }

    // ---- no weapon at night -------------------------------------------------------------------------

    /**
     * Night on hard, two zombies coming, a stone pickaxe and no weapon yet, ten cobblestone (two to
     * spare: the rest is the furnace's), room all round: away from them at a run (they are half as
     * fast), not a pillar that runs out of blocks halfway up nor a hole dug with them at its edge
     * (seed -987...: the pillar failed, dug in on a slope, dead in the first two minutes).
     */
    @GameTest(environment = "challengecraft:survival_night", structure = STRUCTURE, maxTicks = 1000, skyAccess = true, padding = 44)
    public void unarmedZombiesNight(GameTestHelper h) {
        BotArena a = BotArena.wide(h, "unarmed_zombies_night");
        command(h, "difficulty hard");
        command(h, "time set 14000");
        a.fill(-20, GROUND - 4, -20, BotArena.SIZE + 19, GROUND - 3, BotArena.SIZE + 19, Blocks.STONE);
        Bot bot = a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 10), new ItemStack(Items.OAK_PLANKS, 2));
        var body = bot.body();
        List<Mob> zombies = new ArrayList<>();
        zombies.add(mob(h, a, EntityTypes.ZOMBIE, 26, FEET, 20));
        zombies.add(mob(h, a, EntityTypes.ZOMBIE, 25, FEET, 24));
        float[] low = {body.getHealth()};
        a.run(new WaitTask(900), 980, () -> {
            for (var z : zombies) if (z.isAlive() && z.getTarget() != body) z.setTarget(body);
            h.assertTrue(a.bot().body() == body && body.isAlive(), "unarmed_zombies_night: killed");
            low[0] = Math.min(low[0], body.getHealth());
            if (20 - low[0] > 9) h.fail("unarmed_zombies_night: lost " + (20 - low[0]) + " health");
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] unarmed_zombies_night hp {} guardian {}/{}/{}/{} at {}", body.getHealth(),
                    bot.guardian.boxes, bot.guardian.pillars, bot.guardian.digs, bot.guardian.runs, a.feet().toShortString());
            if (h.getTick() < 900) return false;
            BotArena.LOG.info("[BOTTEST] unarmed_zombies_night lost {} health", 20 - low[0]);
            BotArena.day(h);
            return true;
        });
    }

    // ---- skeletons on the way down before the shield ------------------------------------------------

    /**
     * The shield's iron down a staircase (a stone sword, no shield, no armour): the steps open onto
     * a dark room with two skeletons, iron in its far wall. Alive at the end, not shot to pieces in
     * the one-wide stairs (league 9, seed -2038...: down to the iron at y 40, two skeletons, the box
     * no help - dead).
     */
    @GameTest(environment = "challengecraft:survival_stairs", structure = STRUCTURE, maxTicks = 1300, skyAccess = true, padding = 24)
    public void skeletonsDownTheStairs(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "skeletons_down_the_stairs");
        command(h, "difficulty hard");
        // The staircase: from the surface at x 8 down to y 8 at x 16 (one wide, z 20), a roof of stone.
        for (int k = 0; k <= 8; k++) a.fill(8 + k, FEET - k, 20, 8 + k, FEET + 2 - k, 20, Blocks.AIR);
        // The room at its foot, dark under eight blocks of rock: x 17..30, y 8..11, z 15..25.
        a.fill(17, 8, 15, 30, 11, 25, Blocks.AIR);
        a.fill(31, 8, 18, 31, 10, 22, Blocks.IRON_ORE);
        List<Mob> skeletons = new ArrayList<>();
        skeletons.add(mob(h, a, EntityTypes.SKELETON, 27, 8, 18));
        skeletons.add(mob(h, a, EntityTypes.SKELETON, 28, 8, 23));
        Bot bot = a.spawn(7, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_AXE),
                new ItemStack(Items.COBBLESTONE, 24), new ItemStack(Items.BREAD, 6));
        var body = bot.body();
        float[] low = {body.getHealth()};
        var iron = net.minecraft.world.level.block.Blocks.IRON_ORE;
        a.run(new MineTask("raw_iron", s -> s.is(iron), Set.of(Items.RAW_IRON), 3, null, a.abs(31, 9, 20)).knownOnly(), 1280, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "skeletons_down_the_stairs: shot dead");
            low[0] = Math.min(low[0], body.getHealth());
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] skeletons_down_the_stairs hp {} skeletons {} at {} ({})", body.getHealth(),
                    skeletons.stream().filter(Mob::isAlive).count(), a.feet().toShortString(), bot.status());
            if (20 - low[0] > 12) h.fail("skeletons_down_the_stairs: lost " + (20 - low[0]) + " health");
            if (h.getTick() < 1200) return false;
            BotArena.LOG.info("[BOTTEST] skeletons_down_the_stairs lost {} health, iron {}", 20 - low[0], ObtainPlanner.countAny(body, Set.of(Items.RAW_IRON)));
            return true;
        });
    }

    // ---- the learnt cover model -----------------------------------------------------------------

    /**
     * The brawl of three zombies (brawl_zombie_trio) with a stone sword, the learnt cover model
     * choosing for the guardian (whatever -PbotLearned says): no run while armed with more than
     * half the hearts - they are slower, they keep coming, and the fight after the run is had
     * bunched up (the model ran there and lost 10 to 17 hearts, the rules 4.5 standing).
     */
    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void zombieTrioModel(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "zombie_trio_model");
        command(h, "difficulty hard");
        List<Mob> mobs = new ArrayList<>();
        mobs.add(mob(h, a, EntityTypes.ZOMBIE, 26, FEET, 18));
        mobs.add(mob(h, a, EntityTypes.ZOMBIE, 26, FEET, 22));
        mobs.add(mob(h, a, EntityTypes.ZOMBIE, 24, FEET, 26));
        Bot bot = a.spawn(16, FEET, 20, new ItemStack(Items.STONE_SWORD));
        bot.cover.set(CoverPolicy.Mode.MODEL, 1, "cover.json");
        var body = bot.body();
        float[] low = {body.getHealth()};
        int[] runs = {0};
        a.run(new net.kasax.challengecraft.bot.task.KillTask(Set.of(EntityTypes.ZOMBIE), Set.of(), 0, 3), 1500, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "zombie_trio_model: killed");
            // (A run chosen this tick: only when down to half the hearts.)
            if (bot.guardian.runs > runs[0] && body.getHealth() > 10) h.fail("zombie_trio_model: ran from them armed, with " + body.getHealth() + " health");
            runs[0] = bot.guardian.runs;
            low[0] = Math.min(low[0], body.getHealth());
            boolean done = mobs.stream().noneMatch(Mob::isAlive);
            if (done) BotArena.LOG.info("[BOTTEST] zombie_trio_model lost {} health, guardian runs {} fights {}", 20 - low[0], bot.guardian.runs, bot.guardian.fights);
            return done;
        });
    }

    /**
     * At night a spider and two zombies at him, a stone sword, iron helmet and boots, four blocks
     * of dirt, the learnt model choosing: not up a pillar to fight from (the spider climbs it) nor a
     * run (it is quicker) - the league's deaths with a spider among them (four of four times the
     * model took a pillar, a run or a hole there, he died). The choice is the test: it ends a few
     * seconds after the guardian first stepped in.
     */
    @GameTest(environment = "challengecraft:survival_night2", structure = STRUCTURE, maxTicks = 800, skyAccess = true, padding = 44)
    public void spiderPackModel(GameTestHelper h) {
        BotArena a = BotArena.wide(h, "spider_pack_model");
        command(h, "difficulty hard");
        command(h, "time set 14000");
        Bot bot = a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.DIRT, 4), new ItemStack(Items.BREAD, 4));
        bot.cover.set(CoverPolicy.Mode.MODEL, 1, "cover.json");
        var body = bot.body();
        body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        body.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        List<Mob> mobs = new ArrayList<>();
        var spider = mob(h, a, EntityTypes.SPIDER, 25, FEET, 20);
        mobs.add(spider);
        mobs.add(mob(h, a, EntityTypes.ZOMBIE, 20, FEET, 25));
        mobs.add(mob(h, a, EntityTypes.ZOMBIE, 16, FEET, 23));
        long[] stepped = {-1};
        a.run(new WaitTask(700), 780, () -> {
            for (var m : mobs) if (m.isAlive() && m.getTarget() != body) m.setTarget(body);
            h.assertTrue(a.bot().body() == body && body.isAlive(), "spider_pack_model: killed");
            if (spider.isAlive() && (bot.guardian.pillars > 0 || bot.guardian.runs > 0))
                h.fail("spider_pack_model: a pillar or a run with a spider at him (pillars " + bot.guardian.pillars + ", runs " + bot.guardian.runs + ")");
            if (stepped[0] < 0 && bot.guardian.inEpisode()) stepped[0] = h.getTick();
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] spider_pack_model hp {} guardian {}/{}/{}/{}/{} mobs {}", body.getHealth(),
                    bot.guardian.boxes, bot.guardian.pillars, bot.guardian.digs, bot.guardian.runs, bot.guardian.fights, mobs.stream().filter(Mob::isAlive).count());
            if (stepped[0] < 0 || h.getTick() < stepped[0] + 100) return false;
            BotArena.LOG.info("[BOTTEST] spider_pack_model guardian in at tick {}: boxes {} pillars {} digs {} runs {} fights {}, {} health", stepped[0],
                    bot.guardian.boxes, bot.guardian.pillars, bot.guardian.digs, bot.guardian.runs, bot.guardian.fights, body.getHealth());
            for (var m : mobs) m.discard();
            BotArena.day(h);
            return true;
        });
    }
}
