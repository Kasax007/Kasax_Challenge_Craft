package net.kasax.challengecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.client.screen.ChallengeTab;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.widget.TabNavigationWidget;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(CreateWorldScreen.class)
@Environment(EnvType.CLIENT)
public class CreateWorldScreenMixin {
    private ChallengeTab challengeTab;

    // 1) Append our Challenges tab
    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/TabNavigationWidget$Builder;tabs([Lnet/minecraft/client/gui/tab/Tab;)Lnet/minecraft/client/gui/widget/TabNavigationWidget$Builder;"
            ),
            index = 0
    )
    private Tab[] addChallengeTab(Tab[] original) {
        Tab[] extended = Arrays.copyOf(original, original.length + 1);
        ChallengeTab t = new ChallengeTab();
        extended[original.length] = t;
        this.challengeTab = t;
        return extended;
    }

    // 2) Send the selected value when “Create” is invoked
    @Inject(method = "createLevel", at = @At("HEAD"))
    private void onCreateLevel(CallbackInfo ci) {
        if (this.challengeTab != null) {
            ClientPlayNetworking.send(new ChallengePacket(this.challengeTab.getSelectedValue()));
        }
    }
}
