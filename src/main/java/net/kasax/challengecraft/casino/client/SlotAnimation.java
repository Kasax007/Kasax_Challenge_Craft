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
        return t >= totalTicks + 40; // linger a moment on the final screen
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

    public enum Cue { SPIN, STOP, SCATTER, TENSION, WIN_SMALL, WIN_BIG, WIN_EPIC, FREE_SPINS, EXPAND, COINS }

    public record SoundEvent(Cue cue, float pitch) {
    }

    /** Sounds that fall due between two moments of the timeline. */
    public List<SoundEvent> cuesBetween(float from, float to) {
        List<SoundEvent> out = new ArrayList<>();
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
                    if (start >= from && start < to) out.add(new SoundEvent(winCue(result.spinWins()[0]), 1.0f));
                    for (float s = start + 2; s < start + SlotMath.WIN_SHOW_TICKS * 0.7f; s += 3f) {
                        if (s >= from && s < to) out.add(new SoundEvent(Cue.COINS, 0.9f + (s - start) * 0.012f));
                    }
                }
                case FS_INTRO -> {
                    if (start >= from && start < to) out.add(new SoundEvent(Cue.FREE_SPINS, 1.0f));
                }
                case FS_EXPAND -> {
                    if (start >= from && start < to) out.add(new SoundEvent(Cue.EXPAND, 1.0f));
                }
                case FS_OUTRO -> {
                    if (start >= from && start < to) out.add(new SoundEvent(winCue(result.totalWin()), 1.0f));
                }
                default -> {
                }
            }
        }
        return out;
    }

    private Cue winCue(long win) {
        long bet = Math.max(1, result.bet());
        if (win >= bet * 50) return Cue.WIN_EPIC;
        if (win >= bet * 5) return Cue.WIN_BIG;
        return Cue.WIN_SMALL;
    }
}
