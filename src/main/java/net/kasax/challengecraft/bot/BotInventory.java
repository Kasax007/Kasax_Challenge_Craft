package net.kasax.challengecraft.bot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;

/** Counting and finding things in a bot's inventory. */
public final class BotInventory {
    private BotInventory() {
    }

    public static int count(Player p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(item)) n += s.getCount();
        return n;
    }

    public static int count(Player p, TagKey<Item> tag) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(tag)) n += s.getCount();
        return n;
    }

    public static int slotOf(Player p, Item item) {
        var items = p.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) if (items.get(i).is(item)) return i;
        return -1;
    }
}
