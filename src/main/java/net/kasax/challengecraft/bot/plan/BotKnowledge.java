package net.kasax.challengecraft.bot.plan;

import net.kasax.challengecraft.bot.BotManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a bot knows about Minecraft itself, read from the running game instead of written by hand
 * wherever possible: every crafting and smelting recipe (so modded and datapack recipes work too),
 * which blocks drop which items (by rolling the real loot tables), which tools harvest which
 * blocks. Only two things are hand-made: how common a block is in the world, and what mobs drop.
 */
public final class BotKnowledge {
    /** A block that drops the item, with the expected number per break. */
    public record Drop(Block block, double count) {
    }

    /** A mob that drops the item, the expected number per kill and a rough effort to find and kill one. */
    public record MobDrop(EntityType<?> type, double count, double effort) {
    }

    public sealed interface Route permits CraftRoute, SmeltRoute {
        Item result();

        String key();
    }

    /** A crafting recipe: its grid (w×h, empty slots as empty Optionals), the yield and whether it needs a table. */
    public record CraftRoute(String key, CraftingRecipe recipe, Item result, int yield, int width, int height,
                             List<Optional<Ingredient>> grid, boolean table) implements Route {
        public List<Ingredient> ingredients() {
            List<Ingredient> out = new ArrayList<>();
            for (Optional<Ingredient> i : grid) i.ifPresent(out::add);
            return out;
        }
    }

    public record SmeltRoute(String key, AbstractCookingRecipe recipe, Item result, Ingredient input) implements Route {
    }

    private static BotKnowledge instance;

    public static BotKnowledge get(MinecraftServer server) {
        RecipeManager rm = server.getRecipeManager();
        if (instance == null || instance.recipes != rm) {
            long t = System.nanoTime();
            instance = new BotKnowledge(server, rm);
            BotManager.LOG.info("[Bot] knowledge built in {} ms: {} craft/smelt routes, {} droppable items",
                    (System.nanoTime() - t) / 1_000_000, instance.routeCount, instance.drops.size());
        }
        return instance;
    }

    private final RecipeManager recipes;
    private final Map<Item, List<Route>> routes = new IdentityHashMap<>();
    private final Map<Item, List<Drop>> drops = new IdentityHashMap<>();
    private final Map<Item, List<MobDrop>> mobDrops = new IdentityHashMap<>();
    private final List<Item> tools = new ArrayList<>();
    private final Map<Block, List<Item>> harvestTools = new IdentityHashMap<>();
    private int routeCount;

    private BotKnowledge(MinecraftServer server, RecipeManager rm) {
        this.recipes = rm;
        indexRecipes(server, rm);
        indexDrops(server.overworld());
        indexMobs();
        for (Item item : BuiltInRegistries.ITEM) {
            if (new ItemStack(item).has(DataComponents.TOOL)) tools.add(item);
        }
    }

    // ---- recipes ------------------------------------------------------------------------------

