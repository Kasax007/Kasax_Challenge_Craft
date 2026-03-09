// src/main/java/net/kasax/challengecraft/ChallengeCraftClient.java
package net.kasax.challengecraft;

import net.fabricmc.api.ClientModInitializer;
import net.kasax.challengecraft.client.screen.ChallengeRewardOverlay;
import net.kasax.challengecraft.client.screen.TimerOverlay;
import net.kasax.challengecraft.network.ChallengeSyncHandler;
import net.kasax.challengecraft.network.LevelSyncHandler;
import net.kasax.challengecraft.network.StatsSyncHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.network.RestartManager;
import net.kasax.challengecraft.network.RestartPendingPacket;
import net.kasax.challengecraft.client.screen.DedicatedRestartScreen;

import java.util.Collections;
import java.util.List;

public class ChallengeCraftClient implements ClientModInitializer {
    public static int SELECTED_LIMITED_INVENTORY = 36;
    public static int SELECTED_MOB_HEALTH_MULTIPLIER = 1;
    public static int SELECTED_DOUBLE_TROUBLE_MULTIPLIER = 2;
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
      		StatsSyncHandler.register();
        net.kasax.challengecraft.network.EnderDragonDefeatHandler.register();
        ChallengeRewardOverlay.register();
        net.kasax.challengecraft.client.screen.AllItemsHUD.register();
        net.kasax.challengecraft.client.screen.AllEntitiesHUD.register();
        net.kasax.challengecraft.client.screen.AllAchievementsHUD.register();
        net.kasax.challengecraft.client.screen.MobHealthHUD.register();

        ClientPlayNetworking.registerGlobalReceiver(RestartPendingPacket.ID, (payload, context) -> {
            boolean isSP = context.client().getServer() != null;
            RestartManager.setRestartPending(true, payload.worldName(), isSP);
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (RestartManager.isRestartPending() && client.getNetworkHandler() == null && client.world == null) {
                // Client is fully disconnected and world is closed. Now we can safely restart.
                boolean isSP = RestartManager.isSinglePlayer();
                String worldName = RestartManager.getLastWorldName();
                RestartManager.setRestartPending(false, null, false);
                
                if (isSP && worldName != null) {
                    // Use a slight delay to ensure everything is really settled
                    client.execute(() -> {
                        client.createIntegratedServerLoader().start(worldName, () -> {});
                    });
                } else if (!isSP) {
                    client.execute(() -> {
                        client.setScreen(new DedicatedRestartScreen());
                    });
                }
            }
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PLAYER_XP_MAP.clear();
            LOCAL_PLAYER_XP = 0;
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                // Initialize local XP from XpManager if it was 0 (meaning we didn't open main menu screen)
                if (LOCAL_PLAYER_XP == 0) {
                    LOCAL_PLAYER_XP = net.kasax.challengecraft.data.XpManager.getXp(client.player.getUuid());
                    ChallengeCraft.LOGGER.info("Loaded initial local XP from file on join: {}", LOCAL_PLAYER_XP);
                }
                
                // Send our local XP to the server so it knows our level
                if (ClientPlayNetworking.canSend(net.kasax.challengecraft.network.ClientXpSyncPacket.ID)) {
                    ClientPlayNetworking.send(new net.kasax.challengecraft.network.ClientXpSyncPacket(LOCAL_PLAYER_XP, client.player.getUuid()));
                    ChallengeCraft.LOGGER.info("Sent local XP sync to server: {}", LOCAL_PLAYER_XP);
                }
            }
        });
    }
}
