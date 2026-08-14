package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.client.ui.CraftUI;
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
            this.minecraft.setScreenAndShow(new LevelingScreen(this));
        }));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft == null) return;

        com.mojang.blaze3d.platform.Window window = this.minecraft.getWindow();

        boolean isCommaHeld = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_COMMA);
        boolean isPeriodHeld = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_PERIOD);

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

        int level = LevelManager.getLevelForXp(totalXp);
        int stars = LevelManager.getStars(totalXp);
        String xpString = String.format(java.util.Locale.ROOT, "%,d", totalXp);
        Component badge = stars > 0
                ? Component.translatable("challengecraft.mainmenu.badge_stars", stars, xpString)
                : Component.translatable("challengecraft.mainmenu.badge", level, xpString);

        // Intentional profile badge in the corner instead of floating debug text.
        int textWidth = this.font.width(badge);
        int panelX = CraftUI.S.SM;
        int panelY = CraftUI.S.SM;
        int panelW = 22 + textWidth + 10;
        int panelH = 20;
        CraftUI.panelFloat(context, panelX, panelY, panelW, panelH, CraftUI.GOLD);
        CraftUI.gem(context, panelX + 12, panelY + panelH / 2, CraftUI.GOLD);
        context.text(this.font, badge, panelX + 22,
                panelY + (panelH - this.font.lineHeight) / 2 + 1, CraftUI.TEXT_PRIMARY, false);
    }
}
