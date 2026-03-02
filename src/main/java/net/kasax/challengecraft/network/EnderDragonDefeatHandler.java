package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.client.screen.ChallengeRewardOverlay;

public class EnderDragonDefeatHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ChallengeRewardPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                ChallengeRewardOverlay.start(payload.oldXp, payload.newXp, payload.xpGained, payload.isGameCompletion);
            });
        });
    }
}
