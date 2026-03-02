package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.challenges.Chal_12_LimitedInventory;
import net.kasax.challengecraft.challenges.Chal_7_MaxHealthModify;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                        ChallengeCraft.LOGGER.info("[Server] got ChallengePacket from {} → active = {} , maxHearts ticks = {}, slots = {}, mobHealth = {}",
                                context.player().getName().getString(),
                                packet.active,
                                packet.maxHearts,
                                packet.limitedInventorySlots,
                                packet.mobHealthMultiplier
                        );
                        var world = server.getOverworld();
                        ChallengeSavedData data = ChallengeSavedData.get(world);
                        // overwrite your active list
                        data.setActive(packet.active);
                        data.setActivePerks(packet.perks);
                        data.setMaxHeartsTicks(packet.maxHearts);
                        data.setLimitedInventorySlots(packet.limitedInventorySlots);
                        data.setMobHealthMultiplier(packet.mobHealthMultiplier);
                        
                        if (!data.isTainted()) {
                            data.setTainted(true);
                            Text title = Text.translatable("challengecraft.tainted.failed").formatted(Formatting.RED, Formatting.BOLD);
                            Text subtitle = Text.translatable("challengecraft.tainted.failed.desc").formatted(Formatting.GRAY);

                            server.getPlayerManager().sendToAll(new TitleFadeS2CPacket(10, 70, 20));
                            server.getPlayerManager().sendToAll(new TitleS2CPacket(title));
                            server.getPlayerManager().sendToAll(new SubtitleS2CPacket(subtitle));

                            server.getWorlds().forEach(w -> {
                                w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1.0f, 1.0f);
                            });
                        }
                        if (packet.active.contains(7)) {
                            float hearts = packet.maxHearts * 0.5f;
                            net.kasax.challengecraft.challenges.Chal_7_MaxHealthModify.setMaxHearts(hearts);
                            ChallengeCraft.LOGGER.info("[Server] set Chal_7 maxHearts = {}", hearts);
                        }
                        if (packet.active.contains(12)) {
                            int slots = packet.limitedInventorySlots;
                            net.kasax.challengecraft.challenges.Chal_12_LimitedInventory.setLimitedSlots(slots);
                            ChallengeCraft.LOGGER.info("[Server] set Chal_12 slots = {}", slots);
                        }
                        if (packet.active.contains(24)) {
                            int mult = packet.mobHealthMultiplier;
                            net.kasax.challengecraft.challenges.Chal_24_MobHealthMultiply.setMultiplier(mult);
                            ChallengeCraft.LOGGER.info("[Server] set Chal_24 multiplier = {}", mult);
                        }
                        // re‑apply all active challenges
                        ChallengeManager.applyAll(server);
                        ChallengeCraft.LOGGER.info("Packet Handler applyAll " + packet );
                    });
                }
        );
    }
}
