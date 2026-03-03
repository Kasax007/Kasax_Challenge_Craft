package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

public class PlayTimePacketHandler {

    public static void registerServer() {
        //
        // 1) Periodic sync: every second (20 ticks)
        //
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) {
                Identifier chosenId = Stats.PLAY_TIME;
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    int playTicks = player.getStatHandler()
                            .getStat(Stats.CUSTOM.getOrCreateStat(chosenId));
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
                        Identifier chosenId = Stats.PLAY_TIME;

                        int playTicks = player.getStatHandler()
                                .getStat(Stats.CUSTOM.getOrCreateStat(chosenId));

                        sender.sendPacket(new PlayTimeSyncPacket(playTicks));
                    });
                }
        );
    }
}
