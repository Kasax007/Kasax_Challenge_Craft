package net.kasax.challengecraft.casino;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * "Minenfieber", the slot machine: 5 reels × 3 rows, 10 fixed lines, a wild, a scatter and free
 * spins with one randomly chosen <b>expanding</b> lucky item (the "Book of …" mechanic).
 *
 * <p>The reel strips and pay table are the exact ones solved in {@code scripts/casino/slot_final.py}
 * for a fair machine (99.998 %, exact fraction 92754935089733/92757105698400), hit rate 27.8 %, free
 * spins every ~151 spins. Every win is then paid at {@link #PAYOUT_PERCENT} % — the House's 4 % — so
 * the machine returns <b>96.0 %</b>. {@link #theoreticalRtp()} recomputes it at start-up and logs it,
 * so a careless edit to a strip or a pay shows up in the log instead of silently changing the odds.
 * <b>Change strips or pays only together with that script.</b>
 *
 * <p>All amounts here are in <b>line-bet units</b> (a total bet is 10 line bets), which keeps every
 * payout an integer: line wins pay the table value, scatter and expanding wins pay the table value
 * times the total bet, i.e. ×10 line units.
 */
public final class SlotMath {
    public static final int NETHERITE = 0, DIAMOND = 1, EMERALD = 2, GOLD = 3, LAPIS = 4, IRON = 5,
            COPPER = 6, COAL = 7, WILD = 8, SCATTER = 9;
    public static final int ITEM_SYMBOLS = 8;
    public static final int REELS = 5, ROWS = 3, LINES = 10;
    public static final int FREE_SPINS = 10;
    /** Share of the table value that is actually paid out. */
    public static final int PAYOUT_PERCENT = 96;
    /** Maximum win per paid spin including its free spins: 5000 × total bet. */
    public static final long MAX_WIN_UNITS = 5000L * LINES;

    public static final Item[] SYMBOL_ITEMS = {
            Items.NETHERITE_INGOT, Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.LAPIS_LAZULI,
            Items.IRON_INGOT, Items.COPPER_INGOT, Items.COAL, Items.TOTEM_OF_UNDYING, Items.NETHER_STAR
    };

    /** Row index (0 top) per reel for each line. */
    public static final int[][] LINE_ROWS = {
            {1, 1, 1, 1, 1}, {0, 0, 0, 0, 0}, {2, 2, 2, 2, 2}, {0, 1, 2, 1, 0}, {2, 1, 0, 1, 2},
            {1, 0, 0, 0, 1}, {1, 2, 2, 2, 1}, {0, 0, 1, 2, 2}, {2, 2, 1, 0, 0}, {1, 2, 1, 0, 1},
    };

    /** PAY[symbol][run length] in line-bet units. */
    public static final int[][] PAY = {
            {0, 0, 10, 100, 1000, 5000},   // netherite
            {0, 0, 5, 40, 400, 2000},      // diamond
            {0, 0, 0, 30, 100, 750},       // emerald
            {0, 0, 0, 30, 100, 750},       // gold
            {0, 0, 0, 5, 40, 150},         // lapis
            {0, 0, 0, 5, 40, 150},         // iron
            {0, 0, 0, 5, 25, 100},         // copper
            {0, 0, 0, 5, 25, 100},         // coal
            {0, 0, 10, 200, 2000, 10000},  // wild (totem)
    };
    /** Scatter pays anywhere, in multiples of the TOTAL bet, by number of scatters visible. */
    public static final int[] SCATTER_PAY = {0, 0, 0, 2, 17, 500};

    public static final int[][] STRIPS = {
            {3, 4, 7, 0, 5, 9, 7, 2, 7, 6, 6, 0, 6, 6, 9, 1, 7, 0, 5, 6, 6, 4, 6, 7, 2, 4, 6, 7, 1, 5, 5, 5, 4, 7, 2, 2, 7, 6, 4, 3, 4, 3, 6, 6, 0, 3, 8, 6, 1, 7, 5, 1, 7, 5, 5, 4, 6, 5, 6, 2, 6, 7, 5, 7, 4},
            {6, 2, 2, 6, 6, 9, 3, 7, 9, 0, 4, 7, 5, 7, 0, 7, 7, 1, 5, 2, 7, 6, 2, 5, 6, 7, 7, 4, 6, 7, 1, 3, 4, 5, 7, 7, 4, 4, 3, 3, 5, 6, 4, 5, 7, 1, 2, 2, 3, 1, 4, 4, 8, 7, 6, 5, 3, 3, 5, 3, 5, 6, 1, 7, 6, 4},
            {7, 4, 7, 7, 3, 1, 2, 4, 6, 3, 5, 4, 6, 7, 2, 9, 5, 6, 7, 6, 2, 7, 4, 6, 5, 7, 5, 4, 0, 9, 1, 5, 5, 6, 5, 1, 3, 6, 7, 7, 3, 2, 3, 3, 3, 6, 4, 8, 7, 2, 4, 7, 4, 6, 5, 1, 7, 6, 5, 3, 7, 2, 0, 7, 1, 4},
            {6, 4, 6, 7, 7, 4, 9, 3, 6, 5, 5, 7, 3, 4, 1, 6, 4, 1, 4, 2, 6, 2, 7, 5, 4, 8, 3, 7, 2, 7, 7, 7, 7, 1, 2, 7, 5, 3, 6, 5, 9, 5, 0, 7, 6, 3, 1, 7, 2, 1, 6, 3, 4, 6, 7, 4, 4, 6, 5, 5, 5, 0, 3, 7, 2, 3},
            {2, 6, 6, 4, 4, 7, 7, 6, 6, 4, 7, 3, 6, 4, 5, 7, 4, 6, 6, 7, 8, 7, 3, 9, 4, 1, 7, 7, 7, 5, 4, 5, 5, 6, 0, 4, 5, 2, 5, 6, 6, 6, 5, 6, 7, 5, 7, 1, 3, 6, 2, 0, 1, 7, 0, 9, 5, 6, 3, 2, 5, 2, 0, 6, 1},
    };

    // ---- presentation timing, shared with the client animation -------------------------------
    /** Reel r stops at STOP_TICK[r] after the lever is pulled. */
    public static final int[] STOP_TICK = {18, 25, 32, 39, 46};
    /** Extra spin time for a reel still turning while two scatters are already showing. */
    public static final int TENSION_TICKS = 14;
    public static final int WIN_SHOW_TICKS = 22;
    public static final int FS_INTRO_TICKS = 64;
    public static final int FS_SPIN_TICKS = 40;
    public static final int FS_EXPAND_TICKS = 24;
    public static final int FS_OUTRO_TICKS = 50;

    private SlotMath() {
    }

    public static int symbolAt(int reel, int stop, int row) {
        int[] strip = STRIPS[reel];
        return strip[Math.floorMod(stop + row, strip.length)];
    }

    /** Pay of one line in line units, left to right, the wild substituting every item. */
    public static int lineValue(int[] syms) {
        int wilds = 0;
        while (wilds < REELS && syms[wilds] == WILD) wilds++;
        int best = wilds >= 2 ? PAY[WILD][wilds] : 0;
        if (wilds == REELS) return best;
        int x = syms[wilds];
        if (x == SCATTER) return best;
        int n = wilds;
        while (n < REELS && (syms[n] == x || syms[n] == WILD)) n++;
        return Math.max(best, PAY[x][n]);
    }

    public record LineWin(int line, int symbol, int count, int units) {
    }

    /** The outcome of one spin (paid or free), in line units. */
    public record Spin(int[] stops, List<LineWin> lineWins, int scatters, long units, int expandMask) {
    }

    public static Spin evaluate(int[] stops, int lucky) {
        List<LineWin> wins = new ArrayList<>();
        long units = 0;
        int[] syms = new int[REELS];
        for (int l = 0; l < LINES; l++) {
            for (int r = 0; r < REELS; r++) syms[r] = symbolAt(r, stops[r], LINE_ROWS[l][r]);
            int v = lineValue(syms);
            if (v > 0) {
                int wilds = 0;
                while (wilds < REELS && syms[wilds] == WILD) wilds++;
                int wildPay = wilds >= 2 ? PAY[WILD][wilds] : 0;
                int sym = WILD;
                int n = wilds;
                if (v > wildPay && wilds < REELS) {
                    sym = syms[wilds];
                    while (n < REELS && (syms[n] == sym || syms[n] == WILD)) n++;
                }
                wins.add(new LineWin(l, sym, n, v));
                units += v;
            }
        }
        int scatters = 0;
        int luckyMask = 0;
        for (int r = 0; r < REELS; r++) {
            boolean scatter = false;
            for (int row = 0; row < ROWS; row++) {
                int s = symbolAt(r, stops[r], row);
                if (s == SCATTER) scatter = true;
                if (lucky >= 0 && s == lucky) luckyMask |= 1 << r;
            }
            if (scatter) scatters++;
        }
        units += (long) SCATTER_PAY[scatters] * LINES;
        int expandMask = 0;
        if (lucky >= 0) {
            int reels = Integer.bitCount(luckyMask);
            int pay = PAY[lucky][reels];
            if (pay > 0) {
                units += (long) pay * LINES; // pays on all ten lines
                expandMask = luckyMask;
            }
        }
        return new Spin(stops, wins, scatters, units, expandMask);
    }

    public static int[] randomStops(RandomSource random) {
        int[] stops = new int[REELS];
        for (int r = 0; r < REELS; r++) stops[r] = random.nextInt(STRIPS[r].length);
        return stops;
    }

    /** Centi-chips a spin of {@code units} line units pays on a total bet of {@code stake}. */
    public static long pay(long units, long stake) {
        return units * stake * PAYOUT_PERCENT / (100L * LINES);
    }

    /** A paid spin followed by all the free spins it triggers. */
    public record Play(List<Spin> spins, int lucky, long units) {
    }

    public static Play play(RandomSource random) {
        List<Spin> spins = new ArrayList<>();
        Spin base = evaluate(randomStops(random), -1);
        spins.add(base);
        long units = base.units();
        int lucky = -1;
        if (base.scatters() >= 3) {
            lucky = random.nextInt(ITEM_SYMBOLS);
            int left = FREE_SPINS;
            // Hard stop far beyond anything reachable: retriggers are ~6.6 % per spin.
            while (left > 0 && spins.size() < 400) {
                left--;
                Spin fs = evaluate(randomStops(random), lucky);
                spins.add(fs);
                units += fs.units();
                if (fs.scatters() >= 3) left += FREE_SPINS;
            }
        }
        return new Play(spins, lucky, Math.min(units, MAX_WIN_UNITS));
    }

    /** Reel stop times of a free spin, relative to the start of that spin. */
    public static final int[] FS_STOP_TICK = {10, 14, 18, 22, 26};

    public static boolean reelShowsScatter(int reel, int stop) {
        for (int row = 0; row < ROWS; row++) if (symbolAt(reel, stop, row) == SCATTER) return true;
        return false;
    }

    /**
     * When each reel of the PAID spin stops. Once two scatters are showing, every reel still turning
     * spins {@link #TENSION_TICKS} longer — the classic "will the third one come?" moment.
     */
    public static int[] baseStopTicks(int[] stops) {
        int[] t = new int[REELS];
        int scatters = 0;
        int extra = 0;
        for (int r = 0; r < REELS; r++) {
            t[r] = STOP_TICK[r] + extra;
            if (reelShowsScatter(r, stops[r])) scatters++;
            if (scatters >= 2) extra += TENSION_TICKS;
        }
        return t;
    }

    public static int baseSpinTicks(int[] stops) {
        return baseStopTicks(stops)[REELS - 1] + 6;
    }

    /** Ticks the client needs to show a play; the server credits the win when they are over. */
    public static int presentationTicks(Play play) {
        Spin base = play.spins().get(0);
        int ticks = baseSpinTicks(base.stops()) + (base.units() > 0 ? WIN_SHOW_TICKS : 0);
        if (play.spins().size() > 1) {
            ticks += FS_INTRO_TICKS + FS_OUTRO_TICKS;
            for (int i = 1; i < play.spins().size(); i++) {
                Spin s = play.spins().get(i);
                ticks += FS_SPIN_TICKS + (s.expandMask() != 0 ? FS_EXPAND_TICKS : 0);
            }
        }
        return ticks;
    }

    /**
     * Expected return per unit bet, computed exactly from the strips (in doubles): line pays from
     * the per-reel symbol frequencies, scatter and lucky-item visibility from every 3-symbol window.
     * Must print ~0.96.
     */
    public static double theoreticalRtp() {
        double[][] freq = new double[REELS][10];
        double[] scatterVisible = new double[REELS];
        double[][] visible = new double[ITEM_SYMBOLS][REELS];
        for (int r = 0; r < REELS; r++) {
            int[] strip = STRIPS[r];
            for (int s : strip) freq[r][s] += 1.0 / strip.length;
            for (int stop = 0; stop < strip.length; stop++) {
                boolean[] seen = new boolean[10];
                for (int row = 0; row < ROWS; row++) seen[symbolAt(r, stop, row)] = true;
                if (seen[SCATTER]) scatterVisible[r] += 1.0 / strip.length;
                for (int x = 0; x < ITEM_SYMBOLS; x++) if (seen[x]) visible[x][r] += 1.0 / strip.length;
            }
        }
        double lineEv = 0;
        int[] syms = new int[REELS];
        for (int a = 0; a < 10; a++) for (int b = 0; b < 10; b++) for (int c = 0; c < 10; c++)
            for (int d = 0; d < 10; d++) for (int e = 0; e < 10; e++) {
                double p = freq[0][a] * freq[1][b] * freq[2][c] * freq[3][d] * freq[4][e];
                if (p == 0) continue;
                syms[0] = a; syms[1] = b; syms[2] = c; syms[3] = d; syms[4] = e;
                int v = lineValue(syms);
                if (v > 0) lineEv += p * v;
            }
        // No division: ten lines at a tenth of the bet each return exactly the per-line value.
        double[] sc = countDistribution(scatterVisible);
        double pTrigger = sc[3] + sc[4] + sc[5];
        double scatterEv = 0;
        for (int k = 0; k <= REELS; k++) scatterEv += sc[k] * SCATTER_PAY[k];
        double base = lineEv + scatterEv;
        double expandEv = 0;
        for (int x = 0; x < ITEM_SYMBOLS; x++) {
            double[] d = countDistribution(visible[x]);
            for (int k = 0; k <= REELS; k++) expandEv += d[k] * PAY[x][k] / ITEM_SYMBOLS;
        }
        double sessionSpins = FREE_SPINS / (1 - FREE_SPINS * pTrigger);
        return (base + pTrigger * sessionSpins * (base + expandEv)) * PAYOUT_PERCENT / 100.0;
    }

    private static double[] countDistribution(double[] p) {
        double[] d = {1};
        for (double q : p) {
            double[] nd = new double[d.length + 1];
            for (int k = 0; k < d.length; k++) {
                nd[k] += d[k] * (1 - q);
                nd[k + 1] += d[k] * q;
            }
            d = nd;
        }
        return d;
    }
}
