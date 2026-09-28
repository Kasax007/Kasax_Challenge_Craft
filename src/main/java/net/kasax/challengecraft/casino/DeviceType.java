package net.kasax.challengecraft.casino;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The casino's placeable devices. Four of them are games, bought once from the croupier for raw
 * resources (the purchase also unlocks their crafting recipe for that player); the cashier counter
 * is the croupier's own and cannot be obtained.
 *
 * <p>The order of the purchasable ones is the intended progression: slots cost Overworld basics,
 * plinko a little gold and lapis, crash needs gold and gunpowder, roulette diamonds and an emerald.
 *
 * <p>Every device is a small multiblock: the <b>master</b> block (the one the item places, which
 * carries the model and the device registration) plus invisible or partial {@link CasinoPartBlock}s
 * that give the rest of the device its hitbox. Part offsets are given in the device's own frame
 * (see {@link DeviceSpace}): {@code right} as seen by a player standing in front, {@code up}, and
 * {@code back} away from that player.
 */
public enum DeviceType {
    SLOT("slot_machine", true, List.of(
            new Cost(Items.IRON_INGOT, 12),
            new Cost(Items.REDSTONE, 8),
            new Cost(Items.GOLD_INGOT, 1)),
            List.of(new Part(0, 1, 0))),
    PLINKO("plinko_board", true, List.of(
            new Cost(Items.IRON_INGOT, 8),
            new Cost(Items.GOLD_INGOT, 4),
            new Cost(Items.LAPIS_LAZULI, 12)),
            List.of(new Part(1, 0, 0), new Part(0, 1, 0), new Part(1, 1, 0))),
    CRASH("crash_pad", true, List.of(
            new Cost(Items.GOLD_INGOT, 8),
            new Cost(Items.GUNPOWDER, 16),
            new Cost(Items.PAPER, 8)),
            List.of(new Part(1, 0, 0), new Part(1, 1, 0))),
    ROULETTE("roulette_table", true, List.of(
            new Cost(Items.DIAMOND, 4),
            new Cost(Items.GOLD_INGOT, 8),
            new Cost(Items.EMERALD, 1)),
            List.of(new Part(1, 0, 0), new Part(2, 0, 0), new Part(3, 0, 0),
                    new Part(0, 0, 1), new Part(1, 0, 1), new Part(2, 0, 1), new Part(3, 0, 1))),
    CASHIER("cashier_counter", false, List.of(), List.of());

    public record Cost(Item item, int count) {
    }

    /** A cell of the device relative to its master block, in the device frame. */
    public record Part(int right, int up, int back) {
    }

    public final String id;
    public final boolean purchasable;
    public final List<Cost> price;
    /** Part cells in order; a part block's {@code PART} property is its index here plus one. */
    public final List<Part> parts;

    DeviceType(String id, boolean purchasable, List<Cost> price, List<Part> parts) {
        this.id = id;
        this.purchasable = purchasable;
        this.price = price;
        this.parts = parts;
    }

    public boolean isGame() {
        return this != CASHIER;
    }

    public static DeviceType byId(String id) {
        for (DeviceType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }

    /** The purchasable devices in progression order (as shown at the cashier). */
    public static List<DeviceType> games() {
        return List.of(SLOT, PLINKO, CRASH, ROULETTE);
    }
}
