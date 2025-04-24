package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.PlayPayloadHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.client.screen.TimerOverlay;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

public class PlayTimePacketHandler {
    public static void register() {
        //
        // 1) CLIENT‐SIDE: receive our PlayTimeSyncPacket
        //
        ClientPlayNetworking.registerGlobalReceiver(
                PlayTimeSyncPacket.ID,
                new PlayPayloadHandler<PlayTimeSyncPacket>() {
                    @Override
                    public void receive(PlayTimeSyncPacket packet, Context context) {
                        // schedule on the client thread
                        context.client().execute(() -> {
                            TimerOverlay.setBasePlayTicks(packet.playTicks);
                            ChallengeCraft.LOGGER.info("→ synced playTicks = " + packet.playTicks);
                        });
                    }
                }
        );

        //
        // 2) SERVER‐SIDE: when a player JOINS, read and send their play‐ticks
        //
        ServerPlayConnectionEvents.JOIN.register(
                (ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) -> {
                    // run on the server main thread
                    // in your PlayTimePacketHandler, server-side:
                    server.execute(() -> {
                        ServerPlayerEntity player = handler.player;

                        // pick one of these two registered IDs:
                        // Identifier chosenId = Stats.PLAY_TIME;
                        Identifier chosenId = Stats.PLAY_TIME;

                        int playTicks = player.getStatHandler()
                                .getStat(Stats.CUSTOM.getOrCreateStat(chosenId));

                        sender.sendPacket(new PlayTimeSyncPacket(playTicks));
                    });

                }
        );
    }
}
