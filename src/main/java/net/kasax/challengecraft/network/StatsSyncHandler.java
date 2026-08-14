package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.data.StatsManager;

/** Applies personal-best sync packets to the client stats cache. */
public class StatsSyncHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StatsSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                String uuid = "global";
                if (context.player() != null) {
                    uuid = context.player().getStringUUID();
                }
                
                StatsManager.updateStatsFromServer(uuid, payload.bestTimes());
            });
        });
    }
}
