package net.kasax.challengecraft.challenges.lockout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Declarative goal catalog plus the weighted board picker used by lockout runs.
 * Board generation limits repeated goal types first, then relaxes that rule only when a bucket is undersupplied.
 */
public final class LockoutBingoGoalPool {
    private static final int BOARD_SIZE = 25;

    private static final List<String> ALL_LOGS = List.of(
            "minecraft:oak_log",
            "minecraft:spruce_log",
            "minecraft:birch_log",
            "minecraft:jungle_log",
            "minecraft:acacia_log",
            "minecraft:dark_oak_log",
            "minecraft:mangrove_log",
            "minecraft:cherry_log"
    );
    private static final List<String> IRON_ARMOR = List.of(
            "minecraft:iron_helmet",
            "minecraft:iron_chestplate",
            "minecraft:iron_leggings",
            "minecraft:iron_boots"
    );
    private static final List<String> LEATHER_ARMOR = List.of(
            "minecraft:leather_helmet",
            "minecraft:leather_chestplate",
            "minecraft:leather_leggings",
            "minecraft:leather_boots"
    );
    private static final List<String> GOLD_ARMOR = List.of(
            "minecraft:golden_helmet",
            "minecraft:golden_chestplate",
            "minecraft:golden_leggings",
            "minecraft:golden_boots"
    );
    private static final List<String> DIAMOND_ARMOR = List.of(
            "minecraft:diamond_helmet",
            "minecraft:diamond_chestplate",
            "minecraft:diamond_leggings",
            "minecraft:diamond_boots"
    );
    private static final List<String> PIGLIN_BARTER_ITEMS = List.of(
            "minecraft:string",
            "minecraft:quartz",
            "minecraft:obsidian",
            "minecraft:crying_obsidian",
            "minecraft:fire_charge",
            "minecraft:leather",
            "minecraft:soul_sand",
            "minecraft:nether_brick",
            "minecraft:spectral_arrow",
            "minecraft:gravel",
            "minecraft:blackstone",
            "minecraft:magma_cream",
            "minecraft:ender_pearl",
            "minecraft:iron_nugget"
    );
    private static final List<String> BASIC_FLOWERS = List.of(
            "#minecraft:flowers"
    );
    private static final List<String> BASIC_DYES = List.of(
            "minecraft:white_dye",
            "minecraft:orange_dye",
            "minecraft:magenta_dye",
            "minecraft:light_blue_dye",
            "minecraft:yellow_dye",
            "minecraft:lime_dye",
            "minecraft:pink_dye",
            "minecraft:gray_dye",
            "minecraft:light_gray_dye",
            "minecraft:cyan_dye",
            "minecraft:purple_dye",
            "minecraft:blue_dye",
            "minecraft:brown_dye",
            "minecraft:green_dye",
            "minecraft:red_dye",
            "minecraft:black_dye"
    );
    private static final List<String> BASIC_SAPLINGS = List.of(
            "#minecraft:saplings"
    );

