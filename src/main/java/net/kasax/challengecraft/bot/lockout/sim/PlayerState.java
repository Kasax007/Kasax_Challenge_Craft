package net.kasax.challengecraft.bot.lockout.sim;

/**
 * One player in the abstract game: what it holds, where it is, what it is doing, and what its brain
 * remembers of the tiles (tries, rests, overruns - the same bookkeeping {@code LockoutBrain} keeps).
 * Plain fields and arrays, copied cheaply for the look-ahead.
 */
public final class PlayerState {
    // ---- what it holds and knows ----
    public int ms;
    public final int[] res = new int[Res.N];
    /** Position: Overworld blocks from the spawn, or Nether blocks from the portal there. */
    public double x, z;
    /** 0 surface, 1 caves, 2 deep; 0 Overworld, 1 Nether. */
    public int layer, dim;
    /** Places known (bit per {@link Place}), places looked for in vain. */
    public long known, missing;
    /** Resources whose source is at hand (the vein, the flock) since the last walk. */
    public long access;
    /** When the next unit of food will be eaten (game seconds): a full belly lasts some minutes at first. */
    public double fedAt = 240;

    // ---- what it is doing ----
    public double busyUntil;
    /** The step under way: kind, argument, amount, start, planned end; dies during it; the budget runs out first. */
    int stepKind = -1, stepArg, stepAmount;
    /** When the step began, when its time was last booked, when it ends if nothing cuts it, when the player dies in it (MAX: not). */
    double stepBeganAt, stepStart, stepEnd, stepDeathAt = Double.MAX_VALUE;
    boolean stepDies, stepBudget, stepFails;
    /** The plan: steps still to come (from {@link #cursor}). */
    int planLength, cursor;
    int[] planKind = new int[48], planArg = new int[48], planAmount = new int[48];
    /** The tile it is going for (-1: none, a chore), whether as a quick one, and its time allowance. */
    public int target = -1;
    boolean quick;
    double goalStart, goalBudget, goalBudget0, goalEstimate;
    /** When the attempt fails for reasons beyond the plan (MAX: it does not). */
    double goalFailsAt = Double.MAX_VALUE;
    int extensions, stepsSinceCheck;
    /** What the chore under way is for (for the time accounts). */
    int chore = -1;

    // ---- what its brain remembers per tile ----
    public final int[] tries = new int[Board.SIZE], overruns = new int[Board.SIZE], fails = new int[Board.SIZE],
            starts = new int[Board.SIZE], quickTries = new int[Board.SIZE];
    public final double[] restUntil = new double[Board.SIZE], spent = new double[Board.SIZE], overrunEstimate = new double[Board.SIZE];
    /** The greedy brain's own marks: the Nether phase and the iron kit, tried again from when, how often. */
    public double netherRetryAt, kitRetryAt;
    public int netherFails, kitTries;
    /** Chores that failed and wait (bit per chore) - not again before {@link #choreRetryAt}. */
    public double choreRetryAt;
    /** The bundle looked at again from when (its own clock: it used to hold the copper back with it). */
    public double bundleRetryAt;

    // ---- for the results ----
    public int deaths, decisions;
    public double firstTile = -1;
    /** Seconds spent per account: see {@link Sim#ACCOUNTS}. */
    public final double[] time = new double[Sim.ACCOUNTS.length];

    public boolean has(Ms m) {
        return (ms & m.bit) != 0;
    }

    public int count(Res r) {
        return res[r.ordinal()];
    }

    public PlayerState copy() {
        PlayerState c = new PlayerState();
        c.ms = ms;
        System.arraycopy(res, 0, c.res, 0, Res.N);
        c.x = x;
        c.z = z;
        c.layer = layer;
        c.dim = dim;
        c.known = known;
        c.missing = missing;
        c.access = access;
        c.fedAt = fedAt;
        c.busyUntil = busyUntil;
        c.stepKind = stepKind;
        c.stepArg = stepArg;
        c.stepAmount = stepAmount;
        c.stepBeganAt = stepBeganAt;
        c.stepStart = stepStart;
        c.stepEnd = stepEnd;
        c.stepDeathAt = stepDeathAt;
        c.stepDies = stepDies;
        c.stepBudget = stepBudget;
        c.stepFails = stepFails;
        c.planLength = planLength;
        c.cursor = cursor;
        c.planKind = planKind.clone();
        c.planArg = planArg.clone();
        c.planAmount = planAmount.clone();
        c.target = target;
        c.quick = quick;
        c.goalStart = goalStart;
        c.goalBudget = goalBudget;
        c.goalBudget0 = goalBudget0;
        c.goalEstimate = goalEstimate;
        c.goalFailsAt = goalFailsAt;
        c.extensions = extensions;
        c.stepsSinceCheck = stepsSinceCheck;
        c.chore = chore;
        c.bundleRetryAt = bundleRetryAt;
        System.arraycopy(tries, 0, c.tries, 0, Board.SIZE);
        System.arraycopy(overruns, 0, c.overruns, 0, Board.SIZE);
        System.arraycopy(fails, 0, c.fails, 0, Board.SIZE);
        System.arraycopy(starts, 0, c.starts, 0, Board.SIZE);
        System.arraycopy(quickTries, 0, c.quickTries, 0, Board.SIZE);
        System.arraycopy(restUntil, 0, c.restUntil, 0, Board.SIZE);
        System.arraycopy(spent, 0, c.spent, 0, Board.SIZE);
        System.arraycopy(overrunEstimate, 0, c.overrunEstimate, 0, Board.SIZE);
        c.netherRetryAt = netherRetryAt;
        c.kitRetryAt = kitRetryAt;
        c.netherFails = netherFails;
        c.kitTries = kitTries;
        c.choreRetryAt = choreRetryAt;
        c.deaths = deaths;
        c.decisions = decisions;
        c.firstTile = firstTile;
        System.arraycopy(time, 0, c.time, 0, time.length);
        return c;
    }

    /** Clears what it was doing (the plan and the target), keeping what it holds. */
    void idle() {
        stepKind = -1;
        planLength = 0;
        cursor = 0;
        target = -1;
        chore = -1;
        quick = false;
    }
}
