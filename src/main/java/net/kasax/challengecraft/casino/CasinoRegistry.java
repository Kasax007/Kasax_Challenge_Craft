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
import net.minecraft.world.level.material.PushReaction;

import java.util.EnumMap;
import java.util.Map;

/**
 * Blocks, items and the croupier entity of "The House Always Wins". Declared as static finals and
 * registered in {@link #initialize()}, following {@code InfiniteChestRegistry}.
 */
public final class CasinoRegistry {
    public static final Map<DeviceType, Block> DEVICE_BLOCKS = new EnumMap<>(DeviceType.class);
    public static final Map<DeviceType, Item> DEVICE_ITEMS = new EnumMap<>(DeviceType.class);
    public static final Map<DeviceType, Block> PART_BLOCKS = new EnumMap<>(DeviceType.class);
    public static final Map<CasinoBoothBlock.Kind, Block> BOOTH_BLOCKS = new EnumMap<>(CasinoBoothBlock.Kind.class);

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
                    .fireImmune()
                    .noSummon()
                    .noLootTable()
                    .build(CROUPIER_KEY);

    static {
        for (DeviceType type : DeviceType.values()) {
            Identifier id = id(type.id);
            SoundType sound = type == DeviceType.ROULETTE || type == DeviceType.CASHIER || type == DeviceType.PLINKO
                    ? SoundType.WOOD : SoundType.METAL;
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .noOcclusion()
                    // A multiblock: a piston must not tear the cells apart.
                    .pushReaction(PushReaction.IMMOVEABLE)
                    .sound(sound);
            if (type == DeviceType.CASHIER) {
                // The croupier's counter is part of his booth, which nothing may break.
                props = props.strength(-1.0f, 3_600_000.0f).noLootTable();
            } else {
                props = props.strength(2.5f, 6.0f);
            }
            if (type == DeviceType.SLOT || type == DeviceType.PLINKO) {
                props = props.lightLevel(state -> 9);
            } else if (type == DeviceType.CRASH) {
                props = props.lightLevel(state -> 7);
            }
            Block block = new CasinoDeviceBlock(props, type);
            DEVICE_BLOCKS.put(type, block);
            DEVICE_ITEMS.put(type, new BlockItem(block, new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .useBlockDescriptionPrefix()
                    .stacksTo(type == DeviceType.CASHIER ? 64 : 1)));
            if (!type.parts.isEmpty()) {
                PART_BLOCKS.put(type, new CasinoPartBlock(BlockBehaviour.Properties.of()
                        .setId(ResourceKey.create(Registries.BLOCK, partId(type)))
                        .strength(2.5f, 6.0f)
                        .noOcclusion()
                        .noLootTable()
                        .pushReaction(PushReaction.IMMOVEABLE)
                        .sound(sound), type));
            }
        }
        for (CasinoBoothBlock.Kind kind : CasinoBoothBlock.Kind.values()) {
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id(kind.id)))
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .pushReaction(PushReaction.IMMOVEABLE)
                    .sound(switch (kind) {
                        case CARPET -> SoundType.WOOL;
                        case POST -> SoundType.WOOD;
                        case LANTERN -> SoundType.LANTERN;
                        case TRIM -> SoundType.METAL;
                        default -> SoundType.POLISHED_DEEPSLATE;
                    });
            if (kind != CasinoBoothBlock.Kind.FLOOR && kind != CasinoBoothBlock.Kind.TRIM) props = props.noOcclusion();
            if (kind == CasinoBoothBlock.Kind.LANTERN) props = props.lightLevel(state -> 15);
            BOOTH_BLOCKS.put(kind, new CasinoBoothBlock(props, kind));
        }
    }

    private CasinoRegistry() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, path);
    }

    private static Identifier partId(DeviceType type) {
        return id(type == DeviceType.SLOT ? "slot_machine_top" : type.id + "_part");
    }

    public static Block block(DeviceType type) {
        return DEVICE_BLOCKS.get(type);
    }

    public static Block part(DeviceType type) {
        return PART_BLOCKS.get(type);
    }

    public static Block booth(CasinoBoothBlock.Kind kind) {
        return BOOTH_BLOCKS.get(kind);
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
            if (PART_BLOCKS.containsKey(type)) {
                Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, partId(type)), PART_BLOCKS.get(type));
            }
        }
        for (CasinoBoothBlock.Kind kind : CasinoBoothBlock.Kind.values()) {
            Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, id(kind.id)), BOOTH_BLOCKS.get(kind));
        }
        Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, WALLET_ID), CHIP_WALLET);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, CROUPIER_KEY, CROUPIER);

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            for (DeviceType type : DeviceType.values()) {
                if (type.isGame()) entries.accept(DEVICE_ITEMS.get(type));
            }
        });
    }
}
