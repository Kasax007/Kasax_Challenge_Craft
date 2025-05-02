package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.kasax.challengecraft.challenges.Chal_7_MaxHealthModify;
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
                        ChallengeCraft.LOGGER.info("[Server] got ChallengePacket from {} → active = {} , maxHearts ticks = {}, slots = {}",
                                context.player().getName().getString(),
                                packet.active,
                                packet.maxHearts,
                                packet.limitedInventorySlots
                        );
                        var world = player.getWorld();
                        // overwrite your active list
                        ChallengeSavedData.get((ServerWorld) world).setActive(packet.active);
                        ChallengeSavedData.get((ServerWorld) world).setMaxHeartsTicks(packet.maxHearts);
                        ChallengeSavedData.get((ServerWorld) world).setLimitedInventorySlots(packet.limitedInventorySlots);
                        if (packet.active.contains(7)) {
                            float hearts = packet.maxHearts * 0.5f;
                            Chal_7_MaxHealthModify.setMaxHearts(hearts);
                            ChallengeCraft.LOGGER.info("[Server] set Chal_7 maxHearts = {}", hearts);
                        }
                        else if (packet.active.contains(12)) {
                            float slots = packet.limitedInventorySlots * 1f;
                            Chal_12_LimitedInventory.setLimitedSlots((int) slots);
                            ChallengeCraft.LOGGER.info("[Server] set Chal_12 slots = {}", slots);
                        }
                        // re‑apply all active challenges
                        ChallengeManager.applyAll((ServerWorld) world);
                        ChallengeCraft.LOGGER.info("Packet Handler applyAll " + packet );
                    });
                }
        );
    }
}
