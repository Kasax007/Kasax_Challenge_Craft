package net.kasax.challengecraft.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.HashMap;
import java.util.Map;

public class ChallengeIconProvider {
    private static final Map<Integer, ItemStack> ICONS = new HashMap<>();

    static {
        ICONS.put(1, new ItemStack(Items.EXPERIENCE_BOTTLE));
        ICONS.put(2, new ItemStack(Items.COBWEB));
        ICONS.put(3, new ItemStack(Items.ZOMBIE_SPAWN_EGG));
        ICONS.put(4, new ItemStack(Items.CHEST));
        ICONS.put(5, new ItemStack(Items.GOLDEN_APPLE));
        ICONS.put(6, new ItemStack(Items.EMERALD));
        ICONS.put(7, new ItemStack(Items.APPLE));
        ICONS.put(8, new ItemStack(Items.CRAFTING_TABLE));
        ICONS.put(9, new ItemStack(Items.MAP));
        ICONS.put(10, new ItemStack(Items.CLOCK));
        ICONS.put(11, new ItemStack(Items.GRASS_BLOCK));
        ICONS.put(12, new ItemStack(Items.BUNDLE));
        ICONS.put(13, new ItemStack(Items.ENCHANTED_BOOK));
        ICONS.put(14, new ItemStack(Items.COBBLESTONE));
        ICONS.put(15, new ItemStack(Items.BONE));
        ICONS.put(16, new ItemStack(Items.BEDROCK));
        ICONS.put(17, new ItemStack(Items.DIAMOND_BOOTS));
        ICONS.put(18, new ItemStack(Items.IRON_SWORD));
        ICONS.put(19, new ItemStack(Items.IRON_PICKAXE));
        ICONS.put(20, new ItemStack(Items.KNOWLEDGE_BOOK));
        ICONS.put(21, new ItemStack(Items.TOTEM_OF_UNDYING));
        ICONS.put(22, new ItemStack(Items.NETHER_STAR));
        ICONS.put(23, new ItemStack(Items.CREEPER_SPAWN_EGG));
        ICONS.put(24, new ItemStack(Items.GLISTERING_MELON_SLICE));
        ICONS.put(25, new ItemStack(Items.HEART_OF_THE_SEA));
        ICONS.put(26, new ItemStack(Items.WRITABLE_BOOK));
        ICONS.put(27, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        ICONS.put(28, new ItemStack(Items.IRON_BOOTS));
        ICONS.put(29, new ItemStack(Items.LAVA_BUCKET));
        ICONS.put(30, new ItemStack(Items.ANVIL));
        ICONS.put(31, new ItemStack(Items.STONE_PICKAXE));
        ICONS.put(32, new ItemStack(Items.END_CRYSTAL));
        ICONS.put(33, new ItemStack(Items.SLIME_BALL));
        ICONS.put(34, new ItemStack(Items.PHANTOM_MEMBRANE));
        ICONS.put(35, new ItemStack(Items.SPAWNER));
        ICONS.put(36, new ItemStack(Items.WRITTEN_BOOK));
        
        // Perks
        ICONS.put(101, new ItemStack(Items.GOLDEN_CARROT));
        ICONS.put(102, new ItemStack(Items.FEATHER));
        ICONS.put(103, new ItemStack(Items.NETHERITE_CHESTPLATE));
        ICONS.put(104, new ItemStack(Items.MAGMA_CREAM));
        ICONS.put(105, new ItemStack(Items.DIAMOND_SWORD));
        ICONS.put(106, new ItemStack(Items.BOOK));
        ICONS.put(107, new ItemStack(Items.SHIELD));
        ICONS.put(108, new ItemStack(Items.GOLDEN_SWORD));
    }

    public static ItemStack getIcon(int id) {
        return ICONS.getOrDefault(id, new ItemStack(Items.BARRIER));
    }

    public static void drawIcon(DrawContext context, int x, int y, int id) {
        ItemStack stack = getIcon(id);
        context.drawItem(stack, x, y);
        if (id == 28) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            context.drawItem(barrier, x, y);
        }
    }
}
