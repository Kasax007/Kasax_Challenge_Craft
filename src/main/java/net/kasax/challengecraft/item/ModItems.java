package net.kasax.challengecraft.item;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import java.util.function.Function;

/** Registers standalone items used by challenges and progression rewards. */
public class ModItems {
    public static Item register(String name,
                                Function<Item.Properties, Item> factory,
                                Item.Properties settings) {
        Identifier id = Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = factory.apply(settings.setId(key));
        Registry.register(BuiltInRegistries.ITEM, id, item);
        return item;
    }

    public static final Item CHALLENGE_STICK = register(
            "challenge_stick",
            Item::new,
            new Item.Properties()
                    .stacksTo(1)
    );

    public static final Item LOCKOUT_BINGO_MAP = register(
            "lockout_bingo_map",
            LockoutBingoMapItem::new,
            new Item.Properties()
                    .stacksTo(1)
    );

    public static void initialize() {
        ChallengeCraft.LOGGER.info("Registered items for {}", ChallengeCraft.MOD_ID);
    }
}
