package net.kasax.challengecraft.item;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

/** Registers standalone items used by challenges and progression rewards. */
public class ModItems {
    public static Item register(String name,
                                Function<Item.Settings, Item> factory,
                                Item.Settings settings) {
        Identifier id = Identifier.of(ChallengeCraft.MOD_ID, name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item item = factory.apply(settings.registryKey(key));
        Registry.register(Registries.ITEM, id, item);
        return item;
    }

    public static final Item CHALLENGE_STICK = register(
            "challenge_stick",
            Item::new,
            new Item.Settings()
                    .maxCount(1)
    );

    public static final Item LOCKOUT_BINGO_MAP = register(
            "lockout_bingo_map",
            LockoutBingoMapItem::new,
            new Item.Settings()
                    .maxCount(1)
    );

    public static void initialize() {
        ChallengeCraft.LOGGER.info("Registered items for {}", ChallengeCraft.MOD_ID);
    }
}
