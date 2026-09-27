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
import org.spongepowered.asm.mixin.Mixin;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
/** Adds the Challenge Hub (or, on narrow windows, the level button and daily card) to the title screen. */
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /** Whether this init laid out the Challenge Hub (then the corner level badge is not drawn). */
    private boolean challengecraft$hub;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int x = this.width / 2 - 100;
        int y = this.height / 4 + 48 - 24;

        // The hub: one column right of the vanilla buttons. Only where it fits — at GUI scale 4 on
        // 1080p there is no room for it, and the compact arrangement below stays.
        int hubX = x + 200 + 12;
        this.challengecraft$hub = net.kasax.challengecraft.client.widget.ChallengeHub.build(
                (TitleScreen) (Object) this, this::addRenderableWidget,
                hubX, y, this.width - hubX - 8, this.height - y - 14);
        if (this.challengecraft$hub) {
            return;
        }

        this.addRenderableWidget(new AnimatedLevelButton(x, y, 200, 20, Component.translatable("challengecraft.mainmenu.leveling_button"), button -> {
            this.minecraft.setScreenAndShow(new LevelingScreen(this));
        }));

        // Today's daily, to the right of the button column. This is the only place a player ever
        // finds out dailies exist — behind a menu or a command nobody would.
        var card = new net.kasax.challengecraft.client.widget.DailyCardWidget(
                x + 200 + 12, y);
        // Only when it actually fits: on a narrow window it would sit over the buttons or run off
        // the edge, and a menu that breaks at 4x GUI scale is worse than one without a card.
        if (card.getX() + net.kasax.challengecraft.client.widget.DailyCardWidget.WIDTH <= this.width - 4) {
            this.addRenderableWidget(card);
            // A friend's challenge code, straight into world creation with everything filled in.
            int codeY = y + net.kasax.challengecraft.client.widget.DailyCardWidget.HEIGHT + 4;
            if (codeY + 20 <= this.height - 4) {
                this.addRenderableWidget(new net.kasax.challengecraft.client.widget.CraftButton(card.getX(), codeY,
                        net.kasax.challengecraft.client.widget.DailyCardWidget.WIDTH, 20,
                        Component.translatable("challengecraft.code.play"),
                        net.kasax.challengecraft.client.widget.CraftButton.Style.NEUTRAL,
                        b -> this.minecraft.setScreenAndShow(new net.kasax.challengecraft.client.screen.ChallengeCodeScreen(
                                this, null, Component.translatable("challengecraft.code.hint.title"), code -> {
                                    net.kasax.challengecraft.client.ChallengeCodeClient.arm(code);
                                    net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(this.minecraft, () -> {
                                        net.kasax.challengecraft.client.ChallengeCodeClient.clear();
                                        this.minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.TitleScreen());
                                    });
                                }))));
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft == null) return;

        // 26.3: GLFW replaced by SDL; InputConstants.isKeyDown reads the global SDL keyboard
        // state directly and no longer takes a Window, and GLFW_KEY_* moved onto InputConstants.
        boolean isCommaHeld = InputConstants.isKeyDown(InputConstants.KEY_COMMA);
        boolean isPeriodHeld = InputConstants.isKeyDown(InputConstants.KEY_PERIOD);

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
        if (this.challengecraft$hub) {
            return;   // the hub's level card says the same, bigger
        }
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
