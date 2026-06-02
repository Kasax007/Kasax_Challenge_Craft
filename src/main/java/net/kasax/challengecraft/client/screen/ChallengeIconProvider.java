package net.kasax.challengecraft.client.screen;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Central icon lookup for challenge selection UI. */
public class ChallengeIconProvider {
    private static final Map<Integer, Item> ICONS = new HashMap<>();

    static {
        ICONS.put(1, Items.EXPERIENCE_BOTTLE);
        ICONS.put(2, Items.COBWEB);
        ICONS.put(3, Items.ZOMBIE_SPAWN_EGG);
        ICONS.put(4, Items.CHEST);
        ICONS.put(5, Items.GOLDEN_APPLE);
        ICONS.put(6, Items.EMERALD);
        ICONS.put(7, Items.APPLE);
        ICONS.put(8, Items.CRAFTING_TABLE);
        ICONS.put(9, Items.MAP);
        ICONS.put(10, Items.CLOCK);
        ICONS.put(11, Items.GRASS_BLOCK);
        ICONS.put(12, Items.BUNDLE);
        ICONS.put(13, Items.ENCHANTED_BOOK);
        ICONS.put(14, Items.COBBLESTONE);
        ICONS.put(15, Items.BONE);
        ICONS.put(16, Items.BEDROCK);
        ICONS.put(17, Items.DIAMOND_BOOTS);
        ICONS.put(18, Items.IRON_SWORD);
        ICONS.put(19, Items.IRON_PICKAXE);
        ICONS.put(20, Items.KNOWLEDGE_BOOK);
        ICONS.put(21, Items.TOTEM_OF_UNDYING);
        ICONS.put(22, Items.NETHER_STAR);
        ICONS.put(23, Items.CREEPER_SPAWN_EGG);
        ICONS.put(24, Items.GLISTERING_MELON_SLICE);
        ICONS.put(25, Items.HEART_OF_THE_SEA);
        ICONS.put(26, Items.WRITABLE_BOOK);
        ICONS.put(27, Items.CHAINMAIL_CHESTPLATE);
        ICONS.put(28, Items.IRON_BOOTS);
        ICONS.put(29, Items.LAVA_BUCKET);
        ICONS.put(30, Items.ANVIL);
        ICONS.put(31, Items.STONE_PICKAXE);
        ICONS.put(32, Items.END_CRYSTAL);
        ICONS.put(33, Items.SLIME_BALL);
        ICONS.put(34, Items.PHANTOM_MEMBRANE);
        ICONS.put(35, Items.SPAWNER);
        ICONS.put(36, Items.WRITTEN_BOOK);
        ICONS.put(37, Items.REPEATER);
        ICONS.put(38, Items.GLOW_INK_SAC);
        ICONS.put(39, Items.COOKED_BEEF);
        ICONS.put(40, Items.FILLED_MAP);
        
        // Perks
        ICONS.put(101, Items.GOLDEN_CARROT);
        ICONS.put(102, Items.FEATHER);
        ICONS.put(103, Items.NETHERITE_CHESTPLATE);
        ICONS.put(104, Items.MAGMA_CREAM);
        ICONS.put(105, Items.DIAMOND_SWORD);
        ICONS.put(106, Items.BOOK);
        ICONS.put(107, Items.SHIELD);
        ICONS.put(108, Items.GOLDEN_SWORD);
        ICONS.put(109, net.kasax.challengecraft.block.InfiniteChestRegistry.INFINITE_CHEST_ITEM);
    }

    public static ItemStack getIcon(int id) {
        return new ItemStack(ICONS.getOrDefault(id, Items.BARRIER));
    }

    public static void drawIcon(GuiGraphicsExtractor context, int x, int y, int id) {
        ItemStack stack = getIcon(id);
        context.item(stack, x, y);
        if (id == 28 || id == 39) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            context.item(barrier, x, y);
        }
    }
}

