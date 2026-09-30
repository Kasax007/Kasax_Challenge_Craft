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
