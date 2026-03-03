package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.client.screen.ChallengeRewardOverlay;
import net.kasax.challengecraft.data.XpManager;

public class EnderDragonDefeatHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ChallengeRewardPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                XpManager.setXp(context.player().getUuid(), payload.newXp);
                ChallengeRewardOverlay.start(payload.oldXp, payload.newXp, payload.xpGained, payload.isGameCompletion);
            });
        });
    }
}
