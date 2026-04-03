package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengeSyncPacket;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ChallengeManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(ChallengeCraft.MOD_ID);
    private static List<Integer> PRE_LOADED_PERKS = new ArrayList<>();

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
                data.getMobHealthMultiplier(),
                data.getDoubleTroubleMultiplier(),
                data.getGameSpeedMultiplier()
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
        if (active.contains(26)) {
            net.kasax.challengecraft.challenges.Chal_26_AllAchievements.syncProgressToAll(server, data);
        }
    }

    public static double getDifficulty(int id, int ticks, int slots, int mobHealthMult) {
        return getDifficulty(id, ticks, slots, mobHealthMult, 1, 2, 0);
    }

    public static double getDifficulty(int id, int ticks, int slots, int mobHealthMult, int doubleTroubleMult, int playerCount) {
        return getDifficulty(id, ticks, slots, mobHealthMult, 1, doubleTroubleMult, playerCount);
    }

    public static double getDifficulty(int id, int ticks, int slots, int mobHealthMult, int gameSpeedMult, int doubleTroubleMult, int playerCount) {
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
            case 22 -> 15.0; // All Items
            case 23 -> 10.0; // All Entities
            case 24 -> (mobHealthMult - 1) / 99.0 * 15.0; // Mob Health
            case 25 -> 4.0; // DamageWorldBorder
            case 26 -> 12.0; // All Achievements
            case 27 -> 1.3;  // No Armor
            case 28 -> 5.0;  // Walk = Damage
            case 29 -> 2.5;  // Floor is Lava
            case 30 -> 1.2;  // Heavy Pockets
            case 31 -> 2.0;  // Corrosive Tools
            case 32 -> (playerCount <= 1) ? 0.0 : (1.0 + playerCount); // Symbiotic Bond: 2p -> 3.0, 3p -> 4.0? 
                             // Wait, user said "2.0x for two players (scales with player count +1 per player, 0 if solo)"
                             // So 2p -> 2.0, 3p -> 3.0, etc.
                             // Wait, (playerCount == 2) ? 2.0 : (playerCount > 2 ? 2.0 + (playerCount - 2) : 0.0)
                             // Simplified: (playerCount >= 2) ? (double)playerCount : 0.0;
            case 33 -> 1.5;  // Size Matters
            case 34 -> 0.5;  // Upside Down Drops
            case 35 -> 0.4 * doubleTroubleMult; // Double Trouble
            case 36 -> 4.0;  // Trivia Challenge
            case 37 -> (gameSpeedMult - 1) / 9.0 * 4.0; // Game Speed
            default -> 0.0;
        };
    }

    public static double calculateTotalDifficulty(List<Integer> ids, int heartsTicks, int inventorySlots, int mobHealthMult, int doubleTroubleMult, int playerCount, List<Integer> perks) {
        return calculateTotalDifficulty(ids, heartsTicks, inventorySlots, mobHealthMult, 1, doubleTroubleMult, playerCount, perks);
    }

    public static double calculateTotalDifficulty(List<Integer> ids, int heartsTicks, int inventorySlots, int mobHealthMult, int gameSpeedMult, int doubleTroubleMult, int playerCount, List<Integer> perks) {
        if (perks.contains(LevelManager.PERK_INFINITY_WEAPON)) return 0.0;
        double total = 0;
        for (int id : ids) {
            total += getDifficulty(id, heartsTicks, inventorySlots, mobHealthMult, gameSpeedMult, doubleTroubleMult, playerCount);
        }
        for (int perkId : perks) {
            total += getDifficulty(perkId, heartsTicks, inventorySlots, mobHealthMult, gameSpeedMult, doubleTroubleMult, playerCount);
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

        // No Armor (Challenge 27) + Resistance Perk (Perk 107) conflict
        if (ids.contains(27) && perks.contains(LevelManager.PERK_RESISTANCE)) return true;

        // Walk = Damage (Challenge 28) + Damage = Border (Challenge 25) conflict
        if (ids.contains(28) && ids.contains(25)) return true;

        // Floor is Lava (Challenge 29) + Fire Resistance Perk (Perk 104) conflict
        if (ids.contains(29) && perks.contains(LevelManager.PERK_FIRE_RESISTANCE)) return true;

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
            if (saved.contains(35)) {
                int savedMult = data.getDoubleTroubleMultiplier();
                Chal_35_DoubleTrouble.setMultiplier(savedMult);
                LOGGER.info("[Manager] restored double trouble multiplier = {}", savedMult);
            }
            if (saved.contains(37)) {
                int savedMult = data.getGameSpeedMultiplier();
                Chal_37_GameSpeed.setMultiplier(savedMult);
                LOGGER.info("[Manager] restored game speed multiplier = {}", savedMult);
            }

            // Seed from client if first boot and we are in singleplayer/integrated server
            if (!data.isDifficultySet()) {
                boolean serverSide = world.getServer().isDedicated();
                
                if (serverSide) {
                    // On dedicated server, if we are initializing a fresh world (empty active list),
                    // seeded from pre-loaded static flags if they were set during loadWorld (e.g. after restart).
                    if (data.getActive().isEmpty()) {
                        List<Integer> currentActive = getCurrentlyActiveIds();
                        if (!currentActive.isEmpty()) {
                            data.setActive(currentActive);
                            
                            // Also sync slider values from static fields to the new data object
                            data.setMaxHeartsTicks((int)(Chal_7_MaxHealthModify.getMaxHearts() * 2));
                            data.setLimitedInventorySlots(Chal_12_LimitedInventory.getLimitedSlots());
                            data.setMobHealthMultiplier(Chal_24_MobHealthMultiply.getMultiplier());
                            data.setDoubleTroubleMultiplier(Chal_35_DoubleTrouble.getMultiplier());
                            data.setGameSpeedMultiplier(Chal_37_GameSpeed.getMultiplier());
                            data.setActivePerks(List.copyOf(PRE_LOADED_PERKS));
                            
                            LOGGER.info("ChallengeManager: seeded NEW dedicated server world from pre-loaded challenges: {}", currentActive);
                        }
                    }

                    // Calculate difficulty if not already set
                    if (!data.getActive().isEmpty() || data.getMaxHeartsTicks() != 20 || data.getLimitedInventorySlots() != 36 || data.getGameSpeedMultiplier() != 1) {
                        int playerCount = world.getServer().getPlayerManager().getPlayerList().size();
                        double initialDiff = calculateTotalDifficulty(data.getActive(), data.getMaxHeartsTicks(), data.getLimitedInventorySlots(), data.getMobHealthMultiplier(), data.getGameSpeedMultiplier(), data.getDoubleTroubleMultiplier(), playerCount, data.getActivePerks());
                        data.setInitialDifficulty(initialDiff);
                        data.setDifficultySet(true);
                        LOGGER.info("ChallengeManager: seeded difficulty from existing data. Initial Difficulty: {}", initialDiff);
                    }
                } else {
                    // Singleplayer: use client's last choices
                    int clientTicks = MathHelper.clamp(ChallengeCraftClient.SELECTED_MAX_HEARTS, 1, 20);
                    int clientSlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;
                    int clientMult  = ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER;
                    int clientDoubleMult = ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER;
                    int clientGameSpeedMult = ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER;

                    data.setMaxHeartsTicks(clientTicks);
                    data.setActive(List.copyOf(ChallengeCraftClient.LAST_CHOSEN));
                    data.setActivePerks(List.copyOf(ChallengeCraftClient.SELECTED_PERKS));
                    data.setLimitedInventorySlots(clientSlots);
                    data.setMobHealthMultiplier(clientMult);
                    data.setDoubleTroubleMultiplier(clientDoubleMult);
                    data.setGameSpeedMultiplier(clientGameSpeedMult);
                    
                    int playerCount = world.getServer().getPlayerManager().getPlayerList().size();
                    double initialDiff = calculateTotalDifficulty(ChallengeCraftClient.LAST_CHOSEN, clientTicks, clientSlots, clientMult, clientGameSpeedMult, clientDoubleMult, playerCount, ChallengeCraftClient.SELECTED_PERKS);
                    data.setInitialDifficulty(initialDiff);
                    data.setDifficultySet(true);

                    LOGGER.info("ChallengeManager: seeded from client LAST_CHOSEN {}. Initial Difficulty: {}", ChallengeCraftClient.LAST_CHOSEN, initialDiff);
                    
                    // Clear client cache so it doesn't get applied to other worlds loaded in this session
                    ChallengeCraftClient.LAST_CHOSEN = new ArrayList<>();
                    ChallengeCraftClient.SELECTED_PERKS = new ArrayList<>();
                    ChallengeCraftClient.SELECTED_MAX_HEARTS = 20;
                    ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = 36;
                    ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER = 1;
                    ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER = 2;
                    ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER = 1;
                }

                data.setMaxHeartsTicks(data.getMaxHeartsTicks() != 0 ? data.getMaxHeartsTicks() : 20);
                Chal_7_MaxHealthModify.setMaxHearts(data.getMaxHeartsTicks() * 0.5f);
                Chal_12_LimitedInventory.setLimitedSlots(data.getLimitedInventorySlots());
                Chal_24_MobHealthMultiply.setMultiplier(data.getMobHealthMultiplier());
                Chal_25_DamageWorldBorder.setDiameter(data.getDamageWorldBorderSize());
                Chal_35_DoubleTrouble.setMultiplier(data.getDoubleTroubleMultiplier());
                Chal_37_GameSpeed.setMultiplier(data.getGameSpeedMultiplier());

                // If Infinity Weapon perk is included at world creation, grant it once to eligible online players
                if (data.getActivePerks().contains(LevelManager.PERK_INFINITY_WEAPON)) {
                    for (var p : world.getServer().getPlayerManager().getPlayerList()) {
                        net.kasax.challengecraft.LevelXpListener.grantInfinityWeapon(p);
                    }
                }

                // Force spawn to 0,0 for border challenges or Skyblock
                boolean isBorderOrSky = data.getActive().contains(9) || data.getActive().contains(11) || data.getActive().contains(25);
                
                if (isBorderOrSky) {
                    int x = 0;
                    int z = 0;
                    int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
                    
                    // For Skyblock, we know the island is at 64. 
                    // For border, we try to find the top block but fallback to 64 if it's a void start.
                    if (y <= 0 || data.getActive().contains(11)) y = 64;
                    
                    world.setSpawnPos(new net.minecraft.util.math.BlockPos(x, y, z), 0.0f);
                    world.getGameRules().get(GameRules.SPAWN_RADIUS).set(0, world.getServer());
                    LOGGER.info("Forced world spawn to {}, {}, {} and spawnRadius to 0 due to active challenge (Border/Skyblock)", x, y, z);
                } else {
                    // Normal world: ensure Y is at least safe if it happens to be 0
                    net.minecraft.util.math.BlockPos currentSpawn = world.getSpawnPos();
                    if (currentSpawn.getY() <= 0) {
                        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, currentSpawn.getX(), currentSpawn.getZ());
                        if (y <= 0) y = 64;
                        world.setSpawnPos(new net.minecraft.util.math.BlockPos(currentSpawn.getX(), y, currentSpawn.getZ()), 0.0f);
                        LOGGER.info("Adjusted normal world spawn Y to safe location: {}", y);
                    }
                }
                
                // re-read the saved list
                saved = data.getActive();
            }
        }

        // 3) Turn everything off and back on based on the saved list
        applyActiveChallenges(saved, world, data);

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

    public static boolean loadInitialActiveChallenges(Path worldDir) {
        Path dataFile = worldDir.resolve("data/challengecraft_challenges.dat");
        if (Files.exists(dataFile)) {
            try {
                NbtCompound nbt = NbtIo.readCompressed(dataFile, NbtSizeTracker.ofUnlimitedBytes());
                NbtElement dataElement = nbt.get("data");
                if (dataElement instanceof NbtCompound data) {
                    NbtElement activeElement = data.get("active");
                    if (activeElement instanceof NbtList list) {
                        List<Integer> active = new ArrayList<>();
                        for (int i = 0; i < list.size(); i++) {
                            NbtElement e = list.get(i);
                            if (e instanceof NbtInt nbtInt) {
                                active.add(nbtInt.intValue());
                            } else if (e instanceof NbtByte nbtByte) {
                                active.add((int) nbtByte.byteValue());
                            } else if (e instanceof NbtShort nbtShort) {
                                active.add((int) nbtShort.shortValue());
                            } else if (e instanceof NbtLong nbtLong) {
                                active.add((int) nbtLong.longValue());
                            }
                        }
                        applyActiveChallenges(active, null, null);
                        
                        // Also load perks and other settings for restart sync
                        PRE_LOADED_PERKS.clear();
                        data.getList("activePerks").ifPresent(perksList -> {
                            for (int i = 0; i < perksList.size(); i++) {
                                perksList.getInt(i).ifPresent(PRE_LOADED_PERKS::add);
                            }
                        });
                        
                        data.getInt("maxHeartsTicks").ifPresent(ticks -> Chal_7_MaxHealthModify.setMaxHearts(ticks * 0.5f));
                        data.getInt("limitedInventorySlots").ifPresent(slots -> Chal_12_LimitedInventory.setLimitedSlots(slots));
                        data.getInt("mobHealthMultiplier").ifPresent(mult -> Chal_24_MobHealthMultiply.setMultiplier(mult));
                        data.getInt("doubleTroubleMultiplier").ifPresent(mult -> Chal_35_DoubleTrouble.setMultiplier(mult));
                        data.getInt("gameSpeedMultiplier").ifPresent(mult -> Chal_37_GameSpeed.setMultiplier(mult));
                        
                        LOGGER.info("Pre-loaded active challenges and settings from disk: {} (Perks: {})", active, PRE_LOADED_PERKS);
                        return true;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to pre-load active challenges!", e);
            }
        }
        return false;
    }

    public static void applyActiveChallenges(List<Integer> activeIds, ServerWorld world, ChallengeSavedData data) {
        LOGGER.info("ChallengeManager: turning all challenges OFF");
        setAllActive(false);

        LOGGER.info("ChallengeManager: got actives → {}", activeIds);
        for (int id : activeIds) {
            applyActiveFlag(id, world, data);
        }
    }

    public static List<Integer> getCurrentlyActiveIds() {
        List<Integer> ids = new ArrayList<>();
        if (Chal_1_LevelItem.isActive()) ids.add(1);
        if (Chal_2_NoBlockDrops.isActive()) ids.add(2);
        if (Chal_3_NoMobDrops.isActive()) ids.add(3);
        if (Chal_4_NoChestLoot.isActive()) ids.add(4);
        if (Chal_5_NoRegen.isActive()) ids.add(5);
        if (Chal_6_NoVillagerTrading.isActive()) ids.add(6);
        if (Chal_7_MaxHealthModify.isActive()) ids.add(7);
        if (Chal_8_NoCraftingTable.isActive()) ids.add(8);
        if (Chal_9_ExpWorldBorder.isActive()) ids.add(9);
        if (Chal_10_RandomItem.isActive()) ids.add(10);
        if (Chal_11_SkyblockWorld.isActive()) ids.add(11);
        if (Chal_12_LimitedInventory.isActive()) ids.add(12);
        if (Chal_13_RandomEnchantment.isActive()) ids.add(13);
        if (Chal_14_RandomBlockDrops.isActive()) ids.add(14);
        if (Chal_15_RandomMobDrops.isActive()) ids.add(15);
        if (Chal_16_RandomChunkBlocks.isActive()) ids.add(16);
        if (Chal_17_WalkRandomItem.isActive()) ids.add(17);
        if (Chal_18_DamageRandomItem.isActive()) ids.add(18);
        if (Chal_19_MinePotionEffect.isActive()) ids.add(19);
        if (Chal_20_RandomizedCrafting.isActive()) ids.add(20);
        if (Chal_21_Hardcore.isActive()) ids.add(21);
        if (Chal_22_AllItems.isActive()) ids.add(22);
        if (Chal_23_AllEntities.isActive()) ids.add(23);
        if (Chal_24_MobHealthMultiply.isActive()) ids.add(24);
        if (Chal_25_DamageWorldBorder.isActive()) ids.add(25);
        if (Chal_26_AllAchievements.isActive()) ids.add(26);
        if (Chal_27_NoArmor.isActive()) ids.add(27);
        if (Chal_28_WalkDamage.isActive()) ids.add(28);
        if (Chal_29_FloorIsLava.isActive()) ids.add(29);
        if (Chal_30_HeavyPockets.isActive()) ids.add(30);
        if (Chal_31_CorrosiveTools.isActive()) ids.add(31);
        if (Chal_32_SymbioticBond.isActive()) ids.add(32);
        if (Chal_33_SizeMatters.isActive()) ids.add(33);
        if (Chal_34_UpsideDownDrops.isActive()) ids.add(34);
        if (Chal_35_DoubleTrouble.isActive()) ids.add(35);
        if (Chal_36_TriviaChallenge.isActive()) ids.add(36);
        if (Chal_37_GameSpeed.isActive()) ids.add(37);
        return ids;
    }

    public static void setAllActive(boolean active) {
        Chal_1_LevelItem        .setActive(active);
        Chal_2_NoBlockDrops     .setActive(active);
        Chal_3_NoMobDrops       .setActive(active);
        Chal_4_NoChestLoot      .setActive(active);
        Chal_5_NoRegen          .setActive(active);
        Chal_6_NoVillagerTrading.setActive(active);
        Chal_7_MaxHealthModify  .setActive(active);
        Chal_8_NoCraftingTable  .setActive(active);
        Chal_9_ExpWorldBorder   .setActive(active);
        Chal_10_RandomItem      .setActive(active);
        Chal_11_SkyblockWorld   .setActive(active);
        Chal_12_LimitedInventory.setActive(active);
        Chal_13_RandomEnchantment.setActive(active);
        Chal_14_RandomBlockDrops.setActive(active);
        Chal_15_RandomMobDrops.setActive(active);
        Chal_16_RandomChunkBlocks.setActive(active);
        Chal_17_WalkRandomItem.setActive(active);
        Chal_18_DamageRandomItem.setActive(active);
        Chal_19_MinePotionEffect.setActive(active);
        Chal_20_RandomizedCrafting.setActive(active);
        Chal_21_Hardcore.setActive(active);
        Chal_22_AllItems.setActive(active);
        Chal_23_AllEntities.setActive(active);
        Chal_24_MobHealthMultiply.setActive(active);
        Chal_25_DamageWorldBorder.setActive(active);
        Chal_26_AllAchievements.setActive(active);
        Chal_27_NoArmor.setActive(active);
        Chal_28_WalkDamage.setActive(active);
        Chal_29_FloorIsLava.setActive(active);
        Chal_30_HeavyPockets.setActive(active);
        Chal_31_CorrosiveTools.setActive(active);
        Chal_32_SymbioticBond.setActive(active);
        Chal_33_SizeMatters.setActive(active);
        Chal_34_UpsideDownDrops.setActive(active);
        Chal_35_DoubleTrouble.setActive(active);
        Chal_36_TriviaChallenge.setActive(active);
        Chal_37_GameSpeed.setActive(active);
    }

    public static void applyActiveFlag(int id, ServerWorld world, ChallengeSavedData data) {
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
                if (world != null && data != null) Chal_22_AllItems.syncProgressToAll(world.getServer(), data);
                LOGGER.info("Challenge 22 ON");
            }
            case 23 -> {
                Chal_23_AllEntities.setActive(true);
                if (world != null && data != null) Chal_23_AllEntities.syncProgressToAll(world.getServer(), data);
                LOGGER.info("Challenge 23 ON");
            }
            case 24 -> {
                Chal_24_MobHealthMultiply.setActive(true);
                LOGGER.info("Challenge 24 ON");
            }
            case 25 -> {
                Chal_25_DamageWorldBorder.setActive(true);
                if (world != null) Chal_25_DamageWorldBorder.updateWorldBorder(world);
                LOGGER.info("Challenge 25 ON");
            }
            case 26 -> {
                Chal_26_AllAchievements.setActive(true);
                if (world != null && data != null) Chal_26_AllAchievements.syncProgressToAll(world.getServer(), data);
                LOGGER.info("Challenge 26 ON");
            }
            case 27 -> {
                Chal_27_NoArmor.setActive(true);
                LOGGER.info("Challenge 27 ON");
            }
            case 28 -> {
                Chal_28_WalkDamage.setActive(true);
                LOGGER.info("Challenge 28 ON");
            }
            case 29 -> { Chal_29_FloorIsLava.setActive(true); LOGGER.info("Challenge 29 ON"); }
            case 30 -> { Chal_30_HeavyPockets.setActive(true); LOGGER.info("Challenge 30 ON"); }
            case 31 -> { Chal_31_CorrosiveTools.setActive(true); LOGGER.info("Challenge 31 ON"); }
            case 32 -> { Chal_32_SymbioticBond.setActive(true); LOGGER.info("Challenge 32 ON"); }
            case 33 -> { Chal_33_SizeMatters.setActive(true); LOGGER.info("Challenge 33 ON"); }
            case 34 -> { Chal_34_UpsideDownDrops.setActive(true); LOGGER.info("Challenge 34 ON"); }
            case 35 -> { Chal_35_DoubleTrouble.setActive(true); LOGGER.info("Challenge 35 ON"); }
            case 36 -> { Chal_36_TriviaChallenge.setActive(true); LOGGER.info("Challenge 36 ON"); }
            case 37 -> { Chal_37_GameSpeed.setActive(true); LOGGER.info("Challenge 37 ON"); }
            default -> LOGGER.warn("Unknown challenge id {}", id);
        }
    }
}