    private static final List<LockoutBingoGoal> GOALS = List.of(
            item(LockoutBingoGoalCategory.ITEM, "obtain_iron_ingot", LockoutBingoGoalDifficulty.EASY, "minecraft:iron_ingot"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_gold_ingot", LockoutBingoGoalDifficulty.EASY, "minecraft:gold_ingot"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_copper_ingot", LockoutBingoGoalDifficulty.EASY, "minecraft:copper_ingot"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_diamond", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:diamond"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_emerald", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:emerald"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_lapis_lazuli", LockoutBingoGoalDifficulty.EASY, "minecraft:lapis_lazuli"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_redstone", LockoutBingoGoalDifficulty.EASY, "minecraft:redstone"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_coal_block", LockoutBingoGoalDifficulty.EASY, "minecraft:coal_block"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_raw_iron_block", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:raw_iron_block"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_raw_gold_block", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:raw_gold_block"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_obsidian", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:obsidian"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_crying_obsidian", LockoutBingoGoalDifficulty.HARD, "minecraft:crying_obsidian"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_amethyst_shard", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:amethyst_shard"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_calcite", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:calcite"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_tuff", LockoutBingoGoalDifficulty.EASY, "minecraft:tuff"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_deepslate", LockoutBingoGoalDifficulty.EASY, "minecraft:cobbled_deepslate"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_moss_block", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:moss_block"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_dripstone_block", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:dripstone_block"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_pointed_dripstone", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:pointed_dripstone"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_sculk", LockoutBingoGoalDifficulty.HARD, "minecraft:sculk"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_snowball", LockoutBingoGoalDifficulty.EASY, "minecraft:snowball"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_ice", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:ice"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_packed_ice", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:packed_ice"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_blue_ice", LockoutBingoGoalDifficulty.HARD, "minecraft:blue_ice"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_slime_ball", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:slime_ball"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_honey_bottle", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:honey_bottle"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_honeycomb", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:honeycomb"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_name_tag", LockoutBingoGoalDifficulty.HARD, "minecraft:name_tag"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_saddle", LockoutBingoGoalDifficulty.HARD, "minecraft:saddle"),
            tag(LockoutBingoGoalCategory.ITEM, "obtain_music_disc", LockoutBingoGoalDifficulty.HARD, "minecraft:music_discs", "minecraft:music_disc_13"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_ender_pearl", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:ender_pearl"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_ink_sac", LockoutBingoGoalDifficulty.EASY, "minecraft:ink_sac"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_glow_ink_sac", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:glow_ink_sac"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_tropical_fish", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:tropical_fish"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_pufferfish", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:pufferfish"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_turtle_scute", LockoutBingoGoalDifficulty.HARD, "minecraft:scute"),
            item(LockoutBingoGoalCategory.ITEM, "craft_lava_bucket", LockoutBingoGoalDifficulty.EASY, "minecraft:lava_bucket"),
            item(LockoutBingoGoalCategory.ITEM, "craft_water_bucket", LockoutBingoGoalDifficulty.EASY, "minecraft:water_bucket"),
            item(LockoutBingoGoalCategory.ITEM, "craft_golden_apple", LockoutBingoGoalDifficulty.HARD, "minecraft:golden_apple"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_trident", LockoutBingoGoalDifficulty.HARD, "minecraft:trident"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_crossbow", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:crossbow"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_shield", LockoutBingoGoalDifficulty.EASY, "minecraft:shield"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_arrow", LockoutBingoGoalDifficulty.EASY, "minecraft:arrow"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_spectral_arrow", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:spectral_arrow"),
            item(LockoutBingoGoalCategory.ITEM, "obtain_tipped_arrow", LockoutBingoGoalDifficulty.HARD, "minecraft:tipped_arrow"),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_logs", LockoutBingoGoalDifficulty.EASY, 16, "minecraft:oak_log", ALL_LOGS),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_32_cobblestone", LockoutBingoGoalDifficulty.EASY, 32, "minecraft:cobblestone", List.of("minecraft:cobblestone")),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_glass", LockoutBingoGoalDifficulty.EASY, 16, "minecraft:glass", List.of("minecraft:glass")),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_bricks", LockoutBingoGoalDifficulty.MEDIUM, 16, "minecraft:bricks", List.of("minecraft:bricks")),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_sandstone", LockoutBingoGoalDifficulty.EASY, 16, "minecraft:sandstone", List.of("minecraft:sandstone")),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_mud_bricks", LockoutBingoGoalDifficulty.MEDIUM, 16, "minecraft:mud_bricks", List.of("minecraft:mud_bricks")),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_terracotta", LockoutBingoGoalDifficulty.MEDIUM, 16, "minecraft:terracotta", List.of("minecraft:terracotta")),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_concrete", LockoutBingoGoalDifficulty.MEDIUM, 16, "minecraft:white_concrete", List.of("minecraft:white_concrete")),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_prismarine", LockoutBingoGoalDifficulty.HARD, 16, "minecraft:prismarine", List.of("minecraft:prismarine")),
            itemAmount(LockoutBingoGoalCategory.ITEM, "obtain_16_end_stone", LockoutBingoGoalDifficulty.EXPERT, 16, "minecraft:end_stone", List.of("minecraft:end_stone"), false),
            inventorySet(LockoutBingoGoalCategory.ITEM, "obtain_all_log_types_basic", LockoutBingoGoalDifficulty.HARD, "minecraft:oak_log", List.of("minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log", "minecraft:jungle_log")),
            inventorySet(LockoutBingoGoalCategory.ITEM, "obtain_3_flower_types", LockoutBingoGoalDifficulty.EASY, "minecraft:dandelion", BASIC_FLOWERS),
            inventorySet(LockoutBingoGoalCategory.ITEM, "obtain_5_dye_colors", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:red_dye", BASIC_DYES),
            inventorySet(LockoutBingoGoalCategory.ITEM, "obtain_3_sapling_types", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:oak_sapling", BASIC_SAPLINGS),

            dimension(LockoutBingoGoalCategory.NETHER, "enter_nether", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:the_nether", "minecraft:netherrack"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_nether_gold_ore", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:nether_gold_ore"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_netherrack", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:netherrack"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_quartz", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:quartz"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_glowstone", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:glowstone"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_blaze_rod", LockoutBingoGoalDifficulty.HARD, "minecraft:blaze_rod"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_nether_wart", LockoutBingoGoalDifficulty.HARD, "minecraft:nether_wart"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_ghast_tear", LockoutBingoGoalDifficulty.HARD, "minecraft:ghast_tear"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_magma_cream", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:magma_cream"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_ancient_debris", LockoutBingoGoalDifficulty.EXPERT, "minecraft:ancient_debris"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_glowstone_dust", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:glowstone_dust"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_shroomlight", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:shroomlight"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_blackstone", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:blackstone"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_basalt", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:basalt"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_soul_sand", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:soul_sand"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_soul_soil", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:soul_soil"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_warped_fungus", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:warped_fungus"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_crimson_fungus", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:crimson_fungus"),
            item(LockoutBingoGoalCategory.NETHER, "obtain_wither_skeleton_skull", LockoutBingoGoalDifficulty.EXPERT, "minecraft:wither_skeleton_skull"),
            itemMulti(LockoutBingoGoalCategory.NETHER, "obtain_piglin_barter_item", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:gold_ingot", PIGLIN_BARTER_ITEMS),
            kill(LockoutBingoGoalCategory.NETHER, "kill_blaze", LockoutBingoGoalDifficulty.HARD, "minecraft:blaze"),
            kill(LockoutBingoGoalCategory.NETHER, "kill_wither_skeleton", LockoutBingoGoalDifficulty.HARD, "minecraft:wither_skeleton"),
            kill(LockoutBingoGoalCategory.NETHER, "kill_ghast", LockoutBingoGoalDifficulty.HARD, "minecraft:ghast"),
            kill(LockoutBingoGoalCategory.NETHER, "kill_magma_cube", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:magma_cube"),
            kill(LockoutBingoGoalCategory.NETHER, "kill_piglin", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:piglin"),
            kill(LockoutBingoGoalCategory.NETHER, "kill_hoglin", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:hoglin"),
            kill(LockoutBingoGoalCategory.NETHER, "kill_zoglin", LockoutBingoGoalDifficulty.HARD, "minecraft:zoglin"),
            kill(LockoutBingoGoalCategory.NETHER, "kill_strider", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:strider"),
            biome(LockoutBingoGoalCategory.NETHER, "visit_nether_wastes", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:nether_wastes", "minecraft:netherrack"),
            biome(LockoutBingoGoalCategory.NETHER, "visit_crimson_forest", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:crimson_forest", "minecraft:crimson_fungus"),
            biome(LockoutBingoGoalCategory.NETHER, "visit_warped_forest", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:warped_forest", "minecraft:warped_fungus"),
            biome(LockoutBingoGoalCategory.NETHER, "visit_soul_sand_valley", LockoutBingoGoalDifficulty.HARD, "minecraft:soul_sand_valley", "minecraft:soul_sand"),
            biome(LockoutBingoGoalCategory.NETHER, "visit_basalt_deltas", LockoutBingoGoalDifficulty.HARD, "minecraft:basalt_deltas", "minecraft:basalt"),
            interact(LockoutBingoGoalCategory.NETHER, "barter_with_piglin", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:piglin", "minecraft:gold_ingot"),
            equip(LockoutBingoGoalCategory.NETHER, "wear_gold_in_nether", LockoutBingoGoalDifficulty.EASY, GOLD_ARMOR, 1, "minecraft:golden_helmet", "minecraft:the_nether"),

            craft("craft_furnace", LockoutBingoGoalDifficulty.EASY, "minecraft:furnace"),
            craft("craft_blast_furnace", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:blast_furnace"),
            craft("craft_smoker", LockoutBingoGoalDifficulty.EASY, "minecraft:smoker"),
            craft("craft_campfire", LockoutBingoGoalDifficulty.EASY, "minecraft:campfire"),
            craft("craft_shield", LockoutBingoGoalDifficulty.EASY, "minecraft:shield"),
            craft("craft_bow", LockoutBingoGoalDifficulty.EASY, "minecraft:bow"),
            craft("craft_crossbow", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:crossbow"),
            craft("craft_fishing_rod", LockoutBingoGoalDifficulty.EASY, "minecraft:fishing_rod"),
            craft("craft_shears", LockoutBingoGoalDifficulty.EASY, "minecraft:shears"),
            craft("craft_bucket", LockoutBingoGoalDifficulty.EASY, "minecraft:bucket"),
            craft("craft_compass", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:compass"),
            craft("craft_clock", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:clock"),
            craft("craft_map", LockoutBingoGoalDifficulty.EASY, "minecraft:map"),
            craft("craft_iron_pickaxe", LockoutBingoGoalDifficulty.EASY, "minecraft:iron_pickaxe"),
            craft("craft_diamond_pickaxe", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:diamond_pickaxe"),
            craftMulti("craft_iron_armor_piece", LockoutBingoGoalDifficulty.EASY, "minecraft:iron_helmet", IRON_ARMOR),
            equip(LockoutBingoGoalCategory.CRAFT, "craft_full_iron_armor", LockoutBingoGoalDifficulty.MEDIUM, IRON_ARMOR, 4, "minecraft:iron_chestplate", ""),
            craft("craft_golden_helmet", LockoutBingoGoalDifficulty.EASY, "minecraft:golden_helmet"),
            craft("craft_anvil", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:anvil"),
            craft("craft_enchanting_table", LockoutBingoGoalDifficulty.HARD, "minecraft:enchanting_table"),
            craft("craft_bookshelf", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:bookshelf"),
            craft("craft_brewing_stand", LockoutBingoGoalDifficulty.HARD, "minecraft:brewing_stand"),
            craft("craft_cauldron", LockoutBingoGoalDifficulty.EASY, "minecraft:cauldron"),
            craft("craft_piston", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:piston"),
            craft("craft_sticky_piston", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:sticky_piston"),
            craft("craft_observer", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:observer"),
            craft("craft_dispenser", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:dispenser"),
            craft("craft_dropper", LockoutBingoGoalDifficulty.EASY, "minecraft:dropper"),
            craft("craft_redstone_lamp", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:redstone_lamp"),
            craft("craft_note_block", LockoutBingoGoalDifficulty.EASY, "minecraft:note_block"),
            craft("craft_jukebox", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:jukebox"),
            craft("craft_tnt", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:tnt"),
            craft("craft_fire_charge", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:fire_charge"),
            craft("craft_firework_rocket", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:firework_rocket"),
            craft("craft_lead", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:lead"),
            craft("craft_item_frame", LockoutBingoGoalDifficulty.EASY, "minecraft:item_frame"),
            craft("craft_painting", LockoutBingoGoalDifficulty.EASY, "minecraft:painting"),
            craft("craft_flower_pot", LockoutBingoGoalDifficulty.EASY, "minecraft:flower_pot"),
            craft("craft_bricks", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:bricks"),
            craft("craft_cake", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:cake"),
            craft("craft_cookie", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:cookie"),
            craft("craft_pumpkin_pie", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:pumpkin_pie"),
            craft("craft_spyglass", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:spyglass"),
            craft("craft_lightning_rod", LockoutBingoGoalDifficulty.EASY, "minecraft:lightning_rod"),
            craft("craft_copper_bulb", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:copper_bulb"),
            craft("craft_redstone_torch", LockoutBingoGoalDifficulty.EASY, "minecraft:redstone_torch"),
            craft("craft_repeater", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:repeater"),
            craft("craft_comparator", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:comparator"),
            craft("craft_daylight_detector", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:daylight_detector"),
            craft("craft_target_block", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:target"),
            craft("craft_hopper", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:hopper"),

            kill(LockoutBingoGoalCategory.KILL, "kill_zombie", LockoutBingoGoalDifficulty.EASY, "minecraft:zombie"),
            kill(LockoutBingoGoalCategory.KILL, "kill_skeleton", LockoutBingoGoalDifficulty.EASY, "minecraft:skeleton"),
            kill(LockoutBingoGoalCategory.KILL, "kill_creeper", LockoutBingoGoalDifficulty.EASY, "minecraft:creeper"),
            kill(LockoutBingoGoalCategory.KILL, "kill_spider", LockoutBingoGoalDifficulty.EASY, "minecraft:spider"),
            kill(LockoutBingoGoalCategory.KILL, "kill_enderman", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:enderman"),
            kill(LockoutBingoGoalCategory.KILL, "kill_witch", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:witch"),
            kill(LockoutBingoGoalCategory.KILL, "kill_slime", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:slime"),
            kill(LockoutBingoGoalCategory.KILL, "kill_phantom", LockoutBingoGoalDifficulty.HARD, "minecraft:phantom"),
            kill(LockoutBingoGoalCategory.KILL, "kill_pillager", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:pillager"),
            kill(LockoutBingoGoalCategory.KILL, "kill_guardian", LockoutBingoGoalDifficulty.HARD, "minecraft:guardian"),
            kill(LockoutBingoGoalCategory.KILL, "kill_elder_guardian", LockoutBingoGoalDifficulty.EXPERT, "minecraft:elder_guardian"),
            kill(LockoutBingoGoalCategory.KILL, "kill_drowned", LockoutBingoGoalDifficulty.EASY, "minecraft:drowned"),
            kill(LockoutBingoGoalCategory.KILL, "kill_husk", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:husk"),
            kill(LockoutBingoGoalCategory.KILL, "kill_stray", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:stray"),
            kill(LockoutBingoGoalCategory.KILL, "kill_cave_spider", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:cave_spider"),
            kill(LockoutBingoGoalCategory.KILL, "kill_silverfish", LockoutBingoGoalDifficulty.HARD, "minecraft:silverfish"),
            kill(LockoutBingoGoalCategory.KILL, "kill_ravager", LockoutBingoGoalDifficulty.HARD, "minecraft:ravager"),
            kill(LockoutBingoGoalCategory.KILL, "kill_shulker", LockoutBingoGoalDifficulty.EXPERT, "minecraft:shulker", false),
            kill(LockoutBingoGoalCategory.KILL, "kill_breeze", LockoutBingoGoalDifficulty.HARD, "minecraft:breeze"),
            kill(LockoutBingoGoalCategory.KILL, "kill_warden", LockoutBingoGoalDifficulty.EXPERT, "minecraft:warden", false),

            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_desert", LockoutBingoGoalDifficulty.EASY, "minecraft:desert", "minecraft:sand"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_savanna", LockoutBingoGoalDifficulty.EASY, "minecraft:savanna", "minecraft:acacia_sapling"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_jungle", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:jungle", "minecraft:jungle_sapling"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_badlands", LockoutBingoGoalDifficulty.HARD, "minecraft:badlands", "minecraft:red_sand"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_swamp", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:swamp", "minecraft:lily_pad"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_mangrove_swamp", LockoutBingoGoalDifficulty.HARD, "minecraft:mangrove_swamp", "minecraft:mangrove_propagule"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_snowy_biome", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:snowy_plains", "minecraft:snowball"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_cherry_grove", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:cherry_grove", "minecraft:cherry_sapling"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_mushroom_fields", LockoutBingoGoalDifficulty.EXPERT, "minecraft:mushroom_fields", "minecraft:red_mushroom"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_dark_forest", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:dark_forest", "minecraft:dark_oak_sapling"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_bamboo_jungle", LockoutBingoGoalDifficulty.HARD, "minecraft:bamboo_jungle", "minecraft:bamboo"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_ocean", LockoutBingoGoalDifficulty.EASY, "minecraft:ocean", "minecraft:water_bucket"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_warm_ocean", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:warm_ocean", "minecraft:tropical_fish"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_deep_dark", LockoutBingoGoalDifficulty.HARD, "minecraft:deep_dark", "minecraft:sculk"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_dripstone_caves", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:dripstone_caves", "minecraft:pointed_dripstone"),
            biome(LockoutBingoGoalCategory.EXPLORATION, "visit_lush_caves", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:lush_caves", "minecraft:moss_block"),
            structure("visit_village", LockoutBingoGoalDifficulty.EASY, "minecraft:village", "minecraft:bell"),
            structure("visit_desert_pyramid", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:desert_pyramid", "minecraft:chiseled_sandstone"),
            structure("visit_jungle_temple", LockoutBingoGoalDifficulty.HARD, "minecraft:jungle_temple", "minecraft:lever"),
            structure("visit_mineshaft", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:mineshaft", "minecraft:rail"),
            structure("visit_shipwreck", LockoutBingoGoalDifficulty.EASY, "minecraft:shipwreck", "minecraft:oak_planks"),
            structure("visit_ocean_ruin", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:ocean_ruin", "minecraft:trident"),
            structure("visit_ruined_portal", LockoutBingoGoalDifficulty.EASY, "minecraft:ruined_portal", "minecraft:obsidian"),
            structure("visit_pillager_outpost", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:pillager_outpost", "minecraft:crossbow"),
            structure("visit_trial_chambers", LockoutBingoGoalDifficulty.HARD, "minecraft:trial_chambers", "minecraft:trial_spawner"),
            structure("visit_ancient_city", LockoutBingoGoalDifficulty.EXPERT, "minecraft:ancient_city", "minecraft:sculk_shrieker"),
            structure("visit_nether_fortress", LockoutBingoGoalDifficulty.HARD, "minecraft:fortress", "minecraft:nether_bricks"),
            structure("visit_bastion_remnant", LockoutBingoGoalDifficulty.HARD, "minecraft:bastion_remnant", "minecraft:polished_blackstone_bricks"),
            structure("visit_ocean_monument", LockoutBingoGoalDifficulty.HARD, "minecraft:ocean_monument", "minecraft:prismarine"),
            structure("visit_woodland_mansion", LockoutBingoGoalDifficulty.EXPERT, "minecraft:mansion", "minecraft:totem_of_undying"),
            structure("visit_stronghold", LockoutBingoGoalDifficulty.EXPERT, "minecraft:stronghold", "minecraft:end_portal_frame"),
            structure("visit_end_city", LockoutBingoGoalDifficulty.EXPERT, "minecraft:end_city", "minecraft:purpur_block"),

            interact(LockoutBingoGoalCategory.INTERACTION, "milk_cow", LockoutBingoGoalDifficulty.EASY, "minecraft:cow", "minecraft:milk_bucket"),
            interact(LockoutBingoGoalCategory.INTERACTION, "shear_sheep", LockoutBingoGoalDifficulty.EASY, "minecraft:sheep", "minecraft:shears"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "breed_animals", LockoutBingoGoalDifficulty.EASY, "minecraft:animal", "minecraft:wheat"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "breed_cows", LockoutBingoGoalDifficulty.EASY, "minecraft:cow", "minecraft:wheat"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "breed_sheep", LockoutBingoGoalDifficulty.EASY, "minecraft:sheep", "minecraft:wheat"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "breed_pigs", LockoutBingoGoalDifficulty.EASY, "minecraft:pig", "minecraft:carrot"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "breed_chickens", LockoutBingoGoalDifficulty.EASY, "minecraft:chicken", "minecraft:wheat_seeds"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "tame_wolf", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:wolf", "minecraft:bone"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "tame_cat", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:cat", "minecraft:cod"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "tame_horse", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:horse", "minecraft:saddle"),
            interact(LockoutBingoGoalCategory.INTERACTION, "ride_horse", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:horse", "minecraft:saddle"),
            interact(LockoutBingoGoalCategory.INTERACTION, "ride_pig", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:pig", "minecraft:carrot_on_a_stick"),
            interact(LockoutBingoGoalCategory.INTERACTION, "ride_strider", LockoutBingoGoalDifficulty.HARD, "minecraft:strider", "minecraft:warped_fungus_on_a_stick"),
            fishingTodo("catch_fish", LockoutBingoGoalDifficulty.EASY, "minecraft:fishing_rod"),
            consume("eat_apple", LockoutBingoGoalDifficulty.EASY, "minecraft:apple"),
            consume("eat_bread", LockoutBingoGoalDifficulty.EASY, "minecraft:bread"),
            consume("eat_cooked_beef", LockoutBingoGoalDifficulty.EASY, "minecraft:cooked_beef"),
            consume("eat_cooked_porkchop", LockoutBingoGoalDifficulty.EASY, "minecraft:cooked_porkchop"),
            consume("eat_cooked_chicken", LockoutBingoGoalDifficulty.EASY, "minecraft:cooked_chicken"),
            consume("eat_cooked_mutton", LockoutBingoGoalDifficulty.EASY, "minecraft:cooked_mutton"),
            consume("eat_cooked_cod", LockoutBingoGoalDifficulty.EASY, "minecraft:cooked_cod"),
            consume("eat_cooked_salmon", LockoutBingoGoalDifficulty.EASY, "minecraft:cooked_salmon"),
            consume("eat_cookie", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:cookie"),
            consume("eat_cake_slice", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:cake", "minecraft:cake"),
            consume("eat_pumpkin_pie", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:pumpkin_pie"),
            consume("eat_golden_apple", LockoutBingoGoalDifficulty.HARD, "minecraft:golden_apple"),
            consume("eat_glow_berries", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:glow_berries"),
            consume("eat_suspicious_stew", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:suspicious_stew"),
            consume("eat_beetroot_soup", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:beetroot_soup"),
            consume("eat_rabbit_stew", LockoutBingoGoalDifficulty.HARD, "minecraft:rabbit_stew"),
            consume("eat_dried_kelp", LockoutBingoGoalDifficulty.EASY, "minecraft:dried_kelp"),
            consume("eat_honey_bottle", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:honey_bottle"),
            interact(LockoutBingoGoalCategory.INTERACTION, "sleep_in_bed", LockoutBingoGoalDifficulty.EASY, "minecraft:bed", "minecraft:red_bed"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "set_spawn", LockoutBingoGoalDifficulty.EASY, "minecraft:respawn_anchor", "minecraft:respawn_anchor"),
            interact(LockoutBingoGoalCategory.INTERACTION, "use_lectern", LockoutBingoGoalDifficulty.EASY, "minecraft:lectern", "minecraft:lectern"),
            interact(LockoutBingoGoalCategory.INTERACTION, "use_grindstone", LockoutBingoGoalDifficulty.EASY, "minecraft:grindstone", "minecraft:grindstone"),
            interact(LockoutBingoGoalCategory.INTERACTION, "use_stonecutter", LockoutBingoGoalDifficulty.EASY, "minecraft:stonecutter", "minecraft:stonecutter"),
            interact(LockoutBingoGoalCategory.INTERACTION, "use_cartography_table", LockoutBingoGoalDifficulty.EASY, "minecraft:cartography_table", "minecraft:cartography_table"),
            interact(LockoutBingoGoalCategory.INTERACTION, "use_smithing_table", LockoutBingoGoalDifficulty.EASY, "minecraft:smithing_table", "minecraft:smithing_table"),
            interact(LockoutBingoGoalCategory.INTERACTION, "use_loom", LockoutBingoGoalDifficulty.EASY, "minecraft:loom", "minecraft:loom"),
            interact(LockoutBingoGoalCategory.INTERACTION, "light_campfire", LockoutBingoGoalDifficulty.EASY, "minecraft:campfire", "minecraft:campfire"),
            interactTodo(LockoutBingoGoalCategory.INTERACTION, "activate_pressure_plate", LockoutBingoGoalDifficulty.EASY, "minecraft:stone_pressure_plate", "minecraft:stone_pressure_plate"),
            equip(LockoutBingoGoalCategory.INTERACTION, "wear_full_leather", LockoutBingoGoalDifficulty.MEDIUM, LEATHER_ARMOR, 4, "minecraft:leather_chestplate", ""),
            equip(LockoutBingoGoalCategory.INTERACTION, "wear_full_iron", LockoutBingoGoalDifficulty.MEDIUM, IRON_ARMOR, 4, "minecraft:iron_chestplate", ""),
            equip(LockoutBingoGoalCategory.INTERACTION, "wear_full_gold", LockoutBingoGoalDifficulty.MEDIUM, GOLD_ARMOR, 4, "minecraft:golden_chestplate", ""),
            equip(LockoutBingoGoalCategory.INTERACTION, "wear_diamond_piece", LockoutBingoGoalDifficulty.MEDIUM, DIAMOND_ARMOR, 1, "minecraft:diamond_chestplate", ""),
            action(LockoutBingoGoalCategory.INTERACTION, "block_damage_with_shield", LockoutBingoGoalDifficulty.EASY, "minecraft:shield", "minecraft:shield"),
            actionTodo(LockoutBingoGoalCategory.INTERACTION, "obtain_firework_crossbow", LockoutBingoGoalDifficulty.HARD, "minecraft:crossbow", "minecraft:crossbow"),

            trade("trade_with_villager", LockoutBingoGoalDifficulty.EASY, "minecraft:villager", "minecraft:emerald"),
            tradeTodo("obtain_emerald_by_trade", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:emerald", "minecraft:emerald"),
            tradeTodo("trade_with_librarian", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:librarian", "minecraft:book"),
            tradeTodo("trade_with_armorer", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:armorer", "minecraft:iron_chestplate"),
            tradeTodo("trade_with_farmer", LockoutBingoGoalDifficulty.EASY, "minecraft:farmer", "minecraft:bread"),
            tradeTodo("trade_with_cleric", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:cleric", "minecraft:glowstone_dust"),
            tradeTodo("trade_with_toolsmith", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:toolsmith", "minecraft:iron_pickaxe"),
            tradeTodo("trade_with_fletcher", LockoutBingoGoalDifficulty.EASY, "minecraft:fletcher", "minecraft:arrow"),
            tradeTodo("buy_bread", LockoutBingoGoalDifficulty.EASY, "minecraft:bread", "minecraft:bread"),
            tradeTodo("buy_arrows", LockoutBingoGoalDifficulty.EASY, "minecraft:arrow", "minecraft:arrow"),
            tradeTodo("buy_lapis", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:lapis_lazuli", "minecraft:lapis_lazuli"),
            item(LockoutBingoGoalCategory.VILLAGER, "buy_bell", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:bell"),
            interact(LockoutBingoGoalCategory.VILLAGER, "ring_bell", LockoutBingoGoalDifficulty.EASY, "minecraft:bell", "minecraft:bell"),
            interactTodo(LockoutBingoGoalCategory.VILLAGER, "sleep_in_village_bed", LockoutBingoGoalDifficulty.EASY, "minecraft:bed", "minecraft:red_bed"),

            dimension(LockoutBingoGoalCategory.EXPLORATION, "enter_end", LockoutBingoGoalDifficulty.EXPERT, "minecraft:the_end", "minecraft:end_stone", false),
            item(LockoutBingoGoalCategory.EXPLORATION, "obtain_dragon_breath", LockoutBingoGoalDifficulty.EXPERT, "minecraft:dragon_breath", false),
            item(LockoutBingoGoalCategory.EXPLORATION, "obtain_shulker_shell", LockoutBingoGoalDifficulty.EXPERT, "minecraft:shulker_shell", false),
            item(LockoutBingoGoalCategory.EXPLORATION, "obtain_elytra", LockoutBingoGoalDifficulty.EXPERT, "minecraft:elytra", false),
            kill(LockoutBingoGoalCategory.EXPLORATION, "kill_ender_dragon", LockoutBingoGoalDifficulty.EXPERT, "minecraft:ender_dragon", false),
            locationTodo(LockoutBingoGoalCategory.EXPLORATION, "enter_end_gateway", LockoutBingoGoalDifficulty.EXPERT, "challengecraft:end_gateway", "minecraft:end_portal_frame"),

            advancement("advancement_stone_age", LockoutBingoGoalDifficulty.EASY, "minecraft:story/mine_stone", "minecraft:stone_pickaxe"),
            advancement("advancement_acquire_hardware", LockoutBingoGoalDifficulty.EASY, "minecraft:story/smelt_iron", "minecraft:iron_ingot"),
            advancement("advancement_suit_up", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:story/obtain_armor", "minecraft:iron_chestplate"),
            advancement("advancement_diamonds", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:story/mine_diamond", "minecraft:diamond"),
            advancement("advancement_enchanter", LockoutBingoGoalDifficulty.HARD, "minecraft:story/enchant_item", "minecraft:enchanting_table"),
            advancement("advancement_we_need_to_go_deeper", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:story/enter_the_nether", "minecraft:netherrack"),
            advancement("advancement_return_to_sender", LockoutBingoGoalDifficulty.HARD, "minecraft:nether/return_to_sender", "minecraft:ghast_tear"),
            advancement("advancement_into_fire", LockoutBingoGoalDifficulty.HARD, "minecraft:nether/obtain_blaze_rod", "minecraft:blaze_rod"),
            advancement("advancement_local_brewery", LockoutBingoGoalDifficulty.HARD, "minecraft:nether/brew_potion", "minecraft:potion"),
            advancement("advancement_monster_hunter", LockoutBingoGoalDifficulty.EASY, "minecraft:adventure/kill_a_mob", "minecraft:iron_sword"),
            advancement("advancement_take_aim", LockoutBingoGoalDifficulty.EASY, "minecraft:adventure/shoot_arrow", "minecraft:bow"),
            advancement("advancement_sniper_duel", LockoutBingoGoalDifficulty.HARD, "minecraft:adventure/sniper_duel", "minecraft:bow"),
            advancement("advancement_best_friends_forever", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:husbandry/tame_an_animal", "minecraft:bone"),
            advancement("advancement_fishy_business", LockoutBingoGoalDifficulty.EASY, "minecraft:husbandry/fishy_business", "minecraft:cod"),
            advancement("advancement_tactical_fishing", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:husbandry/tactical_fishing", "minecraft:bucket"),

            brew("brew_any_potion", LockoutBingoGoalDifficulty.HARD, "minecraft:potion", "minecraft:brewing_stand"),
            brew("brew_speed_potion", LockoutBingoGoalDifficulty.HARD, "minecraft:swiftness", "minecraft:sugar"),
            brew("brew_strength_potion", LockoutBingoGoalDifficulty.HARD, "minecraft:strength", "minecraft:blaze_powder"),
            brew("brew_fire_resistance", LockoutBingoGoalDifficulty.HARD, "minecraft:fire_resistance", "minecraft:magma_cream"),
            brew("brew_night_vision", LockoutBingoGoalDifficulty.HARD, "minecraft:night_vision", "minecraft:golden_carrot"),
            enchant("enchant_item", LockoutBingoGoalDifficulty.HARD, "minecraft:enchanting_table"),
            enchant("enchant_sword", LockoutBingoGoalDifficulty.HARD, "minecraft:diamond_sword"),
            enchant("enchant_pickaxe", LockoutBingoGoalDifficulty.HARD, "minecraft:diamond_pickaxe"),
            consumeTodo(LockoutBingoGoalCategory.INTERACTION, "drink_potion", LockoutBingoGoalDifficulty.HARD, "minecraft:potion"),
            action(LockoutBingoGoalCategory.INTERACTION, "splash_potion", LockoutBingoGoalDifficulty.HARD, "minecraft:splash_potion", "minecraft:splash_potion"),
            action(LockoutBingoGoalCategory.INTERACTION, "place_tnt", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:tnt", "minecraft:tnt"),
            action(LockoutBingoGoalCategory.INTERACTION, "ignite_tnt", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:tnt", "minecraft:flint_and_steel"),
            action(LockoutBingoGoalCategory.INTERACTION, "shoot_crossbow", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:crossbow", "minecraft:crossbow"),
            action(LockoutBingoGoalCategory.INTERACTION, "hit_target_block", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:target", "minecraft:target"),
            action(LockoutBingoGoalCategory.INTERACTION, "throw_ender_pearl", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:ender_pearl", "minecraft:ender_pearl"),
            action(LockoutBingoGoalCategory.INTERACTION, "throw_trident", LockoutBingoGoalDifficulty.HARD, "minecraft:trident", "minecraft:trident"),
            action(LockoutBingoGoalCategory.INTERACTION, "use_totem", LockoutBingoGoalDifficulty.EXPERT, "minecraft:totem_of_undying", "minecraft:totem_of_undying"),

            damage("survive_explosion", LockoutBingoGoalDifficulty.MEDIUM, "challengecraft:explosion", "minecraft:tnt"),
            damage("take_fall_damage", LockoutBingoGoalDifficulty.EASY, "challengecraft:fall", "minecraft:feather"),
            damage("fall_20_blocks_and_survive", LockoutBingoGoalDifficulty.MEDIUM, "challengecraft:fall_20", "minecraft:feather"),
            damage("burn_and_survive", LockoutBingoGoalDifficulty.EASY, "challengecraft:burn", "minecraft:flint_and_steel"),
            damage("freeze_in_powder_snow", LockoutBingoGoalDifficulty.MEDIUM, "challengecraft:freeze", "minecraft:powder_snow_bucket"),
            damage("get_shot_by_skeleton", LockoutBingoGoalDifficulty.EASY, "challengecraft:skeleton_arrow", "minecraft:arrow"),
            status("get_poisoned", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:poison", "minecraft:spider_eye"),
            status("get_withered", LockoutBingoGoalDifficulty.HARD, "minecraft:wither", "minecraft:wither_rose"),
            status("get_levitation", LockoutBingoGoalDifficulty.EXPERT, "minecraft:levitation", "minecraft:shulker_shell"),
            health("have_10_hearts_missing", LockoutBingoGoalDifficulty.MEDIUM, "minecraft:golden_apple"),
            location("reach_y_minus_50", LockoutBingoGoalDifficulty.EASY, "challengecraft:y_-50", "minecraft:deepslate"),
            location("reach_build_limit", LockoutBingoGoalDifficulty.HARD, "challengecraft:build_limit", "minecraft:scaffolding"),
            location("stand_on_bedrock", LockoutBingoGoalDifficulty.HARD, "challengecraft:bedrock", "minecraft:bedrock")
    );

    private static final Map<String, LockoutBingoGoal> BY_ID = indexGoals();

    private LockoutBingoGoalPool() {
    }

    public static List<LockoutBingoGoal> all() {
        return GOALS;
    }

    public static LockoutBingoGoal byId(String id) {
        return BY_ID.get(id);
    }

    public static List<LockoutBingoGoal> pickBoard(long seed) {
        Random random = new Random(seed);
        List<LockoutBingoGoal> board = new ArrayList<>(BOARD_SIZE);
        Set<String> usedIds = new java.util.LinkedHashSet<>();
        Map<LockoutBingoGoalType, Integer> typeCounts = new EnumMap<>(LockoutBingoGoalType.class);

        // Reserve broad categories up front so a board feels varied before difficulty fillers are added.
        pickInto(board, usedIds, typeCounts, random, 5, goal -> goal.category() == LockoutBingoGoalCategory.ITEM && goal.difficulty().isAtMost(LockoutBingoGoalDifficulty.MEDIUM));
        pickInto(board, usedIds, typeCounts, random, 5, goal -> goal.category() == LockoutBingoGoalCategory.CRAFT && goal.difficulty().isAtMost(LockoutBingoGoalDifficulty.MEDIUM));
        pickInto(board, usedIds, typeCounts, random, 3, goal -> goal.category() == LockoutBingoGoalCategory.KILL && goal.difficulty().isAtMost(LockoutBingoGoalDifficulty.MEDIUM));
        pickInto(board, usedIds, typeCounts, random, 2, goal -> goal.category() == LockoutBingoGoalCategory.EXPLORATION && goal.difficulty().isAtMost(LockoutBingoGoalDifficulty.MEDIUM));
        pickInto(board, usedIds, typeCounts, random, 2, goal -> goal.category() == LockoutBingoGoalCategory.INTERACTION && goal.difficulty().isAtMost(LockoutBingoGoalDifficulty.MEDIUM));
        pickInto(board, usedIds, typeCounts, random, 2, goal -> goal.category() == LockoutBingoGoalCategory.NETHER && goal.difficulty().isAtMost(LockoutBingoGoalDifficulty.MEDIUM));
        pickInto(board, usedIds, typeCounts, random, 1, goal -> goal.category() == LockoutBingoGoalCategory.VILLAGER && goal.difficulty().isAtMost(LockoutBingoGoalDifficulty.MEDIUM));
        pickInto(board, usedIds, typeCounts, random, 2, goal -> goal.difficulty() == LockoutBingoGoalDifficulty.MEDIUM);
        pickInto(board, usedIds, typeCounts, random, 2, goal -> goal.difficulty() == LockoutBingoGoalDifficulty.HARD);
        pickInto(board, usedIds, typeCounts, random, 1, goal -> goal.difficulty() == LockoutBingoGoalDifficulty.EXPERT);

        if (board.size() < BOARD_SIZE) {
            pickInto(board, usedIds, typeCounts, random, BOARD_SIZE - board.size(), goal -> goal.difficulty().isAtMost(LockoutBingoGoalDifficulty.HARD));
        }

        if (board.size() != BOARD_SIZE) {
            throw new IllegalStateException("Unable to generate a 25-goal Lockout Bingo board.");
        }

        Collections.shuffle(board, random);
        return List.copyOf(board);
    }

    private static void pickInto(
            List<LockoutBingoGoal> board,
            Set<String> usedIds,
            Map<LockoutBingoGoalType, Integer> typeCounts,
            Random random,
            int count,
            Predicate<LockoutBingoGoal> predicate
    ) {
        List<LockoutBingoGoal> candidates = selectableGoals().stream()
                .filter(predicate)
                .filter(goal -> !usedIds.contains(goal.id()))
                .toList();

        List<LockoutBingoGoal> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);

        for (LockoutBingoGoal goal : shuffled) {
            if (count <= 0) {
                return;
            }
            if (!canUseByType(typeCounts, goal.type())) {
                continue;
            }
            addGoal(board, usedIds, typeCounts, goal);
            count--;
        }

        if (count <= 0) {
            return;
        }

        for (LockoutBingoGoal goal : shuffled) {
            if (count <= 0) {
                return;
            }
            if (usedIds.contains(goal.id())) {
                continue;
            }
            addGoal(board, usedIds, typeCounts, goal);
            count--;
        }
    }

    private static void addGoal(
            List<LockoutBingoGoal> board,
            Set<String> usedIds,
            Map<LockoutBingoGoalType, Integer> typeCounts,
            LockoutBingoGoal goal
    ) {
        if (!usedIds.add(goal.id())) {
            return;
        }
        board.add(goal);
        typeCounts.merge(goal.type(), 1, Integer::sum);
    }

    private static List<LockoutBingoGoal> selectableGoals() {
        return GOALS.stream()
                .filter(LockoutBingoGoal::isSelectableOnNormalBoard)
                .toList();
    }

    private static boolean canUseByType(Map<LockoutBingoGoalType, Integer> typeCounts, LockoutBingoGoalType type) {
        return typeCounts.getOrDefault(type, 0) < maxTypeCount(type);
    }

    private static int maxTypeCount(LockoutBingoGoalType type) {
        return switch (type) {
            case ITEM -> 7;
            case ITEM_AMOUNT -> 4;
            case ITEM_TAG -> 2;
            case CRAFT -> 6;
            case KILL -> 4;
            case CONSUME -> 4;
            case DIMENSION -> 2;
            case BIOME -> 4;
            case EQUIP -> 3;
            case INTERACT -> 4;
            case TRADE -> 2;
            default -> 2;
        };
    }

    private static Map<String, LockoutBingoGoal> indexGoals() {
        if (GOALS.size() < 120) {
            throw new IllegalStateException("Lockout Bingo goal pool must contain at least 120 predefined goals.");
        }

        Map<String, LockoutBingoGoal> byId = new LinkedHashMap<>();
        for (LockoutBingoGoal goal : GOALS) {
            LockoutBingoGoal replaced = byId.put(goal.id(), goal);
            if (replaced != null) {
                throw new IllegalStateException("Duplicate Lockout Bingo goal id: " + goal.id());
            }
        }
        return Map.copyOf(byId);
    }

    private static LockoutBingoGoal item(LockoutBingoGoalCategory category, String id, LockoutBingoGoalDifficulty difficulty, String itemId) {
        return item(category, id, difficulty, itemId, true);
    }

    private static LockoutBingoGoal item(LockoutBingoGoalCategory category, String id, LockoutBingoGoalDifficulty difficulty, String itemId, boolean selectable) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.ITEM,
                difficulty,
                List.of(itemId),
                "",
                1,
                itemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                selectable,
                "",
                ""
        );
    }

    private static LockoutBingoGoal itemMulti(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String iconItemId,
            List<String> itemIds
    ) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.ITEM,
                difficulty,
                itemIds,
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal itemAmount(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            int amount,
            String iconItemId,
            List<String> itemIds
    ) {
        return itemAmount(category, id, difficulty, amount, iconItemId, itemIds, true);
    }

    private static LockoutBingoGoal itemAmount(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            int amount,
            String iconItemId,
            List<String> itemIds,
            boolean selectable
    ) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.ITEM_AMOUNT,
                difficulty,
                itemIds,
                "",
                amount,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                selectable,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal tag(LockoutBingoGoalCategory category, String id, LockoutBingoGoalDifficulty difficulty, String tagId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.ITEM_TAG,
                difficulty,
                List.of(tagId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal craft(String id, LockoutBingoGoalDifficulty difficulty, String itemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.CRAFT,
                LockoutBingoGoalType.CRAFT,
                difficulty,
                List.of(itemId),
                "",
                1,
                itemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "",
                ""
        );
    }

    private static LockoutBingoGoal craftMulti(String id, LockoutBingoGoalDifficulty difficulty, String iconItemId, List<String> itemIds) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.CRAFT,
                LockoutBingoGoalType.CRAFT,
                difficulty,
                itemIds,
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal kill(LockoutBingoGoalCategory category, String id, LockoutBingoGoalDifficulty difficulty, String entityId) {
        return kill(category, id, difficulty, entityId, true);
    }

    private static LockoutBingoGoal kill(LockoutBingoGoalCategory category, String id, LockoutBingoGoalDifficulty difficulty, String entityId, boolean selectable) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.KILL,
                difficulty,
                List.of(entityId),
                "",
                1,
                "",
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                selectable,
                "",
                ""
        );
    }

    private static LockoutBingoGoal consume(String id, LockoutBingoGoalDifficulty difficulty, String itemId) {
        return consume(id, difficulty, itemId, itemId);
    }

    private static LockoutBingoGoal consume(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.INTERACTION,
                LockoutBingoGoalType.CONSUME,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "",
                ""
        );
    }

    private static LockoutBingoGoal consumeTodo(LockoutBingoGoalCategory category, String id, LockoutBingoGoalDifficulty difficulty, String targetId) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.CONSUME,
                difficulty,
                List.of(targetId),
                "",
                1,
                targetId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "",
                ""
        );
    }

    private static LockoutBingoGoal dimension(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String dimensionId,
            String iconItemId
    ) {
        return dimension(category, id, difficulty, dimensionId, iconItemId, true);
    }

    private static LockoutBingoGoal dimension(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String dimensionId,
            String iconItemId,
            boolean selectable
    ) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.DIMENSION,
                difficulty,
                List.of(dimensionId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                selectable,
                "",
                ""
        );
    }

    private static LockoutBingoGoal biome(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String biomeId,
            String iconItemId
    ) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.BIOME,
                difficulty,
                List.of(biomeId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "",
                ""
        );
    }

    private static LockoutBingoGoal equip(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            List<String> itemIds,
            int amount,
            String iconItemId,
            String contextTarget
    ) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.EQUIP,
                difficulty,
                itemIds,
                contextTarget,
                amount,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal interact(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String targetId,
            String iconItemId
    ) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.INTERACT,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.TODO,
                false,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal interactTodo(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String targetId,
            String iconItemId
    ) {
        boolean implemented = switch (id) {
            case "breed_animals", "breed_cows", "breed_sheep", "breed_pigs", "breed_chickens", "activate_pressure_plate" -> true;
            default -> false;
        };
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.INTERACT,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                implemented ? LockoutBingoGoalImplementationStatus.IMPLEMENTED : LockoutBingoGoalImplementationStatus.TODO,
                implemented,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal trade(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.VILLAGER,
                LockoutBingoGoalType.TRADE,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal tradeTodo(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.VILLAGER,
                LockoutBingoGoalType.TRADE,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal structure(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.EXPLORATION,
                LockoutBingoGoalType.STRUCTURE,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal advancement(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.EXPLORATION,
                LockoutBingoGoalType.ADVANCEMENT,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal brew(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.NETHER,
                LockoutBingoGoalType.BREW,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal enchant(String id, LockoutBingoGoalDifficulty difficulty, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.CRAFT,
                LockoutBingoGoalType.ENCHANT,
                difficulty,
                List.of("challengecraft:enchant"),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal inventorySet(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String iconItemId,
            List<String> targetIds
    ) {
        int requiredAmount = switch (id) {
            case "obtain_all_log_types_basic" -> 4;
            case "obtain_5_dye_colors" -> 5;
            default -> 3;
        };
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.INVENTORY_SET,
                difficulty,
                targetIds,
                "",
                requiredAmount,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal fishingTodo(String id, LockoutBingoGoalDifficulty difficulty, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.INTERACTION,
                LockoutBingoGoalType.FISHING,
                difficulty,
                List.of("challengecraft:fishing"),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal action(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String targetId,
            String iconItemId
    ) {
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.ACTION,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal actionTodo(
            LockoutBingoGoalCategory category,
            String id,
            LockoutBingoGoalDifficulty difficulty,
            String targetId,
            String iconItemId
    ) {
        boolean implemented = "obtain_firework_crossbow".equals(id);
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.ACTION,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                implemented ? LockoutBingoGoalImplementationStatus.IMPLEMENTED : LockoutBingoGoalImplementationStatus.TODO,
                implemented,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal damage(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.INTERACTION,
                LockoutBingoGoalType.DAMAGE_EVENT,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal status(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.INTERACTION,
                LockoutBingoGoalType.STATUS_EFFECT,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal health(String id, LockoutBingoGoalDifficulty difficulty, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.INTERACTION,
                LockoutBingoGoalType.HEALTH_CHECK,
                difficulty,
                List.of("challengecraft:health"),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal location(String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        return new LockoutBingoGoal(
                id,
                LockoutBingoGoalCategory.EXPLORATION,
                LockoutBingoGoalType.LOCATION,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                LockoutBingoGoalImplementationStatus.IMPLEMENTED,
                true,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }

    private static LockoutBingoGoal locationTodo(LockoutBingoGoalCategory category, String id, LockoutBingoGoalDifficulty difficulty, String targetId, String iconItemId) {
        boolean implemented = "enter_end_gateway".equals(id);
        return new LockoutBingoGoal(
                id,
                category,
                LockoutBingoGoalType.LOCATION,
                difficulty,
                List.of(targetId),
                "",
                1,
                iconItemId,
                implemented ? LockoutBingoGoalImplementationStatus.IMPLEMENTED : LockoutBingoGoalImplementationStatus.TODO,
                implemented,
                "challengecraft.lockout.goal." + id + ".title",
                ""
        );
    }
}
