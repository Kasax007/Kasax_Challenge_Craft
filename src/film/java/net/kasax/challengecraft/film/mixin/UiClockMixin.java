package net.kasax.challengecraft.film.mixin;

import net.kasax.challengecraft.film.FilmClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The mod's client UI reads the film's clock instead of the wall clock (see {@link FilmClock#millis()}). */
@Mixin(targets = {
        "net.kasax.challengecraft.casino.client.BlackjackScreen",
        "net.kasax.challengecraft.casino.client.CashierScreen",
        "net.kasax.challengecraft.casino.client.CasinoClient",
        "net.kasax.challengecraft.casino.client.CasinoClientState",
        "net.kasax.challengecraft.casino.client.CasinoHud",
        "net.kasax.challengecraft.casino.client.RouletteScreen",
        "net.kasax.challengecraft.client.ui.Anim",
        "net.kasax.challengecraft.client.ui.Anim$Tween",
        "net.kasax.challengecraft.client.ui.HudStack",
        "net.kasax.challengecraft.client.screen.ChallengeRewardOverlay",
        "net.kasax.challengecraft.client.screen.ForceItemClientState",
        "net.kasax.challengecraft.client.screen.ForceItemResultsScreen",
        "net.kasax.challengecraft.client.screen.LevelingScreen",
        "net.kasax.challengecraft.client.screen.LockoutBingoClientState",
        "net.kasax.challengecraft.client.screen.RedLightHUD",
        "net.kasax.challengecraft.client.screen.TimerOverlay",
        "net.kasax.challengecraft.client.screen.TriviaScreen"
}, remap = false)
public class UiClockMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), require = 0)
    private static long film$clock() {
        return FilmClock.millis();
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"), require = 0)
    private static long film$nanos() {
        return FilmClock.millis() * 1_000_000L;
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"), require = 0)
    private static long film$gameMillis() {
        return FilmClock.millis();
    }
}
