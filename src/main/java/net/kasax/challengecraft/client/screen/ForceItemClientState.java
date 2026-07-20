package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.data.ForceItemBattleSavedData;
import net.kasax.challengecraft.network.ForceItemSyncPacket;

import java.util.List;
import java.util.UUID;

/** Client cache for the latest Force Item Battle sync packet. */
public final class ForceItemClientState {
    private static ForceItemSyncPacket latest =
            new ForceItemSyncPacket(ForceItemBattleSavedData.STATE_IDLE, 0L, List.of());
    private static long lastSyncMillis = 0L;

    private ForceItemClientState() {
    }

    public static void update(ForceItemSyncPacket packet) {
        latest = packet;
        lastSyncMillis = System.currentTimeMillis();
    }

    public static ForceItemSyncPacket get() {
        return latest;
    }

    public static void clear() {
        latest = new ForceItemSyncPacket(ForceItemBattleSavedData.STATE_IDLE, 0L, List.of());
        lastSyncMillis = 0L;
    }

    public static ForceItemSyncPacket.PlayerEntry getEntry(UUID uuid) {
        for (ForceItemSyncPacket.PlayerEntry entry : latest.players()) {
            if (entry.uuid().equals(uuid)) {
                return entry;
            }
        }
        return null;
    }

    /** Remaining battle ticks, extrapolated between sync packets so the HUD timer runs smoothly. */
    public static long remainingTicksNow() {
        if (latest.state() != ForceItemBattleSavedData.STATE_RUNNING) {
            return 0L;
        }
        long elapsed = Math.max(0L, (System.currentTimeMillis() - lastSyncMillis) / 50L);
        return Math.max(0L, latest.remainingTicks() - elapsed);
    }
}
