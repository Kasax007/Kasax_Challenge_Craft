package net.kasax.challengecraft.film.mixin;

import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recipe and advancement toasts pop up over the shots (a picked-up axe unlocks a recipe); the film shows none. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager")
public class NoToastMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true, require = 0)
    private void film$noToast(Toast toast, CallbackInfo ci) {
        ci.cancel();
    }
}
