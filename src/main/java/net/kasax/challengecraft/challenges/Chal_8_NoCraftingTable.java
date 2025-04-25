package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.util.ActionResult;

public class Chal_8_NoCraftingTable {
    private static boolean active = false;

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!active || world.isClient) return ActionResult.PASS;
            if (world.getBlockState(hit.getBlockPos()).isOf(Blocks.CRAFTING_TABLE)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
    public static void setActive(boolean v) { active = v; }
}
