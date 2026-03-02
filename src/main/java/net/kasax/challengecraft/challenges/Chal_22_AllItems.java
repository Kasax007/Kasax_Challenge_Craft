package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.network.AllItemsSyncPacket;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Collectors;

public class Chal_22_AllItems {
    private static boolean active = false;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;

            ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
            List<ItemStack> order = data.getAllItemsOrder();
            if (order.isEmpty()) {
                generateOrder(server, data);
                order = data.getAllItemsOrder();
            }

            int index = data.getAllItemsIndex();
            if (index >= order.size()) return;

            ItemStack currentItem = order.get(index);

            boolean found = false;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (hasItem(player, currentItem)) {
                    found = true;
                    break;
                }
            }

            if (found) {
                index++;
                data.setAllItemsIndex(index);
                syncProgressToAll(server, data);

                // Give some XP for finding the item (scaled by difficulty)
                double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
                long xpPerItem = Math.round(5.0);
                if (xpPerItem > 0 && difficulty > 0) {
                    server.getPlayerManager().getPlayerList().forEach(p -> {
                        LevelManager.addXp(p, xpPerItem);
                    });
                }

                if (index >= order.size()) {
                    completeChallenge(server, data);
                }
            }
        });
    }

    private static boolean hasItem(ServerPlayerEntity player, ItemStack target) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(target.getItem())) {
                if (target.getItem() == Items.POTION || target.getItem() == Items.SPLASH_POTION || target.getItem() == Items.LINGERING_POTION) {
                    if (Objects.equals(stack.get(DataComponentTypes.POTION_CONTENTS), target.get(DataComponentTypes.POTION_CONTENTS))) {
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
        List<ItemStack> survivalItems = getSurvivalItems(server.getRegistryManager());
        
        long seed = server.getOverworld().getSeed();
        Collections.shuffle(survivalItems, new Random(seed));

        data.setAllItemsOrder(survivalItems);
        data.setAllItemsIndex(0);
        syncProgressToAll(server, data);
    }

    private static List<ItemStack> getSurvivalItems(DynamicRegistryManager registryManager) {
        List<ItemStack> items = new ArrayList<>();
        Registry<Item> itemRegistry = registryManager.getOrThrow(RegistryKeys.ITEM);
        Registry<Potion> potionRegistry = registryManager.getOrThrow(RegistryKeys.POTION);

        for (Item item : itemRegistry) {
            Identifier id = itemRegistry.getId(item);
            if (!id.getNamespace().equals("minecraft")) continue;
            
            if (item == Items.AIR) continue;
            if (item == Items.ENCHANTED_BOOK) continue;
            if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) {
                continue;
            }

            if (isBlacklisted(id.getPath())) continue;
            
            items.add(new ItemStack(item));
        }

        // Add Potions
        for (Potion potion : potionRegistry) {
            Identifier pid = potionRegistry.getId(potion);
            if (pid.getPath().equals("empty") || pid.getPath().equals("luck")) continue;
            if (pid.getPath().equals("mundane") || pid.getPath().equals("thick") || pid.getPath().equals("awkward")) {
                continue;
            }
            // Inclusion of Water Bottle
            if (pid.getPath().equals("water")) {
                items.add(PotionContentsComponent.createStack(Items.POTION, potionRegistry.getEntry(potion)));
                continue;
            }

            items.add(PotionContentsComponent.createStack(Items.POTION, potionRegistry.getEntry(potion)));
            items.add(PotionContentsComponent.createStack(Items.SPLASH_POTION, potionRegistry.getEntry(potion)));
            items.add(PotionContentsComponent.createStack(Items.LINGERING_POTION, potionRegistry.getEntry(potion)));
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

    public static Text getFormattedItemName(ItemStack stack) {
        MutableText name = stack.getName().copy();
        if (stack.contains(DataComponentTypes.JUKEBOX_PLAYABLE)) {
             name.append(" (").append(Text.translatable(stack.getItem().getTranslationKey() + ".desc")).append(")");
        }
        return name;
    }

    private static void completeChallenge(MinecraftServer server, ChallengeSavedData data) {
        if (data.isXpAwarded()) return;

        // If All Entities challenge is active, ensure it is also completed
        if (data.getActive().contains(23)) {
            if (data.getAllEntitiesIndex() < data.getAllEntitiesOrder().size()) {
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
        long xpAmount = Math.round(100.0 * difficulty); // Increased completion reward

        if (xpAmount > 0) {
            boolean isGameComp = true;
            if (data.getActive().contains(23) && data.getAllEntitiesIndex() < data.getAllEntitiesOrder().size()) {
                isGameComp = false;
            }

            final boolean finalIsGameComp = isGameComp;
            server.getPlayerManager().getPlayerList().forEach(p -> {
                LevelManager.XpResult res = LevelManager.addXp(p, xpAmount);
                ServerPlayNetworking.send(p, new ChallengeRewardPacket(res.oldXp, res.newXp, res.actualAmount, finalIsGameComp));
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

    public static void skipItem(MinecraftServer server, int amount) {
        if (!active) return;
        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
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
        server.getPlayerManager().getPlayerList().forEach(player -> {
            ServerPlayNetworking.send(player, packet);
        });
    }
}
