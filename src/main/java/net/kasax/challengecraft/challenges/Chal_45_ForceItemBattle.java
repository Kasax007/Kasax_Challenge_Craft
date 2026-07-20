package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.network.ForceItemOpenScreenPacket;
import net.minecraft.util.ActionResult;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.ForceItemBattleSavedData;
import net.kasax.challengecraft.network.ForceItemResultsPacket;
import net.kasax.challengecraft.network.ForceItemSyncPacket;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Force Item Battle (BastiGHG concept): every player gets a personal random item to obtain;
 * collecting it scores a point and assigns the next one. Most items when the timer runs out
 * wins; the winning team/players get a flat 100 lifetime XP via the results ceremony.
 *
 * Teams reuse {@link LockoutBingoTeam}; joining is optional — players without a team compete
 * solo. Jokers (5 per player, like the original) hand the player their current target item.
 */
public class Chal_45_ForceItemBattle {
    private static boolean active = false;
    private static int tickCounter = 0;
    private static List<Item> itemPool = List.of();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter++;
            if (tickCounter % 20 != 0) return; // once per second, like the All Items scan

            ServerWorld overworld = server.getOverworld();
            ForceItemBattleSavedData data = ForceItemBattleSavedData.get(overworld);

            // Tracker item is the command-free entry point — hand it out in every state so
            // lobby players (and late joiners) can always open the game screen.
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!player.isSpectator()) ensureTracker(player);
            }

            if (data.getState() != ForceItemBattleSavedData.STATE_RUNNING) return;

            if (data.getEndGameTime() >= 0 && overworld.getTime() >= data.getEndGameTime()) {
                endBattle(server, data);
                return;
            }

            boolean changed = false;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSpectator()) continue;
                data.ensurePlayer(player.getUuid(), player.getName().getString());

                String itemId = data.getCurrentItem(player.getUuid());
                if (itemId.isEmpty()) {
                    assignNextItem(server, data, player.getUuid());
                    changed = true;
                    continue;
                }

                Item target = Registries.ITEM.get(Identifier.of(itemId));
                if (target != Items.AIR && hasItem(player, target)) {
                    data.addScore(player.getUuid());
                    data.addCollectedItem(player.getUuid(), itemId);
                    int score = data.getScore(player.getUuid());
                    assignNextItem(server, data, player.getUuid());
                    changed = true;

                    server.getPlayerManager().broadcast(
                            Text.translatable("challengecraft.fib.collected",
                                    player.getName(), target.getName(), score).formatted(Formatting.GRAY),
                            false);
                    player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                }
            }

            // Push at least every 5s so client timers cannot drift far, immediately on changes.
            if (changed || tickCounter % 100 == 0) {
                syncBattleToAll(server);
            }
        });

        // Right-clicking the tracker item opens the game screen — the command-free entry point.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!active) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isOf(net.kasax.challengecraft.item.ModItems.FORCE_ITEM_TRACKER)) {
                return ActionResult.PASS;
            }
            if (world.isClient()) return ActionResult.SUCCESS;
            if (player instanceof ServerPlayerEntity serverPlayer) {
                ServerPlayNetworking.send(serverPlayer, new ForceItemOpenScreenPacket());
                return ActionResult.SUCCESS_SERVER;
            }
            return ActionResult.PASS;
        });
    }

    /**
     * The pool of items Force Item Battle can assign as targets. <b>Edit this list</b> to control
     * exactly which items appear — add or remove {@code "minecraft:<id>"} entries freely. Unknown
     * ids are skipped with a warning, so a typo won't crash the game.
     */
    public static final List<String> FORCE_ITEM_IDS = List.of(
            // --- Wood & basic building ---
            "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log", "minecraft:jungle_log",
            "minecraft:acacia_log", "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:cherry_log",
            "minecraft:oak_planks", "minecraft:crafting_table", "minecraft:chest", "minecraft:furnace",
            "minecraft:cobblestone", "minecraft:stone", "minecraft:stone_bricks", "minecraft:smooth_stone",
            "minecraft:glass", "minecraft:sand", "minecraft:gravel", "minecraft:dirt", "minecraft:torch",
            "minecraft:ladder", "minecraft:scaffolding", "minecraft:bookshelf",
            // --- Ores, ingots, gems ---
            "minecraft:coal", "minecraft:charcoal", "minecraft:raw_iron", "minecraft:iron_ingot",
            "minecraft:raw_copper", "minecraft:copper_ingot", "minecraft:raw_gold", "minecraft:gold_ingot",
            "minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:diamond", "minecraft:emerald",
            "minecraft:quartz", "minecraft:amethyst_shard", "minecraft:netherite_ingot", "minecraft:iron_block",
            // --- Tools & combat ---
            "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "minecraft:iron_pickaxe",
            "minecraft:diamond_pickaxe", "minecraft:iron_axe", "minecraft:iron_shovel", "minecraft:iron_hoe",
            "minecraft:iron_sword", "minecraft:diamond_sword", "minecraft:bow", "minecraft:arrow",
            "minecraft:crossbow", "minecraft:shield", "minecraft:flint_and_steel", "minecraft:shears",
            "minecraft:fishing_rod", "minecraft:bucket", "minecraft:water_bucket", "minecraft:lava_bucket",
            "minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots",
            // --- Food & farming ---
            "minecraft:wheat", "minecraft:bread", "minecraft:apple", "minecraft:carrot", "minecraft:potato",
            "minecraft:baked_potato", "minecraft:beetroot", "minecraft:melon_slice", "minecraft:pumpkin",
            "minecraft:sugar_cane", "minecraft:sugar", "minecraft:cookie", "minecraft:cake", "minecraft:egg",
            "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken", "minecraft:cooked_mutton",
            "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:golden_apple", "minecraft:honey_bottle",
            // --- Mob drops & misc materials ---
            "minecraft:leather", "minecraft:feather", "minecraft:string", "minecraft:bone", "minecraft:bone_meal",
            "minecraft:gunpowder", "minecraft:spider_eye", "minecraft:rotten_flesh", "minecraft:ender_pearl",
            "minecraft:slime_ball", "minecraft:blaze_rod", "minecraft:blaze_powder", "minecraft:ghast_tear",
            "minecraft:magma_cream", "minecraft:nether_wart", "minecraft:glowstone_dust", "minecraft:ink_sac",
            "minecraft:glow_ink_sac", "minecraft:honeycomb", "minecraft:phantom_membrane", "minecraft:rabbit_hide",
            // --- Redstone & utility ---
            "minecraft:redstone_torch", "minecraft:repeater", "minecraft:comparator", "minecraft:piston",
            "minecraft:sticky_piston", "minecraft:observer", "minecraft:hopper", "minecraft:dropper",
            "minecraft:dispenser", "minecraft:lever", "minecraft:stone_button", "minecraft:tripwire_hook",
            "minecraft:clock", "minecraft:compass", "minecraft:map", "minecraft:name_tag", "minecraft:lead",
            // --- Wool & decoration ---
            "minecraft:white_wool", "minecraft:red_wool", "minecraft:blue_wool", "minecraft:black_wool",
            "minecraft:white_bed", "minecraft:painting", "minecraft:item_frame", "minecraft:flower_pot",
            "minecraft:oak_sign", "minecraft:oak_boat", "minecraft:minecart", "minecraft:rail",
            // --- End & nether ---
            "minecraft:obsidian", "minecraft:netherrack", "minecraft:soul_sand", "minecraft:glowstone",
            "minecraft:end_stone", "minecraft:chorus_fruit", "minecraft:shulker_shell", "minecraft:ender_eye",

            // --- More logs & wood ---
            "minecraft:bamboo_block", "minecraft:stripped_oak_log", "minecraft:stripped_birch_log",
            "minecraft:stripped_spruce_log", "minecraft:stripped_jungle_log",
            "minecraft:stripped_acacia_log", "minecraft:stripped_dark_oak_log",
            "minecraft:stripped_mangrove_log", "minecraft:stripped_cherry_log",
            "minecraft:bamboo_planks", "minecraft:crimson_stem", "minecraft:warped_stem",
            "minecraft:crimson_planks", "minecraft:warped_planks",

// --- Stone variants ---
            "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
            "minecraft:polished_andesite", "minecraft:polished_diorite",
            "minecraft:polished_granite", "minecraft:deepslate",
            "minecraft:cobbled_deepslate", "minecraft:polished_deepslate",
            "minecraft:deepslate_bricks", "minecraft:deepslate_tiles",
            "minecraft:blackstone", "minecraft:polished_blackstone",
            "minecraft:polished_blackstone_bricks", "minecraft:basalt",
            "minecraft:smooth_basalt", "minecraft:tuff", "minecraft:calcite",
            "minecraft:dripstone_block", "minecraft:pointed_dripstone",

// --- Ore blocks ---
            "minecraft:coal_ore", "minecraft:iron_ore", "minecraft:copper_ore",
            "minecraft:gold_ore", "minecraft:redstone_ore",
            "minecraft:lapis_ore", "minecraft:diamond_ore",
            "minecraft:emerald_ore", "minecraft:deepslate_coal_ore",
            "minecraft:deepslate_iron_ore", "minecraft:deepslate_gold_ore",
            "minecraft:deepslate_redstone_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:deepslate_diamond_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore",
            "minecraft:ancient_debris",

// --- Resource blocks ---
            "minecraft:coal_block", "minecraft:copper_block",
            "minecraft:gold_block", "minecraft:diamond_block",
            "minecraft:emerald_block", "minecraft:redstone_block",
            "minecraft:lapis_block", "minecraft:netherite_block",
            "minecraft:amethyst_block",

// --- More tools ---
            "minecraft:wooden_axe", "minecraft:wooden_shovel",
            "minecraft:wooden_hoe", "minecraft:stone_axe",
            "minecraft:stone_shovel", "minecraft:stone_hoe",
            "minecraft:golden_pickaxe", "minecraft:golden_axe",
            "minecraft:golden_shovel", "minecraft:golden_hoe",
            "minecraft:golden_sword", "minecraft:diamond_axe",
            "minecraft:diamond_shovel", "minecraft:diamond_hoe",
            "minecraft:netherite_pickaxe", "minecraft:netherite_axe",
            "minecraft:netherite_shovel", "minecraft:netherite_hoe",
            "minecraft:netherite_sword",

// --- Armor ---
            "minecraft:leather_helmet", "minecraft:leather_chestplate",
            "minecraft:leather_leggings", "minecraft:leather_boots",
            "minecraft:chainmail_helmet", "minecraft:chainmail_chestplate",
            "minecraft:chainmail_leggings", "minecraft:chainmail_boots",
            "minecraft:golden_helmet", "minecraft:golden_chestplate",
            "minecraft:golden_leggings", "minecraft:golden_boots",
            "minecraft:diamond_helmet", "minecraft:diamond_chestplate",
            "minecraft:diamond_leggings", "minecraft:diamond_boots",
            "minecraft:netherite_helmet", "minecraft:netherite_chestplate",
            "minecraft:netherite_leggings", "minecraft:netherite_boots",
            "minecraft:turtle_helmet",

// --- Farming ---
            "minecraft:wheat_seeds", "minecraft:beetroot_seeds",
            "minecraft:pumpkin_seeds", "minecraft:melon_seeds",
            "minecraft:cocoa_beans", "minecraft:kelp",
            "minecraft:dried_kelp", "minecraft:bamboo",
            "minecraft:sweet_berries", "minecraft:glow_berries",

// --- More food ---
            "minecraft:beef", "minecraft:porkchop",
            "minecraft:chicken", "minecraft:mutton",
            "minecraft:rabbit", "minecraft:cod",
            "minecraft:salmon", "minecraft:tropical_fish",
            "minecraft:pufferfish", "minecraft:poisonous_potato",
            "minecraft:golden_carrot", "minecraft:pumpkin_pie",
            "minecraft:rabbit_stew", "minecraft:mushroom_stew",
            "minecraft:suspicious_stew",

// --- Mob drops ---
            "minecraft:prismarine_shard", "minecraft:prismarine_crystals",
            "minecraft:nautilus_shell", "minecraft:heart_of_the_sea",
            "minecraft:echo_shard", "minecraft:scute",
            "minecraft:turtle_egg", "minecraft:goat_horn",
            "minecraft:totem_of_undying", "minecraft:dragon_breath",
            "minecraft:dragon_egg", "minecraft:sniffer_egg",

// --- Potions & brewing ---
            "minecraft:glass_bottle", "minecraft:potion",
            "minecraft:splash_potion", "minecraft:lingering_potion",
            "minecraft:fermented_spider_eye", "minecraft:glistering_melon_slice",
            "minecraft:golden_carrot", "minecraft:ghast_tear",
            "minecraft:rabbit_foot", "minecraft:dragon_breath",
            "minecraft:brewing_stand", "minecraft:cauldron",

// --- Utility ---
            "minecraft:anvil", "minecraft:chipped_anvil",
            "minecraft:damaged_anvil", "minecraft:grindstone",
            "minecraft:smithing_table", "minecraft:cartography_table",
            "minecraft:stonecutter", "minecraft:loom",
            "minecraft:barrel", "minecraft:blast_furnace",
            "minecraft:smoker", "minecraft:campfire",
            "minecraft:soul_campfire", "minecraft:respawn_anchor",
            "minecraft:lodestone",

// --- Transportation ---
            "minecraft:powered_rail", "minecraft:detector_rail",
            "minecraft:activator_rail", "minecraft:hopper_minecart",
            "minecraft:chest_minecart", "minecraft:furnace_minecart",
            "minecraft:tnt_minecart", "minecraft:oak_chest_boat",
            "minecraft:bamboo_raft", "minecraft:bamboo_chest_raft",

// --- Redstone advanced ---
            "minecraft:daylight_detector", "minecraft:target",
            "minecraft:redstone_lamp", "minecraft:note_block",
            "minecraft:jukebox", "minecraft:tnt",
            "minecraft:lightning_rod", "minecraft:lectern",

// --- Nether ---
            "minecraft:crimson_fungus", "minecraft:warped_fungus",
            "minecraft:warped_roots", "minecraft:crimson_roots",
            "minecraft:nether_bricks", "minecraft:red_nether_bricks",
            "minecraft:nether_brick_fence", "minecraft:soul_soil",
            "minecraft:blackstone", "minecraft:crying_obsidian",

// --- End ---
            "minecraft:purpur_block", "minecraft:purpur_pillar",
            "minecraft:purpur_stairs", "minecraft:end_rod",

// --- Rare loot ---
            "minecraft:enchanted_golden_apple",
            "minecraft:music_disc_13", "minecraft:music_disc_cat",
            "minecraft:music_disc_blocks", "minecraft:music_disc_chirp",
            "minecraft:music_disc_far", "minecraft:music_disc_mall",
            "minecraft:music_disc_mellohi", "minecraft:music_disc_stal",
            "minecraft:music_disc_strad", "minecraft:music_disc_wait",
            "minecraft:music_disc_ward", "minecraft:music_disc_otherside",
            "minecraft:music_disc_relic",

// --- Misc ---
            "minecraft:spyglass", "minecraft:brush",
            "minecraft:bundle", "minecraft:wind_charge",
            "minecraft:fire_charge", "minecraft:snowball",
            "minecraft:egg", "minecraft:ender_chest",
            "minecraft:beacon", "minecraft:conduit",
            "minecraft:bell", "minecraft:lantern",
            "minecraft:soul_lantern", "minecraft:chain"
    );

    private static void buildPoolIfNeeded() {
        if (!itemPool.isEmpty()) return;
        List<Item> pool = new ArrayList<>();
        for (String id : FORCE_ITEM_IDS) {
            Item item = Registries.ITEM.get(Identifier.of(id));
            if (item == Items.AIR) {
                ChallengeCraft.LOGGER.warn("[Chal45] unknown item id in FORCE_ITEM_IDS, skipping: {}", id);
                continue;
            }
            if (!pool.contains(item)) pool.add(item);
        }
        itemPool = pool;
        ChallengeCraft.LOGGER.info("[Chal45] item pool built from FORCE_ITEM_IDS ({} items)", itemPool.size());
    }

    private static boolean hasItem(ServerPlayerEntity player, Item target) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(target)) return true;
        }
        return false;
    }

    private static void assignNextItem(MinecraftServer server, ForceItemBattleSavedData data, UUID uuid) {
        buildPoolIfNeeded();
        if (itemPool.isEmpty()) return;
        String previous = data.getCurrentItem(uuid);
        String next = previous;
        for (int attempt = 0; attempt < 5 && next.equals(previous); attempt++) {
            Item candidate = itemPool.get(server.getOverworld().random.nextInt(itemPool.size()));
            next = Registries.ITEM.getId(candidate).toString();
        }
        data.setCurrentItem(uuid, next);
    }

    /**
     * Activation only opens the lobby — players register and receive the tracker item, but the
     * timer does NOT start (world creation must never auto-start the battle). Starting happens
     * explicitly via {@link #startBattle} (game-screen button, or the solo debug command).
     */
    public static void onActivated(ServerWorld world) {
        MinecraftServer server = world.getServer();
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) continue;
            data.ensurePlayer(player.getUuid(), player.getName().getString());
        }
        syncBattleToAll(server);
    }

    /**
     * Starts the battle from the lobby. Normal starts need at least two non-spectator players
     * (anti XP-farming); {@code debug} bypasses that for solo testing and marks the run as
     * ineligible for the XP prize.
     */
    public static void startBattle(MinecraftServer server, ServerPlayerEntity initiator, boolean debug) {
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        if (!active || data.getState() != ForceItemBattleSavedData.STATE_IDLE) {
            if (initiator != null) {
                initiator.sendMessage(Text.translatable("challengecraft.fib.already_running").formatted(Formatting.RED), false);
            }
            return;
        }

        long participants = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> !p.isSpectator()).count();
        if (!debug && participants < 2) {
            if (initiator != null) {
                initiator.sendMessage(Text.translatable("challengecraft.fib.need_two_players").formatted(Formatting.RED), false);
            }
            return;
        }

        data.setDebugRun(debug);
        int minutes = ChallengeSavedData.get(server.getOverworld()).getForceItemBattleMinutes();
        data.setState(ForceItemBattleSavedData.STATE_RUNNING);
        data.setEndGameTime(server.getOverworld().getTime() + minutes * 60L * 20L);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) continue;
            data.ensurePlayer(player.getUuid(), player.getName().getString());
            assignNextItem(server, data, player.getUuid());
        }
        server.getPlayerManager().broadcast(
                Text.translatable("challengecraft.fib.started", minutes).formatted(Formatting.GOLD, Formatting.BOLD),
                false);
        ChallengeCraft.LOGGER.info("[Chal45] battle started ({} min, debug={})", minutes, debug);
        syncBattleToAll(server);
    }

    private static void endBattle(MinecraftServer server, ForceItemBattleSavedData data) {
        data.setState(ForceItemBattleSavedData.STATE_ENDED);
        data.setEndGameTime(-1L);

        ServerWorld overworld = server.getOverworld();
        BlockPos spawn = overworld.getSpawnPos();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.teleport(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    Set.<PositionFlag>of(), player.getYaw(), player.getPitch(), false);
            player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        Text title = Text.translatable("challengecraft.fib.time_up").formatted(Formatting.GOLD, Formatting.BOLD);
        Text subtitle = Text.translatable("challengecraft.fib.await_results").formatted(Formatting.GRAY);
        server.getPlayerManager().sendToAll(new TitleFadeS2CPacket(10, 70, 20));
        server.getPlayerManager().sendToAll(new TitleS2CPacket(title));
        server.getPlayerManager().sendToAll(new SubtitleS2CPacket(subtitle));

        syncBattleToAll(server);
        ChallengeCraft.LOGGER.info("[Chal45] battle ended, awaiting results command");
    }

    /**
     * Joker: hands the player their current target item AND immediately scores + advances.
     * Scoring must not wait for the once-per-second inventory scan — that let rapid clicks
     * burn several jokers on the same target before the scan caught up.
     */
    public static void useJoker(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        if (!active || data.getState() != ForceItemBattleSavedData.STATE_RUNNING) {
            player.sendMessage(Text.translatable("challengecraft.fib.not_running").formatted(Formatting.RED), false);
            return;
        }
        int left = data.getJokersLeft(player.getUuid());
        if (left <= 0) {
            player.sendMessage(Text.translatable("challengecraft.fib.no_jokers").formatted(Formatting.RED), false);
            return;
        }
        String itemId = data.getCurrentItem(player.getUuid());
        Item target = Registries.ITEM.get(Identifier.of(itemId));
        if (target == Items.AIR) return;

        data.setJokersLeft(player.getUuid(), left - 1);
        player.giveItemStack(new ItemStack(target));
        data.addScore(player.getUuid());
        data.addCollectedItem(player.getUuid(), itemId);
        assignNextItem(server, data, player.getUuid());
        player.sendMessage(Text.translatable("challengecraft.fib.joker_used", target.getName(), left - 1)
                .formatted(Formatting.LIGHT_PURPLE), false);
        syncBattleToAll(server);
    }

    /** ensureMap idiom from Lockout: dedupe extra trackers, hand one out if missing. */
    private static void ensureTracker(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        int firstSlot = -1;
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.getStack(i).isOf(net.kasax.challengecraft.item.ModItems.FORCE_ITEM_TRACKER)) continue;
            if (firstSlot == -1) {
                firstSlot = i;
            } else {
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }
        if (firstSlot == -1) {
            inventory.insertStack(new ItemStack(net.kasax.challengecraft.item.ModItems.FORCE_ITEM_TRACKER));
        }
    }

    /** Op helper: replaces the player's current item without scoring (stuck-item escape hatch). */
    public static void skipItem(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        if (!active || data.getState() != ForceItemBattleSavedData.STATE_RUNNING) return;
        assignNextItem(server, data, player.getUuid());
        syncBattleToAll(server);
    }

    public static void handleTeamAction(ServerPlayerEntity player, net.kasax.challengecraft.network.ForceItemActionPacket.Action action, int teamId) {
        MinecraftServer server = player.getServer();
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        switch (action) {
            case JOIN_TEAM -> {
                if (LockoutBingoTeam.fromOrdinal(teamId) != null) {
                    data.setTeam(player.getUuid(), player.getName().getString(), teamId);
                }
            }
            case LEAVE_TEAM -> data.removeTeam(player.getUuid());
            case REQUEST_SYNC -> { } // fall through to the sync below
            case USE_JOKER -> { useJoker(player); return; }        // already syncs
            case START_BATTLE -> { startBattle(server, player, false); return; }  // already syncs
            case TRIGGER_RESULTS -> { triggerResults(server); return; }
            case NEXT_RESULT -> { advanceCeremony(server); return; }
        }
        syncBattleToAll(server);
    }

    /** Standings sorted best-first. Teams (summed member scores) and solo players can mix. */
    public static List<ForceItemResultsPacket.Entry> computeStandings(ForceItemBattleSavedData data) {
        Map<Integer, Integer> teamScores = new HashMap<>();
        Map<Integer, List<String>> teamMembers = new HashMap<>();
        Map<Integer, List<String>> teamItems = new HashMap<>();
        List<ForceItemResultsPacket.Entry> entries = new ArrayList<>();

        for (Map.Entry<UUID, Integer> e : data.getScores().entrySet()) {
            Integer team = data.getTeamOrdinal(e.getKey());
            String name = data.getPlayerNames().getOrDefault(e.getKey(), "?");
            List<String> collected = data.getCollectedItems(e.getKey());
            if (team != null && LockoutBingoTeam.fromOrdinal(team) != null) {
                teamScores.merge(team, e.getValue(), Integer::sum);
                teamMembers.computeIfAbsent(team, ignored -> new ArrayList<>()).add(name);
                teamItems.computeIfAbsent(team, ignored -> new ArrayList<>()).addAll(collected);
            } else {
                entries.add(new ForceItemResultsPacket.Entry(name, e.getValue(), -1, false, collected));
            }
        }
        teamScores.forEach((team, score) -> entries.add(new ForceItemResultsPacket.Entry(
                String.join(", ", teamMembers.getOrDefault(team, List.of())), score, team, true,
                teamItems.getOrDefault(team, List.of()))));

        entries.sort(Comparator.comparingInt(ForceItemResultsPacket.Entry::score).reversed());
        return entries;
    }

    /** Server-driven ceremony position: 1 = only the last place revealed, size = winner shown. */
    private static int ceremonyStage = 0;

    /** Starts the ceremony: award the flat 100-XP prize once (guarded), open stage 1 everywhere. */
    public static void triggerResults(MinecraftServer server) {
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        List<ForceItemResultsPacket.Entry> standings = computeStandings(data);
        if (standings.isEmpty()) return;

        if (!data.isResultsAwarded() && data.getState() == ForceItemBattleSavedData.STATE_ENDED) {
            // Anti-farming: no prize for solo/debug runs.
            boolean eligible = !data.isDebugRun() && data.getScores().size() >= 2;
            if (eligible) {
                int best = standings.get(0).score();
                for (ForceItemResultsPacket.Entry entry : standings) {
                    if (entry.score() != best) continue;
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        boolean isWinner = entry.teamId() >= 0
                                ? entry.teamId() == orDefault(data.getTeamOrdinal(player.getUuid()))
                                : entry.name().equals(player.getName().getString());
                        if (isWinner) {
                            LevelManager.addXp(player, 100);
                        }
                    }
                }
            }
            data.setResultsAwarded(true);
        }

        ceremonyStage = 1;
        broadcastResults(server, standings);
    }

    /** Continue button (any player): advance the synced ceremony to the next placement. */
    public static void advanceCeremony(MinecraftServer server) {
        if (ceremonyStage <= 0) return;
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        List<ForceItemResultsPacket.Entry> standings = computeStandings(data);
        if (standings.isEmpty()) return;
        ceremonyStage = Math.min(ceremonyStage + 1, standings.size());
        broadcastResults(server, standings);
    }

    private static void broadcastResults(MinecraftServer server, List<ForceItemResultsPacket.Entry> standings) {
        ForceItemResultsPacket packet = new ForceItemResultsPacket(ceremonyStage, standings);
        server.getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, packet));
    }

    private static int orDefault(Integer value) {
        return value == null ? -1 : value;
    }

    /** Op reset: wipes battle state and immediately starts a fresh battle. */
    public static void restartBattle(MinecraftServer server) {
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        data.resetForNewBattle();
        if (active) {
            onActivated(server.getOverworld());
        }
    }

    public static void syncBattleToAll(MinecraftServer server) {
        ForceItemBattleSavedData data = ForceItemBattleSavedData.get(server.getOverworld());
        long remaining = data.getState() == ForceItemBattleSavedData.STATE_RUNNING && data.getEndGameTime() >= 0
                ? Math.max(0, data.getEndGameTime() - server.getOverworld().getTime())
                : 0;

        List<ForceItemSyncPacket.PlayerEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, String> named : data.getPlayerNames().entrySet()) {
            UUID uuid = named.getKey();
            entries.add(new ForceItemSyncPacket.PlayerEntry(
                    uuid,
                    named.getValue(),
                    data.getCurrentItem(uuid),
                    data.getScore(uuid),
                    data.getJokersLeft(uuid),
                    orDefault(data.getTeamOrdinal(uuid)),
                    data.getCollectedItems(uuid)
            ));
        }

        ForceItemSyncPacket packet = new ForceItemSyncPacket(data.getState(), remaining, entries);
        server.getPlayerManager().getPlayerList().forEach(p -> ServerPlayNetworking.send(p, packet));
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) tickCounter = 0;
    }

    public static boolean isActive() {
        return active;
    }
}
