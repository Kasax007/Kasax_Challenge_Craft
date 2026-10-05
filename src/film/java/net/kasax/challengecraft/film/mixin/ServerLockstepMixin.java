package net.kasax.challengecraft.film.mixin;

import net.kasax.challengecraft.film.FilmClock;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** While a scene is filmed, the server ticks only as often as the client does (see {@link FilmClock}). */
@Mixin(MinecraftServer.class)
public abstract class ServerLockstepMixin {
    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void film$lockstep(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (!FilmClock.mayTick()) {
            ci.cancel();
            // Behind schedule, the server loop comes straight back; do not spin a core on it.
            java.util.concurrent.locks.LockSupport.parkNanos(300_000L);
        }
    }
}
