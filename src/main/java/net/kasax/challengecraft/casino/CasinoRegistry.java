package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.EnumMap;
import java.util.Map;

/**
 * Blocks, items and the croupier entity of "The House Always Wins". Declared as static finals and
 * registered in {@link #initialize()}, following {@code InfiniteChestRegistry}.
 */
public final class CasinoRegistry {
    public static final Map<DeviceType, Block> DEVICE_BLOCKS = new EnumMap<>(DeviceType.class);
    public static final Map<DeviceType, Item> DEVICE_ITEMS = new EnumMap<>(DeviceType.class);

    public static final Identifier WALLET_ID = id("chip_wallet");
    public static final Item CHIP_WALLET = new Item(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, WALLET_ID))
            .stacksTo(1));

    public static final Identifier CROUPIER_ID = id("croupier");
    public static final ResourceKey<EntityType<?>> CROUPIER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, CROUPIER_ID);
    public static final EntityType<CroupierEntity> CROUPIER =
            EntityType.Builder.<CroupierEntity>of(CroupierEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(10)
                    .noSummon()
                    .noLootTable()
                    .build(CROUPIER_KEY);

    static {
        for (DeviceType type : DeviceType.values()) {
            Identifier id = id(type.id);
            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .setId(blockKey)
                    .strength(2.5f, 6.0f)
                    .noOcclusion()
                    .sound(type == DeviceType.ROULETTE || type == DeviceType.CASHIER ? SoundType.WOOD : SoundType.METAL);
            if (type == DeviceType.SLOT) {
                props = props.lightLevel(state -> 9);
            } else if (type == DeviceType.CRASH) {
                props = props.lightLevel(state -> 5);
            }
            Block block = new CasinoDeviceBlock(props, type);
            DEVICE_BLOCKS.put(type, block);
            DEVICE_ITEMS.put(type, new BlockItem(block, new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .stacksTo(type == DeviceType.CASHIER ? 64 : 1)));
        }
    }

    private CasinoRegistry() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, path);
    }

    public static Block block(DeviceType type) {
        return DEVICE_BLOCKS.get(type);
    }

    public static Item item(DeviceType type) {
        return DEVICE_ITEMS.get(type);
    }

    /** The device an item places, or null. */
    public static DeviceType deviceOf(Item item) {
        for (Map.Entry<DeviceType, Item> e : DEVICE_ITEMS.entrySet()) {
            if (e.getValue() == item) return e.getKey();
        }
        return null;
    }

    public static void initialize() {
        for (DeviceType type : DeviceType.values()) {
            Identifier id = id(type.id);
            Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, id), DEVICE_BLOCKS.get(type));
            Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, id), DEVICE_ITEMS.get(type));
        }
        Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, WALLET_ID), CHIP_WALLET);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CROUPIER_KEY, CROUPIER);

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            for (DeviceType type : DeviceType.values()) {
                entries.accept(DEVICE_ITEMS.get(type));
            }
        });
    }
}
