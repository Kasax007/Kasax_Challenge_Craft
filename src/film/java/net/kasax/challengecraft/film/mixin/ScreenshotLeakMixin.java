package net.kasax.challengecraft.film.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/**
 * Fabric's client game test hands every screenshot to a consumer that saves it and never closes
 * it: 8 MB of native memory per 1080x1920 frame, so a long shot ran the client out of memory.
 * The film frees each image once it is on disk.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.gametest.context.ClientGameTestContextImpl", remap = false)
public class ScreenshotLeakMixin {
    @Inject(method = "saveScreenshot", at = @At("RETURN"))
    private static void film$free(NativeImage image, String name,
                                  net.fabricmc.fabric.impl.client.gametest.screenshot.TestScreenshotCommonOptionsImpl<?> options, CallbackInfoReturnable<Path> cir) {
        image.close();
    }
}
