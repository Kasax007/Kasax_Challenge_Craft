package net.kasax.challengecraft.block;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

/** Registers the infinite chest block, block entity, and screen handler types. */
public class InfiniteChestRegistry {
    public static final Identifier INFINITE_CHEST_ID = Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "infinite_chest");
    public static final ResourceKey<Block> INFINITE_CHEST_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, INFINITE_CHEST_ID);
    public static final ResourceKey<Item> INFINITE_CHEST_ITEM_KEY = ResourceKey.create(Registries.ITEM, INFINITE_CHEST_ID);

    public static final Block INFINITE_CHEST_BLOCK = new InfiniteChestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).setId(INFINITE_CHEST_BLOCK_KEY).lightLevel(s -> 7).strength(2.5f));
    public static final Item INFINITE_CHEST_ITEM = new BlockItem(INFINITE_CHEST_BLOCK, new Item.Properties().setId(INFINITE_CHEST_ITEM_KEY));

    public static final BlockEntityType<InfiniteChestBlockEntity> INFINITE_CHEST_BLOCK_ENTITY = FabricBlockEntityTypeBuilder.create(InfiniteChestBlockEntity::new, INFINITE_CHEST_BLOCK).build();

    public static final MenuType<InfiniteChestScreenHandler> INFINITE_CHEST_SCREEN_HANDLER = new ExtendedMenuType<>(InfiniteChestScreenHandler::new, InfiniteChestScreenHandler.PacketData.PACKET_CODEC);

    public static void initialize() {
        Registry.register(BuiltInRegistries.BLOCK, INFINITE_CHEST_BLOCK_KEY, INFINITE_CHEST_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, INFINITE_CHEST_ITEM_KEY, INFINITE_CHEST_ITEM);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, INFINITE_CHEST_ID, INFINITE_CHEST_BLOCK_ENTITY);
        Registry.register(BuiltInRegistries.MENU, INFINITE_CHEST_ID, INFINITE_CHEST_SCREEN_HANDLER);

        // 26.2 / Fabric API 0.156: ItemGroupEvents.modifyEntriesEvent -> CreativeModeTabEvents
        // .modifyOutputEvent, whose listener takes a FabricCreativeModeTabOutput (still a
        // CreativeModeTab.Output, so accept(ItemLike) is unchanged).
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(INFINITE_CHEST_ITEM);
        });

        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity((be, direction) -> {
            if (be instanceof InfiniteChestBlockEntity chest) {
                return chest.getItemStorage();
            }
            return null;
        }, INFINITE_CHEST_BLOCK_ENTITY);
    }
}
