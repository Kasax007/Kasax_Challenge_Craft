package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_21_Hardcore;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Draws hardcore-style hearts while the Hardcore challenge is on.
 *
 * <p>26.2 split the old {@code Gui} in two: {@code Gui} now hosts the screen, and the actual HUD
 * drawing moved to {@code Hud} — so {@code Gui.renderHeart} became {@code Hud.extractHeart}, in
 * line with the render/extract rename everywhere else.
 *
 * <p>{@code ordinal = 0} still targets the hardcore flag, which was verified rather than assumed:
 * {@code extractHeart} calls {@code HeartType.getSprite(b1, b3, b2)}, and {@code getSprite} branches
 * to the hardcore sprites on its FIRST parameter. So the first boolean argument is still
 * {@code hardcore} — had the order changed, this would have silently forced "blinking" or "half"
 * instead, which compiles and merely looks wrong.
 */
@Mixin(Hud.class)
public abstract class InGameHudMixin {
    @ModifyVariable(method = "extractHeart", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean forceHardcoreHeart(boolean hardcore) {
        if (Chal_21_Hardcore.isActive()) {
            return true;
        }
        return hardcore;
    }
}
