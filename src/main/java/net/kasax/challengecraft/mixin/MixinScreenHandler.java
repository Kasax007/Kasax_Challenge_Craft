package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.kasax.challengecraft.challenges.Chal_27_NoArmor;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
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
/** Prevents blocked inventory slots from being moved through vanilla screen handlers. */
public abstract class MixinScreenHandler {
    @Inject(
            method = "onSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSlotClick_cancelDisabled(
            int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci
    ) {
        ScreenHandler handler = (ScreenHandler)(Object)this;
        PlayerInventory inv = player.getInventory();

        Chal_40_LockoutBingo.handleScreenSlotClick(player, handler, slotIndex);

        if (Chal_12_LimitedInventory.isActive()) {
            int limited   = Chal_12_LimitedInventory.getLimitedSlots();
            int toDisable = 36 - limited;
            int[] order   = Chal_12_LimitedInventory.getDeactivationOrder();

            if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
                Slot slot = handler.slots.get(slotIndex);
                if (slot.inventory == inv) {
                    int invSlot = slot.getIndex();
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

        // The recipe stays visible, but the result is gated by progression and the selected perk.
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
                            var message = level < 20
                                    ? Text.translatable("challengecraft.infinite_chest.require_level")
                                    : Text.translatable("challengecraft.infinite_chest.require_perk");
                            player.sendMessage(message.formatted(Formatting.RED), true);
                        }
                        ci.cancel();
                        return;
                    }
                }
            }
        }
    }
}
