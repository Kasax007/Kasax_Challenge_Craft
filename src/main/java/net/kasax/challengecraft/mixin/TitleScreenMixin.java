package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.data.XpManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.client.widget.AnimatedLevelButton;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
/** Adds the progression screen button to the title screen. */
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int x = this.width / 2 - 100;
        int y = this.height / 4 + 48 - 24;
        this.addRenderableWidget(new AnimatedLevelButton(x, y, 200, 20, Component.translatable("challengecraft.mainmenu.leveling_button"), button -> {
            this.minecraft.setScreen(new LevelingScreen(this));
        }));
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft == null) return;

        boolean isCommaHeld = InputConstants.isKeyDown(this.minecraft.getWindow(), GLFW.GLFW_KEY_COMMA);
        boolean isPeriodHeld = InputConstants.isKeyDown(this.minecraft.getWindow(), GLFW.GLFW_KEY_PERIOD);

        // Hidden dev shortcut kept away from normal title-screen clicks.
        if (event.x() <= 10 && event.y() <= 10 && isCommaHeld && isPeriodHeld) {
            UUID uuid = this.minecraft.getUser().getProfileId();
            if (uuid != null) {
                XpManager.addXp(uuid, 500);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long totalXp;
        if (this.minecraft != null && this.minecraft.getUser() != null) {
            totalXp = XpManager.getXp(this.minecraft.getUser().getProfileId());
        } else {
            totalXp = XpManager.getTotalXp();
        }
        Component text = Component.translatable("challengecraft.mainmenu.lifetime_xp", totalXp);
        context.text(
                this.font,
                text,
                5, 5,
                0xFFFF55
        );
    }
}
