package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.SlotMath;

import java.util.ArrayList;
import java.util.List;

/**
 * The timeline of one slot play on the client: which spin is showing, where every reel stands at a
 * given moment, which lines won, and which sounds fall due. Built only from the server's
 * {@link CasinoNet.SlotResult}; the line wins are re-evaluated locally with {@link SlotMath}, which is
 * the same code the server paid with, so the highlight can never disagree with the payout.
 *
 * <p>Its total length equals {@link SlotMath#presentationTicks}, the moment the server credits the
 * win — the HUD balance changes exactly when the win banner shows.
 */
@Environment(EnvType.CLIENT)
public final class SlotAnimation {
    public enum Kind { BASE_SPIN, BASE_WIN, FS_INTRO, FS_SPIN, FS_EXPAND, FS_OUTRO, DONE }

    public record Phase(Kind kind, int spin, float local, int length) {
    }

    /** Symbols per tick at full speed. */
    private static final float SPEED = 1.35f;
    /** Ticks of braking before a reel stops. */
    private static final float BRAKE = 7f;

    public final long pos;
    public final CasinoNet.SlotResult result;
    public final long startTick;
    public final int spinCount;
    public final List<SlotMath.Spin> spins = new ArrayList<>();
    private final List<Phase> phases = new ArrayList<>();
    public final int totalTicks;
    public final int[] baseStops;

    public SlotAnimation(CasinoNet.SlotResult result, long startTick) {
        this.pos = result.pos();
        this.result = result;
        this.startTick = startTick;
        this.spinCount = result.spinWins().length;
        for (int i = 0; i < spinCount; i++) {
            int[] stops = new int[SlotMath.REELS];
            System.arraycopy(result.stops(), i * SlotMath.REELS, stops, 0, SlotMath.REELS);
            spins.add(SlotMath.evaluate(stops, i == 0 ? -1 : result.luckySymbol()));
        }
        this.baseStops = spins.get(0).stops();
        int t = 0;
        int baseLen = SlotMath.baseSpinTicks(baseStops);
        phases.add(new Phase(Kind.BASE_SPIN, 0, t, baseLen));
        t += baseLen;
        if (result.spinWins()[0] > 0) {
            phases.add(new Phase(Kind.BASE_WIN, 0, t, SlotMath.WIN_SHOW_TICKS));
            t += SlotMath.WIN_SHOW_TICKS;
        }
        if (spinCount > 1) {
            phases.add(new Phase(Kind.FS_INTRO, 0, t, SlotMath.FS_INTRO_TICKS));
            t += SlotMath.FS_INTRO_TICKS;
            for (int i = 1; i < spinCount; i++) {
                phases.add(new Phase(Kind.FS_SPIN, i, t, SlotMath.FS_SPIN_TICKS));
                t += SlotMath.FS_SPIN_TICKS;
                if (result.expandMasks()[i] != 0) {
                    phases.add(new Phase(Kind.FS_EXPAND, i, t, SlotMath.FS_EXPAND_TICKS));
                    t += SlotMath.FS_EXPAND_TICKS;
                }
            }
            phases.add(new Phase(Kind.FS_OUTRO, spinCount - 1, t, SlotMath.FS_OUTRO_TICKS));
            t += SlotMath.FS_OUTRO_TICKS;
        }
        this.totalTicks = t;
    }

    public float elapsed(float partialTick) {
        return (CasinoClientState.clientTick - startTick) + partialTick;
    }

    public boolean finished(float t) {
        // Linger a moment on the final screen, and never cut a win celebration short.
        float end = totalTicks + 40;
        for (Phase p : phases) {
            int tier = celebrationTier(p);
            if (tier > 0) end = Math.max(end, p.local() + CELEBRATION_TICKS[tier] + 5);
        }
        return t >= end;
    }

    // ---- the lever ------------------------------------------------------------------------------

    /** Ticks the lever takes to go down, the moment it rests there, and the ticks it springs back. */
    public static final int LEVER_DOWN = 5, LEVER_HOLD = 2, LEVER_UP = 10;
    private static final float LEVER_PULLED = -95f;

    /** The lever's angle at time t: pulled at the start of the play, then springing back up. */
    public float leverAngle(float t) {
        if (t <= 0 || t >= LEVER_DOWN + LEVER_HOLD + LEVER_UP) return 0f;
        if (t < LEVER_DOWN) {
            float x = t / LEVER_DOWN;
            return LEVER_PULLED * x * x; // speeding up as the hand pulls it through
        }
        if (t < LEVER_DOWN + LEVER_HOLD) return LEVER_PULLED;
        float x = (t - LEVER_DOWN - LEVER_HOLD) / LEVER_UP;
        // Back up on its spring, overshooting a little past the rest position.
        return LEVER_PULLED * (1f - x) * (1f - x) * (float) Math.cos(1.5 * Math.PI * x);
    }

