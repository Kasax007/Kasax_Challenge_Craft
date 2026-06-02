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

    public static boolean isBlockedBarrier(ItemStack stack) {
        if (stack == null || !stack.is(Items.BARRIER)) {
            return false;
        }

        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name != null && Component.translatable(BLOCKED_NAME_KEY).getString().equals(name.getString());
    }
}
