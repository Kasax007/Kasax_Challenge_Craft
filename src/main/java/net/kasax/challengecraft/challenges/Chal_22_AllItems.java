package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.network.AllItemsSyncPacket;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import java.util.*;
import java.util.stream.Collectors;

/** Tracks the ordered all-items run, syncs HUD state, and awards completion XP once. */
public class Chal_22_AllItems {
    private static boolean active = false;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;

            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            List<ItemStack> order = data.getAllItemsOrder();
            if (order.isEmpty()) {
                generateOrder(server, data);
                order = data.getAllItemsOrder();
            }

            int index = data.getAllItemsIndex();
            if (index >= order.size()) return;

            ItemStack currentItem = order.get(index);

            boolean found = false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (hasItem(player, currentItem)) {
                    found = true;
                    break;
                }
            }

            if (found) {
                index++;
                data.setAllItemsIndex(index);
                syncProgressToAll(server, data);

                double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
                long xpPerItem = Math.round(5.0);
                if (xpPerItem > 0 && difficulty > 0) {
                    server.getPlayerList().getPlayers().forEach(p -> {
                        LevelManager.addXp(p, xpPerItem);
                    });
                }

                if (index >= order.size()) {
                    completeChallenge(server, data);
                }
            }
        });
    }

    private static boolean hasItem(ServerPlayer player, ItemStack target) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(target.getItem())) {
                if (target.getItem() == Items.POTION || target.getItem() == Items.SPLASH_POTION || target.getItem() == Items.LINGERING_POTION) {
                    if (Objects.equals(stack.get(DataComponents.POTION_CONTENTS), target.get(DataComponents.POTION_CONTENTS))) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private static void generateOrder(MinecraftServer server, ChallengeSavedData data) {
        List<ItemStack> survivalItems = getSurvivalItems(server.registryAccess());
        
        long seed = server.overworld().getSeed();
        Collections.shuffle(survivalItems, new Random(seed));

        data.setAllItemsOrder(survivalItems);
        data.setAllItemsIndex(0);
        syncProgressToAll(server, data);
    }

    /** Shared with Force Item Battle (45), which draws per-player targets from the same pool. */
    public static List<ItemStack> getSurvivalItems(RegistryAccess registryManager) {
        List<ItemStack> items = new ArrayList<>();
        Registry<Item> itemRegistry = registryManager.lookupOrThrow(Registries.ITEM);
        Registry<Potion> potionRegistry = registryManager.lookupOrThrow(Registries.POTION);

        for (Item item : itemRegistry) {
            Identifier id = itemRegistry.getKey(item);
            if (id.getNamespace().equals("challengecraft")) continue;
            if (!id.getNamespace().equals("minecraft")) continue;
            
            if (item == Items.AIR) continue;
            if (item == Items.ENCHANTED_BOOK) continue;
            if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) {
                continue;
            }

            if (isBlacklisted(id.getPath())) continue;
            
            items.add(new ItemStack(item));
        }

        for (Potion potion : potionRegistry) {
            Identifier pid = potionRegistry.getKey(potion);
            if (pid.getPath().equals("empty") || pid.getPath().equals("luck")) continue;
            if (pid.getPath().equals("mundane") || pid.getPath().equals("thick") || pid.getPath().equals("awkward")) {
                continue;
            }
            if (pid.getPath().equals("water")) {
                items.add(PotionContents.createItemStack(Items.POTION, potionRegistry.wrapAsHolder(potion)));
                continue;
            }

            items.add(PotionContents.createItemStack(Items.POTION, potionRegistry.wrapAsHolder(potion)));
            items.add(PotionContents.createItemStack(Items.SPLASH_POTION, potionRegistry.wrapAsHolder(potion)));
            items.add(PotionContents.createItemStack(Items.LINGERING_POTION, potionRegistry.wrapAsHolder(potion)));
        }

        return items;
    }

    private static boolean isBlacklisted(String path) {
        if (path.endsWith("_spawn_egg")) return true;
        if (path.startsWith("air")) return true;
        if (path.equals("barrier")) return true;
        if (path.equals("structure_block")) return true;
        if (path.equals("structure_void")) return true;
        if (path.equals("command_block")) return true;
        if (path.equals("repeating_command_block")) return true;
        if (path.equals("chain_command_block")) return true;
        if (path.equals("command_block_minecart")) return true;
        if (path.equals("jigsaw")) return true;
        if (path.equals("light")) return true;
        if (path.equals("debug_stick")) return true;
        if (path.equals("knowledge_book")) return true;
        if (path.equals("spawner")) return true;
        if (path.equals("bedrock")) return true;
        if (path.equals("end_portal_frame")) return true;
        if (path.equals("petrified_oak_slab")) return true;
        if (path.equals("vault")) return true;
        if (path.equals("trial_spawner")) return true;
        if (path.contains("test_block")) return true;
        if (path.equals("filled_map")) return true;
        if (path.equals("player_head")) return true;
        if (path.equals("reinforced_deepslate")) return true;
        if (path.equals("written_book")) return true;
        return false;
    }

    public static Component getFormattedItemName(ItemStack stack) {
        MutableComponent name = stack.getHoverName().copy();
        if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) {
             name.append(" (").append(Component.translatable(stack.getItem().getDescriptionId() + ".desc")).append(")");
        }
        return name;
    }

    private static void completeChallenge(MinecraftServer server, ChallengeSavedData data) {
        List<ServerPlayer> eligiblePlayers = server.getPlayerList().getPlayers().stream()
                .filter(p -> !data.isXpAwarded(p.getUUID()))
                .toList();

        if (eligiblePlayers.isEmpty()) return;

        // Completion rewards are shared across the chained collection challenges.
        if (data.getActive().contains(23)) {
            if (data.getAllEntitiesIndex() < data.getAllEntitiesOrder().size()) {
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
            if (data.getActive().contains(23) && data.getAllEntitiesIndex() < data.getAllEntitiesOrder().size()) {
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

    public static void skipItem(MinecraftServer server, int amount) {
        if (!active) return;
        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
        List<ItemStack> order = data.getAllItemsOrder();
        int index = data.getAllItemsIndex();
        int newIndex = Math.min(index + amount, order.size());
        if (newIndex > index) {
            data.setAllItemsIndex(newIndex);
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
        List<ItemStack> order = data.getAllItemsOrder();
        int index = data.getAllItemsIndex();
        ItemStack currentItem = (index < order.size()) ? order.get(index) : ItemStack.EMPTY;
        AllItemsSyncPacket packet = new AllItemsSyncPacket(currentItem, index, order.size());
        server.getPlayerList().getPlayers().forEach(player -> {
            ServerPlayNetworking.send(player, packet);
        });
    }
}
