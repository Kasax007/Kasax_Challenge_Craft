// src/main/java/net/kasax/challengecraft/mixin/MixinScreenHandler.java
package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.kasax.challengecraft.challenges.Chal_27_NoArmor;
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
        // grab the handler & player‐inventory
        ScreenHandler handler = (ScreenHandler)(Object)this;
        PlayerInventory inv = player.getInventory();

        // 1) Challenge 12: Limited Inventory
        if (Chal_12_LimitedInventory.isActive()) {
            int limited   = Chal_12_LimitedInventory.getLimitedSlots();
            int toDisable = 36 - limited;
            int[] order   = Chal_12_LimitedInventory.getDeactivationOrder();

            if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
                Slot slot = handler.slots.get(slotIndex);
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

            if (actionType == SlotActionType.SWAP && button >= 0 && button < 9) {
                for (int i = 0; i < toDisable; i++) {
                    if (order[i] == button) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        // 2) Challenge 27: No Armor
        if (Chal_27_NoArmor.isActive()) {
            if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
                Slot slot = handler.slots.get(slotIndex);
                if (slot.inventory == inv) {
                    int invSlot = slot.getIndex();
                    if (invSlot >= 36 && invSlot <= 39) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }
    }
}
