package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengeSyncPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ChallengeManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(ChallengeCraft.MOD_ID);

    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            applyTo(world);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> syncToAll(server));
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                Chal_19_MinePotionEffect.applyEffect(serverPlayer, state.getBlock());
            }
        });
    }

    /**
     * Force-reapply all active challenges (e.g. after /reload).
     */
    public static void applyAll(net.minecraft.server.MinecraftServer server) {
        LOGGER.info("ChallengeManager.applyAll: re-applying to all worlds");
        for (ServerWorld world : server.getWorlds()) {
            applyTo(world);
        }
        syncToAll(server);
    }

    public static void syncToAll(net.minecraft.server.MinecraftServer server) {
        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
        List<Integer> active = data.getActive();
        ChallengeSyncPacket pkt = new ChallengeSyncPacket(active);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, pkt);
        }
        if (active.contains(22)) {
            net.kasax.challengecraft.challenges.Chal_22_AllItems.syncProgressToAll(server, data);
        }
        if (active.contains(23)) {
            net.kasax.challengecraft.challenges.Chal_23_AllEntities.syncProgressToAll(server, data);
        }
    }

    public static double getDifficulty(int id, int ticks, int slots) {
        return switch (id) {
            case 1 -> 0.5;  // LevelItem (Harder)
            case 2 -> 0.3;  // NoBlockDrops
            case 3 -> 0.3;  // NoMobDrops
            case 4 -> 0.3;  // NoChestLoot
            case 5 -> 0.5;  // NoRegen
            case 6 -> 0.5;  // NoVillagerTrading
            case 7 -> (20.0 - ticks) / 19.0; // MaxHealthModify: 20 ticks=0, 1 tick=1.0
            case 8 -> 1.0;  // NoCraftingTable
            case 9 -> 5.0;  // ExpWorldBorder
            case 10 -> -0.5; // RandomItem
            case 11 -> 1.5; // SkyblockWorld
            case 12 -> (36.0 - slots) / 35.0; // LimitedInventory: 36 slots=0, 1 slot=1.0
            case 13 -> -0.5; // RandomEnchantment (Easier)
            case 14 -> 0.4;  // RandomBlockDrops
            case 15 -> 0.6;  // RandomMobDrops
            case 16 -> 0.2; // RandomChunkBlocks (Easier)
            case 17 -> 0.4; // WalkRandomItem
            case 18 -> 0.4; // DamageRandomItem
            case 19 -> 0.5; // MinePotionEffect
            case 20 -> 0.8; // RandomizedCrafting
            case 21 -> 1.0; // Hardcore
            case 22 -> 30.0; // All Items (Very Hard!)
            case 23 -> 15.0; // All Entities (Very Hard!)
            default -> 0.0;
        };
    }

    public static double calculateTotalDifficulty(List<Integer> ids, int heartsTicks, int inventorySlots) {
        double total = 0;
        for (int id : ids) {
            total += getDifficulty(id, heartsTicks, inventorySlots);
        }
        return Math.max(0, total); // Ensure it's not negative
    }

    private static void applyTo(ServerWorld world) {
        // 1) Always load or create our saved data from the OVERWORLD
        ServerWorld overworld = world.getServer().getOverworld();
        ChallengeSavedData data = ChallengeSavedData.get(overworld);
        List<Integer> saved = data.getActive();

        // 2) On Overworld load, restore specific settings and handle seeding
        if (world.getRegistryKey() == World.OVERWORLD) {
            // Restore slider values for specific challenges
            if (saved.contains(7)) {
                int savedTicks = data.getMaxHeartsTicks();
                float hearts   = savedTicks * 0.5f;
                Chal_7_MaxHealthModify.setMaxHearts(hearts);
                LOGGER.info("[Manager] restored Chal7 maxHearts = {} hearts", hearts);
            }
            if (saved.contains(12)) {
                int savedSlots = data.getLimitedInventorySlots();
                Chal_12_LimitedInventory.setLimitedSlots(savedSlots);
                LOGGER.info("[Manager] restored limited inventory slots = {}", savedSlots);
            }

            // Seed from client if first boot
            // We check if it's a fresh save. By default 'active' is [1].
            // If the client has something else or if it's truly the first time.
            if (!data.isDifficultySet() && !ChallengeCraftClient.LAST_CHOSEN.isEmpty()) {
                
                int clientTicks = MathHelper.clamp(ChallengeCraftClient.SELECTED_MAX_HEARTS, 1, 20);
                int clientSlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;

                data.setMaxHeartsTicks(clientTicks);
                data.setActive(List.copyOf(ChallengeCraftClient.LAST_CHOSEN));
                data.setLimitedInventorySlots(clientSlots);
                
                double initialDiff = calculateTotalDifficulty(ChallengeCraftClient.LAST_CHOSEN, clientTicks, clientSlots);
                data.setInitialDifficulty(initialDiff);
                data.setDifficultySet(true);

                Chal_7_MaxHealthModify.setMaxHearts(clientTicks * 0.5f);
                Chal_12_LimitedInventory.setLimitedSlots(clientSlots);
                
                LOGGER.info("ChallengeManager: seeded from client LAST_CHOSEN {}. Initial Difficulty: {}", ChallengeCraftClient.LAST_CHOSEN, initialDiff);
                
                // re-read the saved list
                saved = data.getActive();
            }
        }

        // 3) Turn everything off
        LOGGER.info("ChallengeManager: turning all challenges OFF");
        Chal_1_LevelItem        .setActive(false);
        Chal_2_NoBlockDrops     .setActive(false);
        Chal_3_NoMobDrops       .setActive(false);
        Chal_4_NoChestLoot      .setActive(false);
        Chal_5_NoRegen          .setActive(false);
        Chal_6_NoVillagerTrading.setActive(false);
        Chal_7_MaxHealthModify  .setActive(false);
        Chal_8_NoCraftingTable  .setActive(false);
        Chal_9_ExpWorldBorder   .setActive(false);
        Chal_10_RandomItem      .setActive(false);
        Chal_11_SkyblockWorld   .setActive(false);
        Chal_12_LimitedInventory.setActive(false);
        Chal_13_RandomEnchantment.setActive(false);
        Chal_14_RandomBlockDrops.setActive(false);
        Chal_15_RandomMobDrops.setActive(false);
        Chal_16_RandomChunkBlocks.setActive(false);
        Chal_17_WalkRandomItem.setActive(false);
        Chal_18_DamageRandomItem.setActive(false);
        Chal_19_MinePotionEffect.setActive(false);
        Chal_20_RandomizedCrafting.setActive(false);
        Chal_21_Hardcore.setActive(false);
        Chal_22_AllItems.setActive(false);
        Chal_23_AllEntities.setActive(false);

        // 4) Turn back on only the ones in the saved list
        LOGGER.info("ChallengeManager: got actives → {}", saved);
        for (int id : saved) {
            switch (id) {
                case 1  -> { Chal_1_LevelItem        .setActive(true); LOGGER.info("Challenge 1 ON"); }
                case 2  -> { Chal_2_NoBlockDrops     .setActive(true); LOGGER.info("Challenge 2 ON"); }
                case 3  -> { Chal_3_NoMobDrops       .setActive(true); LOGGER.info("Challenge 3 ON"); }
                case 4  -> { Chal_4_NoChestLoot      .setActive(true); LOGGER.info("Challenge 4 ON"); }
                case 5  -> { Chal_5_NoRegen          .setActive(true); LOGGER.info("Challenge 5 ON"); }
                case 6  -> { Chal_6_NoVillagerTrading.setActive(true); LOGGER.info("Challenge 6 ON"); }
                case 7  -> { Chal_7_MaxHealthModify  .setActive(true); LOGGER.info("Challenge 7 ON"); }
                case 8  -> { Chal_8_NoCraftingTable  .setActive(true); LOGGER.info("Challenge 8 ON"); }
                case 9  -> { Chal_9_ExpWorldBorder   .setActive(true); LOGGER.info("Challenge 9 ON"); }
                case 10 -> { Chal_10_RandomItem      .setActive(true); LOGGER.info("Challenge 10 ON"); }
                case 11 -> { Chal_11_SkyblockWorld   .setActive(true); LOGGER.info("Challenge 11 ON"); }
                case 12 -> { Chal_12_LimitedInventory.setActive(true); LOGGER.info("Challenge 12 ON"); }
                case 13 -> { Chal_13_RandomEnchantment.setActive(true); LOGGER.info("Challenge 13 ON"); }
                case 14 -> { Chal_14_RandomBlockDrops.setActive(true); LOGGER.info("Challenge 14 ON"); }
                case 15 -> { Chal_15_RandomMobDrops.setActive(true); LOGGER.info("Challenge 15 ON"); }
                case 16 -> { Chal_16_RandomChunkBlocks.setActive(true); LOGGER.info("Challenge 16 ON"); }
                case 17 -> { Chal_17_WalkRandomItem.setActive(true); LOGGER.info("Challenge 17 ON"); }
                case 18 -> { Chal_18_DamageRandomItem.setActive(true); LOGGER.info("Challenge 18 ON"); }
                case 19 -> { Chal_19_MinePotionEffect.setActive(true); LOGGER.info("Challenge 19 ON"); }
                case 20 -> { Chal_20_RandomizedCrafting.setActive(true); LOGGER.info("Challenge 20 ON"); }
                case 21 -> { Chal_21_Hardcore.setActive(true); LOGGER.info("Challenge 21 ON"); }
                case 22 -> {
                    Chal_22_AllItems.setActive(true);
                    Chal_22_AllItems.syncProgressToAll(world.getServer(), data);
                    LOGGER.info("Challenge 22 ON");
                }
                case 23 -> {
                    Chal_23_AllEntities.setActive(true);
                    Chal_23_AllEntities.syncProgressToAll(world.getServer(), data);
                    LOGGER.info("Challenge 23 ON");
                }
                default -> LOGGER.warn("Unknown challenge id {}", id);
            }
        }

        // 5) Finally, update game rules for block/mob drops
        var rules         = world.getGameRules();
        var tileDropsRule = rules.get(GameRules.DO_TILE_DROPS);
        var mobLootRule   = rules.get(GameRules.DO_MOB_LOOT);

        tileDropsRule.set(!Chal_2_NoBlockDrops.isActive(), world.getServer());
        mobLootRule .set(!Chal_3_NoMobDrops    .isActive(), world.getServer());
    }

}