    // ---- the lucky item pick (free-spins intro) -----------------------------------------------

    /** Intro ticks at which the pick strip starts to run and where it lands on the lucky item. */
    public static final int PICK_START = 16, PICK_LAND = 70;
    /** Symbols the strip passes before it lands: three full rounds and a bit. */
    private static final int PICK_STEPS = 3 * SlotMath.ITEM_SYMBOLS + 5;

    /** How far the pick strip has run at intro time {@code local}, in symbols (slowing down). */
    public float pickSteps(float local) {
        float x = Math.max(0f, Math.min(1f, (local - PICK_START) / (float) (PICK_LAND - PICK_START)));
        float e = 1f - (1f - x) * (1f - x) * (1f - x);
        return PICK_STEPS * e;
    }

    /** The item symbol at strip position {@code step}; the last step is the lucky item. */
    public int pickSymbol(int step) {
        return Math.floorMod(result.luckySymbol() - PICK_STEPS + step, SlotMath.ITEM_SYMBOLS);
    }

    // ---- the expansion ------------------------------------------------------------------------

    /** Ticks one cell takes to turn over to the lucky item. */
    public static final int EXPAND_CELL_TICKS = 6;

    /** The row of a reel where the lucky item stands (the cell the expansion spreads from). */
    public int luckyRow(int spin, int reel) {
        int[] stops = spins.get(spin).stops();
        for (int row = 0; row < SlotMath.ROWS; row++) {
            if (SlotMath.symbolAt(reel, stops[reel], row) == result.luckySymbol()) return row;
        }
        return 1;
    }

    /** Ticks into the expansion at which a cell starts to turn over, or -1 if it does not. */
    public int expandDelay(int spin, int reel, int row) {
        int mask = result.expandMasks()[spin];
        if ((mask & (1 << reel)) == 0) return -1;
        int order = Integer.bitCount(mask & ((1 << reel) - 1));
        return order * 5 + Math.abs(row - luckyRow(spin, reel)) * 4;
    }

    /** 0 = the cell shows its own symbol, 1 = it has turned into the lucky item. */
    public float expandProgress(int spin, int reel, int row, float local) {
        int delay = expandDelay(spin, reel, row);
        if (delay < 0) return 0f;
        if (row == luckyRow(spin, reel)) return 1f;
        return Math.max(0f, Math.min(1f, (local - delay) / EXPAND_CELL_TICKS));
    }

    /** Ticks into the expansion when every cell has turned; the paid lines run after that. */
    public int expandFillEnd(int spin) {
        int end = 0;
        for (int r = 0; r < SlotMath.REELS; r++) {
            for (int row = 0; row < SlotMath.ROWS; row++) {
                int d = expandDelay(spin, r, row);
                if (d >= 0) end = Math.max(end, d + EXPAND_CELL_TICKS);
            }
        }
        return end + 2;
    }

    // ---- win celebrations ---------------------------------------------------------------------

    /** Celebration length per tier: none, big, mega, epic, legendary. */
    public static final int[] CELEBRATION_TICKS = {0, 30, 50, 70, 110};

    /** The tier of a win by its multiple of the stake: 5×, 20×, 50× and 200× and up. */
    public static int tierFor(long win, long bet) {
        long b = Math.max(1, bet);
        if (win >= b * 200) return 4;
        if (win >= b * 50) return 3;
        if (win >= b * 20) return 2;
        if (win >= b * 5) return 1;
        return 0;
    }

    private int celebrationTier(Phase p) {
        if (p.kind() == Kind.BASE_WIN) return tierFor(result.spinWins()[0], result.bet());
        if (p.kind() == Kind.FS_OUTRO) return tierFor(result.totalWin(), result.bet());
        return 0;
    }

    /** A win being celebrated: its tier, the ticks since it began and the amount. */
    public record Celebration(int tier, float local, long win) {
    }

    /** The celebration running at time t, or null. */
    public Celebration celebrationAt(float t) {
        Celebration found = null;
        for (Phase p : phases) {
            int tier = celebrationTier(p);
            if (tier == 0 || t < p.local() || t >= p.local() + CELEBRATION_TICKS[tier]) continue;
            long win = p.kind() == Kind.BASE_WIN ? result.spinWins()[0] : result.totalWin();
            found = new Celebration(tier, t - p.local(), win);
        }
        return found;
    }

    /** The phase at time t; {@code local} is the time since that phase began. */
    public Phase phaseAt(float t) {
        Phase current = phases.get(0);
        for (Phase p : phases) {
            if (t >= p.local()) current = p;
        }
        if (t >= totalTicks) return new Phase(Kind.DONE, spinCount - 1, t - totalTicks, 0);
        return new Phase(current.kind(), current.spin(), t - current.local(), current.length());
    }

