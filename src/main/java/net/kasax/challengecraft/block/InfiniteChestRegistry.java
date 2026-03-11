package net.kasax.challengecraft.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class InfiniteChestRegistry {
    public static final Identifier INFINITE_CHEST_ID = Identifier.of(ChallengeCraft.MOD_ID, "infinite_chest");
    public static final RegistryKey<Block> INFINITE_CHEST_BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, INFINITE_CHEST_ID);
    public static final RegistryKey<Item> INFINITE_CHEST_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, INFINITE_CHEST_ID);

    public static final Block INFINITE_CHEST_BLOCK = new InfiniteChestBlock(AbstractBlock.Settings.copy(Blocks.CHEST).registryKey(INFINITE_CHEST_BLOCK_KEY).luminance(s -> 7).strength(2.5f));
    public static final Item INFINITE_CHEST_ITEM = new BlockItem(INFINITE_CHEST_BLOCK, new Item.Settings().registryKey(INFINITE_CHEST_ITEM_KEY));

    public static final BlockEntityType<InfiniteChestBlockEntity> INFINITE_CHEST_BLOCK_ENTITY = FabricBlockEntityTypeBuilder.create(InfiniteChestBlockEntity::new, INFINITE_CHEST_BLOCK).build();

    public static final ScreenHandlerType<InfiniteChestScreenHandler> INFINITE_CHEST_SCREEN_HANDLER = new ExtendedScreenHandlerType<>(InfiniteChestScreenHandler::new, InfiniteChestScreenHandler.PacketData.PACKET_CODEC);

    public static void initialize() {
        Registry.register(Registries.BLOCK, INFINITE_CHEST_BLOCK_KEY, INFINITE_CHEST_BLOCK);
        Registry.register(Registries.ITEM, INFINITE_CHEST_ITEM_KEY, INFINITE_CHEST_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, INFINITE_CHEST_ID, INFINITE_CHEST_BLOCK_ENTITY);
        Registry.register(Registries.SCREEN_HANDLER, INFINITE_CHEST_ID, INFINITE_CHEST_SCREEN_HANDLER);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(INFINITE_CHEST_ITEM);
        });

        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity((be, direction) -> {
            if (be instanceof InfiniteChestBlockEntity chest) {
                return chest.getItemStorage();
            }
            return null;
        }, INFINITE_CHEST_BLOCK_ENTITY);
    }
}
