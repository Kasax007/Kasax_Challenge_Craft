package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Gives each player a random vanilla item every thirty seconds. */
public class Chal_10_RandomItem {
    private static boolean active = false;
    private static int tickCounter = 0;
    private static final List<Identifier> ITEMS = new ArrayList<>();

    static {
        BuiltInRegistries.ITEM.forEach(item -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("challengecraft")) return;
            ITEMS.add(id);
        });
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter = (tickCounter + 1) % 600; // 600 ticks = 30s
            if (tickCounter == 0) {
                server.getPlayerList().getPlayers().forEach(player -> {
                    Identifier id = ITEMS.get(player.level().getRandom().nextInt(ITEMS.size()));
                    player.addItem(new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
                });
            }
        });
    }
    public static void setActive(boolean v) { active = v; }
    public static boolean isActive() { return active; }
}
