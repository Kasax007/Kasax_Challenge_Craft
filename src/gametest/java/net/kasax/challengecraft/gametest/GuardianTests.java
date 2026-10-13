package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * The guardian, on the three deaths of a game played against Bob: each time it must step in
 * before the hearts run out, whatever the bot is doing, and the bot must live.
 */
public class GuardianTests {

    private static void hard(ServerLevel level) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
    }

    private static <T extends Mob> T mob(GameTestHelper h, BotArena a, net.minecraft.world.entity.EntityType<T> type, int x, int y, int z) {
        T m = type.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        m.setPos(Vec3.atBottomCenterOf(a.abs(x, y, z)));
        m.setPersistenceRequired();
        // (A helmet: the test's daylight would burn a zombie long before the fight is decided.
        // And proof against fire: in the sun a leather helmet is worn through in five seconds, and
        // a skeleton up on a ledge burnt to death before the fight began.)
        if (m instanceof net.minecraft.world.entity.monster.zombie.Zombie) m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 600));
        h.getLevel().addFreshEntity(m);
        m.setTarget(a.bot().body());
        return m;
    }

    /**
     * Bare-handed in a walled pit with a zombie at him, on hard, at full health (the third death:
     * the run got nowhere, then the fist fight): no fist fight on the ground - up a pillar and the
     * zombie hit from above (or walled in), alive at the end.
     */
    @GameTest(environment = "challengecraft:guardian", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 40)
    public void unarmedCornered(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "unarmed_cornered");
        hard(h.getLevel());
        a.fill(17, FEET, 17, 23, FEET + 2, 23, Blocks.STONE);
        a.fill(18, FEET, 18, 22, FEET + 2, 22, Blocks.AIR);
        a.spawn(20, FEET, 20, new ItemStack(Items.DIRT, 16));
        var first = a.bot().body();
        var zombie = mob(h, a, EntityTypes.ZOMBIE, 22, FEET, 20);
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 1600, () -> {
            h.assertTrue(a.bot().body() == first, "unarmed_cornered: died");
            BotArena.LOG.info("[BOTTEST] unarmed_cornered hp {} zombie {} guardian {}/{}/{}", first.getHealth(), zombie.isAlive() ? zombie.getHealth() : "dead",
                    a.bot().guardian.boxes, a.bot().guardian.pillars, a.bot().guardian.digs);
            return !zombie.isAlive() || h.getTick() > 1500;
        });
    }

    /**
     * Seven hearts, two zombies at him, on hard (the first death: a fall into a ravine, then the
     * zombies down there): the cover at once - no run, no trading blows - and alive.
     */
    @GameTest(environment = "challengecraft:guardian", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 40)
    public void lowWithTwoZombies(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "low_with_two_zombies");
        hard(h.getLevel());
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.COBBLESTONE, 16));
        var first = a.bot().body();
        first.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        first.setHealth(7);
        var z1 = mob(h, a, EntityTypes.ZOMBIE, 22, FEET, 20);
        var z2 = mob(h, a, EntityTypes.ZOMBIE, 20, FEET, 22);
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 900, () -> {
            h.assertTrue(a.bot().body() == first, "low_with_two_zombies: died");
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] low_with_two_zombies hp {} zombies {} guardian {}/{}/{}", first.getHealth(),
                    (z1.isAlive() ? 1 : 0) + (z2.isAlive() ? 1 : 0), a.bot().guardian.boxes, a.bot().guardian.pillars, a.bot().guardian.digs);
            return h.getTick() > 800;
        });
    }

    /**
     * A ghast shooting at him on bare netherrack, ten hearts and no armour, on hard (the second
     * death: eating under its fireballs): dug in, out of its sight, and alive.
     */
    @GameTest(environment = "challengecraft:guardian_ghast", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 40)
    public void ghastAtLowHealth(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "ghast_at_low_health");
        hard(h.getLevel());
        a.fill(0, GROUND - 5, 0, 39, GROUND, 39, Blocks.NETHERRACK);
        a.spawn(20, FEET, 12, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.BREAD, 8));
        var first = a.bot().body();
        first.setHealth(10);
        var ghast = mob(h, a, EntityTypes.GHAST, 20, FEET + 6, 32);
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 1200, () -> {
            h.assertTrue(a.bot().body() == first, "ghast_at_low_health: died");
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] ghast_at_low_health hp {} ghast {} guardian {}/{}/{}", first.getHealth(),
                    ghast.isAlive() ? ghast.blockPosition().subtract(a.abs(0, 0, 0)).toShortString() : "dead",
                    a.bot().guardian.boxes, a.bot().guardian.pillars, a.bot().guardian.digs);
            return h.getTick() > 1100;
        });
    }

    /**
     * Three wolves at him (one struck by mistake), nine hearts, a stone sword and blocks, on hard:
     * not walled in with the wolves standing in the sides of the box (a self-play league game:
     * three bites through the gaps, dead) - up out of their reach, or away from them - and alive.
     */
    @GameTest(environment = "challengecraft:guardian", structure = STRUCTURE, maxTicks = 700, skyAccess = true, padding = 40)
    public void wolvesAtHisHeels(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "wolves_at_his_heels");
        hard(h.getLevel());
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.COBBLESTONE, 32));
        var first = a.bot().body();
        first.setHealth(18);
        List<net.minecraft.world.entity.animal.wolf.Wolf> wolves = new java.util.ArrayList<>();
        for (int[] at : new int[][]{{21, 20}, {20, 21}, {19, 20}}) {
            var w = mob(h, a, EntityTypes.WOLF, at[0], FEET, at[1]);
            w.startPersistentAngerTimer();
            wolves.add(w);
        }
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 700, () -> {
            h.assertTrue(a.bot().body() == first, "wolves_at_his_heels: died");
            for (var w : wolves) if (w.isAlive() && w.getTarget() != first) w.setTarget(first);
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] wolves_at_his_heels hp {} guardian {}/{}/{}/{}", first.getHealth(),
                    a.bot().guardian.boxes, a.bot().guardian.pillars, a.bot().guardian.digs, a.bot().guardian.runs);
            return h.getTick() > 600;
        });
    }

    /** One zombie, a stone sword, full health, on hard: a fight he wins - the guardian keeps out of it. */
    @GameTest(environment = "challengecraft:guardian", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 40)
    public void armedFightsOneZombie(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "armed_fights_one_zombie");
        hard(h.getLevel());
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.COBBLESTONE, 16));
        var first = a.bot().body();
        var zombie = mob(h, a, EntityTypes.ZOMBIE, 24, FEET, 20);
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 900, () -> {
            h.assertTrue(a.bot().body() == first, "armed_fights_one_zombie: died");
            h.assertTrue(a.bot().guardian.boxes + a.bot().guardian.pillars + a.bot().guardian.digs == 0, "armed_fights_one_zombie: the guardian stepped in");
            return !zombie.isAlive();
        });
    }

    /**
     * Walled in on hard, a spider at him in the one side that will not go in (a
     * self-play game: seven bites through the gap, not one blow back, dead): the spider struck
     * from inside, the side put in once the gap is clear - alive, the box shut.
     */
    @GameTest(environment = "challengecraft:open_box", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 24)
    public void spiderInTheGap(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "spider_in_the_gap");
        hard(h.getLevel());
        h.getLevel().getServer().getCommands().performPrefixedCommand(h.getLevel().getServer().createCommandSourceStack().withSuppressedOutput(), "time set 1000");
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.COBBLESTONE, 16));
        var body = a.bot().body();
        var spider = mob(h, a, EntityTypes.SPIDER, 22, FEET, 20);
        // (By day, the spider kept at him: the clock is the whole server's, and left at night it
        // kept a dead bot from its respawn in the tests after this one, waiting for the dawn.)
        a.run(net.kasax.challengecraft.bot.task.HideTask.boxed(), 1200, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "spider_in_the_gap: died");
            if (spider.isAlive() && spider.getTarget() != body) spider.setTarget(body);
            var feet = body.blockPosition();
            boolean shut = true;
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                shut &= !h.getLevel().getBlockState(feet.relative(d)).canBeReplaced() && !h.getLevel().getBlockState(feet.above().relative(d)).canBeReplaced();
            }
            shut &= !h.getLevel().getBlockState(feet.above(2)).canBeReplaced();
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] spider_in_the_gap hp {} spider {} shut {}", body.getHealth(),
                    spider.isAlive() ? spider.getHealth() : "dead", shut);
            return shut && h.getTick() > 100;
        });
    }

    /**
     * Walled in with too few blocks for the box (two sides go in, the rest stay open) and a
     * skeleton twelve blocks off shooting in through the gap, on hard (a self-play game: five
     * arrows through the side that would not go in, the shield on the arm, down - dead): the
     * shield up towards it, the arrows in the shield, next to nothing lost in half a minute.
     */
    @GameTest(environment = "challengecraft:open_box", structure = STRUCTURE, maxTicks = 700, skyAccess = true, padding = 24)
    public void skeletonAtTheGap(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "skeleton_at_the_gap");
        hard(h.getLevel());
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.COBBLESTONE, 2));
        var body = a.bot().body();
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        var skeleton = mob(h, a, EntityTypes.SKELETON, 8, FEET, 20);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        // (A helmet: by day it would burn before the test is over.)
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float[] low = {body.getHealth()};
        a.run(net.kasax.challengecraft.bot.task.HideTask.boxed(), 700, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "skeleton_at_the_gap: died");
            if (skeleton.isAlive() && skeleton.getTarget() != body) skeleton.setTarget(body);
            low[0] = Math.min(low[0], body.getHealth());
            int caught = body.getOffhandItem().getDamageValue();
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] skeleton_at_the_gap hp {} lowest {} blocking {} shield wear {}", body.getHealth(), low[0],
                    body.isBlocking(), caught);
            // (Arrows in the shield: it was shot at, and the shield took them.)
            return h.getTick() > 600 && caught > 0 && low[0] >= 14;
        });
    }

    /**
     * A lone skeleton up on a ledge four blocks high, twelve blocks off, on hard; a sword and a
     * shield (a self-play game: "not a fight to be won", walled in, the box would not shut, shot
     * dead through the gap - the shield on the arm, down): no cover needed, the shield up towards
     * it - the guardian keeps out of it, and he lives.
     */
    @GameTest(environment = "challengecraft:open_box", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 24)
    public void loneBowmanBehindShield(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lone_bowman_behind_shield");
        hard(h.getLevel());
        // (A ledge, not a pillar: from a pillar it strafed off and the fight was on the ground.)
        a.fill(6, FEET, 18, 9, FEET + 3, 22, Blocks.STONE);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.COBBLESTONE, 16));
        var body = a.bot().body();
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        var skeleton = mob(h, a, EntityTypes.SKELETON, 8, FEET + 4, 20);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float[] low = {body.getHealth()};
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 900, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "lone_bowman_behind_shield: died");
            if (skeleton.isAlive() && skeleton.getTarget() != body) skeleton.setTarget(body);
            low[0] = Math.min(low[0], body.getHealth());
            int stepped = a.bot().guardian.boxes + a.bot().guardian.pillars + a.bot().guardian.digs;
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] lone_bowman_behind_shield hp {} lowest {} skeleton {} guardian {} {}", body.getHealth(), low[0],
                    skeleton.isAlive() ? skeleton.getHealth() : "dead", stepped, a.bot().status());
            h.assertTrue(stepped == 0, "lone_bowman_behind_shield: the guardian walled him in");
            return (!skeleton.isAlive() || h.getTick() > 800) && low[0] >= 8;
        });
    }

    /**
     * Shut in a box (a self-play game: in a tunnel at night, two zombies waiting outside, walled
     * in again and again for five minutes), full health, a stone sword, on hard: a window at the
     * feet towards one, struck through it crouched, the next at the next side - both dead, next
     * to nothing lost, and the box shut again.
     */
    @GameTest(environment = "challengecraft:open_box", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void zombiesAtTheBox(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "zombies_at_the_box");
        hard(h.getLevel());
        a.fill(19, FEET, 19, 21, FEET + 1, 21, Blocks.COBBLESTONE);
        a.fill(20, FEET, 20, 20, FEET + 1, 20, Blocks.AIR);
        a.fill(20, FEET + 2, 20, 20, FEET + 2, 20, Blocks.COBBLESTONE);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 16));
        var body = a.bot().body();
        var z1 = mob(h, a, EntityTypes.ZOMBIE, 23, FEET, 20);
        var z2 = mob(h, a, EntityTypes.ZOMBIE, 20, FEET, 23);
        float[] low = {body.getHealth()};
        var level = h.getLevel();
        a.run(net.kasax.challengecraft.bot.task.HideTask.boxed(), 1600, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "zombies_at_the_box: died");
            for (var z : List.of(z1, z2)) if (z.isAlive() && z.getTarget() != body) z.setTarget(body);
            low[0] = Math.min(low[0], body.getHealth());
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] zombies_at_the_box hp {} lowest {} zombies {} {}", body.getHealth(), low[0],
                    z1.isAlive() ? z1.getHealth() : "dead", z2.isAlive() ? z2.getHealth() : "dead");
            var feet = body.blockPosition();
            boolean shut = true;
            for (var p : new net.minecraft.core.BlockPos[]{feet.north(), feet.east(), feet.south(), feet.west(),
                    feet.above().north(), feet.above().east(), feet.above().south(), feet.above().west(), feet.above(2)}) {
                shut &= !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
            }
            return !z1.isAlive() && !z2.isAlive() && shut && low[0] >= 16;
        });
    }

    /**
     * Two zombies at him on hard, no armour: up a pillar and from above; then a creeper comes
     * (a self-play game: "kill creeper" took him down off the pillar, the zombies at his back -
     * dead). The pillar is not left for the creeper; alive, not badly hurt.
     */
    @GameTest(environment = "challengecraft:guardian", structure = STRUCTURE, maxTicks = 1300, skyAccess = true, padding = 40)
    public void creeperAtThePillar(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "creeper_at_the_pillar");
        hard(h.getLevel());
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.COBBLESTONE, 16));
        var body = a.bot().body();
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        var z1 = mob(h, a, EntityTypes.ZOMBIE, 24, FEET, 20);
        var z2 = mob(h, a, EntityTypes.ZOMBIE, 20, FEET, 24);
        Mob[] creeper = {null};
        h.runAfterDelay(140, () -> creeper[0] = mob(h, a, EntityTypes.CREEPER, 31, FEET, 20));
        float[] low = {body.getHealth()};
        boolean[] leftForCreeper = {false};
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 1300, () -> {
            h.assertTrue(a.bot().body() == body && body.isAlive(), "creeper_at_the_pillar: died");
            for (var z : List.of(z1, z2)) if (z.isAlive() && z.getTarget() != body) z.setTarget(body);
            if (creeper[0] != null && creeper[0].isAlive() && creeper[0].getTarget() != body) creeper[0].setTarget(body);
            low[0] = Math.min(low[0], body.getHealth());
            // (Off the pillar after the creeper while a zombie still stands: the very thing.)
            boolean pillar = a.bot().tasks().stream().anyMatch(t -> net.kasax.challengecraft.bot.BotTask.innermost(t) instanceof net.kasax.challengecraft.bot.task.PillarFightTask);
            if (!pillar && (z1.isAlive() || z2.isAlive()) && a.bot().current() instanceof net.kasax.challengecraft.bot.task.KillTask k
                    && k.target() instanceof net.minecraft.world.entity.monster.Creeper) leftForCreeper[0] = true;
            h.assertTrue(!leftForCreeper[0], "creeper_at_the_pillar: off the pillar after the creeper");
            if (h.getTick() % 50 == 0) BotArena.LOG.info("[BOTTEST] creeper_at_the_pillar hp {} lowest {} zombies {} {} creeper {} {}", body.getHealth(), low[0],
                    z1.isAlive() ? z1.getHealth() : "dead", z2.isAlive() ? z2.getHealth() : "dead",
                    creeper[0] == null ? "-" : creeper[0].isAlive() ? creeper[0].getHealth() : "gone", a.bot().status());
            return h.getTick() > 1200 && low[0] >= 6;
        });
    }

    /**
     * Walled in among flowers (a poppy at every side by the feet - a meadow, a flower forest): a
     * flower is no wall, a spider walks in through it and an arrow flies through - broken, the
     * block put in its place; the box shut all round.
     */
    @GameTest(environment = "challengecraft:open_box", structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 24)
    public void flowersRoundTheBox(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "flowers_round_the_box");
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            a.fill(20 + d.getStepX(), FEET, 20 + d.getStepZ(), 20 + d.getStepX(), FEET, 20 + d.getStepZ(), Blocks.POPPY);
        }
        a.spawn(20, FEET, 20, new ItemStack(Items.COBBLESTONE, 16));
        var body = a.bot().body();
        var level = h.getLevel();
        a.run(net.kasax.challengecraft.bot.task.HideTask.boxed(), 400, () -> {
            var feet = body.blockPosition();
            boolean shut = true;
            for (var p : new net.minecraft.core.BlockPos[]{feet.north(), feet.east(), feet.south(), feet.west(),
                    feet.above().north(), feet.above().east(), feet.above().south(), feet.above().west(), feet.above(2)}) {
                shut &= !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
            }
            return shut && h.getTick() > 40;
        });
    }

    /**
     * Walled in at the edge of a drop (one side of the box over thin air, as at a ledge in the
     * self-play games, where a skeleton shot in through the side that would not go in): a block
     * under that side first, then the side - the box shut all round, the lid on.
     */
    @GameTest(environment = "challengecraft:open_box", structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 24)
    public void boxAtLedge(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "box_at_ledge");
        a.fill(21, GROUND - 2, 0, 39, GROUND, 39, Blocks.AIR);
        a.spawn(20, FEET, 20, new ItemStack(Items.COBBLESTONE, 20));
        var body = a.bot().body();
        a.run(net.kasax.challengecraft.bot.task.HideTask.boxed(), 600, () -> {
            var feet = body.blockPosition();
            boolean shut = true;
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                shut &= !h.getLevel().getBlockState(feet.relative(d)).canBeReplaced() && !h.getLevel().getBlockState(feet.above().relative(d)).canBeReplaced();
            }
            shut &= !h.getLevel().getBlockState(feet.above(2)).canBeReplaced();
            return shut && h.getTick() > 40;
        });
    }
}
