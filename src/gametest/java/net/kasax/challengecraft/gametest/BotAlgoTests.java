package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotParams;
import net.kasax.challengecraft.bot.belief.Beliefs;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.task.GoToTask;
import net.kasax.challengecraft.bot.task.KillTask;
import net.kasax.challengecraft.bot.task.ObtainTask;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * Round 3's algorithms, each scene played with its knob on and off (the knob set on the test's own
 * bot, so a run with -PbotKnobs leaves these as they are). The "on" test asserts the new way's
 * point (the safe way, the shorter search, the faster walk); the "off" test only has to arrive.
 * Both log one line {@code [BOTTEST] algo <scene> <knob>=<v> ticks=.. hurt=.. ...} - the numbers
 * the round's report compares over three runs.
 *
 * <ul>
 *   <li>{@code risk_*} (knob riskpath, BotRisk): a skeleton beside the short way, a causeway
 *   between lava, a dive along a lake's floor, and a plain course (the cost on open ground).</li>
 *   <li>{@code motion_*} (knob motionsearch, MotionSearch): stopwatch courses - flat, a hill of
 *   steps, a forest, a ditch and a drop - blocks a second with it on and off.</li>
 *   <li>{@code beliefs_*} (knob beliefs, belief.Beliefs): the estimates by context (unit-style),
 *   iron in rock laid out as the 26.3 placement spreads it (three layouts), a cow across a river
 *   and one further off on this side.</li>
 * </ul>
 */
public class BotAlgoTests {

    private static void command(GameTestHelper h, String cmd) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    /** What a run took: ticks, hearts lost (every drop counted), how close it came to a spot. */
    static final class Record {
        final String scene;
        final String knob;
        float lastHp = -1;
        double hurt;
        double closest = Double.MAX_VALUE;
        int maxAirUnder;

        Record(String scene, String knob) {
            this.scene = scene;
            this.knob = knob;
        }

        void tick(Bot bot, Vec3 watch) {
            float hp = bot.body().getHealth();
            if (lastHp >= 0 && hp < lastHp) hurt += lastHp - hp;
            lastHp = hp;
            if (watch != null) closest = Math.min(closest, bot.body().position().distanceTo(watch));
            maxAirUnder = Math.max(maxAirUnder, 300 - bot.body().getAirSupply());
        }

        boolean logged;

        void log(GameTestHelper h, String extra) {
            if (logged) return;
            logged = true;
            BotArena.LOG.info("[BOTTEST] algo {} {} ticks={} hurt={} closest={} air_used={} {}", scene, knob, h.getTick(),
                    String.format("%.1f", hurt), closest == Double.MAX_VALUE ? "-" : String.format("%.1f", closest), maxAirUnder, extra);
        }
    }

    private static Bot knobbed(Bot bot, String knobs) {
        bot.params = BotParams.parse(knobs);
        return bot;
    }

