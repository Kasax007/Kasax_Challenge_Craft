// src/main/java/net/kasax/challengecraft/mixin/CreateWorldScreenMixin.java
package net.kasax.challengecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.widget.TabNavigationWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(CreateWorldScreen.class)
@Environment(EnvType.CLIENT)
public class CreateWorldScreenMixin {
    private net.kasax.challengecraft.client.screen.ChallengeTab challengeTab;

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
        var t = new net.kasax.challengecraft.client.screen.ChallengeTab();
        extended[original.length] = t;
        this.challengeTab = t;
        return extended;
    }

    @Inject(method = "createLevel", at = @At("HEAD"))
    private void onCreateLevel(CallbackInfo ci) {
        int val = this.challengeTab.getSelectedValue();
        // 1) Always stash statically for SP
        ChallengeCraftClient.LAST_CHOSEN = val;

        // 2) If in MP (network handler exists) send the packet
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            ClientPlayNetworking.send(new ChallengePacket(val));
        }
    }
}
