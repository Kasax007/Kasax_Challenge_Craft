package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CrashGame;
import net.kasax.challengecraft.casino.CrashMath;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.kasax.challengecraft.casino.DeviceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The crash station: the launch pad with its rocket, and the console next to it whose monitor shows
 * the round — multiplier, curve, countdown, who is aboard and the last crash points — with the big
 * button (join / cash out) and the chip tray on the desk.
 *
 * <p>The rocket is a small 3D model standing upright on the pad, so it looks the same from every
 * side (it used to be a flat item turned towards the camera).
 */
@Environment(EnvType.CLIENT)
final class CrashView {
    private static final double SU0 = DeviceLayouts.CRASH_SCREEN_U0, SU1 = DeviceLayouts.CRASH_SCREEN_U1;
    private static final double SY0 = DeviceLayouts.CRASH_SCREEN_Y0, SY1 = DeviceLayouts.CRASH_SCREEN_Y1;
    private static final double SV = DeviceLayouts.CRASH_SCREEN_V - 0.05;

    private CrashView() {
    }

    static void draw(DevicePainter p, BlockPos pos, float partial) {
        CasinoNet.CrashState s = CasinoClientState.CRASH.get(pos.asLong());
        int phase = s == null ? CrashGame.WAITING : s.phase();
        drawRocket(p, pos, s, phase, partial);
        drawScreen(p, s, phase, partial);
        drawButton(p, pos, s, phase);
        TrayView.draw(p, DeviceType.CRASH, pos);
    }

    // ---- rocket -------------------------------------------------------------------------------

