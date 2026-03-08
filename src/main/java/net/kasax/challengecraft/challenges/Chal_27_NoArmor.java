package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

/**
 * Chal_27_NoArmor
 *
 * Blocks all 4 armor slots of the player by placing barrier items in them.
 */
public class Chal_27_NoArmor {
    private static boolean active = false;

    /** Call this once at mod startup to hook the per-tick handler. */
    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!active) return;
            for (PlayerEntity player : server.getPlayerManager().getPlayerList()) {
                blockArmor(player);
            }
        });
        ChallengeCraft.LOGGER.info("[Chal27] Registered tick callback");
    }

    /** Enable or disable the armor-blocking behavior. */
    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal27] {}", active ? "activated" : "deactivated");
    }

    private static void blockArmor(PlayerEntity player) {
        var inv = player.getInventory();
        for (int i = 0; i < 4; i++) {
            int slot = 36 + i; // armor slots
            ItemStack current = inv.getStack(slot);
            if (current.getItem() != Items.BARRIER) {
                if (!current.isEmpty()) {
                    player.dropItem(current, false);
                }
                ItemStack blocked = new ItemStack(Items.BARRIER);
                blocked.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Blocked"));
                inv.setStack(slot, blocked);
            }
        }
    }

    /** Used by Mixins or Managers to decide if we're currently enforcing limits. */
    public static boolean isActive() {
        return active;
    }
}
