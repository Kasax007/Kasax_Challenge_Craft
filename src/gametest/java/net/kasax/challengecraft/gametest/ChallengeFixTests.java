package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.challenges.Chal_17_WalkRandomItem;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/** Regression tests for bug reports about the challenges themselves (not the bot). */
public class ChallengeFixTests {
    /** A mob that dies without a player (the sun, a fall, another mob) drops no XP; one a player kills does, mob drops off or not. */
    @GameTest(structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void xpOnlyForPlayerKills(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "xp_only_for_player_kills");
        var level = h.getLevel();
        var server = level.getServer();
        boolean mobDrops = level.getGameRules().get(GameRules.MOB_DROPS);
        level.getGameRules().set(GameRules.MOB_DROPS, false, server);
        var bot = a.spawn(10, FEET, 10);
        var lonely = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
        lonely.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        lonely.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(30, FEET, 10)));
        level.addFreshEntity(lonely);
        var hunted = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
        hunted.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        hunted.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(30, FEET, 30)));
        level.addFreshEntity(hunted);
        int[] t = {0};
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(180), 200, () -> {
            if (++t[0] == 5) {
                lonely.hurtServer(level, level.damageSources().generic(), 1000f);
                hunted.hurtServer(level, level.damageSources().playerAttack(bot.body()), 1000f);
            }
            if (t[0] < 40) return false;
            int lonelyXp = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(a.abs(30, FEET, 10)).inflate(6)).size();
            int huntedXp = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(a.abs(30, FEET, 30)).inflate(6)).size();
            level.getGameRules().set(GameRules.MOB_DROPS, mobDrops, server);
            h.assertTrue(lonelyXp == 0, "a zombie dead without a player dropped " + lonelyXp + " XP orbs");
            h.assertTrue(huntedXp > 0, "a zombie killed by a player dropped no XP (mob drops off)");
            return true;
        });
    }

    /** 500 blocks = item: a trip through a portal far from spawn, or a long teleport, is no walk and gives no items. */
    @GameTest(structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void walkItemNotForPortals(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "walk_item_not_for_portals");
        var server = h.getLevel().getServer();
        var bot = a.spawn(20, FEET, 20);
        var body = bot.body();
        boolean was = Chal_17_WalkRandomItem.isActive();
        Chal_17_WalkRandomItem.setActive(true);
        var home = h.getLevel();
        var nether = server.getLevel(Level.NETHER);
        double hx = body.getX(), hy = body.getY(), hz = body.getZ();
        int[] t = {0};
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(180), 200, () -> {
            t[0]++;
            // (Out to the Nether far from here, back, and a long jump within the world.)
            if (t[0] == 5) body.teleportTo(nether, hx + 3000, 128, hz + 3000, Set.of(), body.getYRot(), 0f, true);
            if (t[0] == 15) body.teleportTo(home, hx, hy, hz, Set.of(), body.getYRot(), 0f, true);
            if (t[0] == 25) body.teleportTo(home, hx + 2000, hy + 60, hz, Set.of(), body.getYRot(), 0f, true);
            if (t[0] == 35) body.teleportTo(home, hx, hy, hz, Set.of(), body.getYRot(), 0f, true);
            if (t[0] < 45) return false;
            int items = 0;
            for (var st : body.getInventory().getNonEquipmentItems()) items += st.getCount();
            Chal_17_WalkRandomItem.setActive(was);
            h.assertTrue(items == 0, "teleports and a portal trip gave " + items + " items as if walked");
            return true;
        });
    }
}
