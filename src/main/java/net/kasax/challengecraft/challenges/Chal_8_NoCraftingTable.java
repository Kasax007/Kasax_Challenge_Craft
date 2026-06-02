package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

/** Blocks direct crafting-table interaction without touching player inventories. */
public class Chal_8_NoCraftingTable {
    private static boolean active = false;

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!active || world.isClientSide()) return InteractionResult.PASS;
            if (world.getBlockState(hit.getBlockPos()).is(Blocks.CRAFTING_TABLE)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }
    public static void setActive(boolean v) { active = v; }
    public static boolean isActive() { return active; }
}
