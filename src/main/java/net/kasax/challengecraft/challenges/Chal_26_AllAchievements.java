package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.network.AdvancementInfo;
import net.kasax.challengecraft.network.AllAchievementsListPacket;
import net.kasax.challengecraft.network.AllAchievementsSyncPacket;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Tracks the ordered advancement run and mirrors the current target to clients. */
public class Chal_26_AllAchievements {
    private static boolean active = false;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;

            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            List<Identifier> order = data.getAllAdvancementsOrder();
            if (order.isEmpty()) {
                generateOrder(server, data);
                order = data.getAllAdvancementsOrder();
            }

            int index = data.getAllAdvancementsIndex();
            if (index >= order.size()) return;

            Identifier currentAdvId = order.get(index);
            AdvancementHolder currentAdv = server.getAdvancements().get(currentAdvId);

            if (currentAdv == null) {
                // Datapacks can remove advancements between sessions; do not stall the run on stale IDs.
                index++;
                data.setAllAdvancementsIndex(index);
                syncProgressToAll(server, data);
                return;
            }

            boolean found = false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                AdvancementProgress progress = player.getAdvancements().getOrStartProgress(currentAdv);
                if (progress.isDone()) {
                    found = true;
                    break;
                }
            }

            if (found) {
                index++;
                data.setAllAdvancementsIndex(index);
                syncProgressToAll(server, data);

                double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
                long xpPerAdv = 10;
                if (xpPerAdv > 0 && difficulty > 0) {
                    server.getPlayerList().getPlayers().forEach(p -> {
                        LevelManager.addXp(p, xpPerAdv);
                    });
                }

                if (index >= order.size()) {
                    completeChallenge(server, data);
                }
            }
        });
    }

    private static void generateOrder(MinecraftServer server, ChallengeSavedData data) {
        List<Identifier> survivalAdvancements = getSurvivalAdvancements(server);
        
        long seed = server.overworld().getSeed();
        Collections.shuffle(survivalAdvancements, new Random(seed));

        data.setAllAdvancementsOrder(survivalAdvancements);
        data.setAllAdvancementsIndex(0);
        syncProgressToAll(server, data);
    }

    private static List<Identifier> getSurvivalAdvancements(MinecraftServer server) {
        List<Identifier> advancements = new ArrayList<>();
        server.getAdvancements().getAllAdvancements().forEach(advancement -> {
            Identifier id = advancement.id();
            if (!id.getNamespace().equals("minecraft")) return;
            
            if (advancement.value().display().isEmpty()) return;
            
            if (advancement.value().parent().isEmpty()) return;
            
            if (id.getPath().startsWith("recipes/")) return;

            advancements.add(id);
        });
        // Keep the pre-shuffle order stable across reloads and registry iteration changes.
        advancements.sort(Comparator.comparing(Identifier::toString));
        return advancements;
    }

    private static AdvancementInfo getInfo(MinecraftServer server, Identifier id) {
        if (id == null) return null;
        AdvancementHolder entry = server.getAdvancements().get(id);
        if (entry != null && entry.value().display().isPresent()) {
            var display = entry.value().display().get();
            return new AdvancementInfo(id, display.getTitle(), display.getIcon().create(), display.getDescription());
        }
        return new AdvancementInfo(id, Component.nullToEmpty(id.toString()), new ItemStack(net.minecraft.world.item.Items.BARRIER), Component.empty());
    }

    public static void sendListToPlayer(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
        List<Identifier> order = data.getAllAdvancementsOrder();
        int index = data.getAllAdvancementsIndex();
        
        List<AdvancementInfo> infoList = order.stream()
                .map(id -> getInfo(server, id))
                .toList();
        
        ServerPlayNetworking.send(player, new AllAchievementsListPacket(infoList, index));
    }

    private static void completeChallenge(MinecraftServer server, ChallengeSavedData data) {
        List<ServerPlayer> eligiblePlayers = server.getPlayerList().getPlayers().stream()
                .filter(p -> !data.isXpAwarded(p.getUUID()))
                .toList();

        if (eligiblePlayers.isEmpty()) return;

        // Completion rewards are shared across the chained collection challenges.
        if (data.getActive().contains(22) && data.getAllItemsIndex() < data.getAllItemsOrder().size()) return;
        if (data.getActive().contains(23) && data.getAllEntitiesIndex() < data.getAllEntitiesOrder().size()) return;

        for (int cid : data.getActive()) {
            eligiblePlayers.forEach(p -> {
                // The world's run clock, not this player's play time: a friend invited a minute
                // before the finish must record the run's real duration, not their own.
                int pTicks = ChallengeTimeUtil.getDisplayRunTicks(p.level().getServer());
                StatsManager.recordCompletion(p.getStringUUID(), cid, pTicks);
            });
        }

        double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
        long xpAmount = Math.round(100.0 * difficulty);

        if (xpAmount > 0) {
            eligiblePlayers.forEach(p -> {
                LevelManager.XpResult res = LevelManager.addXp(p, xpAmount);
                data.setXpAwarded(p.getUUID(), true);
                ServerPlayNetworking.send(p, new ChallengeRewardPacket(res.oldXp, res.newXp, res.actualAmount, true));
                
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
            });
            
            Component chatMsg = Component.translatable("challengecraft.reward.xp_earned", xpAmount)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            server.getPlayerList().broadcastSystemMessage(chatMsg, false);

            Component title = Component.translatable("challengecraft.reward.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            Component subtitle = Component.translatable("challengecraft.reward.xp_earned", xpAmount).withStyle(ChatFormatting.GOLD);

            server.getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            server.getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(title));
            server.getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    public static void skipAdvancement(MinecraftServer server, int amount) {
        if (!active) return;
        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
        List<Identifier> order = data.getAllAdvancementsOrder();
        int index = data.getAllAdvancementsIndex();
        int newIndex = Math.min(index + amount, order.size());
        if (newIndex > index) {
            data.setAllAdvancementsIndex(newIndex);
            syncProgressToAll(server, data);
            if (newIndex >= order.size()) {
                completeChallenge(server, data);
            }
        }
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void syncProgressToAll(MinecraftServer server, ChallengeSavedData data) {
        List<Identifier> order = data.getAllAdvancementsOrder();
        int index = data.getAllAdvancementsIndex();
        Identifier currentId = (index < order.size()) ? order.get(index) : null;
        AdvancementInfo current = getInfo(server, currentId);
        
        AllAchievementsSyncPacket syncPacket = new AllAchievementsSyncPacket(current, index, order.size());
        
        server.getPlayerList().getPlayers().forEach(player -> {
            ServerPlayNetworking.send(player, syncPacket);
        });
    }
}
