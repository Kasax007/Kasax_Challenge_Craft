package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.util.BlockedBarrierItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.stream.IntStream;

/** Limits the usable inventory while keeping the blocked slots visible to the player. */
public class Chal_12_LimitedInventory {
    private static int limitedSlots = 36;
    private static boolean active = false;

    // Preserve the hotbar as long as possible; losing the main grid is less disruptive in play.
    private static final int[] DEACTIVATION_ORDER = IntStream.concat(
            IntStream.range(9, 36),
            IntStream.range(0, 9)
    ).toArray();

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!active) return;
            for (Player player : server.getPlayerList().getPlayers()) {
                limitInventory(player);
            }
        });

        ChallengeCraft.LOGGER.info("[Chal12] Registered tick callback");
    }

    public static void setLimitedSlots(int slots) {
        limitedSlots = Math.max(1, Math.min(slots, 36));
        ChallengeCraft.LOGGER.info("[Chal12] limitedSlots → {}", limitedSlots);
    }

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal12] {}", active ? "activated" : "deactivated");
    }

    private static void limitInventory(Player player) {
        var mainInv = player.getInventory().getNonEquipmentItems();
        int toDisable = 36 - limitedSlots;

        for (int i = 0; i < toDisable; i++) {
            mainInv.set(DEACTIVATION_ORDER[i], BlockedBarrierItem.create());
        }

        for (int i = toDisable; i < DEACTIVATION_ORDER.length; i++) {
            int idx = DEACTIVATION_ORDER[i];
            ItemStack s = mainInv.get(idx);
            if (BlockedBarrierItem.isBlockedBarrier(s)) {
                mainInv.set(idx, ItemStack.EMPTY);
            }
        }
    }
    public static boolean isActive() {
        return active;
    }

    public static int getLimitedSlots() {
        return limitedSlots;
    }

    public static int[] getDeactivationOrder() {
        return DEACTIVATION_ORDER;
    }

}
