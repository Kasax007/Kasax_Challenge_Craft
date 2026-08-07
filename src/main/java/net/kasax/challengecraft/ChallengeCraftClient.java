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
    public static int SELECTED_FIB_MINUTES = 60;
    /** World-creation selections are cached until the integrated server starts. */
    public static List<Integer> LAST_CHOSEN = Collections.emptyList();
    public static List<Integer> SELECTED_PERKS = Collections.emptyList();
    public static int SELECTED_MAX_HEARTS = 20;
    public static long LOCAL_PLAYER_XP = 0;
    public static java.util.Map<java.util.UUID, Long> PLAYER_XP_MAP = new java.util.HashMap<>();
    public static boolean USE_LEGACY_LEVEL_SCREEN_LAYOUT = false;

    /**
     * Refreshes {@link #LOCAL_PLAYER_XP} from the on-disk XP cache and returns it.
     *
     * <p>The DISCONNECT handler below zeroes the field on every world exit, and only the world-join
     * path or a {@code LevelSyncPacket} repopulates it — so any title-screen UI (create-world
     * challenge tab, leveling screen) reading the raw field right after leaving a world sees
     * Level 1 with everything locked. Outside a world the flat file is the only source of truth,
     * so re-read it unconditionally; in-world the join/sync path is authoritative, so the file is
     * only used to seed a still-unsynced {@code 0} (0 reliably means "not synced yet this
     * session", never a stale value, precisely because of the disconnect reset).
     *
     * <p>Screen-construction code should call this instead of reading the field directly.
     */
    public static long refreshLocalPlayerXp() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) {
            java.util.UUID uuid = client.getSession() != null ? client.getSession().getUuidOrNull() : null;
            LOCAL_PLAYER_XP = uuid != null
                    ? net.kasax.challengecraft.data.XpManager.getXp(uuid)
                    : net.kasax.challengecraft.data.XpManager.getTotalXp();
        } else if (LOCAL_PLAYER_XP == 0) {
            LOCAL_PLAYER_XP = net.kasax.challengecraft.data.XpManager.getXp(client.player.getUuid());
        }
        return LOCAL_PLAYER_XP;
    }


    @Override
    public void onInitializeClient() {
        TimerOverlay.register();
      		ChallengeSyncHandler.register();
      		LevelSyncHandler.register();
      		StatsSyncHandler.register();
        net.kasax.challengecraft.network.EnderDragonDefeatHandler.register();
        ChallengeRewardOverlay.register();

        // Objective HUDs share one auto-laid-out stack (row 0), the target HUD sits below (row 1).
        // Model layer MUST be registered before the renderer, which resolves the baked part in
        // its constructor. Skipping the renderer entirely crashes the dev client via
        // MinecraftClient.checkGameData -> EntityRenderers.isMissingRendererFactories.
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                net.kasax.challengecraft.client.render.DiceEntityRenderer.DICE_LAYER,
                net.kasax.challengecraft.client.render.DiceEntityRenderer::getTexturedModelData);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                net.kasax.challengecraft.entity.ModEntities.DICE,
                net.kasax.challengecraft.client.render.DiceEntityRenderer::new);

        net.kasax.challengecraft.client.ui.HudStack.addSource(net.kasax.challengecraft.client.screen.AllItemsHUD::buildCard, 0);
        net.kasax.challengecraft.client.ui.HudStack.addSource(net.kasax.challengecraft.client.screen.AllEntitiesHUD::buildCard, 0);
        net.kasax.challengecraft.client.ui.HudStack.addSource(net.kasax.challengecraft.client.screen.AllAchievementsHUD::buildCard, 0);
        net.kasax.challengecraft.client.ui.HudStack.addSource(net.kasax.challengecraft.client.screen.ProgressiveBlocksHUD::buildCard, 0);
        net.kasax.challengecraft.client.ui.HudStack.addSource(net.kasax.challengecraft.client.screen.ForceItemHUD::buildCard, 0);
        net.kasax.challengecraft.client.ui.HudStack.addSource(net.kasax.challengecraft.client.screen.DiceHUD::buildCard, 0);
        net.kasax.challengecraft.client.screen.DiceReachRenderer.register();
        net.kasax.challengecraft.client.screen.ForceItemHeadIconRenderer.register();
        net.kasax.challengecraft.client.ui.HudStack.addSource(net.kasax.challengecraft.client.screen.MobHealthHUD::buildCard, 1);
        net.kasax.challengecraft.client.ui.HudStack.register();

        net.minecraft.client.gui.screen.ingame.HandledScreens.register(net.kasax.challengecraft.block.InfiniteChestRegistry.INFINITE_CHEST_SCREEN_HANDLER, net.kasax.challengecraft.screen.InfiniteChestScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(net.kasax.challengecraft.network.InfiniteChestSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof net.kasax.challengecraft.screen.InfiniteChestScreen screen) {
                    screen.updateEntries(payload.entries());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RestartPendingPacket.ID, (payload, context) -> {
            boolean isSP = context.client().getServer() != null;
            RestartManager.setRestartPending(true, payload.worldName(), isSP);
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (RestartManager.isRestartPending() && client.getNetworkHandler() == null && client.world == null) {
                boolean isSP = RestartManager.isSinglePlayer();
                String worldName = RestartManager.getLastWorldName();
                RestartManager.setRestartPending(false, null, false);
                
                if (isSP && worldName != null) {
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
            LockoutBingoClientState.clear();
            net.kasax.challengecraft.client.screen.ForceItemClientState.clear();
            net.kasax.challengecraft.client.screen.DiceClientState.clear();
            net.kasax.challengecraft.challenges.Chal_46_Dice.setClientRemaining(0.0);
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                // Joining directly into a world bypasses the title-screen XP preload.
                if (LOCAL_PLAYER_XP == 0) {
                    LOCAL_PLAYER_XP = net.kasax.challengecraft.data.XpManager.getXp(client.player.getUuid());
                    ChallengeCraft.LOGGER.info("Loaded initial local XP from file on join: {}", LOCAL_PLAYER_XP);
                }
                
                if (ClientPlayNetworking.canSend(net.kasax.challengecraft.network.ClientXpSyncPacket.ID)) {
                    ClientPlayNetworking.send(new net.kasax.challengecraft.network.ClientXpSyncPacket(LOCAL_PLAYER_XP, client.player.getUuid()));
                    ChallengeCraft.LOGGER.info("Sent local XP sync to server: {}", LOCAL_PLAYER_XP);
                }
            }
        });
    }
}
