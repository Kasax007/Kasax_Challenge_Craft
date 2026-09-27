package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.network.AllBiomesSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The All Biomes (49) checklist: found biomes first, then the missing ones, each group by dimension.
 * Unordered, unlike the other collections — there is no "current" target, any missing biome counts.
 */
public class AllBiomesScreen extends CollectionScreen {
    public AllBiomesScreen() {
        super(Component.translatable("challengecraft.all_biomes_list.title"), rows(), AllBiomesHUD.foundCount(), false);
    }

    private static List<Row> rows() {
        List<Row> found = new ArrayList<>();
        List<Row> missing = new ArrayList<>();
        List<String> all = AllBiomesHUD.all();
        for (int dim = AllBiomesSyncPacket.OVERWORLD; dim <= AllBiomesSyncPacket.END; dim++) {
            for (int i = 0; i < all.size(); i++) {
                if (AllBiomesHUD.dimensionOf(i) != dim) {
                    continue;
                }
                String id = all.get(i);
                Component name = AllBiomesHUD.biomeName(id);
                Component where = Component.translatable("challengecraft.all_biomes.dimension." + dim);
                Row row = new Row(icon(dim), name, List.of(name, where));
                (AllBiomesHUD.isVisited(id) ? found : missing).add(row);
            }
        }
        found.addAll(missing);
        return found;
    }

    private static ItemStack icon(int dimension) {
        return new ItemStack(switch (dimension) {
            case AllBiomesSyncPacket.NETHER -> Items.NETHERRACK;
            case AllBiomesSyncPacket.END -> Items.END_STONE;
            default -> Items.GRASS_BLOCK;
        });
    }
}
