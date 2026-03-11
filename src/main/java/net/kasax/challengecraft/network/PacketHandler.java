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
                        if (!player.hasPermissionLevel(2)) {
                            ChallengeCraft.LOGGER.warn("[Server] Denied ChallengePacket from {} (no permission)", player.getName().getString());
                            player.sendMessage(Text.literal("You don't have permission to change challenges.").formatted(Formatting.RED), false);
                            return;
                        }

                        // Level Check
                        int playerLevel = net.kasax.challengecraft.LevelManager.getLevelForXp(net.kasax.challengecraft.data.XpManager.getXp(player.getUuid()));
                        long playerXp = net.kasax.challengecraft.data.XpManager.getXp(player.getUuid());
                        
                        for (int cid : packet.active) {
                            if (!net.kasax.challengecraft.LevelManager.isChallengeUnlocked(cid, playerLevel)) {
                                ChallengeCraft.LOGGER.warn("[Server] Denied ChallengePacket from {} (challenge {} locked for level {})", player.getName().getString(), cid, playerLevel);
                                player.sendMessage(Text.literal("You don't have the required level for challenge " + cid).formatted(Formatting.RED), false);
                                return;
                            }
                        }
                        for (int pid : packet.perks) {
                            if (pid == net.kasax.challengecraft.LevelManager.PERK_INFINITY_WEAPON) {
                                if (net.kasax.challengecraft.LevelManager.getStars(playerXp) < 20) {
                                    ChallengeCraft.LOGGER.warn("[Server] Denied ChallengePacket from {} (Infinity Weapon perk locked)", player.getName().getString());
                                    player.sendMessage(Text.literal("You don't have enough Infinity Stars for Infinity Weapon perk").formatted(Formatting.RED), false);
                                    return;
                                }
                            } else if (!net.kasax.challengecraft.LevelManager.isChallengeUnlocked(pid, playerLevel)) {
                                ChallengeCraft.LOGGER.warn("[Server] Denied ChallengePacket from {} (perk {} locked for level {})", player.getName().getString(), pid, playerLevel);
                                player.sendMessage(Text.literal("You don't have the required level for perk " + pid).formatted(Formatting.RED), false);
                                return;
                            }
                        }

                        ChallengeCraft.LOGGER.info("[Server] got ChallengePacket from {} → active = {} , perks = {}, maxHearts ticks = {}, slots = {}, mobHealth = {}, doubleTrouble = {}",
                                context.player().getName().getString(),
                                packet.active,
                                packet.perks,
                                packet.maxHearts,
                                packet.limitedInventorySlots,
                                packet.mobHealthMultiplier,
                                packet.doubleTroubleMultiplier
                        );
                        var world = server.getOverworld();
                        ChallengeSavedData data = ChallengeSavedData.get(world);
                        // capture previous perks to detect first-time activation
                        java.util.List<Integer> prevPerks = new java.util.ArrayList<>(data.getActivePerks());
                        // overwrite your active list
                        data.setActive(packet.active);
                        data.setActivePerks(packet.perks);
                        data.setMaxHeartsTicks(packet.maxHearts);
                        data.setLimitedInventorySlots(packet.limitedInventorySlots);
                        data.setMobHealthMultiplier(packet.mobHealthMultiplier);
                        data.setDoubleTroubleMultiplier(packet.doubleTroubleMultiplier);

                        // If Infinity Weapon perk is newly activated via in-game selection, grant it once to eligible players
                        boolean hadBefore = prevPerks.contains(net.kasax.challengecraft.LevelManager.PERK_INFINITY_WEAPON);
                        boolean hasAfter  = packet.perks.contains(net.kasax.challengecraft.LevelManager.PERK_INFINITY_WEAPON);
                        if (!hadBefore && hasAfter) {
                            for (var p : server.getPlayerManager().getPlayerList()) {
                                net.kasax.challengecraft.LevelXpListener.grantInfinityWeapon(p);
                            }
                        }
                        
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
                        if (packet.active.contains(35)) {
                            int mult = packet.doubleTroubleMultiplier;
                            net.kasax.challengecraft.challenges.Chal_35_DoubleTrouble.setMultiplier(mult);
                            ChallengeCraft.LOGGER.info("[Server] set Chal_35 multiplier = {}", mult);
                        }
                        // re‑apply all active challenges
                        ChallengeManager.applyAll(server);
                        ChallengeCraft.LOGGER.info("Packet Handler applyAll " + packet );

                        if (packet.restart) {
                            ChallengeWorldRestarter.initiateRestart(server);
                        }
                    });
                }
        );

        ServerPlayNetworking.registerGlobalReceiver(
                ClientXpSyncPacket.ID,
                (packet, context) -> {
                    var server = context.server();
                    var player = context.player();
                    server.execute(() -> {
                        if (player.getUuid().equals(packet.uuid)) {
                            long serverXp = net.kasax.challengecraft.data.XpManager.getXp(player.getUuid());
                            if (packet.xp > serverXp) {
                                ChallengeCraft.LOGGER.info("[Server] Received XP sync from client {}: {} (current server XP: {})", player.getName().getString(), packet.xp, serverXp);
                                net.kasax.challengecraft.data.XpManager.setXp(player.getUuid(), packet.xp);
                            }
                            // Always sync back to confirm or correct the client
                            net.kasax.challengecraft.LevelManager.sync(player);
                        }
                    });
                }
        );

        ServerPlayNetworking.registerGlobalReceiver(
                TriviaAnswerPacket.ID,
                (packet, context) -> {
                    var server = context.server();
                    var player = context.player();
                    server.execute(() -> {
                        net.kasax.challengecraft.challenges.Chal_36_TriviaChallenge.handleAnswer(player, packet.answerIndex());
                    });
                }
        );

        ServerPlayNetworking.registerGlobalReceiver(
                InfiniteChestClickPayload.ID,
                (packet, context) -> {
                    var server = context.server();
                    var player = context.player();
                    server.execute(() -> {
                        var world = player.getWorld();
                        if (world.getBlockEntity(packet.pos()) instanceof net.kasax.challengecraft.block.InfiniteChestBlockEntity be) {
                            var storage = be.getStorage();
                            if (packet.button() == -1) {
                                net.minecraft.screen.ScreenHandler handler = player.currentScreenHandler;
                                if (handler instanceof net.kasax.challengecraft.block.InfiniteChestScreenHandler) {
                                    net.minecraft.item.ItemStack cursorStack = handler.getCursorStack();
                                    if (!cursorStack.isEmpty()) {
                                        storage.addStack(cursorStack.copy());
                                        cursorStack.setCount(0);
                                        handler.setCursorStack(net.minecraft.item.ItemStack.EMPTY);
                                        syncInfiniteChest(player, be);
                                    }
                                }
                                return;
                            }
                            net.minecraft.item.ItemStack stack = packet.stack();
                            if (!stack.isEmpty()) {
                                net.kasax.challengecraft.storage.InfiniteChestStorage.ItemStackKey key = net.kasax.challengecraft.storage.InfiniteChestStorage.ItemStackKey.fromStack(stack);
                                long amountToRemove = (packet.button() == 1) ? 1 : 64;
                                long count = storage.removeItems(key, amountToRemove);
                                if (count > 0) {
                                    net.minecraft.item.ItemStack out = key.toStack((int) count);
                                    player.getInventory().offerOrDrop(out);
                                }
                                syncInfiniteChest(player, be);
                            }
                        }
                    });
                }
        );
    }

    public static void syncInfiniteChest(net.minecraft.server.network.ServerPlayerEntity player, net.kasax.challengecraft.block.InfiniteChestBlockEntity be) {
        var storage = be.getStorage();
        java.util.List<InfiniteChestSyncPayload.Entry> entries = new java.util.ArrayList<>();
        storage.getStoredItems().forEach((key, count) -> {
            entries.add(new InfiniteChestSyncPayload.Entry(key.toStack(1), count));
        });
        ServerPlayNetworking.send(player, new InfiniteChestSyncPayload(entries));
    }
}
