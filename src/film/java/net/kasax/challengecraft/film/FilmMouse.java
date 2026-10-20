package net.kasax.challengecraft.film;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * The mouse of a menu shot: a script of glides, waits, clicks and side actions, played by tick
 * inside {@link FilmDirector#shoot}. Positions are GUI coordinates; {@link FilmCursor} draws the
 * pointer where this puts the (virtual) cursor.
 */
final class FilmMouse {
    private final class Step {
        final int from, to;
        Supplier<double[]> target;
        DoubleConsumer during;
        Runnable once;
        boolean click;
        double sx, sy;

        Step(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }

    private final FilmDirector d;
    private final List<Step> steps = new ArrayList<>();
    private final double scale;
    private double x, y;
    private int at;

    FilmMouse(FilmDirector d, double gx, double gy) {
        this.d = d;
        this.scale = d.ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        this.x = gx;
        this.y = gy;
        FilmCursor.shown = true;
        apply();
    }

    int at() {
        return at;
    }

    private void apply() {
        d.ctx.getInput().setCursorPos(x * scale, y * scale);
    }

    /** A left click at a GUI position: handed to the screen as the game would, press and release. */
    private void click(double gx, double gy) {
        d.client(mc -> {
            MouseButtonEvent ev = new MouseButtonEvent(gx, gy, new MouseButtonInfo(0, 0));
            Screen sc = mc.gui.screen();
            if (sc != null) {
                boolean handled = sc.mouseClicked(ev, false);
                sc.mouseReleased(ev);
                FilmDirector.LOG.info("[Film] click at ({}, {}) on {}: {}", String.format("%.1f", gx), String.format("%.1f", gy),
                        sc.getClass().getSimpleName(), handled ? "taken" : "not taken");
            }
        });
    }

    FilmMouse glide(Supplier<double[]> target, int ticks) {
        Step s = new Step(at, at + ticks);
        s.target = target;
        steps.add(s);
        at += ticks;
        return this;
    }

    FilmMouse glide(double tx, double ty, int ticks) {
        return glide(() -> new double[]{tx, ty}, ticks);
    }

    /** Something that runs along {@code ticks} ticks from tick {@code from} (progress 0..1, eased), without moving the script on. */
    FilmMouse alongside(int from, int ticks, DoubleConsumer progress) {
        Step s = new Step(from, from + ticks);
        s.during = progress;
        steps.add(s);
        return this;
    }

    FilmMouse pause(int ticks) {
        at += ticks;
        return this;
    }

    FilmMouse click() {
        Step s = new Step(at, at);
        s.click = true;
        steps.add(s);
        at += 1;
        return this;
    }

    FilmMouse run(Runnable r) {
        Step s = new Step(at, at);
        s.once = r;
        steps.add(s);
        return this;
    }

    void tick(int t) {
        for (Step s : steps) {
            if (t < s.from || t > s.to) continue;
            if (t == s.from) {
                s.sx = x;
                s.sy = y;
                if (s.once != null) s.once.run();
                if (s.click) {
                    click(x, y);
                    FilmCursor.click();
                }
            }
            double f = s.to == s.from ? 1.0 : ease((t - s.from) / (double) (s.to - s.from));
            if (s.during != null) s.during.accept(f);
            if (s.target != null) {
                double[] tg = s.target.get();
                x = s.sx + (tg[0] - s.sx) * f;
                y = s.sy + (tg[1] - s.sy) * f;
                apply();
            }
        }
    }

    private static double ease(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
