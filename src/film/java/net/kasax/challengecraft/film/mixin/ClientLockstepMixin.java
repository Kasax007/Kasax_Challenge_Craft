package net.kasax.challengecraft.film.mixin;

import net.kasax.challengecraft.film.FilmClock;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a scene is filmed, the client simulates only the ticks the director asks for (see
 * {@link FilmClock#mayTickClient()}): the frames of a screenshot run the render loop, and the
 * render loop ticks, which made everything the client moves by itself (the player's own walk,
 * particles, limb swing) run about four times as fast as the server and the shot's clock.
 */
@Mixin(Minecraft.class)
public class ClientLockstepMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void film$lockstep(CallbackInfo ci) {
        if (!FilmClock.mayTickClient()) ci.cancel();
    }
}
