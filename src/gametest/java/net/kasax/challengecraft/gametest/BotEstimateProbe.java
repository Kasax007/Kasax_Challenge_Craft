package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.lockout.LockoutGoals;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * Not a test of skill: the estimates of the tiles the leagues found worst, in one fixed scene by
 * day and by night, logged as [ESTIMATES] lines - to set one estimator against another in the
 * same situation (docs/bob-reference-times.md: before and after the batches and the mob search).
 * The scene: trees, a gravel bank, sugar cane by a pond, three chickens; Bob with stone tools,
 * a sword, a shield, two iron ingots and a crafting table. It only uses what every version of the
 * planner has (LockoutGoals.plan), so the same file measures an older one.
 */
public class BotEstimateProbe {
    private static final List<String> GOALS = List.of("kill_skeleton", "kill_zombie", "kill_creeper", "kill_spider", "kill_enderman",
            "kill_witch", "obtain_name_tag", "obtain_arrow", "craft_firework_rocket", "obtain_16_logs", "obtain_32_cobblestone",
            "craft_bow", "obtain_ender_pearl", "craft_item_frame", "craft_painting", "obtain_coal_block", "craft_bucket", "obtain_gold_ingot");

    private static void time(GameTestHelper h, int ticks) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + ticks);
    }

    @GameTest(environment = "challengecraft:estimate_probe", structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void estimates(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "estimate_probe");
        for (int[] t : new int[][]{{6, 30}, {10, 34}, {30, 6}, {34, 34}}) a.fill(t[0], FEET, t[1], t[0], FEET + 4, t[1], Blocks.OAK_LOG);
        a.fill(26, GROUND, 24, 30, GROUND, 28, Blocks.GRAVEL);
        a.fill(6, GROUND, 6, 9, GROUND, 9, Blocks.WATER);
        a.fill(10, GROUND, 6, 10, GROUND, 9, Blocks.SAND);
        a.fill(10, FEET, 6, 10, FEET + 1, 9, Blocks.SUGAR_CANE);
        for (int i = 0; i < 3; i++) EntityTypes.CHICKEN.spawn(h.getLevel(), a.abs(14 + 2 * i, FEET, 30), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_SHOVEL),
                new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.CRAFTING_TABLE),
                new ItemStack(Items.BREAD, 8));
        time(h, 1000);
        h.runAfterDelay(20, () -> {
            log(a, "day");
            time(h, 14000);
            h.runAfterDelay(10, () -> {
                log(a, "night");
                BotArena.day(h);
                a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 200, () -> true);
            });
        });
    }

    private static void log(BotArena a, String when) {
        for (String id : GOALS) {
            var goal = LockoutBingoGoalPool.byId(id);
            if (goal == null) continue;
            LockoutGoals.Option o;
            try {
                o = LockoutGoals.planAny(a.bot(), new ObtainPlanner(), goal);
            } catch (RuntimeException e) {
                o = null;
            }
            BotArena.LOG.info("[ESTIMATES] {} {} {}", id, when, o == null ? "none" : String.valueOf(Math.round(o.cost())));
        }
    }
}
