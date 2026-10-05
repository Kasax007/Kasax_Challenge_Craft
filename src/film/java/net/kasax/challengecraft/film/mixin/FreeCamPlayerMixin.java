package net.kasax.challengecraft.film.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The game only draws the player when the camera is the player's own (third person). The film's
 * free camera is an invisible armor stand, so the player was missing from every shot of a
 * challenge taken from outside; the film draws it like any other entity.
 */
@Mixin(targets = "net.minecraft.client.renderer.extract.LevelExtractor")
public class FreeCamPlayerMixin {
    @ModifyExpressionValue(method = "extractVisibleEntities",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/client/player/LocalPlayer"), require = 0)
    private boolean film$drawPlayer(boolean isLocalPlayer) {
        return false;
    }
}
