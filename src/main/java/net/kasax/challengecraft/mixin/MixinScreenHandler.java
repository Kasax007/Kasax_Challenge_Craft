// src/main/java/net/kasax/challengecraft/mixin/MixinScreenHandler.java
package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class MixinScreenHandler {
    @Inject(
            method = "onSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSlotClick_cancelDisabled(
            int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci
    ) {
        // only when our challenge is active
        if (!Chal_12_LimitedInventory.isActive()) return;

        // grab the handler & player‐inventory
        ScreenHandler handler = (ScreenHandler)(Object)this;
        PlayerInventory inv = player.getInventory();

        int limited   = Chal_12_LimitedInventory.getLimitedSlots();
        int toDisable = 36 - limited;
        int[] order   = Chal_12_LimitedInventory.getDeactivationOrder();

        // 1) clicking on a disabled slot → cancel
        if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
            Slot slot = handler.slots.get(slotIndex);
            // only care about slots backed by the player's own inventory
            if (slot.inventory == inv) {
                int invSlot = slot.getIndex(); // 0–35 in the player‐inventory
                for (int i = 0; i < toDisable; i++) {
                    if (order[i] == invSlot) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        // 2) number‐key swap into a disabled hotbar slot → cancel
        if (actionType == SlotActionType.SWAP && button >= 0 && button < 9) {
            for (int i = 0; i < toDisable; i++) {
                if (order[i] == button) {
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
