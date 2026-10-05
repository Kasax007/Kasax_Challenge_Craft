package net.kasax.challengecraft.film.mixin;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.client.screen.ChallengeTab;
import net.kasax.challengecraft.film.FilmDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** The film's worlds are created with the challenges its scene names, as if ticked in the tab. */
@Mixin(value = ChallengeTab.class, remap = false)
public class ChallengeTabFilmMixin {
    @Inject(method = "getActive", at = @At("RETURN"), cancellable = true)
    private void film$challenges(CallbackInfoReturnable<List<Integer>> cir) {
        List<Integer> forced = FilmDirector.forcedChallenges;
        if (forced == null) return;
        cir.setReturnValue(new ArrayList<>(forced));
        if (forced.contains(35)) ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER = FilmDirector.doubleTrouble;
        if (forced.contains(37)) ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER = FilmDirector.gameSpeed;
    }
}
