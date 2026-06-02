package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengeSyncPacket;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Applies saved challenge settings and keeps server/client challenge flags in sync. */
public class ChallengeManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(ChallengeCraft.MOD_ID);
    private static List<Integer> PRE_LOADED_PERKS = new ArrayList<>();

    public static void register() {
        ServerLevelEvents.LOAD.register((server, world) -> {
            applyTo(world);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> syncToAll(server));
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                Chal_19_MinePotionEffect.applyEffect(serverPlayer, state.getBlock());
            }
        });
    }

    /** Rebuilds challenge state after reloads and in-game configuration changes. */
    public static void applyAll(net.minecraft.server.MinecraftServer server) {
        LOGGER.info("ChallengeManager.applyAll: re-applying to all worlds");
        for (ServerLevel world : server.getAllLevels()) {
            applyTo(world);
        }
        syncToAll(server);
    }

    public static void syncToAll(net.minecraft.server.MinecraftServer server) {
        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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
        if (active.contains(40)) {
            net.kasax.challengecraft.challenges.Chal_40_LockoutBingo.syncToAll(server);
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
            case 32 -> (playerCount <= 1) ? 0.0 : (1.0 + playerCount); // Symbiotic Bond
            case 33 -> 1.5;  // Size Matters
            case 34 -> 0.5;  // Upside Down Drops
            case 35 -> 0.4 * doubleTroubleMult; // Double Trouble
            case 36 -> 4.0;  // Trivia Challenge
            case 37 -> (gameSpeedMult - 1) / 9.0 * 4.0; // Game Speed
            case 38 -> 5.0;  // Chunk Hunt
            case 39 -> 1.3;  // No Food
            case 40 -> 0.0;  // Lockout Bingo
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
        return Math.max(0, total);
    }

    public static boolean hasConflict(List<Integer> ids, List<Integer> perks) {
        if (ids.contains(2) && ids.contains(14)) return true; // No Block Drops + Random Block Drops
        if (ids.contains(3) && ids.contains(15)) return true; // No Mob Drops + Random Mob Drops
        if (ids.contains(8) && ids.contains(20)) return true; // No Crafting Table + Randomized Crafting
        if (ids.contains(9) && ids.contains(25)) return true; // ExpWorldBorder + DamageWorldBorder
        if (ids.contains(38) && ids.contains(9)) return true; // Chunk Hunt + Level Border
        if (ids.contains(38) && ids.contains(16)) return true; // Chunk Hunt + Random Chunk Blocks
        if (ids.contains(38) && ids.contains(23)) return true; // Chunk Hunt + All Entities
        if (ids.contains(38) && ids.contains(25)) return true; // Chunk Hunt + Damage Border
        if (ids.contains(40) && ids.contains(22)) return true; // Lockout Bingo + All Items
        if (ids.contains(40) && ids.contains(23)) return true; // Lockout Bingo + All Entities
        if (ids.contains(40) && ids.contains(26)) return true; // Lockout Bingo + All Achievements
        if (ids.contains(40) && ids.contains(38)) return true; // Lockout Bingo + Chunk Hunt
        
        if (ids.contains(7) && perks.contains(LevelManager.PERK_TOUGH_SKIN)) return true;

        if (ids.contains(27) && perks.contains(LevelManager.PERK_RESISTANCE)) return true;

        if (ids.contains(28) && ids.contains(25)) return true;

        if (ids.contains(29) && perks.contains(LevelManager.PERK_FIRE_RESISTANCE)) return true;

        return false;
    }

    private static void applyTo(ServerLevel world) {
        // The overworld owns the canonical saved state even when another dimension loads first.
        ServerLevel overworld = world.getServer().overworld();
        ChallengeSavedData data = ChallengeSavedData.get(overworld);
        List<Integer> saved = data.getActive();

        boolean wasExpBorderActive = Chal_9_ExpWorldBorder.isActive();
        boolean wasDamageBorderActive = Chal_25_DamageWorldBorder.isActive();
        boolean wasChunkHuntActive = Chal_38_ChunkHunt.isActive();

        if (world.dimension() == Level.OVERWORLD) {
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

            if (!data.isDifficultySet()) {
                boolean serverSide = world.getServer().isDedicatedServer();
                
                if (serverSide) {
                    // Dedicated restarts restore static challenge flags before world state is available.
                    if (data.getActive().isEmpty()) {
                        List<Integer> currentActive = getCurrentlyActiveIds();
                        if (!currentActive.isEmpty()) {
                            data.setActive(currentActive);
                            
                            data.setMaxHeartsTicks((int)(Chal_7_MaxHealthModify.getMaxHearts() * 2));
                            data.setLimitedInventorySlots(Chal_12_LimitedInventory.getLimitedSlots());
                            data.setMobHealthMultiplier(Chal_24_MobHealthMultiply.getMultiplier());
                            data.setDoubleTroubleMultiplier(Chal_35_DoubleTrouble.getMultiplier());
                            data.setGameSpeedMultiplier(Chal_37_GameSpeed.getMultiplier());
                            data.setActivePerks(List.copyOf(PRE_LOADED_PERKS));
                            
                            LOGGER.info("ChallengeManager: seeded NEW dedicated server world from pre-loaded challenges: {}", currentActive);
                        }
                    }

                    if (!data.getActive().isEmpty() || data.getMaxHeartsTicks() != 20 || data.getLimitedInventorySlots() != 36 || data.getGameSpeedMultiplier() != 1) {
                        int playerCount = world.getServer().getPlayerList().getPlayers().size();
                        double initialDiff = calculateTotalDifficulty(data.getActive(), data.getMaxHeartsTicks(), data.getLimitedInventorySlots(), data.getMobHealthMultiplier(), data.getGameSpeedMultiplier(), data.getDoubleTroubleMultiplier(), playerCount, data.getActivePerks());
                        data.setInitialDifficulty(initialDiff);
                        data.setDifficultySet(true);
                        LOGGER.info("ChallengeManager: seeded difficulty from existing data. Initial Difficulty: {}", initialDiff);
                    }
                } else {
                    int clientTicks = Mth.clamp(ChallengeCraftClient.SELECTED_MAX_HEARTS, 1, 20);
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
                    
                    int playerCount = world.getServer().getPlayerList().getPlayers().size();
                    double initialDiff = calculateTotalDifficulty(ChallengeCraftClient.LAST_CHOSEN, clientTicks, clientSlots, clientMult, clientGameSpeedMult, clientDoubleMult, playerCount, ChallengeCraftClient.SELECTED_PERKS);
                    data.setInitialDifficulty(initialDiff);
                    data.setDifficultySet(true);

                    LOGGER.info("ChallengeManager: seeded from client LAST_CHOSEN {}. Initial Difficulty: {}", ChallengeCraftClient.LAST_CHOSEN, initialDiff);
                    
                    // These selections belong to one world-creation flow and must not leak into the next world.
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

                if (data.getActivePerks().contains(LevelManager.PERK_INFINITY_WEAPON)) {
                    for (var p : world.getServer().getPlayerList().getPlayers()) {
                        net.kasax.challengecraft.LevelXpListener.grantInfinityWeapon(p);
                    }
                }

                boolean isBorderOrSky = data.getActive().contains(9) || data.getActive().contains(11) || data.getActive().contains(25);
                
                if (isBorderOrSky) {
                    int x = 0;
                    int z = 0;
                    int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                    
                    // Skyblock starts on a fixed island. Border worlds keep the first spawn inside the opening border.
                    if (y <= 0 || data.getActive().contains(11)) y = 64;
                    
                    world.setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(world.dimension(), new net.minecraft.core.BlockPos(x, y, z), 0.0f, 0.0f));
                    world.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, world.getServer());
                    LOGGER.info("Forced world spawn to {}, {}, {} and spawnRadius to 0 due to active challenge (Border/Skyblock)", x, y, z);
                } else {
                    net.minecraft.core.BlockPos currentSpawn = world.getRespawnData().pos();
                    if (currentSpawn.getY() <= 0) {
                        int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, currentSpawn.getX(), currentSpawn.getZ());
                        if (y <= 0) y = 64;
                        world.setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(world.dimension(), new net.minecraft.core.BlockPos(currentSpawn.getX(), y, currentSpawn.getZ()), 0.0f, 0.0f));
                        LOGGER.info("Adjusted normal world spawn Y to safe location: {}", y);
                    }
                }
                
                saved = data.getActive();
            }
        }

        applyActiveChallenges(saved, world, data);

        if (wasExpBorderActive && !Chal_9_ExpWorldBorder.isActive()) {
            resetWorldBorder(world);
        }
        if (wasDamageBorderActive && !Chal_25_DamageWorldBorder.isActive()) {
            resetWorldBorder(world);
        }
        if (wasChunkHuntActive && !Chal_38_ChunkHunt.isActive()) {
            Chal_38_ChunkHunt.resetWorldBorder(world);
        }

        if (Chal_9_ExpWorldBorder.isActive() || Chal_25_DamageWorldBorder.isActive()) {
            world.getWorldBorder().setCenter(0.5, 0.5);
            // A nonzero radius can place players outside the tiny opening border.
            world.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, world.getServer());
        }

        var rules = world.getGameRules();
        rules.set(GameRules.BLOCK_DROPS, !Chal_2_NoBlockDrops.isActive(), world.getServer());
        rules.set(GameRules.MOB_DROPS, !Chal_3_NoMobDrops.isActive(), world.getServer());
    }

    private static void resetWorldBorder(ServerLevel world) {
        world.getWorldBorder().setSize(6.0E7);
        world.getGameRules().set(GameRules.RESPAWN_RADIUS, 10, world.getServer());
    }

    public static boolean loadInitialActiveChallenges(Path worldDir) {
        Path dataFile = worldDir.resolve("data/challengecraft_challenges.dat");
        if (Files.exists(dataFile)) {
            try {
                CompoundTag nbt = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
                Tag dataElement = nbt.get("data");
                if (dataElement instanceof CompoundTag data) {
                    Tag activeElement = data.get("active");
                    if (activeElement instanceof ListTag list) {
                        List<Integer> active = new ArrayList<>();
                        for (int i = 0; i < list.size(); i++) {
                            Tag e = list.get(i);
                            if (e instanceof IntTag nbtInt) {
                                active.add(nbtInt.intValue());
                            } else if (e instanceof ByteTag nbtByte) {
                                active.add((int) nbtByte.byteValue());
                            } else if (e instanceof ShortTag nbtShort) {
                                active.add((int) nbtShort.shortValue());
                            } else if (e instanceof LongTag nbtLong) {
                                active.add((int) nbtLong.longValue());
                            }
                        }
                        applyActiveChallenges(active, null, null);
                        
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

    public static void applyActiveChallenges(List<Integer> activeIds, ServerLevel world, ChallengeSavedData data) {
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
        if (Chal_38_ChunkHunt.isActive()) ids.add(38);
        if (Chal_39_NoFood.isActive()) ids.add(39);
        if (Chal_40_LockoutBingo.isActive()) ids.add(40);
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
        Chal_38_ChunkHunt.setActive(active);
        Chal_39_NoFood.setActive(active);
        Chal_40_LockoutBingo.setActive(active);
    }

    public static void applyActiveFlag(int id, ServerLevel world, ChallengeSavedData data) {
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
            case 38 -> {
                Chal_38_ChunkHunt.setActive(true);
                if (world != null) Chal_38_ChunkHunt.updateWorldBorder(world);
                LOGGER.info("Challenge 38 ON");
            }
            case 39 -> { Chal_39_NoFood.setActive(true); LOGGER.info("Challenge 39 ON"); }
            case 40 -> {
                Chal_40_LockoutBingo.setActive(true);
                if (world != null) {
                    Chal_40_LockoutBingo.onActivated(world);
                }
                LOGGER.info("Challenge 40 ON");
            }
            default -> LOGGER.warn("Unknown challenge id {}", id);
        }
    }
}
