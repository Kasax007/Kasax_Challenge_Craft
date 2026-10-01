package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.task.CastPortalTask;
import net.kasax.challengecraft.bot.task.ObtainTask;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/** What the bot makes of the world: tools, portals. */
public class BotSkillTests {
    /** From nothing to a stone pickaxe: a few trees about, stone under the grass. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void stonePickaxe(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "stone_pickaxe");
        for (int[] t : new int[][]{{10, 10}, {14, 26}, {28, 12}}) {
            a.fill(t[0], FEET, t[1], t[0], FEET + 4, t[1], Blocks.OAK_LOG);
            a.fill(t[0] - 2, FEET + 3, t[1] - 2, t[0] + 2, FEET + 5, t[1] + 2, Blocks.OAK_LEAVES);
            a.fill(t[0], FEET + 3, t[1], t[0], FEET + 4, t[1], Blocks.OAK_LOG);
        }
        a.spawn(20, FEET, 20);
        a.run(new ObtainTask(Set.of(Items.STONE_PICKAXE), 1), 2400,
                () -> ObtainPlanner.countAny(a.bot().body(), Set.of(Items.STONE_PICKAXE)) > 0);
    }

    /** Its things lying where it died, 20 blocks off: all picked up again. */
    @GameTest(structure = STRUCTURE, maxTicks = 800, skyAccess = true, padding = 8)
    public void recoverThings(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "recover");
        var level = h.getLevel();
        var spot = a.abs(30, FEET, 30);
        for (ItemStack st : new ItemStack[]{new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.OAK_PLANKS, 7)}) {
            var e = new net.minecraft.world.entity.item.ItemEntity(level, spot.getX() + level.getRandom().nextDouble() * 3, spot.getY() + 0.5,
                    spot.getZ() + level.getRandom().nextDouble() * 3, st);
            level.addFreshEntity(e);
        }
        a.spawn(8, FEET, 8);
        a.run(new net.kasax.challengecraft.bot.task.RecoverTask(spot), 800, () -> ObtainPlanner.countAny(a.bot().body(), Set.of(Items.STONE_PICKAXE)) > 0
                && ObtainPlanner.countAny(a.bot().body(), Set.of(Items.IRON_INGOT)) >= 3 && ObtainPlanner.countAny(a.bot().body(), Set.of(Items.OAK_PLANKS)) >= 7);
    }

    /** Taiga: a big spruce (2 x 2) and a slim one, leaves down to head height. Six logs. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void spruceLogs(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "spruce_logs");
        spruce(a, 10, 10, 2, 16);
        spruce(a, 26, 24, 1, 10);
        a.spawn(20, FEET, 20);
        a.run(new ObtainTask(Set.of(Items.SPRUCE_LOG), 6), 2400,
                () -> ObtainPlanner.countAny(a.bot().body(), Set.of(Items.SPRUCE_LOG)) >= 6);
    }

    private static void spruce(BotArena a, int x, int z, int width, int height) {
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int y = 2; y <= height; y++) {
            int r = Math.max(1, (height - y) / 3 + 1) + (width - 1);
            if (y % 2 == 1 && y < height - 1) r--;
            a.fill(x - r, FEET + y, z - r, x + width - 1 + r, FEET + y, z + width - 1 + r, leaves);
        }
        a.fill(x, FEET, z, x + width - 1, FEET + height - 2, z + width - 1, Blocks.SPRUCE_LOG);
    }

    /** The speedrunners' portal at a lava pool, then through it. (Flaky about one run in three: open.) */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8, maxAttempts = 3, requiredSuccesses = 1)
    public void castPortal(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "cast_portal");
        a.fill(24, GROUND, 4, 27, GROUND, 7, Blocks.LAVA);
        a.spawn(20, FEET, 20, new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.DIRT, 12), new ItemStack(Items.COBBLESTONE, 8));
        a.run(new CastPortalTask(h.getLevel()), 3600, () -> a.bot().body().level().dimension() == Level.NETHER);
    }

    /** Killed (as by a creeper): back on its feet after the respawn, the task stack cleared. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void respawnAfterDeath(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "respawn");
        a.spawn(20, FEET, 20);
        var bot = a.bot();
        boolean[] died = {false};
        h.runAfterDelay(100, () -> bot.body().hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 1000f));
        h.onEachTick(() -> { if (!bot.body().isAlive()) died[0] = true; });
        a.run(new net.kasax.challengecraft.bot.task.GoToTask(a.abs(4, FEET, 4), 1), 400,
                () -> died[0] && bot.body().isAlive() && bot.body().getHealth() >= 20f && bot.current() == null);
    }

    /** A chest behind a pressure plate over TNT (the desert temple's trap): looted, nothing blows up. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void trapChest(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "trap_chest");
        // A corridor one wide, the plate in the middle of it, TNT under the plate.
        a.fill(10, FEET, 19, 24, FEET + 2, 21, Blocks.SANDSTONE);
        a.fill(11, FEET, 20, 23, FEET + 1, 20, Blocks.AIR);
        a.fill(17, GROUND, 20, 17, GROUND, 20, Blocks.TNT);
        a.fill(17, FEET, 20, 17, FEET, 20, Blocks.STONE_PRESSURE_PLATE);
        a.fill(23, FEET, 20, 23, FEET, 20, Blocks.CHEST);
        var chestPos = a.abs(23, FEET, 20);
        if (h.getLevel().getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 2));
        }
        a.spawn(11, FEET, 20, new ItemStack(Items.WOODEN_PICKAXE));
        boolean[] gone = {false};
        h.onEachTick(() -> {
            if (!gone[0] && !h.getLevel().getBlockState(a.abs(17, GROUND, 20)).is(Blocks.TNT)) {
                gone[0] = true;
                BotArena.LOG.info("[BOTTEST] trap_chest: the TNT is gone at tick {}, bot at {}, now {}", h.getTick(), a.feet().toShortString(),
                        h.getLevel().getBlockState(a.abs(17, GROUND, 20)));
            }
        });
        a.run(new net.kasax.challengecraft.bot.task.LootTask(chestPos), 600,
                () -> !gone[0] && ObtainPlanner.countAny(a.bot().body(), Set.of(Items.DIAMOND)) > 0);
    }
}
