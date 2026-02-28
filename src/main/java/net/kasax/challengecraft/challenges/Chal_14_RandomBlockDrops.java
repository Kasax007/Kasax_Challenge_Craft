package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Chal_14_RandomBlockDrops {
    private static boolean active = false;
    private static List<Item> ITEM_LIST = null;

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (active && !player.isCreative() && world instanceof ServerWorld serverWorld) {
                // Get the random item for this block and world seed
                ItemStack stack = getRandomDrop(state.getBlock(), serverWorld);
                if (!stack.isEmpty()) {
                    Block.dropStack(world, pos, stack);
                }
                // Break the block without dropping items
                world.breakBlock(pos, false, player);
                // Cancel the original break event to avoid double breaking/drops
                return false;
            }
            return true;
        });
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static ItemStack getRandomDrop(Block block, ServerWorld world) {
        if (ITEM_LIST == null) {
            ITEM_LIST = new ArrayList<>();
            Registries.ITEM.forEach(item -> {
                // Skip air to avoid getting air drops
                if (Registries.ITEM.getId(item).toString().equals("minecraft:air")) return;
                ITEM_LIST.add(item);
            });
        }
        
        if (ITEM_LIST.isEmpty()) return ItemStack.EMPTY;

        long seed = world.getSeed();
        Identifier blockId = Registries.BLOCK.getId(block);
        
        // Consistent seed per block and world
        long combinedSeed = seed + blockId.toString().hashCode();
        Random random = new Random(combinedSeed);
        
        Item randomItem = ITEM_LIST.get(random.nextInt(ITEM_LIST.size()));
        return new ItemStack(randomItem);
    }
}
