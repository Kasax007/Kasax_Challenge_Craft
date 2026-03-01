package net.kasax.challengecraft.challenges;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

public class Chal_18_DamageRandomItem {
    private static boolean active = false;
    private static final List<Identifier> ITEMS = new ArrayList<>();

    static {
        Registries.ITEM.forEach(item -> ITEMS.add(Registries.ITEM.getId(item)));
    }

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static ItemStack getRandomItem(net.minecraft.util.math.random.Random random) {
        Identifier id = ITEMS.get(random.nextInt(ITEMS.size()));
        return new ItemStack(Registries.ITEM.get(id));
    }
}
