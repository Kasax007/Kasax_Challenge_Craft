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
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;

public class Chal_26_AllAchievements {
    private static boolean active = false;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;

            ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
            List<Identifier> order = data.getAllAdvancementsOrder();
            if (order.isEmpty()) {
                generateOrder(server, data);
                order = data.getAllAdvancementsOrder();
            }

            int index = data.getAllAdvancementsIndex();
            if (index >= order.size()) return;

            Identifier currentAdvId = order.get(index);
            AdvancementEntry currentAdv = server.getAdvancementLoader().get(currentAdvId);

            if (currentAdv == null) {
                // If advancement is missing for some reason, skip it
                index++;
                data.setAllAdvancementsIndex(index);
                syncProgressToAll(server, data);
                return;
            }

            boolean found = false;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                AdvancementProgress progress = player.getAdvancementTracker().getProgress(currentAdv);
                if (progress.isDone()) {
                    found = true;
                    break;
                }
            }

            if (found) {
                index++;
                data.setAllAdvancementsIndex(index);
                syncProgressToAll(server, data);

                // Give some XP for completing the advancement (scaled by difficulty)
                double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
                long xpPerAdv = 10; // Slightly more than items
                if (xpPerAdv > 0 && difficulty > 0) {
                    server.getPlayerManager().getPlayerList().forEach(p -> {
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
        
        long seed = server.getOverworld().getSeed();
        Collections.shuffle(survivalAdvancements, new Random(seed));

        data.setAllAdvancementsOrder(survivalAdvancements);
        data.setAllAdvancementsIndex(0);
        syncProgressToAll(server, data);
    }

    private static List<Identifier> getSurvivalAdvancements(MinecraftServer server) {
        List<Identifier> advancements = new ArrayList<>();
        server.getAdvancementLoader().getAdvancements().forEach(advancement -> {
            Identifier id = advancement.id();
            if (!id.getNamespace().equals("minecraft")) return;
            
            // Exclude technical advancements (no display info)
            if (advancement.value().display().isEmpty()) return;
            
            // Exclude root advancements (no parent)
            if (advancement.value().parent().isEmpty()) return;
            
            // Exclude recipes
            if (id.getPath().startsWith("recipes/")) return;

            advancements.add(id);
        });
        // Sort by ID to keep it deterministic before shuffle
        advancements.sort(Comparator.comparing(Identifier::toString));
        return advancements;
    }

    private static AdvancementInfo getInfo(MinecraftServer server, Identifier id) {
        if (id == null) return null;
        AdvancementEntry entry = server.getAdvancementLoader().get(id);
        if (entry != null && entry.value().display().isPresent()) {
            var display = entry.value().display().get();
            return new AdvancementInfo(id, display.getTitle(), display.getIcon(), display.getDescription());
        }
        return new AdvancementInfo(id, Text.literal(id.toString()), new ItemStack(net.minecraft.item.Items.BARRIER), Text.literal(""));
    }

    public static void sendListToPlayer(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
        List<Identifier> order = data.getAllAdvancementsOrder();
        int index = data.getAllAdvancementsIndex();
        
        List<AdvancementInfo> infoList = order.stream()
                .map(id -> getInfo(server, id))
                .toList();
        
        ServerPlayNetworking.send(player, new AllAchievementsListPacket(infoList, index));
    }

    private static void completeChallenge(MinecraftServer server, ChallengeSavedData data) {
        List<ServerPlayerEntity> eligiblePlayers = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> !data.isXpAwarded(p.getUuid()))
                .toList();

        if (eligiblePlayers.isEmpty()) return;

        // Check if other all-inclusive challenges are done if they are active
        if (data.getActive().contains(22) && data.getAllItemsIndex() < data.getAllItemsOrder().size()) return;
        if (data.getActive().contains(23) && data.getAllEntitiesIndex() < data.getAllEntitiesOrder().size()) return;

        for (int cid : data.getActive()) {
            eligiblePlayers.forEach(p -> {
                int pTicks = ChallengeTimeUtil.getDisplayPlayTicks(p);
                StatsManager.recordCompletion(p.getUuidAsString(), cid, pTicks);
            });
        }

        double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
        long xpAmount = Math.round(100.0 * difficulty);

        if (xpAmount > 0) {
            eligiblePlayers.forEach(p -> {
                LevelManager.XpResult res = LevelManager.addXp(p, xpAmount);
                data.setXpAwarded(p.getUuid(), true);
                ServerPlayNetworking.send(p, new ChallengeRewardPacket(res.oldXp, res.newXp, res.actualAmount, true));
                
                p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
            });
            
            Text chatMsg = Text.translatable("challengecraft.reward.xp_earned", xpAmount)
                    .formatted(Formatting.GOLD, Formatting.BOLD);
            server.getPlayerManager().broadcast(chatMsg, false);

            Text title = Text.translatable("challengecraft.reward.title").formatted(Formatting.GREEN, Formatting.BOLD);
            Text subtitle = Text.translatable("challengecraft.reward.xp_earned", xpAmount).formatted(Formatting.GOLD);

            server.getPlayerManager().sendToAll(new TitleFadeS2CPacket(10, 70, 20));
            server.getPlayerManager().sendToAll(new TitleS2CPacket(title));
            server.getPlayerManager().sendToAll(new SubtitleS2CPacket(subtitle));
        }
    }

    public static void skipAdvancement(MinecraftServer server, int amount) {
        if (!active) return;
        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
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
        
        server.getPlayerManager().getPlayerList().forEach(player -> {
            ServerPlayNetworking.send(player, syncPacket);
        });
    }
}
