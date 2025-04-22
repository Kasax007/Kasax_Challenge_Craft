package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.server.world.ServerWorld;

public class PacketHandler {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                ChallengePacket.ID,
                (packet, context) -> {
                    // packet is already a fully–deserialized ChallengePacket
                    var server = context.server();
                    var player = context.player();

                    // schedule on the main thread
                    server.execute(() -> {
                        var world = player.getWorld();
                        // overwrite your active list
                        ChallengeSavedData.get((ServerWorld) world).setActive(packet.active);
                        // re‑apply all active challenges
                        ChallengeManager.applyAll((ServerWorld) world);
                        ChallengeCraft.LOGGER.info("Packet Handler applyAll " + packet );
                    });
                }
        );
    }
}
