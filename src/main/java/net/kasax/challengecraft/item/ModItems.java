// src/main/java/net/kasax/challengecraft/item/ModItems.java
package net.kasax.challengecraft.item;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ModItems {
    // 1) use the same register helper as the docs
    public static Item register(String name,
                                Function<Item.Settings, Item> factory,
                                Item.Settings settings) {
        Identifier id = Identifier.of(ChallengeCraft.MOD_ID, name);
        // build a RegistryKey so registryKey(...) can pick up the translationKey + id
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        // inject it into settings _before_ constructing your Item
        Item item = factory.apply(settings.registryKey(key));
        // now register with the vanilla item registry
        Registry.register(Registries.ITEM, id, item);
        return item;
    }

    // 2) actually create & register your stick
    public static final Item CHALLENGE_STICK = register(
            "challenge_stick",
            Item::new,
            new Item.Settings()
                    .maxCount(1)
    );

    /** Call this from your ModInitializer to ensure the class loads */
    public static void initialize() {
        // (nothing needed here, calling it forces the static inits above)
        ChallengeCraft.LOGGER.info("Registered items for {}", ChallengeCraft.MOD_ID);
    }
}
