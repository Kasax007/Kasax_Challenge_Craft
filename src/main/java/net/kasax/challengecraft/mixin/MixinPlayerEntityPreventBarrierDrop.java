package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.kasax.challengecraft.challenges.Chal_27_NoArmor;
import net.kasax.challengecraft.util.BlockedBarrierItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
/** Removes synthetic slot blockers before vanilla death-drop handling runs. */
public abstract class MixinPlayerEntityPreventBarrierDrop {
    /** Removes synthetic blocker items before vanilla turns inventory contents into drops. */
    @Inject(
            method = "dropEquipment(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At("HEAD")
    )
    private void onDropInventory(ServerLevel world, CallbackInfo ci) {
        Inventory inv = ((Player)(Object)this).getInventory();

        if (Chal_12_LimitedInventory.isActive()) {
            var main = inv.getNonEquipmentItems();
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
                ItemStack s = inv.getItem(i);
                if (BlockedBarrierItem.isBlockedBarrier(s)) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }
}

