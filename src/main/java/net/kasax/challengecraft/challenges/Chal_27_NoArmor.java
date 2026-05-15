package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.util.BlockedBarrierItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Blocks armor slots with synthetic barrier items so the disabled state is visible in inventory screens. */
public class Chal_27_NoArmor {
    private static boolean active = false;

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!active) return;
            for (PlayerEntity player : server.getPlayerManager().getPlayerList()) {
                blockArmor(player);
            }
        });
        ChallengeCraft.LOGGER.info("[Chal27] Registered tick callback");
    }

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal27] {}", active ? "activated" : "deactivated");
    }

    private static void blockArmor(PlayerEntity player) {
        var inv = player.getInventory();
        for (int i = 0; i < 4; i++) {
            int slot = 36 + i;
            ItemStack current = inv.getStack(slot);
            if (current.getItem() != Items.BARRIER) {
                if (!current.isEmpty()) {
                    player.dropItem(current, false);
                }
                inv.setStack(slot, BlockedBarrierItem.create());
            }
        }
    }

    public static boolean isActive() {
        return active;
    }
}
