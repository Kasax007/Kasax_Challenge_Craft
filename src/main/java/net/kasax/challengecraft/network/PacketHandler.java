package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class PacketHandler {
    public static void register() {
        // Register your C2S receiver exactly like Raft:
        ServerPlayNetworking.registerGlobalReceiver(ChallengePacket.ID, (packet, context) -> {
            // 'context' has server() and player() methods:
            MinecraftServer server = context.server();
            ServerPlayerEntity player = context.player();

            // Schedule on the main thread:
            server.execute(() -> {
                // Store into the world’s PersistentState:
                ChallengeSavedData.get(player.getServerWorld())
                        .setSelectedChallenge(packet.selected);
            });
        });
    }
}
