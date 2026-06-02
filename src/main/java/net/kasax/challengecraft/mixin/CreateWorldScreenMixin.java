package net.kasax.challengecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

@Mixin(CreateWorldScreen.class)
@Environment(EnvType.CLIENT)
/** Adds the challenge setup tab to vanilla world creation. */
public class CreateWorldScreenMixin {
    private net.kasax.challengecraft.client.screen.ChallengeTab challengeTab;

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"
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

    @Inject(method = "createLevel", at = @At("HEAD"), cancellable = true)
    private void onCreateLevel(CallbackInfo ci) {
        List<Integer> chosen = this.challengeTab.getActive();
        List<Integer> perks = this.challengeTab.getSelectedPerks();

        if (net.kasax.challengecraft.ChallengeManager.hasConflict(chosen, perks)) {
            ChallengeCraft.LOGGER.info("[Client:CreateWorld] Blocked world creation due to challenge conflicts: challenges={}, perks={}", chosen, perks);
            ci.cancel();
            return;
        }

        ChallengeCraft.LOGGER.info("[Client:CreateWorld] chosen challenges = {} , perks = {}, maxHearts = {}, Inventory = {}, MobHealth = {}, GameSpeed = {}",
                chosen, perks, ChallengeCraftClient.SELECTED_MAX_HEARTS, ChallengeCraftClient.SELECTED_LIMITED_INVENTORY, ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER, ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER);
        ChallengeCraftClient.LAST_CHOSEN = List.copyOf(chosen);
        ChallengeCraftClient.SELECTED_PERKS = List.copyOf(perks);

        if (Minecraft.getInstance().getConnection() != null) {
            List<Integer> chosenList = ChallengeCraftClient.LAST_CHOSEN;
            List<Integer> perkList = ChallengeCraftClient.SELECTED_PERKS;
            int maxHearts = ChallengeCraftClient.SELECTED_MAX_HEARTS;
            int limitedInventorySlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;
            int mobHealthMult = ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER;
            int doubleTroubleMult = ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER;
            int gameSpeedMult = ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER;
            ClientPlayNetworking.send(
                    new ChallengePacket(chosenList, maxHearts, limitedInventorySlots, mobHealthMult, doubleTroubleMult, gameSpeedMult, perkList)
            );
            ChallengeCraft.LOGGER.info("[Client:CreateWorld] sent ChallengePacket");
        }
    }
}
