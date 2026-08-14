package net.kasax.challengecraft.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Creates and identifies the synthetic barrier items used for blocked equipment slots. */
public final class BlockedBarrierItem {
    private static final String BLOCKED_NAME_KEY = "challengecraft.blocked_barrier";

    private BlockedBarrierItem() {
    }

    public static ItemStack create() {
        ItemStack blocked = new ItemStack(Items.BARRIER);
        blocked.set(DataComponents.CUSTOM_NAME, Component.translatable(BLOCKED_NAME_KEY));
        return blocked;
    }

    /**
     * Stops a blocked barrier from ever being placed as a real block.
     *
     * <p>This guard used to live in {@link net.kasax.challengecraft.challenges.Chal_12_LimitedInventory},
     * gated on that one challenge being active. But No Armor hands out the very same synthetic
     * barriers, and its copies could be dragged out of the armour slots and right-clicked into the
     * world — a free barrier block, from a challenge meant to take something away. These stacks are
     * a UI device this mod invents; none of them should ever be placeable, whichever challenge
     * created them, so the check belongs with the item rather than with a challenge. Real barriers
     * from creative are untouched: they carry no blocked name.
     */
    public static void register() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                isBlockedBarrier(player.getItemInHand(hand))
                        ? net.minecraft.world.InteractionResult.FAIL
                        : net.minecraft.world.InteractionResult.PASS);
    }

    public static boolean isBlockedBarrier(ItemStack stack) {
        if (stack == null || !stack.is(Items.BARRIER)) {
            return false;
        }

        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name != null && Component.translatable(BLOCKED_NAME_KEY).getString().equals(name.getString());
    }
}
