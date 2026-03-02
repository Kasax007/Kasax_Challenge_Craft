package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.client.widget.AnimatedLevelButton;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import java.util.UUID;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int x = this.width / 2 - 100;
        int y = this.height / 4 + 48 - 24;
        this.addDrawableChild(new AnimatedLevelButton(x, y, 200, 20, Text.translatable("challengecraft.mainmenu.leveling_button"), button -> {
            this.client.setScreen(new LevelingScreen(this));
        }));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.client == null) return;

        long windowHandle = this.client.getWindow().getHandle();

        boolean isCommaHeld = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_COMMA);
        boolean isPeriodHeld = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_PERIOD);

        // Top-left 10x10 corner while holding , and .
        if (mouseX <= 10 && mouseY <= 10 && isCommaHeld && isPeriodHeld) {
            UUID uuid = this.client.getSession().getUuidOrNull();
            if (uuid != null) {
                XpManager.addXp(uuid, 500);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long totalXp;
        if (this.client != null && this.client.getSession() != null) {
            totalXp = XpManager.getXp(this.client.getSession().getUuidOrNull());
        } else {
            totalXp = XpManager.getTotalXp();
        }
        Text text = Text.translatable("challengecraft.mainmenu.lifetime_xp", totalXp);
        context.drawTextWithShadow(
                this.textRenderer,
                text,
                5, 5,
                0xFFFF55
        );
    }
}
