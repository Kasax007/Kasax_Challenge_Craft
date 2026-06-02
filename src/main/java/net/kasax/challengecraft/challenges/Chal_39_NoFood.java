package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Blocks edible items and cake interaction while leaving other use actions alone. */
public class Chal_39_NoFood {
    private static final Component BLOCKED_MESSAGE = Component.translatable("challengecraft.worldcreate.challenge39.blocked");
    private static boolean active = false;

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!active) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            if (!stack.has(DataComponents.FOOD)) {
                return InteractionResult.PASS;
            }

            if (!world.isClientSide()) {
                player.sendOverlayMessage(BLOCKED_MESSAGE);
            }
            return InteractionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!active || !world.getBlockState(hit.getBlockPos()).is(Blocks.CAKE)) {
                return InteractionResult.PASS;
            }

            if (player.getItemInHand(hand).is(ItemTags.CANDLES)) {
                return InteractionResult.PASS;
            }

            if (!world.isClientSide()) {
                player.sendOverlayMessage(BLOCKED_MESSAGE);
            }
            return InteractionResult.FAIL;
        });
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
