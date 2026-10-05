package net.kasax.challengecraft.film.mixin;

import net.kasax.challengecraft.film.Cam;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player keeps walking on the held keys while the film's free camera looks on from elsewhere
 * - only in the scenes that ask for it ({@link Cam#freeWalk}): otherwise the player stays where
 * the scene put it, as the game itself leaves a player that is not the camera.
 */
@Mixin(LocalPlayer.class)
public class FreeCamInputMixin {
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true, require = 0)
    private void film$controlled(CallbackInfoReturnable<Boolean> cir) {
        if (Cam.freeWalk) cir.setReturnValue(true);
    }
}
