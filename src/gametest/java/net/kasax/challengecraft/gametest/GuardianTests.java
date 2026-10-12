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
        // (A helmet: the test's daylight would burn a zombie long before the fight is decided.)
        if (m instanceof net.minecraft.world.entity.monster.zombie.Zombie) m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
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
}
