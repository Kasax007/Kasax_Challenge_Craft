package net.kasax.challengecraft.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Drops an item at a player's feet on the server's initiative, exactly as 26.2 did.
 *
 * <p>26.3 removed {@code Player.drop(ItemStack, boolean)} and made the remaining
 * {@code LivingEntity.drop(ItemStack, boolean, Prediction)} swing the main arm on the SERVER too —
 * with either {@code Prediction} value; the flag only decides whether the swinging player receives
 * the animation packet. In 26.2 the swing only ever ran client-side, so a server-driven drop was
 * silent. Every drop the mod does is server-driven (a reward that did not fit, armour forced off, a
 * lost bingo map handed back), and none of them was the player's own gesture, so a visible arm
 * swing would be a new, wrong cue. This goes straight to the half of {@code drop} that spawns the
 * entity.
 */
public final class ServerDrops {
    private ServerDrops() {
    }

    public static @Nullable ItemEntity dropQuietly(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty() || entity.level().isClientSide()) {
            return null;
        }
        ItemEntity item = entity.createItemStackToDrop(stack, false, false);
        if (item != null) {
            entity.level().addFreshEntity(item);
        }
        return item;
    }
}
