package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.data.XpManager;

public class LevelSyncHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LevelSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                net.kasax.challengecraft.ChallengeCraft.LOGGER.info("[Client] Received LevelSyncPacket for {}: {}", payload.uuid, payload.xp);
                if (context.player().getUuid().equals(payload.uuid)) {
                    net.kasax.challengecraft.ChallengeCraftClient.LOCAL_PLAYER_XP = payload.xp;
                    XpManager.setXp(payload.uuid, payload.xp);
                }
                net.kasax.challengecraft.ChallengeCraftClient.PLAYER_XP_MAP.put(payload.uuid, payload.xp);
            });
        });
    }
}
