package net.kasax.challengecraft.film.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game only draws the player when the camera is the player's own (third person). The film's
 * free camera is an invisible armor stand, so the player was missing from every shot of a
 * challenge taken from outside; the film draws it like any other entity - and always: not even
 * the frustum test or a chunk section that has not been compiled yet (a player running into new
 * ground) may take the player out of a shot.
 */
@Mixin(targets = "net.minecraft.client.renderer.extract.LevelExtractor")
public class FreeCamPlayerMixin {
    @ModifyExpressionValue(method = "extractVisibleEntities",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/client/player/LocalPlayer"), require = 0)
    private boolean film$drawPlayer(boolean isLocalPlayer) {
        return false;
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true, require = 0)
    private void film$playerAlwaysVisible(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                          float partialTick, long fadeInMillis, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LocalPlayer && Minecraft.getInstance().getCameraEntity() != entity) {
            cir.setReturnValue(true);
        }
    }
}
