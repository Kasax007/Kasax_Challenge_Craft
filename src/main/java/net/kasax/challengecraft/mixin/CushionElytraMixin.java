package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_47_CushionOnly;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
/** Cushion Only (47): no elytra flight. */
public abstract class CushionElytraMixin {

    /**
     * {@code canGlide} is asked both when a glide starts ({@code tryToStartFallFlying}) and every
     * tick while it lasts ({@code LivingEntity.updateFallFlying}), so answering "no" here both
     * prevents take-off and ends a glide that was already running when the challenge came on.
     * Both logical sides, like every movement rule in this mod.
     */
    @Inject(method = "canGlide", at = @At("HEAD"), cancellable = true)
    private void challengecraft$noGliding(CallbackInfoReturnable<Boolean> cir) {
        if (Chal_47_CushionOnly.isRestricted((Player) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
