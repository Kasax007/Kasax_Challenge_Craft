package net.kasax.challengecraft.mixin;

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
/** Blocks deferred chest loot population for the no-chest-loot challenge. */
public abstract class NoChestLootMixin {
    /** Prevents unopened lootable containers from receiving their deferred loot table. */
    @Inject(
            method = "setLootTable(Lnet/minecraft/registry/RegistryKey;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelSetLootTable(@Nullable RegistryKey<LootTable> table, CallbackInfo ci) {
        if (Chal_4_NoChestLoot.isActive()) {
            ci.cancel();
        }
    }
}
