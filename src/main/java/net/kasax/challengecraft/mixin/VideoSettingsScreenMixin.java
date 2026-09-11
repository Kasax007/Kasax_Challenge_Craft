package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.client.screen.TimerSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the run timer's settings where a player would look for them: Video Settings.
 *
 * <p>Vanilla's video screen is a plain {@code OptionsSubScreen} with an {@code OptionsList} — there
 * is no tab bar to add a tab to — so the entry is a full-width row at the end of the list, which is
 * how vanilla itself leads into a sub-screen from an options page.
 *
 * <p>Appended at {@code TAIL} of {@code addOptions} rather than injected mid-list on purpose: any
 * other mod adding its own rows still gets its own, and a vanilla reshuffle of the video options
 * cannot break the injection point.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    private VideoSettingsScreenMixin(net.minecraft.client.gui.screens.Screen parent,
                                     net.minecraft.client.Options options, Component title) {
        super(parent, options, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void challengecraft$addTimerRow(CallbackInfo ci) {
        OptionsList list = this.list;
        if (list == null) {
            return;
        }
        list.addBig(Button.builder(
                        Component.translatable("challengecraft.timer.open"),
                        b -> Minecraft.getInstance().setScreenAndShow(
                                new TimerSettingsScreen((VideoSettingsScreen) (Object) this)))
                .build());
    }
}