    /** Which spin's symbols the reels are showing at time t. */
    public int spinShown(float t) {
        Phase p = phaseAt(t);
        return switch (p.kind()) {
            case BASE_SPIN, BASE_WIN, FS_INTRO -> p.kind() == Kind.FS_INTRO ? 0 : 0;
            case DONE -> spinCount - 1;
            default -> p.spin();
        };
    }

    /** Stop tick of each reel within the phase that spins it. */
    private int[] stopTicks(Phase p) {
        return p.kind() == Kind.BASE_SPIN ? SlotMath.baseStopTicks(baseStops) : SlotMath.FS_STOP_TICK;
    }

    /**
     * Strip position shown in the top row of a reel (fractional while spinning). Symbols travel
     * downwards, so the position counts down towards the target.
     */
    public float reelPosition(int reel, float t) {
        Phase p = phaseAt(t);
        int spin = spinShown(t);
        int target = spins.get(spin).stops()[reel];
        if (p.kind() != Kind.BASE_SPIN && p.kind() != Kind.FS_SPIN) return target;
        float stopAt = stopTicks(p)[reel];
        float delta = stopAt - p.local();
        if (delta > 0) {
            float dist = delta >= BRAKE ? SPEED * (delta - BRAKE / 2f) : SPEED * delta * delta / (2f * BRAKE);
            return target + dist;
        }
        float after = -delta;
        if (after < 6f) {
            // A small mechanical overshoot, then settle.
            return target - 0.22f * (float) Math.sin(Math.PI * after / 6.0) * (1f - after / 6f);
        }
        return target;
    }

    public boolean reelSpinning(int reel, float t) {
        Phase p = phaseAt(t);
        if (p.kind() != Kind.BASE_SPIN && p.kind() != Kind.FS_SPIN) return false;
        return p.local() < stopTicks(p)[reel];
    }

    /** True for reels still turning after two scatters landed (drawn with a glow). */
    public boolean reelInTension(int reel, float t) {
        Phase p = phaseAt(t);
        if (p.kind() != Kind.BASE_SPIN || !reelSpinning(reel, t)) return false;
        return SlotMath.baseStopTicks(baseStops)[reel] > SlotMath.STOP_TICK[reel];
    }

    /** Chips already won and shown at time t (the count-up of the banner), centi-chips. */
    public long shownWin(float t) {
        Phase p = phaseAt(t);
        long done = 0;
        int lastComplete = switch (p.kind()) {
            case BASE_SPIN -> -1;
            case BASE_WIN, FS_INTRO -> 0;
            case FS_SPIN -> p.spin() - 1;
            case FS_EXPAND, FS_OUTRO, DONE -> p.spin();
        };
        for (int i = 0; i <= lastComplete && i < spinCount; i++) done += result.spinWins()[i];
        done = Math.min(done, result.totalWin());
        if (p.kind() == Kind.BASE_WIN) {
            float k = Math.min(1f, p.local() / (SlotMath.WIN_SHOW_TICKS * 0.7f));
            return (long) (done * k);
        }
        if (p.kind() == Kind.DONE || p.kind() == Kind.FS_OUTRO) return result.totalWin();
        return done;
    }

    public long bet() {
        return result.bet();
    }

    // ---- sound events -------------------------------------------------------------------------

    public enum Cue { LEVER, SPIN, STOP, SCATTER, TENSION, WIN_SMALL, WIN_BIG, WIN_EPIC, FREE_SPINS, EXPAND, COINS }

    public record SoundEvent(Cue cue, float pitch) {
    }

