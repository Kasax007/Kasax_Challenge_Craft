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
    private int challengeTabIndex;

    @org.spongepowered.asm.mixin.Shadow
    private net.minecraft.client.gui.components.tabs.MenuTabBar tabNavigationBar;

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    // Hand-maintained: inner-class builder methods are invisible to any source
                    // remapper. Yarn TabNavigationWidget$Builder.tabs(Tab[]) became Mojmap
                    // addTabs(Tab...), and in 26.2 the create-world screen switched builders
                    // entirely — it now goes through MenuTabBar$Builder (a TabNavigationBar$Builder
                    // subclass), so the OWNER changed even though the method did not. Confirmed by
                    // disassembling CreateWorldScreen.init, which calls exactly this descriptor.
                    target = "Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;"
            ),
            index = 0
    )
    private Tab[] addChallengeTab(Tab[] original) {
        Tab[] extended = Arrays.copyOf(original, original.length + 1);
        // init() runs again on every resize and on every return from a sub-screen (the code dialog,
        // vanilla's game-rule editor), and vanilla builds fresh tabs each time. A fresh challenge
        // tab would forget every card the player had ticked, so it is built once and reused; the
        // vanilla tabs rebuild themselves from the shared uiState.
        if (this.challengeTab == null) {
            CreateWorldScreen self = (CreateWorldScreen) (Object) this;
            var t = new net.kasax.challengecraft.client.screen.ChallengeTab();
            t.bindSeed(() -> self.getUiState().getSeed(), seed -> self.getUiState().setSeed(seed));
            var code = net.kasax.challengecraft.client.ChallengeCodeClient.pending();
            if (code != null && !net.kasax.challengecraft.client.DailyClientState.isArmed()) {
                t.applySetup(code);
                ChallengeCraft.LOGGER.info("[Client:CreateWorld] Challenge-Code {} vorbelegt", code.encode());
            }
            net.kasax.challengecraft.client.ChallengeCodeClient.clear();
            this.challengeTab = t;
        }
        this.challengeTabIndex = original.length;
        extended[original.length] = this.challengeTab;
        return extended;
    }

    /**
     * Pins the daily's seed — or a title-screen code's — into the world-creation state.
     *
     * <p>A daily is only comparable if everyone plays the same terrain, so the seed is not the
     * player's to choose here. Written into the UI state rather than intercepted later, so the
     * player can SEE which seed they are getting instead of having it silently swapped underneath
     * them at create time. At HEAD, not TAIL: the World tab copies the seed into its text box once,
     * when it is built inside init(), and has no listener for later changes — set at TAIL, the seed
     * applied but the box kept showing the old value.
     */
    @Inject(method = "init", at = @At("HEAD"))
    private void challengecraft$applyDailySeed(CallbackInfo ci) {
        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        var entry = net.kasax.challengecraft.client.DailyClientState.entry();
        if (entry != null) {
            if (entry.hasFixedSeed()) {
                self.getUiState().setSeed(Long.toString(entry.seed()));
                ChallengeCraft.LOGGER.info("[Client:CreateWorld] Daily-Seed {} vorbelegt", entry.seed());
            }
            return;
        }
        var code = net.kasax.challengecraft.client.ChallengeCodeClient.pending();
        if (code != null && code.hasSeed()) {
            self.getUiState().setSeed(Long.toString(code.seed()));
        }
    }

    /** Back from the code dialog, land on the challenge tab again rather than on the first one. */
    @Inject(method = "init", at = @At("TAIL"))
    private void challengecraft$reselectChallengeTab(CallbackInfo ci) {
        if (this.challengeTab != null && this.challengeTab.consumeReopen() && this.tabNavigationBar != null) {
            this.tabNavigationBar.selectTab(this.challengeTabIndex, false);
        }
    }

    @Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
    private void onCreateLevel(CallbackInfo ci) {
        // Belt-and-braces on the seed. init() pre-fills the field so the player can SEE which seed
        // they are getting, but the field itself stays typeable — it belongs to the World tab and
        // disabling it from here would mean reaching into vanilla's widget tree. Re-applying it at
        // the moment of creation makes the value correct regardless of what was typed, which is the
        // part that actually matters for two players' times being comparable.
        var dailyEntry = net.kasax.challengecraft.client.DailyClientState.entry();
        if (dailyEntry != null && dailyEntry.hasFixedSeed()) {
            CreateWorldScreen self = (CreateWorldScreen) (Object) this;
            String want = Long.toString(dailyEntry.seed());
            if (!want.equals(self.getUiState().getSeed())) {
                ChallengeCraft.LOGGER.info("[Client:CreateWorld] Seed war '{}', wird auf den Daily-Seed {} gesetzt",
                        self.getUiState().getSeed(), want);
                self.getUiState().setSeed(want);
            }
        }

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
        // The static is the channel that actually works at creation time; the packet below only
        // fires when a connection already exists, which it does not here.
        ChallengeCraftClient.SELECTED_DAILY_INDEX = net.kasax.challengecraft.client.DailyClientState.index();

        if (Minecraft.getInstance().getConnection() != null) {
            List<Integer> chosenList = ChallengeCraftClient.LAST_CHOSEN;
            List<Integer> perkList = ChallengeCraftClient.SELECTED_PERKS;
            int maxHearts = ChallengeCraftClient.SELECTED_MAX_HEARTS;
            int limitedInventorySlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;
            int mobHealthMult = ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER;
            int doubleTroubleMult = ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER;
            int gameSpeedMult = ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER;
            int fibMinutes = ChallengeCraftClient.SELECTED_FIB_MINUTES;
            int dailyIndex = net.kasax.challengecraft.client.DailyClientState.index();
            ClientPlayNetworking.send(
                    new ChallengePacket(chosenList, maxHearts, limitedInventorySlots, mobHealthMult,
                            doubleTroubleMult, gameSpeedMult, fibMinutes, perkList, false, dailyIndex)
            );
            ChallengeCraft.LOGGER.info("[Client:CreateWorld] sent ChallengePacket (daily={})", dailyIndex);
            if (dailyIndex >= 0) {
                // Once per day per player, counted when the world is actually created — not when
                // the card was clicked, so backing out of world creation costs nothing.
                var uuid = Minecraft.getInstance().getUser().getProfileId();
                if (uuid != null) {
                    net.kasax.challengecraft.daily.DailyManager.markPlayedToday(uuid);
                }
            }
            net.kasax.challengecraft.client.DailyClientState.clear();
        }
    }
}
