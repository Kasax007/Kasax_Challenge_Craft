package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.network.AllEntitiesSyncPacket;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import java.util.*;

/** Tracks the ordered all-entities run, syncs HUD state, and awards completion XP once. */
public class Chal_23_AllEntities {
    private static boolean active = false;

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!active) return;
            if (damageSource.getEntity() instanceof ServerPlayer player) {
                MinecraftServer server = player.level().getServer();
                if (server == null) return;

                ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
                List<EntityType<?>> order = data.getAllEntitiesOrder();
                if (order.isEmpty()) {
                    generateOrder(server, data);
                    order = data.getAllEntitiesOrder();
                }

                int index = data.getAllEntitiesIndex();
                if (index >= order.size()) return;

                EntityType<?> currentTarget = order.get(index);
                if (entity.getType() == currentTarget) {
                    index++;
                    data.setAllEntitiesIndex(index);
                    syncProgressToAll(server, data);

                    double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
                    long xpPerEntity = Math.round(10.0);
                    if (xpPerEntity > 0 && difficulty > 0) {
                        server.getPlayerList().getPlayers().forEach(p -> {
                            LevelManager.addXp(p, xpPerEntity);
                        });
                    }

                    if (index >= order.size()) {
                        completeChallenge(server, data);
                    }
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            if (data.getAllEntitiesOrder().isEmpty()) {
                generateOrder(server, data);
            }
        });
    }

    private static void generateOrder(MinecraftServer server, ChallengeSavedData data) {
        List<EntityType<?>> entityTypes = getEntitiesWithSpawnEggs();
        
        long seed = server.overworld().getSeed();
        Collections.shuffle(entityTypes, new Random(seed));

        ChallengeCraft.LOGGER.info("[Chal23] entity pool: {} killable types (spawn-egg filtered)", entityTypes.size());
        data.setAllEntitiesOrder(entityTypes);
        data.setAllEntitiesIndex(0);
        syncProgressToAll(server, data);
    }

    public static List<EntityType<?>> getEntitiesWithSpawnEggs() {
        List<EntityType<?>> list = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            // isPresent(), NOT != null. 26.2 changed byId to return Optional<Holder<Item>>, which
            // is never null — so the old null check passed for EVERY entity type in the registry and
            // the run demanded you kill primed TNT, boats, item frames, markers, item displays,
            // lightning bolts, eyes of ender and wither skulls. "Has a spawn egg" is the intended
            // rule and is exactly the set of mobs a player can legitimately find and kill.
            // The Ender Dragon stays IN. It used to be excluded, but the run only pays out once
            // every entity is done, and killing the dragon is the mod's finish line anyway — so a
            // list that omits it is missing the one kill every run ends with. It has a spawn egg in
            // 26.2, so the filter below keeps it.
            if (SpawnEggItem.byId(type).isPresent()) {
                list.add(type);
            }
        }
        return list;
    }

    private static void completeChallenge(MinecraftServer server, ChallengeSavedData data) {
        List<ServerPlayer> eligiblePlayers = server.getPlayerList().getPlayers().stream()
                .filter(p -> !data.isXpAwarded(p.getUUID()))
                .toList();

        if (eligiblePlayers.isEmpty()) return;

        // Completion rewards are shared across the chained collection challenges.
        if (data.getActive().contains(22)) {
            if (data.getAllItemsIndex() < data.getAllItemsOrder().size()) {
                return;
            }
        }

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
            boolean isGameComp = true;
            if (data.getActive().contains(22) && data.getAllItemsIndex() < data.getAllItemsOrder().size()) {
                isGameComp = false;
            }

            final boolean finalIsGameComp = isGameComp;
            eligiblePlayers.forEach(p -> {
                LevelManager.XpResult res = LevelManager.addXp(p, xpAmount);
                data.setXpAwarded(p.getUUID(), true);
                ServerPlayNetworking.send(p, new ChallengeRewardPacket(res.oldXp, res.newXp, res.actualAmount, finalIsGameComp));
                
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

    public static void syncProgressToAll(MinecraftServer server, ChallengeSavedData data) {
        List<EntityType<?>> order = data.getAllEntitiesOrder();
        int index = data.getAllEntitiesIndex();
        EntityType<?> current = (index < order.size()) ? order.get(index) : null;
        
        AllEntitiesSyncPacket packet = new AllEntitiesSyncPacket(current, index, order.size());
        server.getPlayerList().getPlayers().forEach(player -> {
            ServerPlayNetworking.send(player, packet);
        });
    }

    public static void skipEntity(MinecraftServer server, int amount) {
        if (!active) return;
        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
        List<EntityType<?>> order = data.getAllEntitiesOrder();
        int index = data.getAllEntitiesIndex();
        int newIndex = Math.min(index + amount, order.size());
        if (newIndex > index) {
            data.setAllEntitiesIndex(newIndex);
            syncProgressToAll(server, data);
            if (newIndex >= order.size()) {
                completeChallenge(server, data);
            }
        }
    }

    public static void setActive(boolean active) {
        Chal_23_AllEntities.active = active;
    }

    public static boolean isActive() {
        return active;
    }

    public static ItemStack getIcon(EntityType<?> type) {
        // SpawnEggItem.byId returns Optional<Holder<Item>> in 26.2.
        return net.minecraft.world.item.SpawnEggItem.byId(type)
                .map(ItemStack::new)
                .orElseGet(() -> new ItemStack(Items.ZOMBIE_SPAWN_EGG));
    }
}
