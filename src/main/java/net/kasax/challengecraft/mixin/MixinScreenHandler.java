// src/main/java/net/kasax/challengecraft/mixin/MixinScreenHandler.java
package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.kasax.challengecraft.challenges.Chal_27_NoArmor;
import net.kasax.challengecraft.block.InfiniteChestRegistry;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

        // 3) Infinite Chest Level Requirement (Level 20 and Perk active to craft)
        if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
            Slot slot = handler.slots.get(slotIndex);
            if (slot instanceof CraftingResultSlot) {
                ItemStack stack = slot.getStack();
                if (stack.isOf(InfiniteChestRegistry.INFINITE_CHEST_ITEM)) {
                    long xp = LevelManager.getPlayerXp(player);
                    int level = LevelManager.getLevelForXp(xp);
                    
                    boolean perkActive = false;
                    if (player.getWorld() instanceof ServerWorld serverWorld) {
                        ChallengeSavedData data = ChallengeSavedData.get(serverWorld.getServer().getOverworld());
                        if (data.getActivePerks().contains(LevelManager.PERK_INFINITE_CHEST)) {
                            perkActive = true;
                        }
                    }

                    if (level < 20 || !perkActive) {
                        if (!player.getWorld().isClient) {
                            String message = level < 20 ? "You must be level 20 to craft the Infinite Chest!" : "The Infinite Chest perk is not active!";
                            player.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
                        }
                        ci.cancel();
                        return;
                    }
                }
            }
        }
    }
}