    /** A monster that stays where it is put (no walking), with a helmet against the sun. */
    private static <T extends Mob> T fixedMob(GameTestHelper h, BotArena a, EntityType<T> type, int x, int y, int z) {
        T m = type.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        m.setPos(Vec3.atBottomCenterOf(a.abs(x, y, z)));
        m.setPersistenceRequired();
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        if (m instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        h.getLevel().addFreshEntity(m);
        return m;
    }

    // ---- riskpath: danger in the way's price ------------------------------------------------------

    /**
     * A wall four high across the wide arena (x 19..20, z -14..53) and two skeletons (bows,
     * helmets, rooted, on the watch) just behind it beside the straight way: the short way digs
     * through the wall under their noses and walks off with its back to them; the way round the
     * wall's end is some forty blocks longer. With the risk model the way keeps out of their
     * range (they notice a player within sixteen blocks).
     */
    private void riskSkeleton(GameTestHelper h, int knob) {
        String name = "risk_skeleton_" + (knob == 1 ? "on" : "off");
        BotArena a = BotArena.wide(h, name);
        BotArena.day(h);
        command(h, "difficulty hard");
        a.fill(19, FEET, -14, 20, FEET + 3, 53, Blocks.STONE);
        var s1 = fixedMob(h, a, net.minecraft.world.entity.EntityTypes.SKELETON, 23, FEET, 17);
        var s2 = fixedMob(h, a, net.minecraft.world.entity.EntityTypes.SKELETON, 23, FEET, 23);
        Bot bot = knobbed(a.spawn(2, FEET, 20, new ItemStack(Items.STONE_PICKAXE)), "riskpath=" + knob);
        Record r = new Record("risk_skeleton", "riskpath=" + knob);
        Vec3 at = Vec3.atBottomCenterOf(a.abs(23, FEET, 20));
        double wallEast = a.abs(21, 0, 0).getX();
        a.run(new GoToTask(a.abs(37, FEET, 20), 1.5), 1300, () -> {
            // (How close it came to them in their sight: east of the wall.)
            r.tick(bot, bot.body().getX() >= wallEast ? at : null);
            boolean arrived = a.near(37, FEET, 20, 1.6);
            // (The old way may end in a hole, hidden from the two that never leave: logged at the
            // end all the same - its numbers are what the comparison needs.)
            boolean done = arrived || knob == 0 && h.getTick() >= 1290;
            if (done) {
                r.log(h, arrived ? "arrived" : "not_arrived");
                s1.discard();
                s2.discard();
                if (knob == 1) h.assertTrue(r.closest >= 10, name + ": came within " + r.closest + " of the skeletons");
            }
            return done;
        });
    }

    @GameTest(environment = "challengecraft:algo_risk", structure = STRUCTURE, maxTicks = 1300, skyAccess = true, padding = 44)
    public void riskSkeletonOn(GameTestHelper h) {
        riskSkeleton(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_risk", structure = STRUCTURE, maxTicks = 1300, skyAccess = true, padding = 44)
    public void riskSkeletonOff(GameTestHelper h) {
        riskSkeleton(h, 0);
    }

    /**
     * A causeway a block wide between two lava pools (x 10..28) across the wide arena, the way
     * round them some fifty blocks longer: with the risk model the way round (a slip there is
     * death), without it the causeway (eight ticks a step dearer, the old flat extra).
     */
    private void riskLava(GameTestHelper h, int knob) {
        String name = "risk_lava_" + (knob == 1 ? "on" : "off");
        BotArena a = BotArena.wide(h, name);
        BotArena.day(h);
        a.fill(10, GROUND, -12, 28, GROUND, 19, Blocks.LAVA);
        a.fill(10, GROUND, 21, 28, GROUND, 52, Blocks.LAVA);
        Bot bot = knobbed(a.spawn(2, FEET, 20), "riskpath=" + knob);
        Record r = new Record("risk_lava", "riskpath=" + knob);
        Vec3 mid = Vec3.atBottomCenterOf(a.abs(19, FEET, 20));
        a.run(new GoToTask(a.abs(37, FEET, 20), 1.5), 1600, () -> {
            r.tick(bot, mid);
            boolean done = a.near(37, FEET, 20, 1.6);
            if (done) {
                r.log(h, "");
                if (knob == 1) h.assertTrue(r.closest >= 8, name + ": took the causeway (" + r.closest + ")");
            }
            return done;
        });
    }

    @GameTest(environment = "challengecraft:algo_risk", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 44)
    public void riskLavaOn(GameTestHelper h) {
        riskLava(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_risk", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 44)
    public void riskLavaOff(GameTestHelper h) {
        riskLava(h, 0);
    }

    /**
     * A lake eight deep and sixty long (x -14..49), the bot on its floor at one end (come down for
     * something there, air full) and a spot on the floor at the other end to reach: along the
     * floor is the shortest way, but sixty blocks under water is past the air; with the risk model
     * the way goes up for breath.
     */
    private void riskDive(GameTestHelper h, int knob) {
        String name = "risk_dive_" + (knob == 1 ? "on" : "off");
        BotArena a = BotArena.wide(h, name);
        BotArena.day(h);
        a.fill(-15, GROUND - 8, 16, 50, GROUND, 24, Blocks.STONE);
        a.fill(-14, GROUND - 7, 17, 49, GROUND, 23, Blocks.WATER);
        Bot bot = knobbed(a.spawn(-12, GROUND - 7, 20), "riskpath=" + knob);
        Record r = new Record("risk_dive", "riskpath=" + knob);
        BlockPos goal = a.abs(47, GROUND - 7, 20);
        a.run(new GoToTask(goal, 1.5), 2000, () -> {
            r.tick(bot, null);
            boolean done = bot.body().blockPosition().closerThan(goal, 1.8);
            if (done) {
                r.log(h, "");
                if (knob == 1) h.assertTrue(r.maxAirUnder < 300, name + ": out of air on the way (" + r.maxAirUnder + ")");
            }
            return done;
        });
    }

    @GameTest(environment = "challengecraft:algo_risk", structure = STRUCTURE, maxTicks = 2000, skyAccess = true, padding = 44)
    public void riskDiveOn(GameTestHelper h) {
        riskDive(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_risk", structure = STRUCTURE, maxTicks = 2000, skyAccess = true, padding = 44)
    public void riskDiveOff(GameTestHelper h) {
        riskDive(h, 0);
    }

    /** Seventy-five blocks of open flat ground by day: what the risk model costs where there is no danger. */
    private void riskPlain(GameTestHelper h, int knob) {
        String name = "risk_plain_" + (knob == 1 ? "on" : "off");
        BotArena a = BotArena.wide(h, name);
        BotArena.day(h);
        Bot bot = knobbed(a.spawn(-18, FEET, 20), "riskpath=" + knob);
        Record r = new Record("risk_plain", "riskpath=" + knob);
        a.run(new GoToTask(a.abs(57, FEET, 20), 1.5), 900, () -> {
            r.tick(bot, null);
            boolean done = a.near(57, FEET, 20, 1.6);
            if (done) r.log(h, "");
            return done;
        });
    }

    @GameTest(environment = "challengecraft:algo_risk", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 44)
    public void riskPlainOn(GameTestHelper h) {
        riskPlain(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_risk", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 44)
    public void riskPlainOff(GameTestHelper h) {
        riskPlain(h, 0);
    }

    // ---- beliefs: the world model ---------------------------------------------------------------

    /**
     * The estimates react to the context (no play, figures only): iron from the plains' surface,
     * from a mountain's, with a cave known at its height; logs with all the country in view read
     * and no tree in it - desert, plains - and with nothing read; the planner's estimate for raw
     * iron with an iron ore in sight and with none.
     */
    @GameTest(environment = "challengecraft:algo_beliefs", structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void beliefsEstimates(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "beliefs_estimates");
        double expose = Beliefs.exposePerSecond(0.56);
        var plains = Beliefs.ore("iron", new BlockPos(0, 65, 0), 66, 3.0, expose, null);
        var mountain = Beliefs.ore("iron", new BlockPos(0, 150, 0), 152, 3.0, expose, null);
        var cave = Beliefs.ore("iron", new BlockPos(0, 65, 0), 66, 3.0, expose, new BlockPos(30, 22, 0));
        double old = 55 + (65 - 14) * 3.0;
        int view = 12;
        Beliefs desert = new Beliefs(), plainsRead = new Beliefs(), unread = new Beliefs();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                desert.pretend(dx, dz, "desert", 0);
                plainsRead.pretend(dx, dz, "plains", 0);
            }
        }
        BlockPos o = new BlockPos(8, 65, 8);
        double logsDesert = desert.logs(h.getLevel(), o, view).seconds(), logsPlains = plainsRead.logs(h.getLevel(), o, view).seconds(),
                logsUnread = unread.logs(h.getLevel(), o, view).seconds();
        Bot bot = knobbed(a.spawn(4, FEET, 4, new ItemStack(Items.STONE_PICKAXE)), "beliefs=1");
        double none = new ObtainPlanner().estimate(bot, java.util.Set.of(Items.RAW_IRON), 1);
        var feet = bot.body().blockPosition();
        BotArena.LOG.info("[BOTTEST] beliefs_estimates context: feet {} surface {} cave {} stone {}", feet.toShortString(),
                h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()),
                bot.memory().cave(h.getLevel(), feet, 16, 24, 128, java.util.List.of()), bot.tools().breakTicks(Blocks.STONE.defaultBlockState()));
        bot.params = BotParams.parse("beliefs=0");
        double noneOld = new ObtainPlanner().estimate(bot, java.util.Set.of(Items.RAW_IRON), 1);
        bot.params = BotParams.parse("beliefs=1");
        a.fill(12, FEET, 12, 12, FEET, 12, Blocks.IRON_ORE);
        double seen = new ObtainPlanner().estimate(bot, java.util.Set.of(Items.RAW_IRON), 1);
        BotArena.LOG.info("[BOTTEST] algo beliefs_estimates iron: plains {}s at y{} (old flat {}s at y14), mountain {}s at y{}, cave known {}s at y{}"
                        + " | logs: all read desert {}s, all read plains {}s, nothing read {}s | planner raw iron: seen {}s, none {}s (old way {}s)",
                (int) plains.seconds(), plains.y(), (int) old, (int) mountain.seconds(), mountain.y(), (int) cave.seconds(), cave.y(),
                (int) logsDesert, (int) logsPlains, (int) logsUnread, (int) seen, (int) none, (int) noneOld);
        h.assertTrue(mountain.seconds() < plains.seconds() / 2, "mountain not shorter: " + mountain + " vs " + plains);
        h.assertTrue(cave.seconds() < plains.seconds(), "cave not shorter: " + cave + " vs " + plains);
        h.assertTrue(logsDesert > 2 * logsPlains && logsDesert > 120, "desert logs not long: " + logsDesert + " vs " + logsPlains);
        h.assertTrue(seen < none / 3, "seen iron not short: " + seen + " vs " + none);
        a.bot().body().discard();
        h.succeed();
    }

    /**
     * Rock from y 4 to y 64 with iron laid out as the 26.3 placement spreads it (veins of five at
     * the density of the priors, a seeded layout, none in the outer three blocks), the bot on top
     * (y 65, a plains start) with stone pickaxes: one raw iron. The world model digs where it
     * believes best from here; the old way to y 14. Only logged: the finds are mostly luck.
     */
    private void beliefsIron(GameTestHelper h, int seed, int knob) {
        String name = "beliefs_iron_" + seed + "_" + (knob == 1 ? "on" : "off");
        BotArena a = BotArena.flat(h, name);
        BotArena.day(h);
        // (Whatever height the test put the arena at: rock from y 4 to y 64, as under a plains
        // start, the bot on top at y 65.)
        int y0 = a.abs(0, 0, 0).getY(), bottom = 4 - y0, top = 64 - y0;
        java.util.Random rng = new java.util.Random(seed * 7919L);
        // (A hundred and twenty wide: the staircase down moves a block a level, and the tunnel
        // at the depth must not run into the block's faces.)
        int lo = -40, hi = 79;
        a.fill(lo, bottom, lo, hi, top, hi, Blocks.STONE);
        a.fill(lo, top + 1, lo, hi, top + 4, hi, Blocks.AIR);
        int placed = 0;
        // (Not in the outer three blocks: the block of rock stands in the open, and ore in its
        // faces would be seen from outside - a cliff the real hills do not have on every side.)
        for (int ry = bottom + 3; ry < top; ry++) {
            double perLayer = Beliefs.density("iron", ry + y0) * (hi - lo - 5) * (hi - lo - 5) / 5;
            int veins = (int) perLayer + (rng.nextDouble() < perLayer % 1 ? 1 : 0);
            for (int v = 0; v < veins; v++) {
                int x = lo + 3 + rng.nextInt(hi - lo - 7), z = lo + 3 + rng.nextInt(hi - lo - 7);
                for (int k = 0; k < 5; k++) {
                    int oy = Math.min(top - 1, ry + rng.nextInt(2));
                    a.fill(x + rng.nextInt(2), oy, z + rng.nextInt(2), x + rng.nextInt(2), oy, z + rng.nextInt(2), Blocks.IRON_ORE);
                    placed++;
                }
            }
        }
        // (The kit of the kit tests' mountain iron: spare pickaxes, food, blocks - else the plan wants wood first.)
        Bot bot = knobbed(a.spawn(20, top + 1, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.BREAD, 16), new ItemStack(Items.COBBLESTONE, 32)), "beliefs=" + knob);
        Record r = new Record("beliefs_iron_" + seed, "beliefs=" + knob);
        int[] low = {Integer.MAX_VALUE};
        int ores = placed;
        a.run(new ObtainTask(java.util.Set.of(Items.RAW_IRON), 1), 9000, () -> {
            r.tick(bot, null);
            low[0] = Math.min(low[0], bot.body().getBlockY());
            boolean found = bot.body().getInventory().countItem(Items.RAW_IRON) >= 1;
            boolean done = found || h.getTick() >= 8990;
            if (done) r.log(h, (found ? "found" : "not_found") + " deepest_y=" + low[0] + " ores=" + ores);
            return done;
        });
    }

    @GameTest(environment = "challengecraft:algo_iron", structure = STRUCTURE, maxTicks = 9000, skyAccess = true, padding = 64)
    public void beliefsIron1On(GameTestHelper h) {
        beliefsIron(h, 1, 1);
    }

    @GameTest(environment = "challengecraft:algo_iron", structure = STRUCTURE, maxTicks = 9000, skyAccess = true, padding = 64)
    public void beliefsIron1Off(GameTestHelper h) {
        beliefsIron(h, 1, 0);
    }

    @GameTest(environment = "challengecraft:algo_iron", structure = STRUCTURE, maxTicks = 9000, skyAccess = true, padding = 64)
    public void beliefsIron2On(GameTestHelper h) {
        beliefsIron(h, 2, 1);
    }

    @GameTest(environment = "challengecraft:algo_iron", structure = STRUCTURE, maxTicks = 9000, skyAccess = true, padding = 64)
    public void beliefsIron2Off(GameTestHelper h) {
        beliefsIron(h, 2, 0);
    }

    @GameTest(environment = "challengecraft:algo_iron", structure = STRUCTURE, maxTicks = 9000, skyAccess = true, padding = 64)
    public void beliefsIron3On(GameTestHelper h) {
        beliefsIron(h, 3, 1);
    }

    @GameTest(environment = "challengecraft:algo_iron", structure = STRUCTURE, maxTicks = 9000, skyAccess = true, padding = 64)
    public void beliefsIron3Off(GameTestHelper h) {
        beliefsIron(h, 3, 0);
    }

    /** An animal that stays where it is put. */
    private static <T extends Mob> T rooted(GameTestHelper h, BotArena a, EntityType<T> type, int x, int y, int z) {
        T m = type.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        m.setPos(Vec3.atBottomCenterOf(a.abs(x, y, z)));
        m.setPersistenceRequired();
        m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        h.getLevel().addFreshEntity(m);
        return m;
    }

    /**
     * A river twenty-four wide (x 4..27) between the bot and a cow twenty-nine blocks east; another
     * cow thirty blocks south on this side. The nearer by the straight line is across the
     * water (eleven seconds of swimming and climbing out); the world model's travel time picks
     * the one on foot.
     */
    private void beliefsCows(GameTestHelper h, int knob) {
        String name = "beliefs_cows_" + (knob == 1 ? "on" : "off");
        BotArena a = BotArena.wide(h, name);
        BotArena.day(h);
        a.fill(4, GROUND - 3, -20, 27, GROUND - 3, 59, Blocks.STONE);
        a.fill(4, GROUND - 2, -20, 27, GROUND, 59, Blocks.WATER);
        var across = rooted(h, a, net.minecraft.world.entity.EntityTypes.COW, 30, FEET, 1);
        var near = rooted(h, a, net.minecraft.world.entity.EntityTypes.COW, 1, FEET, 31);
        Bot bot = knobbed(a.spawn(1, FEET, 1, new ItemStack(Items.STONE_SWORD)), "beliefs=" + knob);
        Record r = new Record("beliefs_cows", "beliefs=" + knob);
        a.run(new KillTask(java.util.Set.of(net.minecraft.world.entity.EntityTypes.COW), java.util.Set.of(), 1, 1), 1200, () -> {
            r.tick(bot, null);
            boolean done = !across.isAlive() || !near.isAlive();
            if (done) {
                r.log(h, "killed=" + (!near.isAlive() ? "this_side" : "across"));
                across.discard();
                near.discard();
                if (knob == 1) h.assertTrue(!near.isAlive(), name + ": went across the river");
            }
            return done;
        });
    }

    @GameTest(environment = "challengecraft:algo_beliefs", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 44)
    public void beliefsCowsOn(GameTestHelper h) {
        beliefsCows(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_beliefs", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 44)
    public void beliefsCowsOff(GameTestHelper h) {
        beliefsCows(h, 0);
    }

    // ---- motionsearch: movement by the game's physics -----------------------------------------------

    /** A course timed: from {@code from} to {@code to} (relative), the straight distance over the ticks. */
    private void course(GameTestHelper h, BotArena a, String scene, int knob, BlockPos from, BlockPos to, int maxTicks) {
        Bot bot = knobbed(a.spawn(from.getX(), from.getY(), from.getZ()), "motionsearch=" + knob);
        Record r = new Record(scene, "motionsearch=" + knob);
        double dist = Math.sqrt(from.distSqr(to));
        a.run(new GoToTask(a.abs(to.getX(), to.getY(), to.getZ()), 1.5), maxTicks, () -> {
            r.tick(bot, null);
            boolean done = a.near(to.getX(), to.getY(), to.getZ(), 1.6);
            if (done) r.log(h, String.format(java.util.Locale.ROOT, "bps=%.2f", dist / (h.getTick() / 20.0)));
            return done;
        });
    }

    /** Sixty-four blocks of flat ground. */
    private void motionFlat(GameTestHelper h, int knob) {
        BotArena a = BotArena.wide(h, "motion_flat_" + knob);
        BotArena.day(h);
        course(h, a, "motion_flat", knob, new BlockPos(-12, FEET, 8), new BlockPos(52, FEET, 8), 600);
    }

    /** A hill of one-block steps, eight up, a top, eight down, across the arena. */
    private void motionHill(GameTestHelper h, int knob) {
        BotArena a = BotArena.wide(h, "motion_hill_" + knob);
        BotArena.day(h);
        for (int k = 0; k < 8; k++) {
            a.fill(8 + k, FEET, 12, 31 - k, FEET + k, 28, Blocks.STONE);
        }
        course(h, a, "motion_hill", knob, new BlockPos(-10, FEET, 20), new BlockPos(50, FEET, 20), 900);
    }

    /** A forest: trunks five high with a crown, seeded, across the arena; walked through on a slant. */
    private void motionForest(GameTestHelper h, int knob) {
        BotArena a = BotArena.wide(h, "motion_forest_" + knob);
        BotArena.day(h);
        java.util.Random rng = new java.util.Random(31);
        for (int t = 0; t < 70; t++) {
            int x = rng.nextInt(40), z = rng.nextInt(40);
            a.fill(x - 1, FEET + 4, z - 1, x + 1, FEET + 5, z + 1, Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            a.fill(x, FEET, z, x, FEET + 4, z, Blocks.OAK_LOG);
        }
        course(h, a, "motion_forest", knob, new BlockPos(-10, FEET, -8), new BlockPos(48, FEET, 46), 1200);
    }

    /** A ditch two wide and three deep, then a drop of three: down and on. */
    private void motionGap(GameTestHelper h, int knob) {
        BotArena a = BotArena.wide(h, "motion_gap_" + knob);
        BotArena.day(h);
        a.fill(10, GROUND - 2, -20, 11, GROUND, 59, Blocks.AIR);
        a.fill(26, GROUND - 3, -20, 59, GROUND, 59, Blocks.AIR);
        a.fill(26, GROUND - 4, -20, 59, GROUND - 4, 59, Blocks.GRASS_BLOCK);
        course(h, a, "motion_gap", knob, new BlockPos(-10, FEET, 20), new BlockPos(50, FEET - 3, 20), 900);
    }

    @GameTest(environment = "challengecraft:algo_motion", structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 24)
    public void motionFlatOn(GameTestHelper h) {
        motionFlat(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_motion", structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 24)
    public void motionFlatOff(GameTestHelper h) {
        motionFlat(h, 0);
    }

    @GameTest(environment = "challengecraft:algo_motion", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 24)
    public void motionHillOn(GameTestHelper h) {
        motionHill(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_motion", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 24)
    public void motionHillOff(GameTestHelper h) {
        motionHill(h, 0);
    }

    @GameTest(environment = "challengecraft:algo_motion", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 24)
    public void motionForestOn(GameTestHelper h) {
        motionForest(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_motion", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 24)
    public void motionForestOff(GameTestHelper h) {
        motionForest(h, 0);
    }

    @GameTest(environment = "challengecraft:algo_motion", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 24)
    public void motionGapOn(GameTestHelper h) {
        motionGap(h, 1);
    }

    @GameTest(environment = "challengecraft:algo_motion", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 24)
    public void motionGapOff(GameTestHelper h) {
        motionGap(h, 0);
    }
}
