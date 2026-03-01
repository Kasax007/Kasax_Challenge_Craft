package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.data.XpManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long totalXp = XpManager.getTotalXp();
        Text text = Text.translatable("challengecraft.mainmenu.lifetime_xp", totalXp);
        context.drawTextWithShadow(
                this.textRenderer,
                text,
                5, 5,
                0xFFFF55
        );
    }
}
