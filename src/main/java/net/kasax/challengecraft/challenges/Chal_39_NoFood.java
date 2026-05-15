package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class Chal_39_NoFood {
    private static final Text BLOCKED_MESSAGE = Text.translatable("challengecraft.worldcreate.challenge39.blocked");
    private static boolean active = false;

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!active) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);
            if (!stack.contains(DataComponentTypes.FOOD)) {
                return ActionResult.PASS;
            }

            if (!world.isClient) {
                player.sendMessage(BLOCKED_MESSAGE, true);
            }
            return ActionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!active || !world.getBlockState(hit.getBlockPos()).isOf(Blocks.CAKE)) {
                return ActionResult.PASS;
            }

            if (player.getStackInHand(hand).isIn(ItemTags.CANDLES)) {
                return ActionResult.PASS;
            }

            if (!world.isClient) {
                player.sendMessage(BLOCKED_MESSAGE, true);
            }
            return ActionResult.FAIL;
        });
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
