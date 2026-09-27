package net.kasax.challengecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.kasax.challengecraft.challenges.Chal_47_CushionOnly;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Cushion.class)
/** Cushion Only (47): a cushion the player breaks simply disappears. */
public abstract class CushionBreakMixin {

    /**
     * Placing never uses a cushion up ({@code CushionInfiniteMixin}), so dropping one on break would
     * only print new cushions - every hop would leave another item lying around. Vanilla already
     * does exactly this for creative players ({@code hasInfiniteMaterials}); this extends it to a
     * restricted player's own breaks. Fire, lava and lightning still drop as usual.
     */
    @WrapOperation(
            method = "dropItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/decoration/Cushion;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;")
    )
    private @Nullable ItemEntity challengecraft$noDrop(Cushion self, ServerLevel level, ItemStack stack,
                                                       Operation<ItemEntity> original,
                                                       @Local(argsOnly = true) @Nullable Entity causedBy) {
        if (causedBy instanceof Player player && Chal_47_CushionOnly.isRestricted(player)) {
            return null;
        }
        return original.call(self, level, stack);
    }
}
