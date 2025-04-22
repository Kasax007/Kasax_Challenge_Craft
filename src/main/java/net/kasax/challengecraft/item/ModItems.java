package net.kasax.challengecraft.item;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item CHALLENGE_STICK = registerItem("challenge_stick", new Item(new Item.Settings().maxCount(1)));

    public static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM,
                Identifier.of(ChallengeCraft.MOD_ID, name),
                item
        );
    }

    public static void registerModItems() {
        ChallengeCraft.LOGGER.info("Registering Mod Items for " + ChallengeCraft.MOD_ID);
    }
}
