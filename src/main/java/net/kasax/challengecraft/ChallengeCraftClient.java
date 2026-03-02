// src/main/java/net/kasax/challengecraft/ChallengeCraftClient.java
package net.kasax.challengecraft;

import net.fabricmc.api.ClientModInitializer;
import net.kasax.challengecraft.client.screen.ChallengeRewardOverlay;
import net.kasax.challengecraft.client.screen.TimerOverlay;
import net.kasax.challengecraft.network.ChallengeSyncHandler;
import net.kasax.challengecraft.network.LevelSyncHandler;

import java.util.Collections;
import java.util.List;

public class ChallengeCraftClient implements ClientModInitializer {
    public static int SELECTED_LIMITED_INVENTORY = 36;
    public static int SELECTED_MOB_HEALTH_MULTIPLIER = 1;
    /** Used for single‐player: last value chosen on the Create World screen */
    public static List<Integer> LAST_CHOSEN = Collections.singletonList(1);
    public static List<Integer> SELECTED_PERKS = Collections.emptyList();
    public static int SELECTED_MAX_HEARTS = 20;
    public static long LOCAL_PLAYER_XP = 0;


    @Override
    public void onInitializeClient() {
        TimerOverlay.register();
        ChallengeSyncHandler.register();
        LevelSyncHandler.register();
        net.kasax.challengecraft.network.EnderDragonDefeatHandler.register();
        ChallengeRewardOverlay.register();
        net.kasax.challengecraft.client.screen.AllItemsHUD.register();
        net.kasax.challengecraft.client.screen.AllEntitiesHUD.register();
        net.kasax.challengecraft.client.screen.MobHealthHUD.register();
    }
}
