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
        List<Integer> perks = data.getActivePerks();
        ChallengeSyncPacket pkt = new ChallengeSyncPacket(
                active,
                perks,
                data.getMaxHeartsTicks(),
                data.getLimitedInventorySlots(),
                data.getMobHealthMultiplier()
        );
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

    public static double getDifficulty(int id, int ticks, int slots, int mobHealthMult) {
        if (id == LevelManager.PERK_INFINITY_WEAPON) return 0.0;
        if (LevelManager.ALL_PERKS.contains(id)) {
            return -0.5;
        }
        return switch (id) {
            case 1 -> 0.8;  // LevelItem (Harder)
            case 2 -> 1.0;  // NoBlockDrops (Very Hard)
            case 3 -> 0.8;  // NoMobDrops
            case 4 -> 0.5;  // NoChestLoot
            case 5 -> 1.5;  // NoRegen (Hard)
            case 6 -> 0.7;  // NoVillagerTrading
            case 7 -> (20.0 - ticks) / 19.0 * 5.0; // MaxHealthModify: increased weight
            case 8 -> 2.0;  // NoCraftingTable (Massive impact)
            case 9 -> 3.0;  // ExpWorldBorder
            case 10 -> -0.5; // RandomItem (Helpful)
            case 11 -> 2.5; // SkyblockWorld (Hard)
            case 12 -> (36.0 - slots) / 35.0 * 5.0; // LimitedInventory: increased weight
            case 13 -> -0.5; // RandomEnchantment (Helpful)
            case 14 -> 0.5;  // RandomBlockDrops
            case 15 -> 0.7;  // RandomMobDrops
            case 16 -> 0.2; // RandomChunkBlocks
            case 17 -> 0.4; // WalkRandomItem
            case 18 -> 0.4; // DamageRandomItem
            case 19 -> 0.6; // MinePotionEffect
            case 20 -> 1.5; // RandomizedCrafting (Hard)
            case 21 -> 3.0; // Hardcore (One life)
            case 22 -> 15.0; // All Items (Gives XP per item now)
            case 23 -> 10.0; // All Entities (Gives XP per entity now)
            case 24 -> (mobHealthMult - 1) / 99.0 * 15.0; // Mob Health
            case 25 -> 4.0; // DamageWorldBorder
            default -> 0.0;
        };
    }

    public static double calculateTotalDifficulty(List<Integer> ids, int heartsTicks, int inventorySlots, int mobHealthMult, List<Integer> perks) {
        if (perks.contains(LevelManager.PERK_INFINITY_WEAPON)) return 0.0;
        double total = 0;
        for (int id : ids) {
            total += getDifficulty(id, heartsTicks, inventorySlots, mobHealthMult);
        }
        for (int perkId : perks) {
            total += getDifficulty(perkId, heartsTicks, inventorySlots, mobHealthMult);
        }
        return Math.max(0, total); // Ensure it's not negative
    }

    public static boolean hasConflict(List<Integer> ids, List<Integer> perks) {
        if (ids.contains(2) && ids.contains(14)) return true; // No Block Drops + Random Block Drops
        if (ids.contains(3) && ids.contains(15)) return true; // No Mob Drops + Random Mob Drops
        if (ids.contains(8) && ids.contains(20)) return true; // No Crafting Table + Randomized Crafting
        if (ids.contains(9) && ids.contains(25)) return true; // ExpWorldBorder + DamageWorldBorder
        
        // Added Max Hearts (Perk 103) + Max Health Modifier (Challenge 7) conflict
        if (ids.contains(7) && perks.contains(LevelManager.PERK_TOUGH_SKIN)) return true;

        return false;
    }

    private static void applyTo(ServerWorld world) {
        // 1) Always load or create our saved data from the OVERWORLD
        ServerWorld overworld = world.getServer().getOverworld();
        ChallengeSavedData data = ChallengeSavedData.get(overworld);
        List<Integer> saved = data.getActive();

        boolean wasExpBorderActive = Chal_9_ExpWorldBorder.isActive();
        boolean wasDamageBorderActive = Chal_25_DamageWorldBorder.isActive();

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
            if (saved.contains(24)) {
                int savedMult = data.getMobHealthMultiplier();
                Chal_24_MobHealthMultiply.setMultiplier(savedMult);
                LOGGER.info("[Manager] restored mob health multiplier = {}", savedMult);
            }
            if (saved.contains(25)) {
                double savedSize = data.getDamageWorldBorderSize();
                Chal_25_DamageWorldBorder.setDiameter(savedSize);
                LOGGER.info("[Manager] restored damage world border size = {}", savedSize);
            }

            // Seed from client if first boot and we are in singleplayer/integrated server
            if (!data.isDifficultySet()) {
                boolean serverSide = world.getServer().isDedicated();
                
                if (serverSide) {
                    // On dedicated server, if we already have some active challenges, just calculate difficulty and mark set
                    if (!data.getActive().isEmpty() || data.getMaxHeartsTicks() != 20 || data.getLimitedInventorySlots() != 36) {
                        double initialDiff = calculateTotalDifficulty(data.getActive(), data.getMaxHeartsTicks(), data.getLimitedInventorySlots(), data.getMobHealthMultiplier(), data.getActivePerks());
                        data.setInitialDifficulty(initialDiff);
                        data.setDifficultySet(true);
                        LOGGER.info("ChallengeManager: seeded from existing data (dedicated server). Initial Difficulty: {}", initialDiff);
                    }
                } else {
                    // Singleplayer: use client's last choices
                    int clientTicks = MathHelper.clamp(ChallengeCraftClient.SELECTED_MAX_HEARTS, 1, 20);
                    int clientSlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;
                    int clientMult  = ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER;

                    data.setMaxHeartsTicks(clientTicks);
                    data.setActive(List.copyOf(ChallengeCraftClient.LAST_CHOSEN));
                    data.setActivePerks(List.copyOf(ChallengeCraftClient.SELECTED_PERKS));
                    data.setLimitedInventorySlots(clientSlots);
                    data.setMobHealthMultiplier(clientMult);
                    
                    double initialDiff = calculateTotalDifficulty(ChallengeCraftClient.LAST_CHOSEN, clientTicks, clientSlots, clientMult, ChallengeCraftClient.SELECTED_PERKS);
                    data.setInitialDifficulty(initialDiff);
                    data.setDifficultySet(true);

                    LOGGER.info("ChallengeManager: seeded from client LAST_CHOSEN {}. Initial Difficulty: {}", ChallengeCraftClient.LAST_CHOSEN, initialDiff);
                }

                // Apply current settings to challenge classes
                Chal_7_MaxHealthModify.setMaxHearts(data.getMaxHeartsTicks() * 0.5f);
                Chal_12_LimitedInventory.setLimitedSlots(data.getLimitedInventorySlots());
                Chal_24_MobHealthMultiply.setMultiplier(data.getMobHealthMultiplier());
                Chal_25_DamageWorldBorder.setDiameter(data.getDamageWorldBorderSize());

                // If Infinity Weapon perk is included at world creation, grant it once to eligible online players
                if (data.getActivePerks().contains(LevelManager.PERK_INFINITY_WEAPON)) {
                    for (var p : world.getServer().getPlayerManager().getPlayerList()) {
                        net.kasax.challengecraft.LevelXpListener.grantInfinityWeapon(p);
                    }
                }

                // Force spawn to 0,0 for border challenges in new world
                if (data.getActive().contains(9) || data.getActive().contains(25)) {
                    int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, 0, 0);
                    world.setSpawnPos(new net.minecraft.util.math.BlockPos(0, y, 0), 0.0f);
                    world.getGameRules().get(GameRules.SPAWN_RADIUS).set(0, world.getServer());
                    LOGGER.info("Forced world spawn to 0, {}, 0 and spawnRadius to 0 due to border challenge", y);
                }
                
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
        Chal_24_MobHealthMultiply.setActive(false);
        Chal_25_DamageWorldBorder.setActive(false);

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
                case 24 -> {
                    Chal_24_MobHealthMultiply.setActive(true);
                    LOGGER.info("Challenge 24 ON");
                }
                case 25 -> {
                    Chal_25_DamageWorldBorder.setActive(true);
                    Chal_25_DamageWorldBorder.updateWorldBorder(world);
                    LOGGER.info("Challenge 25 ON");
                }
                default -> LOGGER.warn("Unknown challenge id {}", id);
            }
        }

        // 4.5) Reset border if deactivated
        if (wasExpBorderActive && !Chal_9_ExpWorldBorder.isActive()) {
            resetWorldBorder(world);
        }
        if (wasDamageBorderActive && !Chal_25_DamageWorldBorder.isActive()) {
            resetWorldBorder(world);
        }

        // 4.6) Set center for border challenges
        if (Chal_9_ExpWorldBorder.isActive() || Chal_25_DamageWorldBorder.isActive()) {
            world.getWorldBorder().setCenter(0.5, 0.5);
            // Force 0 spawn radius so players don't spawn outside the tiny initial border
            world.getGameRules().get(GameRules.SPAWN_RADIUS).set(0, world.getServer());
        }

        // 5) Finally, update game rules for block/mob drops
        var rules         = world.getGameRules();
        var tileDropsRule = rules.get(GameRules.DO_TILE_DROPS);
        var mobLootRule   = rules.get(GameRules.DO_MOB_LOOT);

        tileDropsRule.set(!Chal_2_NoBlockDrops.isActive(), world.getServer());
        mobLootRule .set(!Chal_3_NoMobDrops    .isActive(), world.getServer());
    }

    private static void resetWorldBorder(ServerWorld world) {
        world.getWorldBorder().setSize(6.0E7);
        // Restore default spawn radius when border challenge is off
        world.getGameRules().get(GameRules.SPAWN_RADIUS).set(10, world.getServer());
    }
}
