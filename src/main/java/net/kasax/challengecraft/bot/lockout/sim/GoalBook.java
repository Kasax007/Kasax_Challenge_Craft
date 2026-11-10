package net.kasax.challengecraft.bot.lockout.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.kasax.challengecraft.bot.lockout.sim.Ms.*;
import static net.kasax.challengecraft.bot.lockout.sim.Place.*;
import static net.kasax.challengecraft.bot.lockout.sim.Res.*;

/**
 * Every goal of the Lockout pool ({@code LockoutBingoGoalPool}) as an abstract {@link Recipe}.
 * The seconds are a good player's: what a speedrunner needs with the way known, before the luck of
 * finding things. A {@link Profile} turns them into Bob's (or anyone's) pace.
 *
 * <p>Kept by hand: a goal added to the pool needs a line here ({@code SimSelfTest} checks that
 * every goal of the pool has one). The lines follow the pool's order of types. {@code noBob()}:
 * Bob knows no way to it (the game test {@code coverage}, 9.11.2026: 46 of 312 goals without a way
 * in a flat arena; those that only wanted water, lava, snow, a village or the night there are not
 * marked).
 */
public final class GoalBook {
    private static final List<Recipe> ALL = new ArrayList<>();
    private static final Map<String, Recipe> BY_ID = new HashMap<>();

    private GoalBook() {
    }

    public static Recipe get(String id) {
        return BY_ID.get(id);
    }

    public static List<Recipe> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** The share of the attempts that failed in the leagues' journals, over all goals (see {@link #measured}). */
    public static double SEEN_FAIL = 0.415;

