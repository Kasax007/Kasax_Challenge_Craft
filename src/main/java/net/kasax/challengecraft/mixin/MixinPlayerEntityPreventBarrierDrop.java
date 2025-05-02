// src/main/java/net/kasax/challengecraft/mixin/MixinPlayerEntityPreventBarrierDrop.java
package net.kasax.challengecraft.mixin;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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
        if (!Chal_12_LimitedInventory.isActive()) return;

        PlayerInventory inv = ((PlayerEntity)(Object)this).getInventory();
        var main = inv.getMainStacks();
        int limited   = Chal_12_LimitedInventory.getLimitedSlots();
        int toDisable = 36 - limited;
        int[] order   = Chal_12_LimitedInventory.getDeactivationOrder();

        for (int i = 0; i < toDisable; i++) {
            int idx = order[i];
            ItemStack s = main.get(idx);
            // only clear the exact barrier we placed
            if (s.getItem() == Items.BARRIER) {
                Text name = s.get(DataComponentTypes.CUSTOM_NAME);
                if (name != null && "Blocked".equals(name.getString())) {
                    main.set(idx, ItemStack.EMPTY);
                }
            }
        }
    }
}

