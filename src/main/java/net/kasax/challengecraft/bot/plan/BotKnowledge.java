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
        BlockState s = block.defaultBlockState();
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (id.contains("nether") || id.startsWith("soul_") || id.contains("basalt") || id.contains("blackstone")
                || id.contains("crimson") || id.contains("warped") || id.equals("glowstone") || id.equals("magma_block")
                || id.equals("ancient_debris") || id.equals("shroomlight") || id.contains("quartz_ore")) return 900;
        if (id.startsWith("end_") || id.contains("purpur") || id.contains("chorus") || id.contains("shulker")) return 2500;
        if (s.is(BlockTags.LEAVES)) return 5;
        if (s.is(BlockTags.LOGS)) return id.contains("cherry") || id.contains("mangrove") || id.contains("jungle")
                || id.contains("acacia") || id.contains("dark_oak") || id.contains("pale") ? 90 : 8;
        if (s.is(BlockTags.SMALL_FLOWERS)) return 10;
        return switch (id) {
            case "dirt", "grass_block", "stone", "sand", "gravel", "short_grass", "tall_grass", "deepslate", "andesite",
                 "diorite", "granite", "fern", "large_fern" -> 3;
            case "sandstone", "red_sand", "snow", "snow_block", "tuff", "water", "sugar_cane", "coarse_dirt" -> 15;
            case "coal_ore" -> 12;
            case "deepslate_coal_ore", "copper_ore" -> 18;
            case "iron_ore" -> 22;
            case "deepslate_copper_ore", "deepslate_iron_ore", "clay", "kelp", "kelp_plant", "seagrass" -> 35;
            case "gold_ore", "deepslate_gold_ore", "redstone_ore", "deepslate_redstone_ore", "lapis_ore",
                 "deepslate_lapis_ore", "pumpkin", "cactus", "brown_mushroom", "red_mushroom", "calcite" -> 70;
            case "melon", "ice", "moss_block", "dripstone_block", "pointed_dripstone", "sweet_berry_bush", "cobweb",
                 "bamboo", "mud", "podzol", "mycelium", "terracotta", "red_sandstone", "vine", "lily_pad" -> 120;
            case "obsidian" -> 180;
            case "diamond_ore", "deepslate_diamond_ore", "packed_ice", "amethyst_cluster", "blue_ice", "sculk" -> 300;
            case "emerald_ore", "deepslate_emerald_ore" -> 450;
            default -> 200;
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
    }

    private void mob(EntityType<?> type, double effort, Object... itemsAndCounts) {
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            Item item = (Item) itemsAndCounts[i];
            double n = (Double) itemsAndCounts[i + 1];
            mobDrops.computeIfAbsent(item, k -> new ArrayList<>()).add(new MobDrop(type, n, effort));
        }
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
