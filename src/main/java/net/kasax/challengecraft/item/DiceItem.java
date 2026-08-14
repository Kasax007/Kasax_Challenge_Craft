package net.kasax.challengecraft.item;

import net.minecraft.world.item.Item;

/**
 * The Würfel (d6) thrown by challenge 46. A bare shell on purpose: like
 * {@link ForceItemTrackerItem} and {@link LockoutBingoMapItem}, the right-click behaviour lives in
 * the challenge's own {@code UseItemCallback} (see {@code Chal_46_Dice.register()}) so the
 * challenge's {@code active} flag gates it and the item is inert when the challenge is off.
 *
 * <p>The item is never consumed by throwing — the callback only spawns a {@code DiceEntity}.
 */
public class DiceItem extends Item {
    public DiceItem(Properties settings) {
        super(settings);
    }
}
