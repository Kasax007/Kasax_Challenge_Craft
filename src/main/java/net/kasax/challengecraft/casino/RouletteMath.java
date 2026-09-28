package net.kasax.challengecraft.casino;

/**
 * Classic European single-zero roulette: a bet covering k numbers returns 36/k × stake (35:1 on a
 * single number, 1:1 on red), and the zero loses everything that does not cover it — a return of
 * 36/37 = 97.3 %. The House keeps another 1/75 of every payout, which brings every bet to exactly
 * <b>96 %</b> (36/37 × 74/75 = 0.96), the same edge as every other game in the casino.
 *
 * <p>Splits, corners etc. touching the zero are not offered; the zero can only be backed straight.
 */
public final class RouletteMath {
    public static final int STRAIGHT = 0, SPLIT = 1, STREET = 2, CORNER = 3, SIX_LINE = 4, DOZEN = 5,
            COLUMN = 6, RED = 7, BLACK = 8, EVEN = 9, ODD = 10, LOW = 11, HIGH = 12;

    /** Wheel order of the European wheel, clockwise from zero. */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
            24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};

    private static final boolean[] IS_RED = new boolean[37];

    static {
        for (int n : new int[]{1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}) IS_RED[n] = true;
    }

    private RouletteMath() {
    }

    public static boolean isRed(int n) {
        return n > 0 && n <= 36 && IS_RED[n];
    }

    /** 0 = green, 1 = red, 2 = black. */
    public static int colour(int n) {
        return n == 0 ? 0 : isRed(n) ? 1 : 2;
    }

    /** Position of a number on the wheel, 0..36. */
    public static int wheelIndex(int n) {
        for (int i = 0; i < WHEEL.length; i++) if (WHEEL[i] == n) return i;
        return 0;
    }

    /**
     * Whether a bet is well-formed. Split targets are encoded as {@code low * 100 + high} of two
     * horizontally or vertically adjacent numbers on the layout (1–36 only); streets and six-lines
     * by their first number (1, 4, …); corners by their smallest number, which must not sit in the
     * top row (n % 3 == 0) and must be ≤ 32.
     */
    public static boolean valid(int kind, int target) {
        return switch (kind) {
            case STRAIGHT -> target >= 0 && target <= 36;
            case SPLIT -> {
                int a = target / 100, b = target % 100;
                yield a >= 1 && b <= 36 && a < b && ((b - a == 3) || (b - a == 1 && a % 3 != 0));
            }
            case STREET -> target >= 1 && target <= 34 && (target - 1) % 3 == 0;
            case CORNER -> target >= 1 && target <= 32 && target % 3 != 0;
            case SIX_LINE -> target >= 1 && target <= 31 && (target - 1) % 3 == 0;
            case DOZEN, COLUMN -> target >= 0 && target <= 2;
            case RED, BLACK, EVEN, ODD, LOW, HIGH -> true;
            default -> false;
        };
    }

    public static boolean covers(int kind, int target, int n) {
        if (n == 0) return kind == STRAIGHT && target == 0;
        return switch (kind) {
            case STRAIGHT -> n == target;
            case SPLIT -> n == target / 100 || n == target % 100;
            case STREET -> n >= target && n < target + 3;
            case CORNER -> n == target || n == target + 1 || n == target + 3 || n == target + 4;
            case SIX_LINE -> n >= target && n < target + 6;
            case DOZEN -> (n - 1) / 12 == target;
            case COLUMN -> (n - 1) % 3 == target;
            case RED -> isRed(n);
            case BLACK -> !isRed(n);
            case EVEN -> n % 2 == 0;
            case ODD -> n % 2 == 1;
            case LOW -> n <= 18;
            case HIGH -> n >= 19;
            default -> false;
        };
    }

    public static int numbersCovered(int kind, int target) {
        return switch (kind) {
            case STRAIGHT -> 1;
            case SPLIT -> 2;
            case STREET -> 3;
            case CORNER -> 4;
            case SIX_LINE -> 6;
            case DOZEN, COLUMN -> 12;
            default -> 18;
        };
    }

    /** Payout odds as printed on the table: "35:1" for a straight bet. */
    public static int odds(int kind) {
        return 36 / numbersCovered(kind, 0) - 1;
    }

    /** What a bet of {@code stake} returns (stake included) when the ball lands on {@code n}. */
    public static long payout(int kind, int target, long stake, int n) {
        if (!covers(kind, target, n)) return 0;
        // Classic odds, minus the House's seventy-fifth.
        return stake * 36 / numbersCovered(kind, target) * 74 / 75;
    }
}
