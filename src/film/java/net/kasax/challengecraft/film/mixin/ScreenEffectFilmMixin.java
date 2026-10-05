package net.kasax.challengecraft.film.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fire, water and block overlays belong to the player's own eyes, not to the film's free camera. */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectFilmMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void film$onlyOwnEyes(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getCameraEntity() != mc.player) ci.cancel();
    }
}