    /** Sounds that fall due between two moments of the timeline. */
    public List<SoundEvent> cuesBetween(float from, float to) {
        List<SoundEvent> out = new ArrayList<>();
        // The lever clicks as it bottoms out and again as it snaps back.
        if (LEVER_DOWN >= from && LEVER_DOWN < to) out.add(new SoundEvent(Cue.LEVER, 0.7f));
        float back = LEVER_DOWN + LEVER_HOLD + LEVER_UP * 0.55f;
        if (back >= from && back < to) out.add(new SoundEvent(Cue.LEVER, 0.95f));
        for (Phase p : phases) {
            float start = p.local();
            switch (p.kind()) {
                case BASE_SPIN, FS_SPIN -> {
                    int[] stops = p.kind() == Kind.BASE_SPIN ? SlotMath.baseStopTicks(baseStops) : SlotMath.FS_STOP_TICK;
                    int spinIdx = p.kind() == Kind.BASE_SPIN ? 0 : p.spin();
                    int[] stopIdx = spins.get(spinIdx).stops();
                    for (float s = start; s < start + stops[SlotMath.REELS - 1]; s += 9f) {
                        if (s >= from && s < to) out.add(new SoundEvent(Cue.SPIN, 1.0f));
                    }
                    int scatters = 0;
                    for (int r = 0; r < SlotMath.REELS; r++) {
                        float at = start + stops[r];
                        boolean scatter = SlotMath.reelShowsScatter(r, stopIdx[r]);
                        if (scatter) scatters++;
                        if (at >= from && at < to) {
                            out.add(new SoundEvent(Cue.STOP, 0.85f + 0.06f * r));
                            if (scatter) out.add(new SoundEvent(Cue.SCATTER, 0.9f + 0.12f * scatters));
                            if (p.kind() == Kind.BASE_SPIN && scatters == 2 && scatter && r < SlotMath.REELS - 1) {
                                out.add(new SoundEvent(Cue.TENSION, 1.0f));
                            }
                        }
                    }
                }
                case BASE_WIN -> {
                    addWinCues(out, start, from, to, result.spinWins()[0]);
                    for (float s = start + 2; s < start + SlotMath.WIN_SHOW_TICKS * 0.7f; s += 3f) {
                        if (s >= from && s < to) out.add(new SoundEvent(Cue.COINS, 0.9f + (s - start) * 0.012f));
                    }
                }
                case FS_INTRO -> {
                    if (start >= from && start < to) out.add(new SoundEvent(Cue.FREE_SPINS, 1.0f));
                    // A click for every item the pick strip passes, higher as it slows down.
                    int before = (int) Math.floor(pickSteps(from - start));
                    int after = (int) Math.floor(pickSteps(to - start));
                    if (after > before && to - start > PICK_START) {
                        out.add(new SoundEvent(Cue.STOP, 1.25f + 0.02f * after));
                    }
                    float land = start + PICK_LAND;
                    if (land >= from && land < to) {
                        out.add(new SoundEvent(Cue.SCATTER, 1.2f));
                        out.add(new SoundEvent(Cue.EXPAND, 1.3f));
                    }
                }
                case FS_EXPAND -> {
                    if (start >= from && start < to) out.add(new SoundEvent(Cue.EXPAND, 1.0f));
                    // Each cell that turns over clicks, rising from reel to reel.
                    for (int r = 0; r < SlotMath.REELS; r++) {
                        for (int row = 0; row < SlotMath.ROWS; row++) {
                            int d = expandDelay(p.spin(), r, row);
                            if (d < 0 || row == luckyRow(p.spin(), r)) continue;
                            float at = start + d;
                            if (at >= from && at < to) out.add(new SoundEvent(Cue.STOP, 1.05f + 0.07f * r + 0.03f * row));
                        }
                    }
                    float lines = start + expandFillEnd(p.spin());
                    if (lines >= from && lines < to) out.add(new SoundEvent(Cue.WIN_SMALL, 1.1f));
                    for (float s = lines + 2; s < start + p.length(); s += 4f) {
                        if (s >= from && s < to) out.add(new SoundEvent(Cue.COINS, 1.0f + (s - lines) * 0.01f));
                    }
                }
                case FS_OUTRO -> {
                    addWinCues(out, start, from, to, result.totalWin());
                }
                default -> {
                }
            }
        }
        return out;
    }

    /** The fanfare of a win, grander with every tier. */
    private void addWinCues(List<SoundEvent> out, float start, float from, float to, long win) {
        int tier = tierFor(win, result.bet());
        List<SoundEvent> at0 = new ArrayList<>();
        switch (tier) {
            case 0 -> at0.add(new SoundEvent(Cue.WIN_SMALL, 1.0f));
            case 1 -> at0.add(new SoundEvent(Cue.WIN_BIG, 1.0f));
            case 2 -> {
                at0.add(new SoundEvent(Cue.WIN_BIG, 1.0f));
                if (start + 14 >= from && start + 14 < to) out.add(new SoundEvent(Cue.WIN_BIG, 1.25f));
            }
            default -> {
                at0.add(new SoundEvent(Cue.WIN_EPIC, 1.0f));
                if (tier == 4 && start + 40 >= from && start + 40 < to) out.add(new SoundEvent(Cue.WIN_EPIC, 1.25f));
            }
        }
        if (start >= from && start < to) out.addAll(at0);
        // A shower of coins, longer for bigger wins.
        int coins = tier == 0 ? 0 : CELEBRATION_TICKS[tier] - 10;
        for (float s = start + 6; s < start + 6 + coins; s += 5f) {
            if (s >= from && s < to) out.add(new SoundEvent(Cue.COINS, 0.9f + (s - start) * 0.006f));
        }
    }
}
