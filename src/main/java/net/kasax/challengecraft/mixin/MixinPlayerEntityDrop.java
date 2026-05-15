package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
/** Redirects selected death drops for active inventory-based challenges. */
public abstract class MixinPlayerEntityDrop {
    @Inject(
            method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAnyDrop(ItemStack stack, boolean dropAtSelf, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (!Chal_12_LimitedInventory.isActive()) return;

        LivingEntity self = (LivingEntity)(Object)this;

        if (!(self instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity)self;

        PlayerInventory inv = player.getInventory();
        int sel         = inv.getSelectedSlot();
        int limited     = Chal_12_LimitedInventory.getLimitedSlots();
        int toDisable   = 36 - limited;
        int[] order     = Chal_12_LimitedInventory.getDeactivationOrder();

        for (int i = 0; i < toDisable; i++) {
            if (order[i] < 9 && order[i] == sel) {
                cir.setReturnValue(null);
                return;
            }
        }
    }
}