    private void indexRecipes(MinecraftServer server, RecipeManager rm) {
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            try {
                String key = holder.id().identifier().toString();
                if (holder.value() instanceof ShapedRecipe r && !r.isSpecial()) {
                    List<Optional<Ingredient>> grid = r.getIngredients();
                    List<ItemStack> sample = new ArrayList<>();
                    for (Optional<Ingredient> i : grid) sample.add(i.map(BotKnowledge::sample).orElse(ItemStack.EMPTY));
                    ItemStack out = r.assemble(CraftingInput.of(r.getWidth(), r.getHeight(), sample));
                    add(new CraftRoute(key, r, out.getItem(), out.getCount(), r.getWidth(), r.getHeight(), grid,
                            r.getWidth() > 2 || r.getHeight() > 2), out);
                } else if (holder.value() instanceof ShapelessRecipe r && !r.isSpecial()) {
                    List<Ingredient> ings = r.placementInfo().ingredients();
                    if (ings.isEmpty() || ings.size() > 9) continue;
                    List<Optional<Ingredient>> grid = new ArrayList<>();
                    List<ItemStack> sample = new ArrayList<>();
                    for (Ingredient i : ings) {
                        grid.add(Optional.of(i));
                        sample.add(sample(i));
                    }
                    ItemStack out = r.assemble(CraftingInput.of(ings.size(), 1, sample));
                    add(new CraftRoute(key, r, out.getItem(), out.getCount(), ings.size(), 1, grid, ings.size() > 4), out);
                } else if (holder.value() instanceof AbstractCookingRecipe r && r.getType() == RecipeType.SMELTING) {
                    ItemStack out = r.assemble(new SingleRecipeInput(sample(r.input())));
                    add(new SmeltRoute(key, r, out.getItem(), r.input()), out);
                }
            } catch (RuntimeException e) {
                // A recipe that cannot be assembled from plain items (components, special inputs): skip it.
            }
        }
    }

    private void add(Route route, ItemStack out) {
        if (out.isEmpty()) return;
        routes.computeIfAbsent(route.result(), k -> new ArrayList<>()).add(route);
        routeCount++;
    }

    static ItemStack sample(Ingredient ingredient) {
        return ingredient.items().findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
    }

    public static List<Item> items(Ingredient ingredient) {
        return ingredient.items().map(Holder::value).toList();
    }

    // ---- blocks -------------------------------------------------------------------------------

    private void indexDrops(ServerLevel level) {
        BlockPos pos = level.getRespawnData().pos();
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        final int rolls = 12;
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) continue;
            if (state.getDestroySpeed(level, pos) < 0) continue;
            Map<Item, Integer> got = new HashMap<>();
            try {
                for (int i = 0; i < rolls; i++) {
                    for (ItemStack s : Block.getDrops(state, level, pos, null, null, tool)) {
                        got.merge(s.getItem(), s.getCount(), Integer::sum);
                    }
                }
            } catch (RuntimeException e) {
                continue;
            }
            got.forEach((item, n) -> drops.computeIfAbsent(item, k -> new ArrayList<>()).add(new Drop(block, n / (double) rolls)));
        }
    }

    /**
     * How long it takes, roughly, to come across a block of this kind in the overworld (in seconds
     * of play). Things the bot can see right now cost almost nothing instead; the planner handles that.
     */
    public static double rarity(Block block) {
        return rarity(block, false);
    }

    /** As {@link #rarity(Block)}, for the dimension the bot is in: in the Nether it is the Nether's blocks that are around. */
    public static double rarity(Block block, boolean nether) {
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (nether) {
            return switch (id) {
                case "netherrack" -> 3;
                case "soul_sand", "soul_soil", "nether_quartz_ore", "nether_gold_ore", "gravel", "magma_block" -> 25;
                case "basalt", "blackstone", "crimson_stem", "warped_stem", "crimson_nylium", "warped_nylium",
                     "crimson_fungus", "warped_fungus", "nether_wart_block", "warped_wart_block", "weeping_vines", "twisting_vines" -> 50;
                case "glowstone", "shroomlight", "gilded_blackstone" -> 90;
                case "obsidian", "crying_obsidian" -> 200;
                case "nether_wart", "nether_bricks", "nether_brick_fence" -> 500; // fortresses
                case "ancient_debris" -> 1500;
                default -> 9000;
            };
        }
        BlockState s = block.defaultBlockState();
        if (id.contains("nether") || id.startsWith("soul_") || id.contains("basalt") || id.contains("blackstone")
                || id.contains("crimson") || id.contains("warped") || id.equals("glowstone") || id.equals("magma_block")
                || id.equals("ancient_debris") || id.equals("shroomlight") || id.contains("quartz_ore")) return 5000; // the Nether: not yet
        if (id.startsWith("end_") || id.contains("purpur") || id.contains("chorus") || id.contains("shulker")) return 9000;
        if (s.is(BlockTags.LEAVES)) return 5;
        // Storage blocks and the copper building blocks (trial chambers): never worth a search,
        // the ore is (nine ingots a block only if one happens to be in sight).
        if (!id.contains("ore") && (id.contains("copper") || id.startsWith("raw_") && id.endsWith("_block")
                || id.matches("(iron|gold|diamond|emerald|lapis|redstone|coal|netherite)_block"))) return 9000;
        // Trees not in sight: the common kinds are a short walk away, the others need their biome.
        if (s.is(BlockTags.LOGS)) return id.contains("oak") && !id.contains("dark") || id.contains("birch") || id.contains("spruce") ? 30 : 150;
        if (s.is(BlockTags.SMALL_FLOWERS)) return id.equals("dandelion") || id.equals("poppy") ? 12 : 90;
        return switch (id) {
            case "dirt", "grass_block", "sand", "gravel", "short_grass", "tall_grass", "fern", "large_fern" -> 3;
            // Under a few blocks of dirt anywhere: dig down.
            case "stone", "andesite", "diorite", "granite" -> 8;
            case "sandstone", "red_sand", "snow", "snow_block", "water", "sugar_cane", "coarse_dirt" -> 15;
            case "coal_ore", "deepslate", "tuff" -> 30;
            case "copper_ore", "deepslate_coal_ore" -> 40;
            case "iron_ore" -> 55;
            case "deepslate_copper_ore", "deepslate_iron_ore", "clay", "kelp", "kelp_plant", "seagrass" -> 70;
            case "pumpkin", "cactus", "brown_mushroom", "red_mushroom", "calcite" -> 90;
            case "redstone_ore", "deepslate_redstone_ore" -> 220;
            case "gold_ore", "deepslate_gold_ore", "lapis_ore", "deepslate_lapis_ore" -> 300;
            case "melon", "ice", "moss_block", "dripstone_block", "pointed_dripstone",
                 "sweet_berry_bush", "cobweb", "bamboo", "mud", "podzol", "mycelium", "terracotta", "red_sandstone",
                 "vine", "lily_pad", "cocoa" -> 140;
            // Rarely just lying about (a ruined portal): poured from lava and water instead.
            case "obsidian" -> 600;
            // Only deep in the big ore veins, and nine of the raw ore each: never worth a search.
            case "raw_iron_block", "raw_copper_block", "raw_gold_block" -> 9000;
            case "packed_ice", "amethyst_cluster", "blue_ice", "sculk", "hay_block", "bell" -> 320;
            case "diamond_ore", "deepslate_diamond_ore" -> 600;
            case "emerald_ore", "deepslate_emerald_ore" -> 600;
            // Anything else is not lying around in the overworld (storage blocks, bricks, ...): make it.
            default -> 1500;
        };
    }

    /**
     * Blocks a biome is known for (by a word in the biome's name): once such a biome is in view,
     * finding them is a walk there rather than luck.
     */
    public static List<String> typicalOf(String biome) {
        List<String> out = new ArrayList<>();
        if (biome.contains("desert")) out.addAll(List.of("sand", "cactus", "dead_bush", "sandstone"));
        if (biome.contains("jungle")) out.addAll(List.of("jungle_log", "cocoa", "melon", "bamboo", "vine"));
        if (biome.contains("swamp")) out.addAll(List.of("lily_pad", "clay", "vine", "blue_orchid", "mud", "mangrove_log"));
        if (biome.contains("snowy") || biome.contains("frozen") || biome.contains("ice")) out.addAll(List.of("snow", "snow_block", "ice", "packed_ice"));
        if (biome.contains("savanna")) out.addAll(List.of("acacia_log"));
        if (biome.contains("dark_forest")) out.addAll(List.of("dark_oak_log", "brown_mushroom", "red_mushroom"));
        if (biome.contains("cherry")) out.addAll(List.of("cherry_log", "pink_petals"));
        if (biome.contains("badlands")) out.addAll(List.of("terracotta", "red_sand", "red_sandstone", "gold_ore"));
        if (biome.contains("ocean") || biome.contains("river")) out.addAll(List.of("sand", "clay", "kelp_plant", "kelp", "seagrass", "gravel", "sugar_cane"));
        if (biome.contains("beach")) out.addAll(List.of("sand", "sugar_cane"));
        if (biome.contains("mushroom")) out.addAll(List.of("brown_mushroom", "red_mushroom", "mycelium"));
        if (biome.contains("taiga")) out.addAll(List.of("spruce_log", "sweet_berry_bush", "fern", "podzol"));
        if (biome.contains("birch")) out.addAll(List.of("birch_log"));
        if (biome.contains("flower")) out.addAll(List.of("allium", "azure_bluet", "cornflower", "lily_of_the_valley", "oxeye_daisy", "orange_tulip", "red_tulip", "white_tulip", "pink_tulip"));
        if (biome.contains("plains") || biome.contains("meadow")) out.addAll(List.of("oxeye_daisy", "azure_bluet", "cornflower"));
        if (biome.contains("forest") && !biome.contains("dark")) out.addAll(List.of("lily_of_the_valley", "birch_log", "oak_log"));
        if (biome.contains("dark_forest") || biome.contains("swamp") || biome.contains("wooded") || biome.contains("windswept_forest")) out.add("oak_log");
        if (biome.contains("taiga") || biome.contains("grove") || biome.contains("windswept")) out.add("spruce_log");
        if (biome.contains("lush")) out.addAll(List.of("moss_block", "azalea", "clay", "spore_blossom"));
        if (biome.contains("dripstone")) out.addAll(List.of("pointed_dripstone", "dripstone_block"));
        if (biome.contains("deep_dark")) out.addAll(List.of("sculk", "sculk_vein"));
        if (biome.contains("peaks") || biome.contains("slopes") || biome.contains("grove")) out.addAll(List.of("snow_block", "ice", "packed_ice", "emerald_ore"));
        if (biome.contains("dappled")) out.addAll(List.of("poplar_log"));
        return out;
    }

    /**
     * The height to dig to for a block found underground (the most common height of an ore),
     * or null for blocks the bot looks for on the surface.
     */
    public static Integer depth(Block block, int surfaceY) {
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return switch (id) {
            case "stone", "andesite", "diorite", "granite" -> surfaceY - 6;
            case "coal_ore", "copper_ore", "deepslate_coal_ore", "deepslate_copper_ore" -> 44;
            case "iron_ore", "deepslate_iron_ore" -> 14;
            case "lapis_ore", "deepslate_lapis_ore", "deepslate", "tuff" -> -2;
            case "gold_ore", "deepslate_gold_ore" -> -18;
            case "redstone_ore", "deepslate_redstone_ore", "diamond_ore", "deepslate_diamond_ore" -> -53;
            default -> null;
        };
    }

    public List<Drop> blocksDropping(Item item) {
        return drops.getOrDefault(item, List.of());
    }

    public List<Item> harvestTools(Block block) {
        return harvestTools.computeIfAbsent(block, b -> {
            BlockState s = b.defaultBlockState();
            List<Item> out = new ArrayList<>();
            for (Item t : tools) if (new ItemStack(t).isCorrectToolForDrops(s)) out.add(t);
            return out;
        });
    }

    // ---- mobs ---------------------------------------------------------------------------------

    private void indexMobs() {
        mob(EntityTypes.ZOMBIE, 45, Items.ROTTEN_FLESH, 1.0);
        mob(EntityTypes.SKELETON, 55, Items.BONE, 1.0, Items.ARROW, 1.0);
        mob(EntityTypes.SPIDER, 55, Items.STRING, 1.0, Items.SPIDER_EYE, 0.33);
        mob(EntityTypes.CREEPER, 70, Items.GUNPOWDER, 1.0);
        mob(EntityTypes.COW, 25, Items.LEATHER, 1.0, Items.BEEF, 2.0);
        mob(EntityTypes.PIG, 25, Items.PORKCHOP, 2.0);
        mob(EntityTypes.SHEEP, 25, Items.MUTTON, 1.5, net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("white_wool")), 1.0);
        mob(EntityTypes.CHICKEN, 25, Items.CHICKEN, 1.0, Items.FEATHER, 1.0);
        mob(EntityTypes.RABBIT, 80, Items.RABBIT, 0.5, Items.RABBIT_HIDE, 0.5);
        mob(EntityTypes.ENDERMAN, 200, Items.ENDER_PEARL, 0.5);
        mob(EntityTypes.SLIME, 400, Items.SLIME_BALL, 1.0);
        mob(EntityTypes.SQUID, 90, Items.INK_SAC, 2.0);
        mob(EntityTypes.GLOW_SQUID, 250, Items.GLOW_INK_SAC, 2.0);
        mob(EntityTypes.COD, 80, Items.COD, 1.0);
        mob(EntityTypes.SALMON, 80, Items.SALMON, 1.0);
        mob(EntityTypes.DROWNED, 120, Items.ROTTEN_FLESH, 1.0);
        mob(EntityTypes.HORSE, 90, Items.LEATHER, 1.0);
        mob(EntityTypes.WITCH, 300, Items.GLOWSTONE_DUST, 0.7, Items.REDSTONE, 0.7);
        // Their arrows are tipped (slowness, poison): the only tipped arrows without brewing.
        mob(EntityTypes.STRAY, 120, Items.TIPPED_ARROW, 0.5, Items.BONE, 1.0);
        mob(EntityTypes.BOGGED, 120, Items.TIPPED_ARROW, 0.5, Items.BONE, 1.0);
        // The Nether (only counted there, see ObtainPlanner#mobCost).
        mob(EntityTypes.ZOMBIFIED_PIGLIN, 20, Items.GOLD_NUGGET, 1.0, Items.ROTTEN_FLESH, 1.0);
        mob(EntityTypes.PIGLIN, 40, Items.GOLD_INGOT, 0.1);
        mob(EntityTypes.MAGMA_CUBE, 80, Items.MAGMA_CREAM, 0.25);
        mob(EntityTypes.GHAST, 200, Items.GHAST_TEAR, 0.5, Items.GUNPOWDER, 1.0);
        mob(EntityTypes.HOGLIN, 90, Items.PORKCHOP, 3.0, Items.LEATHER, 0.5);
        mob(EntityTypes.STRIDER, 90, Items.STRING, 3.0);
        mob(EntityTypes.BLAZE, 600, Items.BLAZE_ROD, 0.5);
        mob(EntityTypes.WITHER_SKELETON, 700, Items.COAL, 0.33, Items.BONE, 1.0);
    }

    private void mob(EntityType<?> type, double effort, Object... itemsAndCounts) {
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            Item item = (Item) itemsAndCounts[i];
            double n = (Double) itemsAndCounts[i + 1];
            mobDrops.computeIfAbsent(item, k -> new ArrayList<>()).add(new MobDrop(type, n, effort));
        }
    }

    /** The effort to find and kill one of these mobs, from the drop table; {@code unknown} for others. */
    /** Mobs that live in the Nether (and nowhere else). */
    public static final java.util.Set<EntityType<?>> NETHER_MOBS = java.util.Set.of(EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.PIGLIN,
            EntityTypes.MAGMA_CUBE, EntityTypes.GHAST, EntityTypes.HOGLIN, EntityTypes.STRIDER, EntityTypes.BLAZE,
            EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE);

    public double mobEffort(EntityType<?> type, double unknown) {
        for (List<MobDrop> l : mobDrops.values()) for (MobDrop d : l) if (d.type() == type) return d.effort();
        return unknown;
    }

    public List<MobDrop> mobsDropping(Item item) {
        return mobDrops.getOrDefault(item, List.of());
    }

    // ---- recipes, looked up -------------------------------------------------------------------

    public List<Route> routesTo(Item item) {
        return routes.getOrDefault(item, List.of());
    }

    public Set<Item> craftableItems() {
        return new LinkedHashSet<>(routes.keySet());
    }

    /** Burn value of a fuel in items smelted (coal = 8), 0 for no fuel. */
    public static double fuelValue(ItemStack s) {
        if (s.is(Items.COAL) || s.is(Items.CHARCOAL)) return 8;
        if (s.is(Items.COAL_BLOCK)) return 80;
        if (s.is(Items.BLAZE_ROD)) return 12;
        if (s.is(Items.DRIED_KELP_BLOCK)) return 20;
        if (s.is(Items.LAVA_BUCKET)) return 100;
        if (s.is(Items.STICK)) return 0.5;
        if (s.is(net.minecraft.tags.ItemTags.PLANKS) || s.is(net.minecraft.tags.ItemTags.LOGS)) return 1.5;
        // Everything else that burns (tools, tables, boats) is worth more than the fuel.
        return 0;
    }

}
