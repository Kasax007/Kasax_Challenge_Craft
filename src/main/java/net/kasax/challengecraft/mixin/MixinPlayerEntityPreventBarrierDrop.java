// src/main/java/net/kasax/challengecraft/mixin/MixinPlayerEntityPreventBarrierDrop.java
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
public abstract class MixinPlayerEntityPreventBarrierDrop {
    /**
     * Before the player’s inventory is dumped on death, remove
     * any of our “Blocked”‐barrier stacks so they never spawn as
     * dropped items.
     */
    @Inject(
            method = "dropInventory(Lnet/minecraft/server/world/ServerWorld;)V",
            at = @At("HEAD")
    )
    private void onDropInventory(ServerWorld world, CallbackInfo ci) {
        PlayerInventory inv = ((PlayerEntity)(Object)this).getInventory();

        // 1) Challenge 12
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

        // 2) Challenge 27
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

