package net.kasax.challengecraft.film.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The crosshair belongs to the player's own eyes: under the film's free camera it would sit on whatever is in the middle of the frame. */
@Mixin(targets = "net.minecraft.client.gui.Hud")
public class NoCrosshairMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void film$noCrosshair(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getCameraEntity() != mc.player) ci.cancel();
    }
}
