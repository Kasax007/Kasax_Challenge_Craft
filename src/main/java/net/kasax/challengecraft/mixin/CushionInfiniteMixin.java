package net.kasax.challengecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.kasax.challengecraft.challenges.Chal_47_CushionOnly;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CushionItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CushionItem.class)
/** Cushion Only (47): placing a cushion never uses one up. */
public abstract class CushionInfiniteMixin {

    /**
     * The cushion is the only way to move, so counting them only ever produced chores - fetching a
     * cushion back after every hop. With the stack never shrinking, a stray cushion costs nothing.
     * Only the consume is skipped; everything else about placing stays vanilla, including the
     * normal block reach (which {@code CushionReachPickMixin} lets sitting down match).
     */
    @WrapOperation(
            method = "useOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V")
    )
    private void challengecraft$keepCushion(ItemStack stack, int amount, LivingEntity user, Operation<Void> original) {
        if (user instanceof Player player && Chal_47_CushionOnly.isRestricted(player)) {
            return;
        }
        original.call(stack, amount, user);
    }
}
