package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.util.BlockedBarrierItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Blocks armor slots with synthetic barrier items so the disabled state is visible in inventory screens. */
public class Chal_27_NoArmor {
    private static boolean active = false;

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!active) return;
            for (Player player : server.getPlayerList().getPlayers()) {
                blockArmor(player);
            }
        });
        ChallengeCraft.LOGGER.info("[Chal27] Registered tick callback");
    }

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal27] {}", active ? "activated" : "deactivated");
    }

    private static void blockArmor(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < 4; i++) {
            int slot = 36 + i;
            ItemStack current = inv.getItem(slot);
            if (current.getItem() != Items.BARRIER) {
                if (!current.isEmpty()) {
                    player.drop(current, false);
                }
                inv.setItem(slot, BlockedBarrierItem.create());
            }
        }
    }

    public static boolean isActive() {
        return active;
    }
}
