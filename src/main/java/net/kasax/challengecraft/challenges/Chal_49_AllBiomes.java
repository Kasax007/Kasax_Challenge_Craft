package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.network.AllBiomesSyncPacket;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * "Alle Biome" / All Biomes (challenge 49): stand in every biome of the Overworld, the Nether and
 * the End, in any order. The whole server works on one shared checklist.
 *
 * <p>Which biomes count is read from the world's own generators
 * ({@code BiomeSource.possibleBiomes()} of the three vanilla dimensions), never from a hand list:
 * that is exactly what this world can generate, including whatever a version adds (26.3's dappled
 * forest and sulfur caves came in by themselves) and excluding what it cannot. Cave biomes count
 * like any other — finding a deep dark is part of the run.
 *
 * <p>Like All Achievements it pays 10 XP per new biome and the full payout when the list is done.
 * It chains with the other collections (22, 23, 26): the run only completes when all of them are.
 */
public class Chal_49_AllBiomes {
    private static boolean active = false;

    /** biome id -> dimension ({@link AllBiomesSyncPacket} constants), per server, in display order. */
    private static Map<String, Integer> required;
    private static MinecraftServer requiredFor;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active || server.getTickCount() % 10 != 0) {
                return;
            }
            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            Map<String, Integer> all = required(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isSpectator()) {
                    continue;
                }
                Optional<ResourceKey<Biome>> key = player.level().getBiome(player.blockPosition()).unwrapKey();
                if (key.isEmpty()) {
                    continue;
                }
                String id = key.get().identifier().toString();
                if (all.containsKey(id) && data.addVisitedBiome(id)) {
                    onFound(server, data, player, id, all);
                }
            }
        });
        ServerPlayerEvents.JOIN.register(player -> {
            if (active) {
                MinecraftServer server = player.level().getServer();
                ServerPlayNetworking.send(player, packet(server, ChallengeSavedData.get(server.overworld())));
            }
        });
    }

    /** Pushes the checklist to everyone — on activation, and whenever a biome is found. */
    public static void syncToAll(MinecraftServer server) {
        AllBiomesSyncPacket pkt = packet(server, ChallengeSavedData.get(server.overworld()));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, pkt);
        }
    }

    private static void onFound(MinecraftServer server, ChallengeSavedData data, ServerPlayer finder, String id,
                                Map<String, Integer> all) {
        int found = countFound(data, all);
        Component name = Component.translatable(Identifier.parse(id).toLanguageKey("biome"));
        Component line = Component.translatable("challengecraft.all_biomes.found", finder.getDisplayName(), name, found, all.size())
                .withStyle(ChatFormatting.GREEN);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendOverlayMessage(line);
        }
        finder.level().playSound(null, finder.getX(), finder.getY(), finder.getZ(),
                SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.8f, 1.2f);
        syncToAll(server);

        double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
        if (difficulty > 0) {
            server.getPlayerList().getPlayers().forEach(p -> LevelManager.addXp(p, 10));
        }
        if (found >= all.size()) {
            completeChallenge(server, data);
        }
    }

    /** Whether every required biome has been found. Used by the other collections' completion checks. */
    public static boolean isComplete(MinecraftServer server, ChallengeSavedData data) {
        Map<String, Integer> all = required(server);
        return countFound(data, all) >= all.size();
    }

    private static int countFound(ChallengeSavedData data, Map<String, Integer> all) {
        int n = 0;
        for (String id : data.getAllBiomesVisited()) {
            if (all.containsKey(id)) {
                n++;
            }
        }
        return n;
    }

    private static Map<String, Integer> required(MinecraftServer server) {
        if (required == null || requiredFor != server) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            ServerLevel nether = server.getLevel(Level.NETHER);
            ServerLevel end = server.getLevel(Level.END);
            Map<String, Integer> map = new LinkedHashMap<>();
            addDimension(map, overworld, AllBiomesSyncPacket.OVERWORLD);
            addDimension(map, nether, AllBiomesSyncPacket.NETHER);
            addDimension(map, end, AllBiomesSyncPacket.END);
            // applyActiveFlag syncs while the OVERWORLD is still loading, before the Nether and the
            // End exist. Caching that answer made the run need 56 biomes and no Nether or End at all
            // (measured in the boot harness). Only a list built from all three dimensions is kept.
            if (overworld == null || nether == null || end == null) {
                return map;
            }
            required = map;
            requiredFor = server;
            net.kasax.challengecraft.ChallengeCraft.LOGGER.info("[AllBiomes] {} biomes required ({} overworld, {} nether, {} end)",
                    map.size(), map.values().stream().filter(d -> d == 0).count(),
                    map.values().stream().filter(d -> d == 1).count(), map.values().stream().filter(d -> d == 2).count());
        }
        return required;
    }

    private static void addDimension(Map<String, Integer> map, ServerLevel level, int dimension) {
        if (level == null) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (Holder<Biome> holder : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            holder.unwrapKey().ifPresent(k -> ids.add(k.identifier().toString()));
        }
        ids.sort(String::compareTo);
        for (String id : ids) {
            map.putIfAbsent(id, dimension);
        }
    }

    private static AllBiomesSyncPacket packet(MinecraftServer server, ChallengeSavedData data) {
        Map<String, Integer> all = required(server);
        return new AllBiomesSyncPacket(new ArrayList<>(all.keySet()), new ArrayList<>(all.values()),
                data.getAllBiomesVisited());
    }

    /** Op escape hatch: marks the first missing biome found (for a biome a seed simply lacks). */
    public static boolean skipBiome(MinecraftServer server) {
        if (!active) {
            return false;
        }
        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
        Map<String, Integer> all = required(server);
        for (String id : all.keySet()) {
            if (data.addVisitedBiome(id)) {
                syncToAll(server);
                if (countFound(data, all) >= all.size()) {
                    completeChallenge(server, data);
                }
                return true;
            }
        }
        return false;
    }

    private static void completeChallenge(MinecraftServer server, ChallengeSavedData data) {
        List<ServerPlayer> eligible = server.getPlayerList().getPlayers().stream()
                .filter(p -> !data.isXpAwarded(p.getUUID()))
                .toList();
        if (eligible.isEmpty()) {
            return;
        }
        // Chained collections: the run is only over when every one of them is.
        if (data.getActive().contains(22) && data.getAllItemsIndex() < data.getAllItemsOrder().size()) return;
        if (data.getActive().contains(23) && data.getAllEntitiesIndex() < data.getAllEntitiesOrder().size()) return;
        if (data.getActive().contains(26) && data.getAllAdvancementsIndex() < data.getAllAdvancementsOrder().size()) return;

        final int summaryTicks = ChallengeTimeUtil.getDisplayRunTicks(server);
        final Map<UUID, Boolean> newBest = new java.util.HashMap<>();
        eligible.forEach(p -> newBest.put(p.getUUID(),
                net.kasax.challengecraft.network.RunSummary.isNewBest(p, data.getActive(), summaryTicks)));
        if (!data.isDailyRun()) {
            for (int cid : data.getActive()) {
                eligible.forEach(p -> StatsManager.recordCompletion(p.getStringUUID(), cid, summaryTicks));
            }
        }

        double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
        long xpAmount = Math.round(100.0 * difficulty);
        if (xpAmount <= 0) {
            return;
        }
        eligible.forEach(p -> {
            LevelManager.XpResult res = LevelManager.addXp(p, xpAmount);
            data.setXpAwarded(p.getUUID(), true);
            ServerPlayNetworking.send(p, new ChallengeRewardPacket(res.oldXp, res.newXp, res.actualAmount, true));
            net.kasax.challengecraft.network.RunSummary.sendTimed(p,
                    net.kasax.challengecraft.network.RunSummaryPacket.Kind.ALL_BIOMES,
                    data, res.actualAmount, res.oldXp, res.newXp, newBest.getOrDefault(p.getUUID(), false));
        });
        server.getPlayerList().broadcastSystemMessage(Component.translatable("challengecraft.reward.xp_earned", xpAmount)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        server.getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        server.getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(
                Component.translatable("challengecraft.reward.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
        server.getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(
                Component.translatable("challengecraft.reward.xp_earned", xpAmount).withStyle(ChatFormatting.GOLD)));
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }
}
