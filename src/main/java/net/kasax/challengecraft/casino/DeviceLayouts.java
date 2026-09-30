package net.kasax.challengecraft.casino;

import java.util.List;

/**
 * Where everything sits on the devices, in {@link DeviceSpace} pixels: their outlines, the chip
 * trays, the roulette layout and the plinko board. Server and client read the same numbers, so what
 * a player sees highlighted is exactly what a click does.
 *
 * <p>The casino has no menus for playing: a device is played by looking at a part of it and
 * right-clicking. {@link #zoneAt} turns the spot a player aims at into a {@link Zone}.
 */
public final class DeviceLayouts {
    private DeviceLayouts() {
    }

    // ---- zones --------------------------------------------------------------------------------

    public static final int NONE = 0;
    /** The device itself: spin, drop a ball, join a round or cash out. */
    public static final int PLAY = 1;
    /** A chip of the tray; {@code a} = slot 0..4 of the visible window. */
    public static final int CHIP = 2;
    /** The crash console's big button: join or cash out. */
    public static final int BUTTON = 3;
    /** Roulette: take all of one's chips back. */
    public static final int TAKE_BACK = 4;
    /** Roulette: a bet; {@code a} = {@link RouletteMath} kind, {@code b} = target. */
    public static final int BET = 5;
    /** Cashier: the bell that closes the deal for the items on the counter. */
    public static final int BELL = 6;
    /** Cashier: an item lying on the counter; {@code a} = its slot 0..8. */
    public static final int PENDING = 7;
    /** Cashier during a hand of blackjack: {@code a} = 0 hit, 1 stand, 2 double, 3 split. */
    public static final int BJ = 8;

    public record Zone(int kind, int a, int b) {
        public static final Zone EMPTY = new Zone(NONE, 0, 0);
    }

    // ---- chip trays ---------------------------------------------------------------------------

    /** Selectable stakes in chips; one step past the last is all-in. */
    public static final long[] STAKES = {10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000,
            50_000, 100_000, 250_000, 500_000, 1_000_000};
    public static final int ALL_IN_LEVEL = STAKES.length;
    public static final int TRAY_SLOTS = 5;

