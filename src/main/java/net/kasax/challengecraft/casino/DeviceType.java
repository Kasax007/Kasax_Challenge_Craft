package net.kasax.challengecraft.casino;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The casino's placeable devices. Three of them are games, bought once from the croupier for raw
 * resources (the purchase also unlocks their crafting recipe for that player); the cashier counter
 * is decoration that stands in front of the croupier.
 *
 * <p>The order of the purchasable ones is the intended progression: slots cost Overworld basics,
 * crash needs gold and gunpowder, roulette needs diamonds and an emerald.
 */
public enum DeviceType {
    SLOT("slot_machine", true, List.of(
            new Cost(Items.IRON_INGOT, 12),
            new Cost(Items.REDSTONE, 8),
            new Cost(Items.GOLD_INGOT, 1))),
    CRASH("crash_pad", true, List.of(
            new Cost(Items.GOLD_INGOT, 8),
            new Cost(Items.GUNPOWDER, 16),
            new Cost(Items.PAPER, 8))),
    ROULETTE("roulette_table", true, List.of(
            new Cost(Items.DIAMOND, 4),
            new Cost(Items.GOLD_INGOT, 8),
            new Cost(Items.EMERALD, 1))),
    CASHIER("cashier_counter", false, List.of());

    public record Cost(Item item, int count) {
    }

    public final String id;
    public final boolean purchasable;
    public final List<Cost> price;

    DeviceType(String id, boolean purchasable, List<Cost> price) {
        this.id = id;
        this.purchasable = purchasable;
        this.price = price;
    }

    public static DeviceType byId(String id) {
        for (DeviceType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}
