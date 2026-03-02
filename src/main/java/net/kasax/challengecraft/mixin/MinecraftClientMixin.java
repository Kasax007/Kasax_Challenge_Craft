package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.network.RestartManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        if (RestartManager.isRestartPending() && screen != null) {
            if (!(screen instanceof net.kasax.challengecraft.client.screen.RestartingScreen)) {
                MinecraftClient client = (MinecraftClient) (Object) this;
                client.setScreen(new net.kasax.challengecraft.client.screen.RestartingScreen());
                ci.cancel();
            }
        }
    }
}