    private static void drawRocket(DevicePainter p, BlockPos pos, CasinoNet.CrashState s, int phase, float partial) {
        if (phase == CrashGame.CRASHED) return; // it burst; the explosion was spawned as particles
        double base = 2.0;
        double sway = 0;
        if (phase == CrashGame.FLYING) {
            base += CasinoWorldRenderer.rocketHeight(CasinoWorldRenderer.flightTicks(s, partial)) * 16.0;
        } else if (phase == CrashGame.BETTING) {
            sway = Math.sin((CasinoClientState.clientTick + partial) * 1.3) * 0.06;
            if (p.mc.level.getRandom().nextFloat() < 0.3f) {
                p.mc.level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 0, 0.02, 0);
            }
        }
        rocket(p, 8.0 + sway, base, 8.0);
    }

    /** An upright rocket: striped body, nose cone and four fins, base centre at (u, y, v). */
    private static void rocket(DevicePainter p, double u, double y, double v) {
        int sides = 8;
        double r = 1.35, bodyH = 8.0, band = 1.6, noseH = 3.2;
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI * 2 * i / sides, a1 = Math.PI * 2 * (i + 1) / sides;
            double ua = u + Math.cos(a0) * r, va = v + Math.sin(a0) * r, ub = u + Math.cos(a1) * r, vb = v + Math.sin(a1) * r;
            float light = 0.72f + 0.28f * (float) Math.cos(a0 + 0.4);
            for (double h = 0; h < bodyH - 0.01; h += band) {
                boolean red = ((int) Math.round(h / band)) % 2 == 0;
                int c = DevicePainter.shade(red ? 0xFFD02A2A : 0xFFF2F2F2, light);
                p.quad(ua, y + h, va, ub, y + h, vb, ub, y + h + band, vb, ua, y + h + band, va, c);
            }
            // Nose cone.
            int nose = DevicePainter.shade(0xFFE8B84A, light);
            p.quad(ua, y + bodyH, va, ub, y + bodyH, vb, u, y + bodyH + noseH, v, u, y + bodyH + noseH, v, nose);
        }
        // Fins.
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + Math.PI / 2 * k;
            double cu = Math.cos(a), cv = Math.sin(a);
            p.quad(u + cu * r * 0.9, y, v + cv * r * 0.9, u + cu * (r + 1.5), y - 0.6, v + cv * (r + 1.5),
                    u + cu * (r + 1.1), y + 2.2, v + cv * (r + 1.1), u + cu * r * 0.9, y + 3.2, v + cv * r * 0.9, 0xFF9A1C1C);
        }
        // Nozzle.
        p.box(u - 0.9, y - 0.7, v - 0.9, u + 0.9, y, v + 0.9, 0xFF3A3F47);
    }

    // ---- monitor ------------------------------------------------------------------------------

    private static void drawScreen(DevicePainter p, CasinoNet.CrashState s, int phase, float partial) {
        p.rectFront(SU0, SY0, SU1, SY1, SV, 0xFF05070D);
        double w = SU1 - SU0;
        p.textFront(Component.literal("CRASH"), SU0 + 0.5, SY1 - 0.9, SV, 0.9, 0xFFFF9B6A, -1);

        // Last crash points, newest first, at the top right.
        if (s != null) {
            double hu = SU1 - 0.4;
            for (int i = 0; i < Math.min(4, s.history().length); i++) {
                double m = s.history()[i] / 100.0;
                String txt = String.format(Locale.ROOT, "%.1f", m);
                int c = m < 2 ? 0xFFE25B5B : m < 10 ? 0xFF7BE0A4 : 0xFFFFC53D;
                p.textFront(Component.literal(txt), hu, SY1 - 0.9, SV, 0.8, c, 1);
                hu -= p.width(Component.literal(txt)) * 0.8 / 7.0 + 0.6;
            }
        }

        double flight = s == null ? 0 : CasinoWorldRenderer.flightTicks(s, partial);
        double m = phase == CrashGame.CRASHED ? s.crashedAt() / 100.0
                : phase == CrashGame.FLYING ? CrashMath.multiplierAt((int) flight)
                * Math.exp(CrashMath.GROWTH_PER_SECOND * (flight - (int) flight) / 20.0) : 1.0;
        int colour = phase == CrashGame.CRASHED ? 0xFFE25B5B : m < 2 ? 0xFFFFFFFF : m < 5 ? 0xFF7BE0A4
                : m < 20 ? 0xFFFFC53D : 0xFFD08CFF;

        // The curve.
        double gu0 = SU0 + 0.5, gu1 = SU1 - 0.5, gy0 = SY0 + 3.6, gy1 = SY0 + 8.8;
        p.rectFront(gu0, gy0, gu1, gy1, SV - 0.06, 0xFF0B1020);
        for (int i = 1; i < 4; i++) {
            double gy = gy0 + (gy1 - gy0) * i / 4;
            p.rectFront(gu0, gy - 0.03, gu1, gy + 0.03, SV - 0.12, 0x30FFFFFF);
        }
        if (phase == CrashGame.FLYING || phase == CrashGame.CRASHED) {
            double maxT = Math.max(200, flight);
            double maxM = Math.max(2.0, m * 1.15);
            double pu = gu0, py = gy0;
            for (int i = 1; i <= 40; i++) {
                double ft = flight * i / 40.0;
                double mm = Math.exp(CrashMath.GROWTH_PER_SECOND * ft / 20.0);
                double cu = gu0 + (gu1 - gu0) * ft / maxT;
                double cy = Math.min(gy1, gy0 + (gy1 - gy0 - 0.2) * (mm - 1.0) / (maxM - 1.0));
                segment(p, pu, py, cu, cy, SV - 0.18, colour);
                pu = cu;
                py = cy;
            }
        }
        // The multiplier, big, over the curve.
        String mult = String.format(Locale.ROOT, "%.2f×", m);
        SlotView.fit(p, Component.literal(mult), (SU0 + SU1) / 2, gy1 + 1.9, SV - 0.24, w - 1.0, 2.4, colour);

        // Status line.
        Component status = switch (phase) {
            case CrashGame.BETTING -> Component.translatable("challengecraft.casino.crash.betting", (s.ticksLeft() + 19) / 20);
            case CrashGame.FLYING -> Component.translatable("challengecraft.casino.crash.flying");
            case CrashGame.CRASHED -> Component.translatable("challengecraft.casino.crash.crashed",
                    String.format(Locale.ROOT, "%.2f", s.crashedAt() / 100.0));
            default -> Component.translatable("challengecraft.casino.crash.waiting");
        };
        SlotView.fit(p, status, (SU0 + SU1) / 2, gy0 - 0.9, SV - 0.24, w - 1.0, 0.85,
                phase == CrashGame.CRASHED ? 0xFFE25B5B : 0xFFB7C0D0);

        // Who is aboard: the viewer first, then the others.
        if (s != null && !s.seats().isEmpty()) {
            String me = p.mc.player.getName().getString();
            java.util.List<CasinoNet.CrashSeat> seats = new java.util.ArrayList<>(s.seats());
            seats.sort((a, b) -> Boolean.compare(!a.player().equals(me), !b.player().equals(me)));
            double ly = gy0 - 2.0;
            for (int i = 0; i < Math.min(2, seats.size()); i++) {
                CasinoNet.CrashSeat seat = seats.get(i);
                String right = seat.cashedAt() > 0 ? String.format(Locale.ROOT, "%.2f×", seat.cashedAt() / 100.0)
                        : phase == CrashGame.CRASHED ? "✖" : "…";
                int c = seat.cashedAt() > 0 ? 0xFF7BE0A4 : phase == CrashGame.CRASHED ? 0xFFE25B5B : 0xFFE8E8E8;
                String name = seat.player().length() > 10 ? seat.player().substring(0, 10) : seat.player();
                p.textFront(Component.literal(name + " " + CasinoEconomy.format(seat.bet())), SU0 + 0.5, ly, SV - 0.24,
                        0.75, seat.player().equals(me) ? 0xFFFFE08A : 0xFF9AA3B5, -1);
                p.textFront(Component.literal(right), SU1 - 0.5, ly, SV - 0.24, 0.75, c, 1);
                ly -= 1.05;
            }
        }
    }

    private static void segment(DevicePainter p, double u0, double y0, double u1, double y1, double v, int argb) {
        double du = u1 - u0, dy = y1 - y0;
        double len = Math.max(1e-4, Math.sqrt(du * du + dy * dy));
        double nu = -dy / len * 0.12, ny = du / len * 0.12;
        p.quad(u0 - nu, y0 - ny, v, u1 - nu, y1 - ny, v, u1 + nu, y1 + ny, v, u0 + nu, y0 + ny, v, argb);
    }

    // ---- the big button -----------------------------------------------------------------------

    private static void drawButton(DevicePainter p, BlockPos pos, CasinoNet.CrashState s, int phase) {
        String me = p.mc.player.getName().getString();
        boolean aboard = s != null && s.seats().stream().anyMatch(x -> x.player().equals(me) && x.cashedAt() == 0);
        boolean cashOut = phase == CrashGame.FLYING && aboard;
        boolean join = phase == CrashGame.WAITING || (phase == CrashGame.BETTING && !aboard);
        CasinoClientState.Aim aim = CasinoClientState.aim;
        boolean hover = aim != null && aim.master().equals(pos) && aim.zone().kind() == DeviceLayouts.BUTTON;
        double pulse = 0.75 + 0.25 * Math.sin(CasinoClientState.clientTick * 0.5);
        int base = cashOut ? 0xFF2FBF5B : join ? 0xFFD03030 : 0xFF6A2020;
        int c = DevicePainter.shade(base, (float) ((cashOut ? pulse : 0.9) * (hover ? 1.15 : 1.0)));
        double u = DeviceLayouts.CRASH_BUTTON_U, v = DeviceLayouts.CRASH_BUTTON_V, r = DeviceLayouts.CRASH_BUTTON_R;
        p.discTop(u, v, 12.45, r + 0.3, 0xFF2A2E35, 20);
        p.discTop(u, v, 12.9, r, c, 20);
        Component label = Component.translatable(cashOut ? "challengecraft.casino.crash.button.cash_out"
                : "challengecraft.casino.crash.button.join");
        double lw = p.width(label) * 0.7 / 7.0;
        double h = lw > r * 1.7 ? 0.7 * r * 1.7 / lw : 0.7;
        p.textTop(label, u, v, 12.92, h, 0xFFFFFFFF, 0, DevicePainter.FULLBRIGHT);
    }
}
