package net.kasax.challengecraft;

import net.fabricmc.api.ClientModInitializer;
import net.kasax.challengecraft.client.screen.ChallengeRewardOverlay;
import net.kasax.challengecraft.client.screen.LockoutBingoClientState;
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

/** Registers client-only HUDs, sync handlers, screens, and restart flow helpers. */
public class ChallengeCraftClient implements ClientModInitializer {
    public static int SELECTED_LIMITED_INVENTORY = 36;
    public static int SELECTED_MOB_HEALTH_MULTIPLIER = 1;
    public static int SELECTED_DOUBLE_TROUBLE_MULTIPLIER = 2;
    public static int SELECTED_GAME_SPEED_MULTIPLIER = 1;
    /** World-creation selections are cached until the integrated server starts. */
    public static List<Integer> LAST_CHOSEN = Collections.emptyList();
    public static List<Integer> SELECTED_PERKS = Collections.emptyList();
    public static int SELECTED_MAX_HEARTS = 20;
    public static long LOCAL_PLAYER_XP = 0;
    public static java.util.Map<java.util.UUID, Long> PLAYER_XP_MAP = new java.util.HashMap<>();
    public static boolean USE_LEGACY_LEVEL_SCREEN_LAYOUT = false;


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

        net.minecraft.client.gui.screens.MenuScreens.register(net.kasax.challengecraft.block.InfiniteChestRegistry.INFINITE_CHEST_SCREEN_HANDLER, net.kasax.challengecraft.screen.InfiniteChestScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(net.kasax.challengecraft.network.InfiniteChestSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof net.kasax.challengecraft.screen.InfiniteChestScreen screen) {
                    screen.updateEntries(payload.entries());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RestartPendingPacket.ID, (payload, context) -> {
            boolean isSP = context.client().hasSingleplayerServer();
            RestartManager.setRestartPending(true, payload.worldName(), isSP);
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (RestartManager.isRestartPending() && client.getConnection() == null && client.level == null) {
                boolean isSP = RestartManager.isSinglePlayer();
                String worldName = RestartManager.getLastWorldName();
                RestartManager.setRestartPending(false, null, false);
                
                if (isSP && worldName != null) {
                    client.execute(() -> {
                        client.createWorldOpenFlows().openWorld(worldName, () -> {});
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
            LockoutBingoClientState.clear();
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                // Joining directly into a world bypasses the title-screen XP preload.
                if (LOCAL_PLAYER_XP == 0) {
                    LOCAL_PLAYER_XP = net.kasax.challengecraft.data.XpManager.getXp(client.player.getUUID());
                    ChallengeCraft.LOGGER.info("Loaded initial local XP from file on join: {}", LOCAL_PLAYER_XP);
                }
                
                if (ClientPlayNetworking.canSend(net.kasax.challengecraft.network.ClientXpSyncPacket.ID)) {
                    ClientPlayNetworking.send(new net.kasax.challengecraft.network.ClientXpSyncPacket(LOCAL_PLAYER_XP, client.player.getUUID()));
                    ChallengeCraft.LOGGER.info("Sent local XP sync to server: {}", LOCAL_PLAYER_XP);
                }
            }
        });
    }
}
