package net.kasax.challengecraft.challenges;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Supplies the item pool used when walking converts ground into random drops. */
public class Chal_17_WalkRandomItem {
    private static boolean active = false;
    private static final List<Identifier> ITEMS = new ArrayList<>();

    static {
        BuiltInRegistries.ITEM.forEach(item -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("challengecraft")) return;
            ITEMS.add(id);
        });
    }

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static ItemStack getRandomItem(net.minecraft.util.RandomSource random) {
        Identifier id = ITEMS.get(random.nextInt(ITEMS.size()));
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }
}