    /**
     * What the leagues' journals saw of each goal (bob-knowledge/goal-priors.json, "outcomes":
     * attempts counted and got, from scripts/bot/analysis/goal_priors.py) into the recipes, for the
     * simulator's failure chances per goal. Without the file (or Gson), nothing: the kinds' chances.
     */
    private static void measured() {
        try (var in = GoalBook.class.getResourceAsStream("/bob-knowledge/goal-priors.json")) {
            if (in == null) return;
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("outcomes")) return;
            int starts = 0, done = 0;
            for (var e : root.getAsJsonObject("outcomes").entrySet()) {
                var a = e.getValue().getAsJsonArray();
                int n = a.get(0).getAsInt(), k = a.get(1).getAsInt();
                starts += n;
                done += k;
                Recipe r = BY_ID.get(e.getKey());
                if (r == null) continue;
                r.seenStarts = n;
                r.seenDone = k;
            }
            if (starts >= 20) SEEN_FAIL = 1 - done / (double) starts;
        } catch (Exception | LinkageError e) {
            // (No goal priors to be read: every goal fails as its kind does.)
        }
    }

    private static Recipe g(String id) {
        Recipe r = new Recipe(id);
        r.index = ALL.size();
        if (BY_ID.put(id, r) != null) throw new IllegalStateException("goal twice in the book: " + id);
        ALL.add(r);
        return r;
    }

    static {
        // ---- items: ores and what comes of them ----------------------------------------------
        g("obtain_iron_ingot").have(IRON, 1);
        g("obtain_gold_ingot").have(GOLD, 1);
        g("obtain_copper_ingot").have(COPPER, 1);
        g("obtain_diamond").have(DIAMOND, 1);
        g("obtain_emerald").have(EMERALD, 1);
        g("obtain_lapis_lazuli").have(LAPIS, 1);
        g("obtain_redstone").have(REDSTONE, 1);
        g("obtain_coal_block").ms(WOOD).use(COAL, 9).craft(2);
        g("obtain_raw_iron_block").ms(STONE).use(IRON, 9).craft(2);
        g("obtain_raw_gold_block").use(GOLD, 9).craft(2);
        g("obtain_obsidian").have(OBSIDIAN, 1);
        g("obtain_crying_obsidian").ms(DIAMOND_PICK).at(RUINED_PORTAL).gather(15, 0.3);
        // A geode: down in the stone, found by luck (or a mountain's calcite).
        g("obtain_amethyst_shard").ms(WOOD).at(CAVE).gather(100, 0.8);
        g("obtain_calcite").ms(WOOD).at(CAVE).gather(110, 0.8);
        g("obtain_tuff").ms(WOOD).at(DEEP).gather(6, 0.3);
        g("obtain_deepslate").ms(WOOD).at(DEEP).gather(4, 0.2);
        g("obtain_moss_block").at(LUSH).gather(5, 0.2);
        g("obtain_dripstone_block").ms(WOOD).at(DRIPSTONE).gather(5, 0.2);
        g("obtain_pointed_dripstone").at(DRIPSTONE).gather(4, 0.2);
        g("obtain_sculk").at(DEEP_DARK).gather(5, 0.2);
        g("obtain_snowball").ms(WOOD).at(SNOWY).gather(4, 0.2);
        // Ice drops only to silk touch: an enchanting table and the luck of the enchantment.
        g("obtain_ice").ms(ENCHANTING_TABLE).use(LAPIS, 6).at(SNOWY).craft(240).noBob();
        g("obtain_packed_ice").ms(ENCHANTING_TABLE).use(LAPIS, 6).at(SNOWY).craft(280).noBob();
        g("obtain_blue_ice").ms(ENCHANTING_TABLE).use(LAPIS, 6).at(SNOWY).craft(320).noBob();
        g("obtain_slime_ball").have(SLIME, 1);
        g("obtain_honey_bottle").ms(FURNACE).use(SAND, 3).gather(220, 0.85);
        g("obtain_honeycomb").ms(SHEARS).gather(65, 0.7);
        // Chests of a dungeon or a mineshaft, or a fishing treasure.
        g("obtain_name_tag").at(CAMP).gather(60, 0.7);
        g("obtain_saddle").ms(STONE).at(MINESHAFT).gather(150, 0.85);
        g("obtain_music_disc").ms(STONE).at(MINESHAFT).gather(320, 0.9).noBob();
        g("obtain_ender_pearl").have(ENDER_PEARL, 1);
        g("obtain_ink_sac").have(INK, 1);
        g("obtain_glow_ink_sac").ms(STONE).at(CAVE).animal(140, 0.85);
        g("obtain_tropical_fish").ms(ROD).at(WATER).animal(150, 0.9).noBob();
        g("obtain_pufferfish").ms(ROD).at(WATER).animal(70, 0.85).noBob();
        g("obtain_turtle_scute").at(OCEAN).animal(700, 0.4).noBob();
        g("craft_lava_bucket").ms(BUCKET).at(LAVA_POOL).gather(4, 0);
        g("craft_water_bucket").ms(WATER_BUCKET).auto(WATER_BUCKET);
        g("craft_golden_apple").ms(WOOD).use(GOLD, 8).gather(60, 0.75);
        g("obtain_trident").ms(STONE).at(OCEAN).monster(900, 30, 0.06).noBob();
        g("obtain_crossbow").ms(WOOD).use(STRING, 2).use(IRON, 2).craft(3);
        g("obtain_shield").ms(SHIELD).auto(SHIELD);
        g("obtain_arrow").ms(WOOD).use(FLINT, 1).use(FEATHER, 1).craft(2);
        g("obtain_spectral_arrow").ms(WOOD).use(FLINT, 1).use(FEATHER, 1).use(GLOWSTONE, 4).craft(3);
        g("obtain_tipped_arrow").ms(STONE).at(SNOWY).monster(220, 10, 0.06);
        g("obtain_16_logs").gather(25, 0.1);
        g("obtain_32_cobblestone").ms(WOOD).gather(28, 0.1);
        g("obtain_16_glass").ms(FURNACE).use(SAND, 16).craft(60);
        g("obtain_16_bricks").ms(FURNACE).use(CLAY, 64).craft(200);
        g("obtain_16_sandstone").ms(WOOD).at(DESERT).gather(25, 0.1);
        g("obtain_16_mud_bricks").ms(FURNACE).use(WHEAT, 16).use(SAND, 3).craft(100);
        g("obtain_16_terracotta").ms(FURNACE).use(CLAY, 64).craft(180);
        g("obtain_16_concrete").ms(WOOD).use(SAND, 8).use(BONE, 1).at(WATER).craft(60);
        g("obtain_16_prismarine").ms(STONE).at(MONUMENT).monster(30, 40, 0.05).heavy();
        g("obtain_16_end_stone").at(END).gather(30, 0).impossible();
        g("obtain_all_log_types_basic").at(JUNGLE).gather(70, 0.5);
        g("obtain_3_flower_types").gather(25, 0.5);
        g("obtain_5_dye_colors").ms(WOOD).gather(120, 0.5);
        // (Three kinds of tree: two of them in other biomes as often as not - leagues 9 and 10: 0 of 5.)
        g("obtain_3_sapling_types").gather(160, 0.7);

        // ---- the Nether ------------------------------------------------------------------------
        g("enter_nether").ms(NETHER_VISITED).auto(NETHER_VISITED);
        // As a block: silk touch only.
        g("obtain_nether_gold_ore").ms(NETHER_VISITED, ENCHANTING_TABLE).use(LAPIS, 6).at(NETHER).craft(240).noBob();
        g("obtain_netherrack").ms(WOOD, NETHER_VISITED).at(NETHER).nether(2, 0);
        g("obtain_quartz").have(QUARTZ, 1);
        g("obtain_glowstone").use(GLOWSTONE, 4).craft(2);
        g("obtain_blaze_rod").have(BLAZE_ROD, 1);
        g("obtain_nether_wart").have(NETHER_WART, 1);
        g("obtain_ghast_tear").ms(BOW, STONE).use(FLINT, 1).use(FEATHER, 1).at(SOUL_VALLEY).monster(110, 20, 0.08).heavy();
        g("obtain_magma_cream").have(MAGMA_CREAM, 1);
        g("obtain_ancient_debris").ms(DIAMOND_PICK, NETHER_VISITED).at(NETHER).nether(420, 0.9);
        g("obtain_glowstone_dust").have(GLOWSTONE, 1);
        g("obtain_shroomlight").ms(NETHER_VISITED).at(CRIMSON).nether(15, 0.3);
        g("obtain_blackstone").ms(WOOD, NETHER_VISITED).at(BASTION).nether(5, 0.1);
        g("obtain_basalt").ms(WOOD, NETHER_VISITED).at(DELTAS).nether(5, 0.1);
        g("obtain_soul_sand").ms(NETHER_VISITED).at(SOUL_VALLEY).nether(3, 0.1);
        g("obtain_soul_soil").ms(NETHER_VISITED).at(SOUL_VALLEY).nether(3, 0.1);
        g("obtain_warped_fungus").ms(NETHER_VISITED).at(WARPED).nether(3, 0.2);
        g("obtain_crimson_fungus").ms(NETHER_VISITED).at(CRIMSON).nether(3, 0.2);
        g("obtain_wither_skeleton_skull").ms(STONE, NETHER_VISITED).at(FORTRESS).monster(600, 30, 0.1).heavy();
        // Any of them: gravel, leather or string from up here does it.
        g("obtain_piglin_barter_item").ms(WOOD).gather(15, 0.3);
        g("kill_blaze").ms(STONE, NETHER_VISITED).at(FORTRESS).monster(40, 15, 0.08).heavy();
        g("kill_wither_skeleton").ms(STONE, NETHER_VISITED).at(FORTRESS).monster(60, 15, 0.1).heavy();
        g("kill_ghast").ms(BOW, NETHER_VISITED).use(FLINT, 1).use(FEATHER, 1).at(SOUL_VALLEY).monster(80, 20, 0.08).heavy();
        g("kill_magma_cube").ms(STONE, NETHER_VISITED).at(DELTAS).monster(40, 10, 0.04);
        g("kill_piglin").ms(STONE, NETHER_VISITED).at(CRIMSON).monster(25, 10, 0.05);
        g("kill_hoglin").ms(STONE, NETHER_VISITED).at(CRIMSON).monster(40, 15, 0.06);
        g("kill_zoglin").ms(STONE, NETHER_VISITED).at(CRIMSON).monster(900, 15, 0.06);
        g("kill_strider").ms(BOW, NETHER_VISITED).use(FLINT, 1).use(FEATHER, 1).at(NETHER).monster(40, 15, 0.03);
        g("visit_nether_wastes").ms(NETHER_VISITED).at(WASTES).explore(1);
        g("visit_crimson_forest").ms(NETHER_VISITED).at(CRIMSON).explore(1);
        g("visit_warped_forest").ms(NETHER_VISITED).at(WARPED).explore(1);
        g("visit_soul_sand_valley").ms(NETHER_VISITED).at(SOUL_VALLEY).explore(1);
        g("visit_basalt_deltas").ms(NETHER_VISITED).at(DELTAS).explore(1);
        g("barter_with_piglin").ms(NETHER_VISITED).use(GOLD, 1).at(CRIMSON).nether(25, 0.4);
        g("wear_gold_in_nether").ms(NETHER_VISITED).use(GOLD, 4).at(NETHER).craft(3);

        // ---- crafting ----------------------------------------------------------------------------
        g("craft_furnace").ms(FURNACE).auto(FURNACE);
        g("craft_blast_furnace").ms(FURNACE).use(IRON, 5).craft(40);
        g("craft_smoker").ms(FURNACE).craft(8);
        g("craft_campfire").ms(WOOD).use(COAL, 1).craft(4);
        g("craft_shield").ms(SHIELD).auto(SHIELD);
        g("craft_bow").ms(BOW).auto(BOW);
        g("craft_crossbow").ms(WOOD).use(STRING, 2).use(IRON, 2).craft(3);
        g("craft_fishing_rod").ms(ROD).auto(ROD);
        g("craft_shears").ms(SHEARS).auto(SHEARS);
        g("craft_bucket").ms(BUCKET).auto(BUCKET);
        g("craft_compass").ms(WOOD).use(IRON, 4).use(REDSTONE, 1).craft(2);
        g("craft_clock").ms(WOOD).use(GOLD, 4).use(REDSTONE, 1).craft(2);
        g("craft_map").ms(WOOD).use(PAPER, 8).use(IRON, 4).use(REDSTONE, 1).craft(4);
        g("craft_iron_pickaxe").ms(IRON_PICK).auto(IRON_PICK);
        g("craft_diamond_pickaxe").ms(DIAMOND_PICK).auto(DIAMOND_PICK);
        g("craft_iron_armor_piece").ms(FURNACE).use(IRON, 4).craft(2).auto(ARMOR);
        g("craft_full_iron_armor").ms(FURNACE).use(IRON, 24).craft(6);
        g("craft_golden_helmet").ms(WOOD).use(GOLD, 5).craft(2);
        g("craft_anvil").ms(FURNACE).use(IRON, 31).craft(4);
        g("craft_enchanting_table").ms(ENCHANTING_TABLE).auto(ENCHANTING_TABLE);
        g("craft_bookshelf").ms(WOOD).use(PAPER, 9).use(LEATHER, 3).craft(5);
        g("craft_brewing_stand").ms(BREWING_STAND).auto(BREWING_STAND);
        g("craft_cauldron").ms(WOOD).use(IRON, 7).craft(2);
        g("craft_piston").ms(STONE).use(IRON, 1).use(REDSTONE, 1).craft(4);
        g("craft_sticky_piston").ms(STONE).use(IRON, 1).use(REDSTONE, 1).use(SLIME, 1).craft(4);
        g("craft_observer").ms(STONE).use(REDSTONE, 2).use(QUARTZ, 1).craft(4);
        g("craft_hopper").ms(WOOD).use(IRON, 5).craft(3);
        g("craft_dispenser").ms(STONE).use(REDSTONE, 1).use(STRING, 3).craft(4);
        g("craft_dropper").ms(STONE).use(REDSTONE, 1).craft(3);
        g("craft_comparator").ms(STONE).use(REDSTONE, 3).use(QUARTZ, 1).craft(4);
        g("craft_repeater").ms(STONE).use(REDSTONE, 3).craft(4);
        g("craft_redstone_torch").ms(WOOD).use(REDSTONE, 1).craft(2);
        g("craft_redstone_lamp").ms(WOOD).use(REDSTONE, 4).use(GLOWSTONE, 4).craft(3);
        g("craft_daylight_detector").ms(FURNACE).use(QUARTZ, 3).use(SAND, 3).craft(15);
        g("craft_target_block").ms(WOOD).use(REDSTONE, 4).use(WHEAT, 9).craft(5);
        g("craft_tnt").ms(WOOD).use(GUNPOWDER, 5).use(SAND, 4).craft(3);
        g("craft_fire_charge").ms(WOOD).use(GUNPOWDER, 1).use(BLAZE_ROD, 1).use(COAL, 1).craft(2);
        g("craft_firework_rocket").ms(WOOD).use(GUNPOWDER, 1).use(PAPER, 1).craft(2);
        g("craft_jukebox").ms(WOOD).use(DIAMOND, 1).craft(3);
        g("craft_lead").ms(WOOD).use(STRING, 4).use(SLIME, 1).craft(2);
        g("craft_note_block").ms(WOOD).use(REDSTONE, 1).craft(3);
        g("craft_painting").ms(WOOD).use(WOOL, 1).craft(3);
        g("craft_item_frame").ms(WOOD).use(LEATHER, 1).craft(3);
        g("craft_flower_pot").ms(FURNACE).use(CLAY, 3).craft(20);
        // (The furnace smelts while the player does something else beside it: Bob's meanwhile.)
        g("craft_bricks").ms(FURNACE).use(CLAY, 4).craft(25);
        g("craft_lightning_rod").ms(WOOD).use(COPPER, 3).craft(2);
        g("craft_copper_bulb").ms(WOOD).use(COPPER, 27).use(BLAZE_ROD, 1).use(REDSTONE, 1).craft(5);
        g("craft_spyglass").ms(STONE).use(COPPER, 2).at(CAVE).gather(100, 0.8);
        g("craft_cake").ms(BUCKET).use(WHEAT, 3).use(PAPER, 2).gather(40, 0.5).noBob();
        g("craft_cookie").ms(WOOD).use(WHEAT, 2).at(JUNGLE).gather(10, 0.3);
        g("craft_pumpkin_pie").ms(WOOD).use(PAPER, 1).gather(120, 0.7).noBob();

        // ---- doing things ------------------------------------------------------------------------
        g("block_damage_with_shield").ms(SHIELD).monster(35, 5, 0.03).chance(0.12, SHIELD);
        g("hit_target_block").ms(BOW).use(REDSTONE, 4).use(WHEAT, 9).use(FLINT, 1).use(FEATHER, 1).craft(10);
        g("ignite_tnt").ms(FLINT_STEEL).use(GUNPOWDER, 5).use(SAND, 4).craft(5);
        g("place_tnt").ms(WOOD).use(GUNPOWDER, 5).use(SAND, 4).craft(4);
        g("obtain_firework_crossbow").ms(WOOD).use(STRING, 2).use(IRON, 2).use(GUNPOWDER, 1).use(PAPER, 1).craft(6);
        g("shoot_crossbow").ms(WOOD).use(STRING, 2).use(IRON, 2).use(FLINT, 1).use(FEATHER, 1).craft(6);
        g("splash_potion").ms(BREWING_STAND, FURNACE).use(SAND, 3).use(GUNPOWDER, 1).use(BLAZE_ROD, 1).craft(60);
        g("throw_ender_pearl").use(ENDER_PEARL, 1).craft(2);
        g("throw_trident").ms(STONE).at(OCEAN).monster(900, 30, 0.06).noBob();
        g("use_totem").at(MANSION).monster(300, 60, 0.2).heavy().noBob();
        g("drink_potion").ms(FURNACE).use(SAND, 3).at(WATER).craft(15);
        g("advancement_monster_hunter").ms(STONE).monster(25, 6, 0.03).chance(0.15);
        g("advancement_take_aim").ms(BOW).use(FLINT, 1).use(FEATHER, 1).craft(15);
        g("advancement_sniper_duel").ms(BOW, STONE).use(FLINT, 2).use(FEATHER, 2).monster(200, 20, 0.06).noBob();
        g("advancement_return_to_sender").ms(STONE, NETHER_VISITED).at(SOUL_VALLEY).monster(200, 20, 0.1).heavy();
        g("advancement_best_friends_forever").use(BONE, 2).animal(45, 0.6);
        g("advancement_fishy_business").ms(ROD).at(WATER).animal(25, 0.6);
        g("advancement_tactical_fishing").ms(BUCKET).at(WATER).animal(40, 0.6);
        g("advancement_local_brewery").ms(BREWING_STAND, FURNACE).use(SAND, 3).use(NETHER_WART, 1).use(BLAZE_ROD, 1).craft(30);
        g("advancement_enchanter").ms(ENCHANTING_TABLE).use(LAPIS, 3).craft(60);
        g("advancement_into_fire").have(BLAZE_ROD, 1);
        g("advancement_diamonds").have(DIAMOND, 1);
        g("advancement_suit_up").ms(FURNACE).use(IRON, 4).craft(2).auto(ARMOR);
        g("advancement_stone_age").ms(STONE).auto(STONE);
        g("advancement_acquire_hardware").have(IRON, 1);
        g("advancement_we_need_to_go_deeper").ms(NETHER_VISITED).auto(NETHER_VISITED);

        // ---- places ---------------------------------------------------------------------------------
        g("visit_badlands").at(BADLANDS).explore(1);
        g("visit_bamboo_jungle").at(BAMBOO).explore(1);
        g("visit_cherry_grove").at(CHERRY).explore(1);
        g("visit_dappled_forest").at(DAPPLED).explore(1);
        g("visit_dark_forest").at(DARK_FOREST).explore(1);
        g("visit_deep_dark").ms(WOOD).at(DEEP_DARK).explore(1);
        g("visit_desert").at(DESERT).explore(1);
        g("visit_dripstone_caves").ms(WOOD).at(DRIPSTONE).explore(1);
        g("visit_jungle").at(JUNGLE).explore(1);
        g("visit_lush_caves").ms(WOOD).at(LUSH).explore(1);
        g("visit_mangrove_swamp").at(MANGROVE).explore(1);
        g("visit_mushroom_fields").at(MUSHROOM).explore(1);
        g("visit_ocean").at(OCEAN).explore(1);
        g("visit_savanna").at(SAVANNA).explore(1);
        g("visit_snowy_biome").at(SNOWY).explore(1);
        g("visit_swamp").at(SWAMP).explore(1);
        g("visit_warm_ocean").at(WARM_OCEAN).explore(1);
        g("visit_abandoned_camp").at(CAMP).explore(2);
        g("visit_ancient_city").ms(WOOD).at(ANCIENT_CITY).explore(3).hazard(0.05);
        g("visit_bastion_remnant").ms(NETHER_VISITED).at(BASTION).explore(2).hazard(0.03);
        g("visit_desert_pyramid").at(DESERT_PYRAMID).explore(2);
        g("visit_end_city").at(END).explore(1).impossible();
        g("visit_jungle_temple").at(JUNGLE_TEMPLE).explore(2);
        g("visit_mineshaft").ms(WOOD).at(MINESHAFT).explore(2);
        g("visit_nether_fortress").ms(NETHER_VISITED).at(FORTRESS).explore(2).hazard(0.03);
        g("visit_ocean_monument").at(MONUMENT).explore(3);
        g("visit_ocean_ruin").at(OCEAN_RUIN).explore(3);
        g("visit_pillager_outpost").at(OUTPOST).explore(2).hazard(0.04);
        g("visit_ruined_portal").at(RUINED_PORTAL).explore(1);
        g("visit_shipwreck").at(SHIPWRECK).explore(2);
        g("visit_stronghold").at(STRONGHOLD).explore(60).noBob();
        g("visit_trial_chambers").ms(WOOD).at(TRIAL_CHAMBERS).explore(3).hazard(0.04);
        g("visit_village").at(VILLAGE).explore(1);
        g("visit_woodland_mansion").at(MANSION).explore(2).noBob();

        // ---- eating and drinking ------------------------------------------------------------------
        g("eat_apple").gather(60, 0.8);
        g("eat_beetroot_soup").ms(WOOD).at(VILLAGE).gather(20, 0.4);
        g("eat_bread").ms(WOOD).use(WHEAT, 3).craft(3);
        g("eat_cake_slice").ms(BUCKET).use(WHEAT, 3).use(PAPER, 2).gather(45, 0.5).noBob();
        g("eat_cooked_beef").ms(FURNACE).animal(25, 0.5);
        g("eat_cooked_chicken").ms(FURNACE).animal(25, 0.5);
        g("eat_cooked_cod").ms(FURNACE).at(WATER).animal(40, 0.6);
        g("eat_cooked_mutton").ms(FURNACE).animal(25, 0.5);
        g("eat_cooked_porkchop").ms(FURNACE).animal(25, 0.5);
        g("eat_cooked_salmon").ms(FURNACE).at(WATER).animal(45, 0.6);
        g("eat_cookie").ms(WOOD).use(WHEAT, 2).at(JUNGLE).gather(12, 0.3);
        g("eat_dried_kelp").ms(FURNACE).at(OCEAN).gather(20, 0.2);
        g("eat_glow_berries").at(LUSH).gather(5, 0.3);
        g("eat_golden_apple").ms(WOOD).use(GOLD, 8).gather(60, 0.75);
        g("eat_honey_bottle").ms(FURNACE).use(SAND, 3).gather(225, 0.85);
        g("eat_pumpkin_pie").ms(WOOD).use(PAPER, 1).gather(125, 0.7).noBob();
        g("eat_rabbit_stew").ms(FURNACE).at(VILLAGE).animal(150, 0.6);
        g("eat_suspicious_stew").ms(WOOD).gather(140, 0.75);

        // ---- the body: falls, fire, effects, heights ------------------------------------------------
        g("burn_and_survive").ms(WOOD).use(COAL, 1).stunt(6, 0);
        g("fall_20_blocks_and_survive").ms(STONE).stunt(45, 0).hazard(0.02);
        g("freeze_in_powder_snow").at(SNOWY).stunt(20, 0.5);
        g("get_shot_by_skeleton").monster(30, 2, 0.04).chance(0.12);
        g("survive_explosion").ms(STONE).monster(35, 5, 0.05);
        g("take_fall_damage").stunt(8, 0);
        g("have_10_hearts_missing").ms(STONE).stunt(90, 0).hazard(0.03);
        g("get_levitation").at(END).stunt(10, 0).impossible();
        g("get_poisoned").monster(70, 5, 0.03);
        g("get_withered").ms(STONE, NETHER_VISITED).at(FORTRESS).monster(60, 5, 0.12).heavy();
        // (Fifty levels of staircase below where the deepslate begins.)
        g("reach_y_minus_50").ms(WOOD).at(DEEP).stunt(90, 0);
        g("stand_on_bedrock").ms(STONE).at(DEEP).stunt(50, 0);
        g("reach_build_limit").ms(WOOD).stunt(220, 0);
        g("enter_end_gateway").at(END).stunt(10, 0).impossible();

        // ---- brewing and enchanting ------------------------------------------------------------------
        g("brew_any_potion").ms(BREWING_STAND, FURNACE).use(SAND, 3).use(NETHER_WART, 1).use(BLAZE_ROD, 1).craft(45);
        g("brew_fire_resistance").ms(BREWING_STAND, FURNACE).use(SAND, 3).use(NETHER_WART, 1).use(BLAZE_ROD, 1).use(MAGMA_CREAM, 1).craft(60);
        g("brew_night_vision").ms(BREWING_STAND, FURNACE).use(SAND, 3).use(NETHER_WART, 1).use(BLAZE_ROD, 1).use(GOLD, 1).at(VILLAGE).craft(75);
        g("brew_speed_potion").ms(BREWING_STAND, FURNACE).use(SAND, 3).use(NETHER_WART, 1).use(BLAZE_ROD, 1).use(PAPER, 1).craft(60);
        g("brew_strength_potion").ms(BREWING_STAND, FURNACE).use(SAND, 3).use(NETHER_WART, 1).use(BLAZE_ROD, 2).craft(60);
        g("enchant_item").ms(ENCHANTING_TABLE).use(LAPIS, 3).craft(60);
        g("enchant_pickaxe").ms(ENCHANTING_TABLE).use(LAPIS, 3).craft(60);
        g("enchant_sword").ms(ENCHANTING_TABLE).use(LAPIS, 3).craft(60);

        // ---- armour worn ------------------------------------------------------------------------------
        g("wear_diamond_piece").ms(WOOD).use(DIAMOND, 4).craft(2);
        g("wear_full_gold").ms(WOOD).use(GOLD, 24).craft(6);
        g("wear_full_iron").ms(FURNACE).use(IRON, 24).craft(6);
        g("wear_full_leather").ms(WOOD).use(LEATHER, 24).craft(6);

        // ---- fishing, animals, blocks used ---------------------------------------------------------
        g("catch_fish").ms(ROD).at(WATER).animal(25, 0.6);
        g("activate_pressure_plate").ms(FURNACE).craft(15);
        g("breed_animals").animal(40, 0.5);
        g("breed_chickens").animal(45, 0.5);
        g("breed_cows").use(WHEAT, 2).animal(25, 0.5);
        g("breed_pigs").at(VILLAGE).animal(35, 0.5);
        g("breed_sheep").use(WHEAT, 2).animal(25, 0.5);
        g("light_campfire").ms(FLINT_STEEL).use(COAL, 1).craft(8);
        g("milk_cow").ms(BUCKET).animal(20, 0.5);
        g("ride_horse").animal(40, 0.6);
        g("ride_pig").ms(STONE).at(MINESHAFT).gather(170, 0.85);
        g("ride_strider").ms(STONE, NETHER_VISITED).at(MINESHAFT).gather(200, 0.85);
        g("ring_bell").at(VILLAGE).craft(3);
        g("set_spawn").ms(BED).craft(5);
        g("shear_sheep").ms(SHEARS).animal(15, 0.4);
        g("sleep_in_bed").ms(BED).craft(10).night();
        g("sleep_in_village_bed").at(VILLAGE).craft(20).night();
        g("tame_cat").at(VILLAGE).animal(80, 0.6);
        g("tame_horse").animal(60, 0.6);
        g("tame_wolf").use(BONE, 2).animal(40, 0.6);
        g("use_cartography_table").ms(WOOD).use(PAPER, 2).craft(5);
        g("use_grindstone").ms(STONE).craft(8);
        g("use_lectern").ms(WOOD).use(PAPER, 9).use(LEATHER, 3).craft(8);
        g("use_loom").ms(WOOD).use(STRING, 2).craft(5);
        g("use_smithing_table").ms(WOOD).use(IRON, 2).craft(5);
        g("use_stonecutter").ms(FURNACE).use(IRON, 1).craft(15);

        // ---- monsters of the Overworld ---------------------------------------------------------------
        g("kill_breeze").ms(STONE).at(TRIAL_CHAMBERS).monster(60, 20, 0.08).heavy().noBob();
        g("kill_cave_spider").ms(STONE).at(MINESHAFT).monster(40, 8, 0.05);
        g("kill_creeper").ms(STONE).monster(35, 6, 0.04).chance(0.05);
        g("kill_drowned").ms(STONE).at(WATER).monster(60, 10, 0.05);
        g("kill_elder_guardian").ms(STONE).at(MONUMENT).monster(120, 60, 0.15).heavy().noBob();
        g("kill_enderman").ms(STONE).monster(80, 20, 0.12).heavy();
        g("kill_guardian").ms(STONE).at(MONUMENT).monster(20, 20, 0.08).heavy().noBob();
        g("kill_husk").ms(STONE).at(DESERT).monster(30, 6, 0.03);
        g("kill_phantom").ms(STONE).monster(30, 15, 0.04).notBefore(3600).noBob();
        g("kill_pillager").ms(STONE).at(OUTPOST).monster(20, 15, 0.08);
        g("kill_ravager").ms(STONE).at(OUTPOST).monster(1500, 30, 0.15).heavy().noBob();
        g("kill_silverfish").ms(STONE).at(MOUNTAIN).monster(200, 5, 0.01);
        g("kill_skeleton").ms(STONE).monster(30, 10, 0.06).chance(0.08);
        g("kill_slime").ms(STONE).at(SWAMP).monster(150, 10, 0.02);
        g("kill_spider").ms(STONE).monster(30, 6, 0.03).chance(0.08);
        g("kill_stray").ms(STONE).at(SNOWY).monster(30, 10, 0.06);
        g("kill_witch").ms(STONE).at(WITCH_HUT).monster(200, 15, 0.08).heavy();
        g("kill_zombie").ms(STONE).monster(25, 5, 0.03).chance(0.10);
        // Off the board: the End and the deep dark's warden.
        g("kill_shulker").at(END).monster(60, 30, 0.1).impossible();
        g("kill_warden").at(ANCIENT_CITY).monster(60, 120, 0.5).impossible();
        g("kill_ender_dragon").at(END).monster(60, 300, 0.2).impossible();
        g("enter_end").at(END).explore(1).impossible();
        g("obtain_dragon_breath").at(END).explore(1).impossible();
        g("obtain_shulker_shell").at(END).explore(1).impossible();
        g("obtain_elytra").at(END).explore(1).impossible();

        // ---- villagers ------------------------------------------------------------------------------------
        g("buy_bell").ms(WOOD).at(VILLAGE).craft(5);
        g("buy_arrows").use(EMERALD, 1).at(VILLAGE).trade(15, 0.4);
        g("buy_bread").use(EMERALD, 1).at(VILLAGE).trade(10, 0.3);
        g("buy_lapis").use(EMERALD, 3).at(VILLAGE).trade(200, 0.5);
        g("obtain_emerald_by_trade").use(WHEAT, 20).at(VILLAGE).trade(20, 0.3);
        // (Any offer: the fletcher's sticks, the farmer's wheat.)
        g("trade_with_villager").at(VILLAGE).trade(30, 0.3);
        g("trade_with_armorer").use(COAL, 15).at(VILLAGE).trade(45, 0.5);
        g("trade_with_cleric").at(VILLAGE).trade(140, 0.6);
        g("trade_with_farmer").use(WHEAT, 20).at(VILLAGE).trade(25, 0.3);
        g("trade_with_fletcher").ms(WOOD).at(VILLAGE).trade(35, 0.5);
        g("trade_with_librarian").use(PAPER, 24).at(VILLAGE).trade(40, 0.5);
        g("trade_with_toolsmith").use(COAL, 15).at(VILLAGE).trade(45, 0.5);
        measured();
    }
}
