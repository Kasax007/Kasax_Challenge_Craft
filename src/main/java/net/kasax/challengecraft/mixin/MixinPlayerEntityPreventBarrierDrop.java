package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.kasax.challengecraft.challenges.Chal_27_NoArmor;
import net.kasax.challengecraft.util.BlockedBarrierItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
/** Removes synthetic slot blockers before vanilla death-drop handling runs. */
public abstract class MixinPlayerEntityPreventBarrierDrop {
    /** Removes synthetic blocker items before vanilla turns inventory contents into drops. */
    @Inject(
            method = "dropInventory(Lnet/minecraft/server/world/ServerWorld;)V",
            at = @At("HEAD")
    )
    private void onDropInventory(ServerWorld world, CallbackInfo ci) {
        PlayerInventory inv = ((PlayerEntity)(Object)this).getInventory();

        if (Chal_12_LimitedInventory.isActive()) {
            var main = inv.getMainStacks();
            int limited   = Chal_12_LimitedInventory.getLimitedSlots();
            int toDisable = 36 - limited;
            int[] order   = Chal_12_LimitedInventory.getDeactivationOrder();

            for (int i = 0; i < toDisable; i++) {
                int idx = order[i];
                ItemStack s = main.get(idx);
                if (BlockedBarrierItem.isBlockedBarrier(s)) {
                    main.set(idx, ItemStack.EMPTY);
                }
            }
        }

        if (Chal_27_NoArmor.isActive()) {
            for (int i = 36; i <= 39; i++) {
                ItemStack s = inv.getStack(i);
                if (BlockedBarrierItem.isBlockedBarrier(s)) {
                    inv.setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }
}

