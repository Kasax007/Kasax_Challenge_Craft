package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

public class Chal_13_RandomEnchantment {
    private static boolean active = false;
    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter = (tickCounter + 1) % 600; // 600 ticks = 30s
            if (tickCounter == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ItemStack stack = player.getMainHandStack();
                    if (stack.isEmpty()) continue;

                    var registry = player.getWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                    registry.getRandom(player.getWorld().getRandom()).ifPresent(enchantEntry -> {
                        int currentLevel = EnchantmentHelper.getLevel(enchantEntry, stack);
                        stack.addEnchantment(enchantEntry, currentLevel + 1);
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
