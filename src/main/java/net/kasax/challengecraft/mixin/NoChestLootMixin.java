// src/main/java/net/kasax/challengecraft/mixin/NoChestLootMixin.java
package net.kasax.challengecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_4_NoChestLoot;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

@Mixin(LootableContainerBlockEntity.class)
public abstract class NoChestLootMixin {
    /**
     * Whenever vanilla tries to inject the chest's loot‐table key from NBT,
     * cancel it if our "no chest loot" challenge is active.
     */
    @Inject(
            method = "setLootTable(Lnet/minecraft/registry/RegistryKey;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelSetLootTable(@Nullable RegistryKey<LootTable> table, CallbackInfo ci) {
        if (Chal_4_NoChestLoot.isActive()) {
            // never store the loot table → chest stays empty
            ci.cancel();
        }
    }
}
