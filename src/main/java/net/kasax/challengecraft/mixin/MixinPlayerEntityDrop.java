package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
/** Redirects selected death drops for active inventory-based challenges. */
public abstract class MixinPlayerEntityDrop {
    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAnyDrop(ItemStack stack, boolean dropAtSelf, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (!Chal_12_LimitedInventory.isActive()) return;

        LivingEntity self = (LivingEntity)(Object)this;

        if (!(self instanceof Player)) return;
        Player player = (Player)self;

        Inventory inv = player.getInventory();
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
