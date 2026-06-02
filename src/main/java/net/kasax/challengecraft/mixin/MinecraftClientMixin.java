package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.network.RestartManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
/** Adds title-screen entry points and client-side XP preload hooks. */
public abstract class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {
        if (RestartManager.isRestartPending() && screen != null) {
            if (!(screen instanceof net.kasax.challengecraft.client.screen.RestartingScreen)) {
                Minecraft client = (Minecraft) (Object) this;
                client.setScreen(new net.kasax.challengecraft.client.screen.RestartingScreen());
                ci.cancel();
            }
        }
    }
}
