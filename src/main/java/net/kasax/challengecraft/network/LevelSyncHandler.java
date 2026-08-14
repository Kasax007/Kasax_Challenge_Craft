package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.data.XpManager;

/** Applies XP sync packets to local player state and remote nameplate caches. */
public class LevelSyncHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LevelSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                net.kasax.challengecraft.ChallengeCraft.LOGGER.info("[Client] Received LevelSyncPacket for {}: {}", payload.uuid, payload.xp);
                if (context.player().getUUID().equals(payload.uuid)) {
                    boolean changed = ChallengeCraftClient.LOCAL_PLAYER_XP != payload.xp;
                    net.kasax.challengecraft.ChallengeCraftClient.LOCAL_PLAYER_XP = payload.xp;
                    XpManager.setXp(payload.uuid, payload.xp);

                    // LevelingScreen snapshots XP once at construction/init time and has no other
                    // way to learn that the authoritative value arrived late -- e.g. right after
                    // joining a freshly created world, before this packet lands (see the
                    // LevelingScreen constructor). Rebuild it in place instead of leaving stale
                    // data on screen until the player closes and reopens it.
                    if (changed && context.client().gui.screen() instanceof LevelingScreen levelingScreen) {
                        context.client().setScreenAndShow(new LevelingScreen(levelingScreen.getParentScreen()));
                    }
                }
                net.kasax.challengecraft.ChallengeCraftClient.PLAYER_XP_MAP.put(payload.uuid, payload.xp);
            });
        });
    }
}
