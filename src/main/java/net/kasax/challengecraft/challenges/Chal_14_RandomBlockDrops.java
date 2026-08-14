package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Replaces normal block drops with a deterministic random item mapping per world seed. */
public class Chal_14_RandomBlockDrops {
    private static boolean active = false;
    private static List<Item> ITEM_LIST = null;

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (active && !player.isCreative() && world instanceof ServerLevel serverWorld) {
                // The custom chest must survive as itself so its storage contract stays intact.
                if (state.getBlock() == net.kasax.challengecraft.block.InfiniteChestRegistry.INFINITE_CHEST_BLOCK) {
                    return true;
                }
                
                ItemStack stack = getRandomDrop(state.getBlock(), serverWorld);
                if (!stack.isEmpty()) {
                    Block.popResource(world, pos, stack);
                }
                world.destroyBlock(pos, false, player);
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

    public static ItemStack getRandomDrop(Block block, ServerLevel world) {
        if (ITEM_LIST == null) {
            ITEM_LIST = new ArrayList<>();
            BuiltInRegistries.ITEM.forEach(item -> {
                // Skip air to avoid getting air drops
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id.toString().equals("minecraft:air")) return;
                if (id.getNamespace().equals("challengecraft")) return;
                ITEM_LIST.add(item);
            });
        }
        
        if (ITEM_LIST.isEmpty()) return ItemStack.EMPTY;

        long seed = world.getSeed();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        
        // Consistent seed per block and world
        long combinedSeed = seed + blockId.toString().hashCode();
        Random random = new Random(combinedSeed);
        
        Item randomItem = ITEM_LIST.get(random.nextInt(ITEM_LIST.size()));
        return new ItemStack(randomItem);
    }
}
