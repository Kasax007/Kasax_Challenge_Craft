package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_21_Hardcore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @ModifyVariable(method = "drawHeart", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean forceHardcoreHeart(boolean hardcore) {
        if (Chal_21_Hardcore.isActive()) {
            return true;
        }
        return hardcore;
    }
}
