package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.block.InfiniteChestBlockEntity;
import net.kasax.challengecraft.block.InfiniteChestRegistry;
import net.kasax.challengecraft.block.InfiniteChestScreenHandler;
import net.kasax.challengecraft.challenges.Chal_17_WalkRandomItem;
import net.kasax.challengecraft.network.InfiniteChestClickPayload;
import net.kasax.challengecraft.network.PacketHandler;
import net.kasax.challengecraft.storage.InfiniteChestStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
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

    /**
     * Infinite chest clicks: only the chest the player has open counts (and only within reach),
     * a left click takes one stack of that item, and every change marks the chunk for saving -
     * before, the next load undid it: a withdrawal came back (a dupe), a deposit was lost.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void infiniteChestClicks(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "infinite_chest_clicks");
        var level = h.getLevel();
        BlockPos chestPos = a.abs(20, FEET, 22), otherPos = a.abs(5, FEET, 5);
        level.setBlockAndUpdate(chestPos, InfiniteChestRegistry.INFINITE_CHEST_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(otherPos, InfiniteChestRegistry.INFINITE_CHEST_BLOCK.defaultBlockState());
        var chest = (InfiniteChestBlockEntity) level.getBlockEntity(chestPos);
        var other = (InfiniteChestBlockEntity) level.getBlockEntity(otherPos);
        chest.getStorage().addStack(new ItemStack(Items.DIAMOND_SWORD, 3));
        chest.getStorage().addStack(new ItemStack(Items.ENDER_PEARL, 40));
        other.getStorage().addStack(new ItemStack(Items.DIAMOND, 10));
        var bot = a.spawn(20, FEET, 20);
        var body = bot.body();
        int[] t = {0};
        // Every check runs once; the test reports all that failed together.
        java.util.List<String> bad = new java.util.ArrayList<>();
        java.util.function.BiConsumer<Boolean, String> check = (ok, msg) -> { if (!ok) bad.add(msg); };
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(180), 200, () -> {
            if (++t[0] < 5) return false;
            if (t[0] > 5) {
                h.assertTrue(bad.isEmpty(), String.join("; ", bad));
                return true;
            }
            var chunk = level.getChunkAt(chestPos);
            withdraw(body, chestPos, Items.ENDER_PEARL);
            check.accept(stored(chest, Items.ENDER_PEARL) == 40, "a click without the chest open took items");
            body.openMenu(chest);
            check.accept(body.containerMenu instanceof InfiniteChestScreenHandler, "the infinite chest did not open");
            withdraw(body, otherPos, Items.DIAMOND);
            check.accept(stored(other, Items.DIAMOND) == 10, "a click naming another chest than the open one took from it");
            chunk.tryMarkSaved();
            withdraw(body, chestPos, Items.ENDER_PEARL);
            check.accept(stored(chest, Items.ENDER_PEARL) == 24, "a left click on ender pearls took "
                    + (40 - stored(chest, Items.ENDER_PEARL)) + ", not one stack (16)");
            check.accept(chunk.isUnsaved(), "a withdrawal did not mark the chest for saving");
            withdraw(body, chestPos, Items.DIAMOND_SWORD);
            check.accept(stored(chest, Items.DIAMOND_SWORD) == 2, "a left click on swords took "
                    + (3 - stored(chest, Items.DIAMOND_SWORD)) + ", not one");
            chunk.tryMarkSaved();
            body.containerMenu.setCarried(new ItemStack(Items.DIRT, 5));
            PacketHandler.handleInfiniteChestClick(body, new InfiniteChestClickPayload(chestPos, ItemStack.EMPTY, -1, false));
            check.accept(stored(chest, Items.DIRT) == 5 && body.containerMenu.getCarried().isEmpty(), "the cursor stack was not deposited");
            check.accept(chunk.isUnsaved(), "a deposit did not mark the chest for saving");
            body.teleportTo(level, body.getX() + 30, body.getY(), body.getZ(), Set.of(), body.getYRot(), 0f, true);
            check.accept(!body.containerMenu.stillValid(body), "the chest menu stayed valid 30 blocks away");
            withdraw(body, chestPos, Items.ENDER_PEARL);
            check.accept(stored(chest, Items.ENDER_PEARL) == 24, "a click from 30 blocks away took items");
            body.closeContainer();
            h.assertTrue(bad.isEmpty(), String.join("; ", bad));
            return true;
        });
    }

    /**
     * Dice: the client's challenge sync switches every challenge off and the active ones on again,
     * in singleplayer on the server's own flags - that must not take a player's remaining blocks
     * (it did, on every sync: a LAN player joining, the re-sync after a join). Only the Dice flag
     * is touched, and only within this call, so the bot tests running alongside never see it.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 20, skyAccess = true, padding = 8)
    public void diceBudgetSurvivesSync(GameTestHelper h) {
        var uuid = java.util.UUID.randomUUID();
        boolean was = net.kasax.challengecraft.challenges.Chal_46_Dice.isActive();
        net.kasax.challengecraft.challenges.Chal_46_Dice.setActive(true);
        net.kasax.challengecraft.challenges.Chal_46_Dice.setBudget(uuid, 5);
        net.kasax.challengecraft.challenges.Chal_46_Dice.setActive(false);
        net.kasax.challengecraft.challenges.Chal_46_Dice.setActive(true);
        double left = net.kasax.challengecraft.challenges.Chal_46_Dice.getBudget(uuid);
        net.kasax.challengecraft.challenges.Chal_46_Dice.setBudget(uuid, 0);
        net.kasax.challengecraft.challenges.Chal_46_Dice.setActive(was);
        h.assertTrue(left == 5, "a challenge sync took the dice budget: " + left + " of 5 blocks left");
        h.succeed();
    }

    private static void withdraw(ServerPlayer player, BlockPos pos, Item item) {
        PacketHandler.handleInfiniteChestClick(player, new InfiniteChestClickPayload(pos, new ItemStack(item), 0, false));
    }

    private static long stored(InfiniteChestBlockEntity chest, Item item) {
        return chest.getStorage().getStoredItems().getOrDefault(InfiniteChestStorage.ItemStackKey.fromStack(new ItemStack(item)), 0L);
    }
}
