package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayTimePacketHandler {
    private static long lastSyncMillis = 0L;

    public static void registerServer() {
        //
        // 1) Periodic sync: every real second, independent of current server TPS
        //
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            if (lastSyncMillis == 0L) {
                lastSyncMillis = now;
            }

            if (now - lastSyncMillis >= 1000L) {
                lastSyncMillis = now;
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    int playTicks = ChallengeTimeUtil.getDisplayPlayTicks(player);
                    ServerPlayNetworking.send(player, new PlayTimeSyncPacket(playTicks));
                }
            }
        });

        //
        // 2) SERVER‐SIDE: when a player JOINS, read and send their play‐ticks
        //
        ServerPlayConnectionEvents.JOIN.register(
                (ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) -> {
                    // run on the server main thread
                    server.execute(() -> {
                        ServerPlayerEntity player = handler.player;
                        int playTicks = ChallengeTimeUtil.getDisplayPlayTicks(player);

                        sender.sendPacket(new PlayTimeSyncPacket(playTicks));
                    });
                }
        );
    }
}
