package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraftClient;

public class LevelSyncHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LevelSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                ChallengeCraftClient.LOCAL_PLAYER_XP = payload.xp;
            });
        });
    }
}
