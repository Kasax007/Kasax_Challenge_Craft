package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.network.AllEntitiesSyncPacket;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;

public class Chal_23_AllEntities {
    private static boolean active = false;

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!active) return;
            if (damageSource.getAttacker() instanceof ServerPlayerEntity player) {
                MinecraftServer server = player.getServer();
                if (server == null) return;

                ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
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

                    if (index >= order.size()) {
                        completeChallenge(server, data);
                    }
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
            if (data.getAllEntitiesOrder().isEmpty()) {
                generateOrder(server, data);
            }
        });
    }

    private static void generateOrder(MinecraftServer server, ChallengeSavedData data) {
        List<EntityType<?>> entityTypes = getEntitiesWithSpawnEggs();
        
        long seed = server.getOverworld().getSeed();
        Collections.shuffle(entityTypes, new Random(seed));

        data.setAllEntitiesOrder(entityTypes);
        data.setAllEntitiesIndex(0);
        syncProgressToAll(server, data);
    }

    private static List<EntityType<?>> getEntitiesWithSpawnEggs() {
        List<EntityType<?>> list = new ArrayList<>();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            if (SpawnEggItem.forEntity(type) != null) {
                list.add(type);
            }
        }
        return list;
    }

    private static void completeChallenge(MinecraftServer server, ChallengeSavedData data) {
        if (data.isXpAwarded()) return;

        // If All Items challenge is active, ensure it is also completed
        if (data.getActive().contains(22)) {
            if (data.getAllItemsIndex() < data.getAllItemsOrder().size()) {
                return;
            }
        }

        int playTicks = 0;
        if (!server.getPlayerManager().getPlayerList().isEmpty()) {
            playTicks = server.getPlayerManager().getPlayerList().get(0).getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
        }

        for (int cid : data.getActive()) {
            StatsManager.recordCompletion(cid, playTicks);
        }

        double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
        long xpAmount = Math.round(100.0 * difficulty);

        if (xpAmount > 0) {
            server.getPlayerManager().getPlayerList().forEach(p -> {
                LevelManager.XpResult res = LevelManager.addXp(p, xpAmount);
                ServerPlayNetworking.send(p, new ChallengeRewardPacket(res.oldXp, res.newXp, res.actualAmount));
            });
            Text chatMsg = Text.translatable("challengecraft.reward.xp_earned", xpAmount)
                    .formatted(Formatting.GOLD, Formatting.BOLD);
            server.getPlayerManager().broadcast(chatMsg, false);

            server.getPlayerManager().getPlayerList().forEach(p -> {
                p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
            });

            Text title = Text.translatable("challengecraft.reward.title").formatted(Formatting.GREEN, Formatting.BOLD);
            Text subtitle = Text.translatable("challengecraft.reward.xp_earned", xpAmount).formatted(Formatting.GOLD);

            server.getPlayerManager().sendToAll(new TitleFadeS2CPacket(10, 70, 20));
            server.getPlayerManager().sendToAll(new TitleS2CPacket(title));
            server.getPlayerManager().sendToAll(new SubtitleS2CPacket(subtitle));
        }

        data.setXpAwarded(true);
    }

    public static void syncProgressToAll(MinecraftServer server, ChallengeSavedData data) {
        List<EntityType<?>> order = data.getAllEntitiesOrder();
        int index = data.getAllEntitiesIndex();
        EntityType<?> current = (index < order.size()) ? order.get(index) : null;
        
        AllEntitiesSyncPacket packet = new AllEntitiesSyncPacket(current, index, order.size());
        server.getPlayerManager().getPlayerList().forEach(player -> {
            ServerPlayNetworking.send(player, packet);
        });
    }

    public static void skipEntity(MinecraftServer server, int amount) {
        if (!active) return;
        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
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
        net.minecraft.item.SpawnEggItem egg = net.minecraft.item.SpawnEggItem.forEntity(type);
        if (egg != null) return new ItemStack(egg);
        return new ItemStack(Items.ZOMBIE_SPAWN_EGG);
    }
}
