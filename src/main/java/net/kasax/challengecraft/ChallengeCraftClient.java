// src/main/java/net/kasax/challengecraft/ChallengeCraftClient.java
package net.kasax.challengecraft;

import net.fabricmc.api.ClientModInitializer;
import net.kasax.challengecraft.client.screen.ChallengeRewardOverlay;
import net.kasax.challengecraft.client.screen.TimerOverlay;
import net.kasax.challengecraft.network.ChallengeSyncHandler;
import net.kasax.challengecraft.network.LevelSyncHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.network.RestartManager;
import net.kasax.challengecraft.network.RestartPendingPacket;

import java.util.Collections;
import java.util.List;

public class ChallengeCraftClient implements ClientModInitializer {
    public static int SELECTED_LIMITED_INVENTORY = 36;
    public static int SELECTED_MOB_HEALTH_MULTIPLIER = 1;
    /** Used for single‐player: last value chosen on the Create World screen */
    public static List<Integer> LAST_CHOSEN = Collections.emptyList();
    public static List<Integer> SELECTED_PERKS = Collections.emptyList();
    public static int SELECTED_MAX_HEARTS = 20;
    public static long LOCAL_PLAYER_XP = 0;
    public static java.util.Map<java.util.UUID, Long> PLAYER_XP_MAP = new java.util.HashMap<>();


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

        ClientPlayNetworking.registerGlobalReceiver(RestartPendingPacket.ID, (payload, context) -> {
            RestartManager.setRestartPending(true, payload.worldName());
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (RestartManager.isRestartPending() && client.getNetworkHandler() == null && client.world == null) {
                // Client is fully disconnected and world is closed. Now we can safely restart.
                String worldName = RestartManager.getLastWorldName();
                RestartManager.setRestartPending(false, null);
                
                // Use a slight delay to ensure everything is really settled
                client.execute(() -> {
                    client.createIntegratedServerLoader().start(worldName, () -> {});
                });
            }
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PLAYER_XP_MAP.clear();
            LOCAL_PLAYER_XP = 0;
        });
    }
}
