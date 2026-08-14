package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Adds a random enchantment level to each player's held item on a timer. */
public class Chal_13_RandomEnchantment {
    private static boolean active = false;
    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter = (tickCounter + 1) % 600; // 600 ticks = 30s
            if (tickCounter == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ItemStack stack = player.getMainHandItem();
                    if (stack.isEmpty()) continue;

                    var registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    registry.getRandom(player.level().getRandom()).ifPresent(enchantEntry -> {
                        int currentLevel = EnchantmentHelper.getItemEnchantmentLevel(enchantEntry, stack);
                        stack.enchant(enchantEntry, currentLevel + 1);
                    });
                }
            }
        });
    }

    public static void setActive(boolean v) {
        active = v;
        if (!active) tickCounter = 0;
    }

    public static boolean isActive() {
        return active;
    }
}
