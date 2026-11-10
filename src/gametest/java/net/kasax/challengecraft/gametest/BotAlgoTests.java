package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotParams;
import net.kasax.challengecraft.bot.task.GoToTask;
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
}
