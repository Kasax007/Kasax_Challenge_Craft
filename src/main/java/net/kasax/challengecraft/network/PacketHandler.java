package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Server receivers for mutable client requests such as challenge edits and lockout actions. */
public class PacketHandler {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                ChallengePacket.ID,
                (packet, context) -> {
                    var server = context.server();
                    var player = context.player();

                    server.execute(() -> {
                        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                            ChallengeCraft.LOGGER.warn("[Server] Denied ChallengePacket from {} (no permission)", player.getName().getString());
                            player.sendSystemMessage(Component.translatable("challengecraft.permission.change_challenges").withStyle(ChatFormatting.RED));
                            return;
                        }

                        int playerLevel = net.kasax.challengecraft.LevelManager.getLevelForXp(net.kasax.challengecraft.data.XpManager.getXp(player.getUUID()));
                        long playerXp = net.kasax.challengecraft.data.XpManager.getXp(player.getUUID());
                        
                        for (int cid : packet.active) {
                            if (!net.kasax.challengecraft.LevelManager.isChallengeUnlocked(cid, playerLevel)) {
                                ChallengeCraft.LOGGER.warn("[Server] Denied ChallengePacket from {} (challenge {} locked for level {})", player.getName().getString(), cid, playerLevel);
                                player.sendSystemMessage(Component.translatable("challengecraft.requirement.challenge_level", cid).withStyle(ChatFormatting.RED));
                                return;
                            }
                        }
                        for (int pid : packet.perks) {
                            if (pid == net.kasax.challengecraft.LevelManager.PERK_INFINITY_WEAPON) {
                                if (net.kasax.challengecraft.LevelManager.getStars(playerXp) < 20) {
                                    ChallengeCraft.LOGGER.warn("[Server] Denied ChallengePacket from {} (Infinity Weapon perk locked)", player.getName().getString());
                                    player.sendSystemMessage(Component.translatable("challengecraft.requirement.infinity_weapon_stars").withStyle(ChatFormatting.RED));
                                    return;
                                }
                            } else if (!net.kasax.challengecraft.LevelManager.isChallengeUnlocked(pid, playerLevel)) {
                                ChallengeCraft.LOGGER.warn("[Server] Denied ChallengePacket from {} (perk {} locked for level {})", player.getName().getString(), pid, playerLevel);
                                player.sendSystemMessage(Component.translatable("challengecraft.requirement.perk_level", pid).withStyle(ChatFormatting.RED));
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
                        var world = server.overworld();
                        ChallengeSavedData data = ChallengeSavedData.get(world);
                        java.util.List<Integer> prevPerks = new java.util.ArrayList<>(data.getActivePerks());
                        data.setActive(packet.active);
                        data.setActivePerks(packet.perks);
                        data.setMaxHeartsTicks(packet.maxHearts);
                        data.setLimitedInventorySlots(packet.limitedInventorySlots);
                        data.setMobHealthMultiplier(packet.mobHealthMultiplier);
                        data.setDoubleTroubleMultiplier(packet.doubleTroubleMultiplier);
                        data.setGameSpeedMultiplier(packet.gameSpeedMultiplier);

                        boolean hadBefore = prevPerks.contains(net.kasax.challengecraft.LevelManager.PERK_INFINITY_WEAPON);
                        boolean hasAfter  = packet.perks.contains(net.kasax.challengecraft.LevelManager.PERK_INFINITY_WEAPON);
                        if (!hadBefore && hasAfter) {
                            for (var p : server.getPlayerList().getPlayers()) {
                                net.kasax.challengecraft.LevelXpListener.grantInfinityWeapon(p);
                            }
                        }
                        
                        if (!data.isTainted()) {
                            data.setTainted(true);
                            Component title = Component.translatable("challengecraft.tainted.failed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                            Component subtitle = Component.translatable("challengecraft.tainted.failed.desc").withStyle(ChatFormatting.GRAY);

                            server.getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                            server.getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(title));
                            server.getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(subtitle));

                            server.getAllLevels().forEach(w -> {
                                w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 1.0f, 1.0f);
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
                        if (packet.active.contains(37)) {
                            int mult = packet.gameSpeedMultiplier;
                            net.kasax.challengecraft.challenges.Chal_37_GameSpeed.setMultiplier(mult);
                            ChallengeCraft.LOGGER.info("[Server] set Chal_37 multiplier = {}", mult);
                        }
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
                        if (player.getUUID().equals(packet.uuid)) {
                            long serverXp = net.kasax.challengecraft.data.XpManager.getXp(player.getUUID());
                            if (packet.xp > serverXp) {
                                ChallengeCraft.LOGGER.info("[Server] Received XP sync from client {}: {} (current server XP: {})", player.getName().getString(), packet.xp, serverXp);
                                net.kasax.challengecraft.data.XpManager.setXp(player.getUUID(), packet.xp);
                            }
                            // The server remains authoritative if the client has stale local XP.
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
                        var world = player.level();
                        if (world.getBlockEntity(packet.pos()) instanceof net.kasax.challengecraft.block.InfiniteChestBlockEntity be) {
                            var storage = be.getStorage();
                            if (packet.button() == -1) {
                                net.minecraft.world.inventory.AbstractContainerMenu handler = player.containerMenu;
                                if (handler instanceof net.kasax.challengecraft.block.InfiniteChestScreenHandler) {
                                    net.minecraft.world.item.ItemStack cursorStack = handler.getCarried();
                                    if (!cursorStack.isEmpty()) {
                                        storage.addStack(cursorStack.copy());
                                        cursorStack.setCount(0);
                                        handler.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                                        syncInfiniteChest(player, be);
                                    }
                                }
                                return;
                            }
                            net.minecraft.world.item.ItemStack stack = packet.stack();
                            if (!stack.isEmpty()) {
                                net.kasax.challengecraft.storage.InfiniteChestStorage.ItemStackKey key = net.kasax.challengecraft.storage.InfiniteChestStorage.ItemStackKey.fromStack(stack);
                                long amountToRemove = (packet.button() == 1) ? 1 : 64;
                                long count = storage.removeItems(key, amountToRemove);
                                if (count > 0) {
                                    net.minecraft.world.item.ItemStack out = key.toStack((int) count);
                                    if (!player.getInventory().add(out)) {
                                        player.drop(out, false);
                                    }
                                }
                                syncInfiniteChest(player, be);
                            }
                        }
                    });
                }
        );

        ServerPlayNetworking.registerGlobalReceiver(
                LockoutBingoActionPacket.ID,
                (packet, context) -> {
                    var server = context.server();
                    var player = context.player();
                    server.execute(() -> Chal_40_LockoutBingo.handleAction(player, packet));
                }
        );
    }

    public static void syncInfiniteChest(net.minecraft.server.level.ServerPlayer player, net.kasax.challengecraft.block.InfiniteChestBlockEntity be) {
        var storage = be.getStorage();
        java.util.List<InfiniteChestSyncPayload.Entry> entries = new java.util.ArrayList<>();
        storage.getStoredItems().forEach((key, count) -> {
            entries.add(new InfiniteChestSyncPayload.Entry(key.toStack(1), count));
        });
        ServerPlayNetworking.send(player, new InfiniteChestSyncPayload(entries));
    }
}
