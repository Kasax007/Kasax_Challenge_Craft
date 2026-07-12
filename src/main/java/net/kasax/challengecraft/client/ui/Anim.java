package net.kasax.challengecraft.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * Small, frame-rate-independent animation helpers shared across the UI.
 *
 * <p>Time is read from the wall clock so animations advance smoothly regardless of render FPS or
 * game pause state. Use {@link Tween} for per-widget state (hover lift, selection glow) and the
 * static time functions for global oscillations (pulses, shine sweeps, spinners).
 */
public final class Anim {
    private Anim() {
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    // ---- Easing --------------------------------------------------------------------------

    public static float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public static float easeInOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    // ---- Global time oscillations --------------------------------------------------------

    /** Smooth 0..1 triangle-ish oscillation with the given period. */
    public static float pulse(long periodMillis) {
        double phase = (now() % periodMillis) / (double) periodMillis;
        return (float) (0.5 + 0.5 * Math.sin(phase * 2.0 * Math.PI));
    }

    /** A rising 0..1 sawtooth with the given period — good for a left-to-right shine sweep. */
    public static float sweep(long periodMillis) {
        return (now() % periodMillis) / (float) periodMillis;
    }

    /** 0..1 progress since {@code startMillis}, clamped — a one-shot fade-in driver. */
    public static float fade(long startMillis, long durationMillis) {
        if (durationMillis <= 0) {
            return 1f;
        }
        return MathHelper.clamp((now() - startMillis) / (float) durationMillis, 0f, 1f);
    }

    // ---- Spinner -------------------------------------------------------------------------

    /**
     * Draws an indeterminate spinner as a ring of fading square dots rotating around
     * ({@code cx}, {@code cy}). Uses only {@code fill}, so it works on any screen.
     */
    public static void spinner(DrawContext context, int cx, int cy, int radius, int color) {
        int dots = 8;
        float head = sweep(900) * dots;
        for (int i = 0; i < dots; i++) {
            double angle = (i / (double) dots) * 2.0 * Math.PI - Math.PI / 2.0;
            int dx = cx + (int) Math.round(Math.cos(angle) * radius);
            int dy = cy + (int) Math.round(Math.sin(angle) * radius);
            float dist = (head - i + dots) % dots;
            float alpha = MathHelper.clamp(1f - dist / dots, 0.15f, 1f);
            int size = dist < 1.5f ? 2 : 1;
            context.fill(dx - size, dy - size, dx + size, dy + size, CraftUI.applyAlpha(color, alpha));
        }
    }

    /**
     * Returns a per-character brightness multiplier (0..1) for a TimerOverlay-style shine sweep,
     * where {@code fraction} is the current sweep position (0..1) across {@code total} characters.
     */
    public static float shineAt(int index, int total, float fraction, float bandWidth) {
        float head = fraction * (total + bandWidth * 2) - bandWidth;
        float dist = Math.abs(index - head);
        return dist > bandWidth ? 0f : 1f - dist / bandWidth;
    }

    // ---- Per-widget smoothed value -------------------------------------------------------

    /**
     * A smoothed scalar that eases toward a target every time it is updated, independent of
     * frame rate. Store one per animated property (e.g. a card's hover lift) and call
     * {@link #approach} each frame with the desired target.
     */
    public static final class Tween {
        private float current;
        private long lastNanos;
        private boolean primed;

        public Tween(float initial) {
            this.current = initial;
        }

        /**
         * Eases toward {@code target}. {@code speed} is the approximate responsiveness
         * (higher = snappier); ~12 feels crisp for hover states.
         */
        public float approach(float target, float speed) {
            long nowNanos = System.nanoTime();
            if (!primed) {
                lastNanos = nowNanos;
                primed = true;
            }
            float dt = (nowNanos - lastNanos) / 1_000_000_000f;
            lastNanos = nowNanos;
            dt = MathHelper.clamp(dt, 0f, 0.1f); // guard against pauses / first frame
            float factor = 1f - (float) Math.exp(-speed * dt);
            current += (target - current) * factor;
            return current;
        }

        public float get() {
            return current;
        }

        public void set(float value) {
            this.current = value;
        }
    }
}
