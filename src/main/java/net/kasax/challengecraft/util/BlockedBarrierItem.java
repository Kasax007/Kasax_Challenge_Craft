package net.kasax.challengecraft.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class BlockedBarrierItem {
    private static final String BLOCKED_NAME = "Blocked";

    private BlockedBarrierItem() {
    }

    public static ItemStack create() {
        ItemStack blocked = new ItemStack(Items.BARRIER);
        blocked.set(DataComponentTypes.CUSTOM_NAME, Text.literal(BLOCKED_NAME));
        return blocked;
    }

    public static boolean isBlockedBarrier(ItemStack stack) {
        if (stack == null || !stack.isOf(Items.BARRIER)) {
            return false;
        }

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name != null && BLOCKED_NAME.equals(name.getString());
    }
}
