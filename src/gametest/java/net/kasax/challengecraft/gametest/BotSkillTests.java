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
}
