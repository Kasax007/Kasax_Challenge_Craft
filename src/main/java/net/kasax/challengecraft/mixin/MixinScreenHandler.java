package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.kasax.challengecraft.challenges.Chal_27_NoArmor;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.block.InfiniteChestRegistry;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
/** Prevents blocked inventory slots from being moved through vanilla screen handlers. */
public abstract class MixinScreenHandler {
    @Inject(
            method = "clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSlotClick_cancelDisabled(
            int slotIndex, int button, ContainerInput actionType, Player player, CallbackInfo ci
    ) {
        AbstractContainerMenu handler = (AbstractContainerMenu)(Object)this;
        Inventory inv = player.getInventory();

        Chal_40_LockoutBingo.handleScreenSlotClick(player, handler, slotIndex);

        if (Chal_12_LimitedInventory.isActive()) {
            int limited   = Chal_12_LimitedInventory.getLimitedSlots();
            int toDisable = 36 - limited;
            int[] order   = Chal_12_LimitedInventory.getDeactivationOrder();

            if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
                Slot slot = handler.slots.get(slotIndex);
                if (slot.container == inv) {
                    int invSlot = slot.getContainerSlot();
                    for (int i = 0; i < toDisable; i++) {
                        if (order[i] == invSlot) {
                            ci.cancel();
                            return;
                        }
                    }
                }
            }

            if (actionType == ContainerInput.SWAP && button >= 0 && button < 9) {
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
                if (slot.container == inv) {
                    int invSlot = slot.getContainerSlot();
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
            if (slot instanceof ResultSlot) {
                ItemStack stack = slot.getItem();
                if (stack.is(InfiniteChestRegistry.INFINITE_CHEST_ITEM)) {
                    long xp = LevelManager.getPlayerXp(player);
                    int level = LevelManager.getLevelForXp(xp);
                    
                    boolean perkActive = false;
                    if (player.level() instanceof ServerLevel serverWorld) {
                        ChallengeSavedData data = ChallengeSavedData.get(serverWorld.getServer().overworld());
                        if (data.getActivePerks().contains(LevelManager.PERK_INFINITE_CHEST)) {
                            perkActive = true;
                        }
                    }

                    if (level < 20 || !perkActive) {
                        if (!player.level().isClientSide()) {
                            var message = level < 20
                                    ? Component.translatable("challengecraft.infinite_chest.require_level")
                                    : Component.translatable("challengecraft.infinite_chest.require_perk");
                            player.sendOverlayMessage(message.withStyle(ChatFormatting.RED));
                        }
                        ci.cancel();
                        return;
                    }
                }
            }
        }
    }
}
