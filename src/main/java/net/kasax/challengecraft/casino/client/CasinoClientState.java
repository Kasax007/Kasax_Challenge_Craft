package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.kasax.challengecraft.casino.DeviceType;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client copy of everything the casino shows. Separate from the server statics on purpose: in
 * singleplayer both sides share a JVM, and the client must never read or mutate server state.
 */
@Environment(EnvType.CLIENT)
public final class CasinoClientState {
    public record DevicePos(BlockPos pos, DeviceType type) {
    }

    /** What the player is aiming at on a device right now (refreshed every client tick). */
    public record Aim(BlockPos master, DeviceType type, DeviceLayouts.Zone zone) {
    }

    /** A plinko ball on its way down; {@code landedAt} is set once it reached its bucket. */
    public record PlinkoDrop(CasinoNet.PlinkoBall ball, long startTick, long landedAt) {
    }

    public static volatile CasinoNet.State state;
    public static long stateReceivedAt;
    public static final List<DevicePos> DEVICES = new ArrayList<>();
    public static final Map<Long, SlotAnimation> SLOTS = new HashMap<>();
    /** The last finished play of every machine: its reels keep showing it until the next spin. */
    public static final Map<Long, SlotAnimation> SLOT_LAST = new HashMap<>();
    public static final List<PlinkoDrop> PLINKO_BALLS = new ArrayList<>();
    /** Per board: the ball that landed last, and the viewer's own last ball. */
    public static final Map<Long, PlinkoDrop> PLINKO_LAST = new HashMap<>();
    public static final Map<Long, PlinkoDrop> PLINKO_MINE = new HashMap<>();
    public static volatile Aim aim;
    public static final Map<Long, CasinoNet.RouletteState> ROULETTE = new HashMap<>();
    public static final Map<Long, Long> ROULETTE_RECEIVED = new HashMap<>();
    public static final Map<Long, CasinoNet.CrashState> CRASH = new HashMap<>();
    public static final Map<Long, Long> CRASH_RECEIVED = new HashMap<>();
    public static CasinoNet.Blackjack blackjack;
    /** Client tick counter used by every animation (advanced in {@link CasinoClient}). */
    public static long clientTick;

    private CasinoClientState() {
    }

    public static void setDevices(int[] data) {
        DEVICES.clear();
        DeviceType[] types = DeviceType.values();
        for (int i = 0; i + 3 < data.length; i += 4) {
            int t = data[i + 3];
            if (t < 0 || t >= types.length) continue;
            DEVICES.add(new DevicePos(new BlockPos(data[i], data[i + 1], data[i + 2]), types[t]));
        }
    }

    /** Seconds left until the next fee, counted down locally between state packets. */
    public static int secondsToFee() {
        CasinoNet.State s = state;
        if (s == null) return 0;
        long elapsedTicks = (System.currentTimeMillis() - stateReceivedAt) / 50L;
        return (int) Math.max(0, (s.ticksToFee() - elapsedTicks) / 20);
    }

    public static long balance() {
        CasinoNet.State s = state;
        return s == null ? 0 : s.balance();
    }

    public static void clear() {
        state = null;
        DEVICES.clear();
        SLOTS.clear();
        SLOT_LAST.clear();
        PLINKO_BALLS.clear();
        PLINKO_LAST.clear();
        PLINKO_MINE.clear();
        aim = null;
        ROULETTE.clear();
        ROULETTE_RECEIVED.clear();
        CRASH.clear();
        CRASH_RECEIVED.clear();
        blackjack = null;
    }
}
