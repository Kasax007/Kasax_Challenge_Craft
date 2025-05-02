package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.stream.IntStream;

/**
 * Chal_12_LimitedInventory
 *
 * Limits the player's 36‐slot main inventory down to `limitedSlots`.
 * All disabled slots show a single barrier item.  Hotbar is the last to disable.
 */
public class Chal_12_LimitedInventory {
    private static int limitedSlots = 36;
    private static boolean active = false;

    // first disable index 9–35 (main grid), then 0–8 (hotbar)
    private static final int[] DEACTIVATION_ORDER = IntStream.concat(
            IntStream.range(9, 36),
            IntStream.range(0, 9)
    ).toArray();

    /** Call this once at mod startup to hook the per‐tick handler. */
    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!active) return;
            for (PlayerEntity player : server.getPlayerManager().getPlayerList()) {
                limitInventory(player);
            }
        });
        ChallengeCraft.LOGGER.info("[Chal12] Registered tick callback");
    }

    /** Called by your ChallengeManager when the world’s slider value changes or on load. */
    public static void setLimitedSlots(int slots) {
        limitedSlots = Math.max(1, Math.min(slots, 36));
        ChallengeCraft.LOGGER.info("[Chal12] limitedSlots → {}", limitedSlots);
    }

    /** Enable or disable the inventory‐limiting behavior. */
    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal12] {}", active ? "activated" : "deactivated");
    }

    private static void limitInventory(PlayerEntity player) {
        var mainInv = player.getInventory().getMainStacks(); // now using public getter
        int toDisable = 36 - limitedSlots;

        // 1) Place a barrier in the first `toDisable` slots (per our order)
        for (int i = 0; i < toDisable; i++) {
            ItemStack blocked = new ItemStack(Items.BARRIER);
            blocked.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Blocked"));
            mainInv.set(DEACTIVATION_ORDER[i], blocked);
        }

        // 2) Clear out any remaining barriers beyond that point
        for (int i = toDisable; i < DEACTIVATION_ORDER.length; i++) {
            int idx = DEACTIVATION_ORDER[i];
            ItemStack s = mainInv.get(idx);
            if (s.getItem() == Items.BARRIER && s.getCount() == 1) {
                mainInv.set(idx, ItemStack.EMPTY);
            }
        }
    }
    /** Used by our Mixin to decide if we’re currently enforcing limits. */
    public static boolean isActive() {
        return active;
    }

    /** How many real slots the player may use. */
    public static int getLimitedSlots() {
        return limitedSlots;
    }

    /** The exact order in which slots were deactivated. */
    public static int[] getDeactivationOrder() {
        return DEACTIVATION_ORDER;
    }

}