    /** The level shown in tray slot {@code slot} while the player's stake is {@code level}. */
    /**
     * An amount of chips as the real chips that make it up, largest first: the stake levels
     * ({@link #STAKES}) a cashier would hand over. At most {@code max} chips; whatever does not fit
     * (and any rest below the smallest chip) is left out.
     */
    public static int[] chipsFor(long chips, int max) {
        List<Integer> out = new java.util.ArrayList<>();
        long left = chips;
        for (int level = STAKES.length - 1; level >= 0 && out.size() < max; level--) {
            while (left >= STAKES[level] && out.size() < max) {
                out.add(level);
                left -= STAKES[level];
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    public static int trayLevel(int level, int slot) {
        int current = level < 0 ? ALL_IN_LEVEL : level;
        int start = Math.max(0, Math.min(ALL_IN_LEVEL + 1 - TRAY_SLOTS, current - TRAY_SLOTS / 2));
        return start + slot;
    }

    /** Tray geometry: centre u of slot 0, spacing, v, top y and chip radius. */
    public record Tray(double u0, double step, double v, double y, double radius) {
        public double u(int slot) {
            return u0 + step * slot;
        }
    }

    public static Tray tray(DeviceType type) {
        return switch (type) {
            case SLOT -> new Tray(3.2, 2.4, 1.7, 4.2, 1.0);
            case PLINKO -> new Tray(10.0, 3.0, 1.7, 6.2, 1.25);
            case CRASH -> new Tray(18.6, 2.45, 4.6, 12.0, 1.0);
            case ROULETTE -> new Tray(26.0, 4.0, 3.5, 14.0, 1.6);
            default -> null;
        };
    }

    // ---- outlines -----------------------------------------------------------------------------

    /** The solid parts of a device as {u0, y0, v0, u1, y1, v1} boxes. */
    public static List<double[]> outline(DeviceType type) {
        return switch (type) {
            case SLOT -> List.of(
                    new double[]{1, 0, 2, 15, 22, 15},
                    new double[]{2, 22, 3, 14, 28, 14},
                    new double[]{1.5, 3, 0.2, 14.5, 4.2, 3.2});
            case PLINKO -> List.of(
                    new double[]{0.5, 0, 3, 31.5, 6, 14},
                    new double[]{7, 5, 0.2, 25, 6.2, 3.2},
                    new double[]{0, 6, PLINKO_BOARD_V, 32, 32, 14});
            case CRASH -> List.of(
                    new double[]{0, 0, 0, 16, 5, 16},
                    new double[]{17, 0, 2, 31, 12, 15},
                    new double[]{17, 12, CRASH_SCREEN_V, 31, 29, 15});
            case ROULETTE -> List.of(
                    new double[]{0, 0, 0, 64, 14, 32},
                    new double[]{0, 14, 0, 64, 14.8, 0.8},
                    new double[]{0, 14, 31.2, 64, 14.8, 32},
                    new double[]{0, 14, 0, 0.8, 14.8, 32},
                    new double[]{63.2, 14, 0, 64, 14.8, 32},
                    new double[]{1, 14, 30, 17, 30, 32});
            case CASHIER -> List.of(new double[]{0, 0, 1, 16, 16.8, 15});
        };
    }

    // ---- the plinko board ---------------------------------------------------------------------

    public static final double PLINKO_BOARD_V = 9.0;
    public static final double PLINKO_CENTER_U = 16.0;
    /** Horizontal distance between pegs, which is also the width of a bucket. */
    public static final double PLINKO_PEG_DX = 28.0 / 13.0;
    public static final double PLINKO_TOP_Y = 29.0;
    public static final double PLINKO_ROW_DY = 1.62;
    public static final double PLINKO_BUCKET_Y = 7.0;

    public static double plinkoPegU(int row, int index) {
        return PLINKO_CENTER_U + (index - (row + 2) / 2.0) * PLINKO_PEG_DX;
    }

    public static double plinkoPegY(int row) {
        return PLINKO_TOP_Y - row * PLINKO_ROW_DY;
    }

    public static double plinkoBucketU(int bucket) {
        return PLINKO_CENTER_U + (bucket - PlinkoMath.ROWS / 2.0) * PLINKO_PEG_DX;
    }

    // ---- the crash console --------------------------------------------------------------------

    public static final double CRASH_SCREEN_V = 13.0;
    /** The screen's display area on the console (front face at v = CRASH_SCREEN_V). */
    public static final double CRASH_SCREEN_U0 = 17.8, CRASH_SCREEN_U1 = 30.2, CRASH_SCREEN_Y0 = 13.2, CRASH_SCREEN_Y1 = 28.2;
    public static final double CRASH_BUTTON_U = 24.0, CRASH_BUTTON_V = 9.5, CRASH_BUTTON_R = 2.3;

    // ---- the roulette layout ------------------------------------------------------------------

    public static final double WHEEL_U = 9.0, WHEEL_V = 16.0, WHEEL_R = 7.4, TABLE_TOP = 14.0;
    public static final double ZERO_U0 = 19.0, GRID_U0 = 22.5, CELL_W = 3.1, GRID_V0 = 15.0, CELL_H = 4.0;
    public static final double GRID_U1 = GRID_U0 + 12 * CELL_W;
    public static final double COLUMN_U1 = GRID_U1 + 2.8;
    public static final double DOZEN_V0 = 11.0, EVEN_V0 = 7.0;
    public static final double TAKE_BACK_U0 = 47.0, TAKE_BACK_U1 = 60.0, TAKE_BACK_V0 = 1.6, TAKE_BACK_V1 = 5.4;
    /** How close to a line between two numbers counts as a bet on that line. */
    private static final double EDGE = 0.55;
    /** The six even-money boxes, left to right. */
    public static final int[] EVEN_KINDS = {RouletteMath.LOW, RouletteMath.EVEN, RouletteMath.RED, RouletteMath.BLACK,
            RouletteMath.ODD, RouletteMath.HIGH};

    public static double columnCenterU(int column) {
        return GRID_U0 + (column + 0.5) * CELL_W;
    }

    public static double rowCenterV(int row) {
        return GRID_V0 + (row + 0.5) * CELL_H;
    }

    /** Where the chips of a bet lie on the felt: {u, v}. */
    public static double[] betAnchor(int kind, int target) {
        switch (kind) {
            case RouletteMath.STRAIGHT -> {
                if (target == 0) return new double[]{(ZERO_U0 + GRID_U0) / 2, GRID_V0 + 1.5 * CELL_H};
                int n = target - 1;
                return new double[]{columnCenterU(n / 3), rowCenterV(n % 3)};
            }
            case RouletteMath.SPLIT -> {
                int a = target / 100 - 1, b = target % 100 - 1;
                if (b - a == 3) return new double[]{GRID_U0 + (a / 3 + 1) * CELL_W, rowCenterV(a % 3)};
                return new double[]{columnCenterU(a / 3), GRID_V0 + (a % 3 + 1) * CELL_H};
            }
            case RouletteMath.STREET -> {
                return new double[]{columnCenterU((target - 1) / 3), GRID_V0};
            }
            case RouletteMath.SIX_LINE -> {
                return new double[]{GRID_U0 + ((target - 1) / 3 + 1) * CELL_W, GRID_V0};
            }
            case RouletteMath.CORNER -> {
                int n = target - 1;
                return new double[]{GRID_U0 + (n / 3 + 1) * CELL_W, GRID_V0 + (n % 3 + 1) * CELL_H};
            }
            case RouletteMath.DOZEN -> {
                return new double[]{GRID_U0 + (target * 4 + 2) * CELL_W, (DOZEN_V0 + GRID_V0) / 2};
            }
            case RouletteMath.COLUMN -> {
                return new double[]{(GRID_U1 + COLUMN_U1) / 2, rowCenterV(target)};
            }
            default -> {
                int i = 0;
                for (int k = 0; k < EVEN_KINDS.length; k++) if (EVEN_KINDS[k] == kind) i = k;
                return new double[]{GRID_U0 + (i * 2 + 1) * CELL_W, (EVEN_V0 + DOZEN_V0) / 2};
            }
        }
    }

    private static Zone rouletteZone(double u, double y, double v) {
        if (y < TABLE_TOP - 1.0 || y > TABLE_TOP + 3.5) return Zone.EMPTY;
        Tray t = tray(DeviceType.ROULETTE);
        for (int i = 0; i < TRAY_SLOTS; i++) {
            if (sq(u - t.u(i)) + sq(v - t.v()) <= sq(t.radius() * 1.3)) return new Zone(CHIP, i, 0);
        }
        if (u >= TAKE_BACK_U0 && u <= TAKE_BACK_U1 && v >= TAKE_BACK_V0 && v <= TAKE_BACK_V1) {
            return new Zone(TAKE_BACK, 0, 0);
        }
        double gridV1 = GRID_V0 + 3 * CELL_H;
        if (u >= ZERO_U0 && u < GRID_U0 - 0.2 && v >= GRID_V0 && v <= gridV1) {
            return new Zone(BET, RouletteMath.STRAIGHT, 0);
        }
        if (u >= GRID_U1 && u <= COLUMN_U1 && v >= GRID_V0 && v <= gridV1) {
            return new Zone(BET, RouletteMath.COLUMN, (int) Math.min(2, Math.floor((v - GRID_V0) / CELL_H)));
        }
        if (u < GRID_U0 || u > GRID_U1) return Zone.EMPTY;
        if (v >= GRID_V0 - EDGE && v <= gridV1) {
            double fc = (u - GRID_U0) / CELL_W, fr = (v - GRID_V0) / CELL_H;
            int c = (int) Math.min(11, Math.max(0, Math.floor(fc)));
            int r = (int) Math.min(2, Math.max(0, Math.floor(fr)));
            int bc = (int) Math.round(fc), br = (int) Math.round(fr);
            boolean nearV = Math.abs(fc - bc) * CELL_W < EDGE && bc >= 1 && bc <= 11;
            boolean nearH = Math.abs(fr - br) * CELL_H < EDGE && br >= 1 && br <= 2;
            if (Math.abs(v - GRID_V0) < EDGE) {
                return nearV ? new Zone(BET, RouletteMath.SIX_LINE, 3 * (bc - 1) + 1)
                        : new Zone(BET, RouletteMath.STREET, 3 * c + 1);
            }
            if (nearV && nearH) return new Zone(BET, RouletteMath.CORNER, 3 * (bc - 1) + br);
            if (nearH) {
                int a = 3 * c + br;
                return new Zone(BET, RouletteMath.SPLIT, a * 100 + a + 1);
            }
            if (nearV) {
                int a = 3 * (bc - 1) + r + 1;
                return new Zone(BET, RouletteMath.SPLIT, a * 100 + a + 3);
            }
            return new Zone(BET, RouletteMath.STRAIGHT, 3 * c + r + 1);
        }
        if (v >= DOZEN_V0 && v < GRID_V0 - EDGE) {
            return new Zone(BET, RouletteMath.DOZEN, (int) Math.min(2, Math.floor((u - GRID_U0) / (4 * CELL_W))));
        }
        if (v >= EVEN_V0 && v < DOZEN_V0) {
            return new Zone(BET, EVEN_KINDS[(int) Math.min(5, Math.floor((u - GRID_U0) / (2 * CELL_W)))], 0);
        }
        return Zone.EMPTY;
    }

    // ---- the cashier counters -----------------------------------------------------------------

    /** Top of the marble counter slab. */
    public static final double COUNTER_TOP = 14.5;
    /** The deposit tray on the middle counter: 3 × 3 slots, slot i at (SLOT_U[i % 3], SLOT_V[i / 3]). */
    public static final double[] SLOT_U = {3.4, 8.0, 12.6};
    public static final double[] SLOT_V = {6.2, 9.6, 13.0};
    public static final int DEPOSIT_SLOTS = 9;
    public static final double BELL_U = 13.3, BELL_V = 3.1, BELL_R = 1.5;
    /** The four blackjack plaques along the front edge of a counter. */
    public static final double PLAQUE_V0 = 1.3, PLAQUE_V1 = 3.5, PLAQUE_U0 = 0.6, PLAQUE_GAP = 0.3;
    public static final double PLAQUE_W = (16 - 2 * PLAQUE_U0 - 3 * PLAQUE_GAP) / 4;

    public static double plaqueU(int i) {
        return PLAQUE_U0 + i * (PLAQUE_W + PLAQUE_GAP);
    }

    /**
     * The zone on counter {@code index} (0..2): with a hand of blackjack running on it for this
     * player, its four plaques; otherwise the bell and the deposit tray of the middle counter.
     */
    public static Zone counterZone(int index, boolean playing, double u, double y, double v) {
        if (Math.abs(y - COUNTER_TOP) > 3.5) return new Zone(PLAY, 0, 0);
        if (playing) {
            if (v >= PLAQUE_V0 - 0.3 && v <= PLAQUE_V1 + 0.3) {
                for (int i = 0; i < 4; i++) {
                    if (u >= plaqueU(i) - 0.15 && u <= plaqueU(i) + PLAQUE_W + 0.15) return new Zone(BJ, i, 0);
                }
            }
            return new Zone(PLAY, 0, 0);
        }
        if (index != 1) return new Zone(PLAY, 0, 0);
        if (sq(u - BELL_U) + sq(v - BELL_V) <= sq(BELL_R)) return new Zone(BELL, 0, 0);
        for (int i = 0; i < DEPOSIT_SLOTS; i++) {
            if (Math.abs(u - SLOT_U[i % 3]) <= 2.1 && Math.abs(v - SLOT_V[i / 3]) <= 1.6) return new Zone(PENDING, i, 0);
        }
        return new Zone(PLAY, 0, 0);
    }

    // ---- hit zones ----------------------------------------------------------------------------

    /** The zone at a device-space point, as returned by {@link DeviceSpace#toDevice} (x = u, z = v). */
    public static Zone zoneAt(DeviceType type, net.minecraft.world.phys.Vec3 device) {
        return zoneAt(type, device.x, device.y, device.z);
    }

    /** The zone at a device-space point on the surface of a device. */
    public static Zone zoneAt(DeviceType type, double u, double y, double v) {
        if (type == DeviceType.ROULETTE) return rouletteZone(u, y, v);
        if (type == DeviceType.CASHIER) return new Zone(PLAY, 0, 0);
        Tray t = tray(type);
        if (t != null && Math.abs(y - t.y()) < 1.2) {
            for (int i = 0; i < TRAY_SLOTS; i++) {
                if (sq(u - t.u(i)) + sq(v - t.v()) <= sq(t.radius() * 1.25)) return new Zone(CHIP, i, 0);
            }
        }
        if (type == DeviceType.CRASH && y >= 11.5 && y <= 13.5
                && sq(u - CRASH_BUTTON_U) + sq(v - CRASH_BUTTON_V) <= sq(CRASH_BUTTON_R * 1.15)) {
            return new Zone(BUTTON, 0, 0);
        }
        return new Zone(PLAY, 0, 0);
    }

    private static double sq(double d) {
        return d * d;
    }
}
