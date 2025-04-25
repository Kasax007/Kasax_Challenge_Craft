package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class Chal_10_RandomItem {
    private static boolean active = false;
    private static int tickCounter = 0;
    private static final List<Identifier> ITEMS = new ArrayList<>();

    static {
        Registries.ITEM.forEach(item -> ITEMS.add(Registries.ITEM.getId(item)));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter = (tickCounter + 1) % 600; // 600 ticks = 30s
            if (tickCounter == 0) {
                server.getPlayerManager().getPlayerList().forEach(player -> {
                    Identifier id = ITEMS.get(player.getWorld().random.nextInt(ITEMS.size()));
                    player.giveItemStack(new ItemStack(Registries.ITEM.get(id)));
                });
            }
        });
    }
    public static void setActive(boolean v) { active = v; }
}
