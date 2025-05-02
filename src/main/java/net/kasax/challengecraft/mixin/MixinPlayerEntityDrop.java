// src/main/java/net/kasax/challengecraft/mixin/MixinLivingEntityDrop.java
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
public abstract class MixinPlayerEntityDrop {
    @Inject(
            method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAnyDrop(ItemStack stack, boolean dropAtSelf, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        // only when our challenge is active
        if (!Chal_12_LimitedInventory.isActive()) return;

        // cast the mixin-instance back to LivingEntity
        LivingEntity self = (LivingEntity)(Object)this;

        // only for real players
        if (!(self instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity)self;

        PlayerInventory inv = player.getInventory();
        int sel         = inv.getSelectedSlot();     // 0–8 hotbar index
        int limited     = Chal_12_LimitedInventory.getLimitedSlots();
        int toDisable   = 36 - limited;
        int[] order     = Chal_12_LimitedInventory.getDeactivationOrder();

        // if our selected hotbar‐index is in the disabled set, block the drop:
        for (int i = 0; i < toDisable; i++) {
            if (order[i] < 9 && order[i] == sel) {
                // cancel the drop entirely
                cir.setReturnValue(null);
                return;
            }
        }
    }
}
