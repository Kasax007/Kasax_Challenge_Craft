package net.kasax.challengecraft.film.mixin;

import net.kasax.challengecraft.film.Cam;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Seen from the free camera the film player is "someone else" and wears his name tag; no shot but
 * the name-tag one wants it (see {@link Cam#ownNameTag}).
 */
@Mixin(targets = "net.minecraft.client.renderer.entity.player.AvatarRenderer")
public class OwnNameTagMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void film$noOwnTag(Avatar avatar, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (avatar instanceof LocalPlayer && !Cam.ownNameTag) cir.setReturnValue(false);
    }
}
