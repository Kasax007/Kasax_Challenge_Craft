package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_4_NoChestLoot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

@Mixin(RandomizableContainerBlockEntity.class)
/** Blocks deferred chest loot population for the no-chest-loot challenge. */
public abstract class NoChestLootMixin {
    /** Prevents unopened lootable containers from receiving their deferred loot table. */
    @Inject(
            method = "setLootTable(Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelSetLootTable(@Nullable ResourceKey<LootTable> table, CallbackInfo ci) {
        if (Chal_4_NoChestLoot.isActive()) {
            ci.cancel();
        }
    }
}
