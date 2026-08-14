package net.kasax.challengecraft.client.screen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable list view for the Progressive Block Drops order — which blocks are already unlocked,
 * which one is the current target, and what is still to come.
 */
public class ProgressiveBlocksScreen extends CollectionScreen {
    public ProgressiveBlocksScreen(List<String> blockIds, int currentIndex) {
        super(Component.translatable("challengecraft.progressive_blocks_list.title"),
                toRows(blockIds), currentIndex);
    }

    private static List<Row> toRows(List<String> blockIds) {
        List<Row> rows = new ArrayList<>();
        for (String id : blockIds) {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            rows.add(new Row(new ItemStack(block.asItem()), block.getName(), List.of()));
        }
        return rows;
    }
}
