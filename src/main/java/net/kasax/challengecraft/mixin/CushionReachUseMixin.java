package net.kasax.challengecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.kasax.challengecraft.challenges.Chal_47_CushionOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
/** Cushion Only (47): a right-click on a cushion counts as far as the crosshair found it. */
public abstract class CushionReachUseMixin {

    /**
     * {@code startUseItem} checks the entity reach a second time, to the nearest point of the
     * target's box, before it sends the interaction. Widened by the same amount as
     * {@code CushionReachPickMixin}, or the crosshair would find a far cushion and the click would
     * then silently do nothing. The nearest point is never further than where the ray entered, so
     * whatever the pick accepted passes here too.
     */
    @WrapOperation(
            method = "startUseItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z")
    )
    private boolean challengecraft$cushionAsFarAsBlocks(LocalPlayer player, Entity entity, double buffer,
                                                        Operation<Boolean> original) {
        if (entity instanceof Cushion && Chal_47_CushionOnly.isRestricted(player)) {
            buffer += Math.max(0.0, player.blockInteractionRange() - player.entityInteractionRange());
        }
        return original.call(player, entity, buffer);
    }
}
