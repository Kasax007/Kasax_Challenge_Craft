package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.kasax.challengecraft.casino.DeviceType;
import net.kasax.challengecraft.casino.RouletteGame;
import net.kasax.challengecraft.casino.RouletteMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.kasax.challengecraft.casino.DeviceLayouts.*;

/**
 * The roulette table: the wheel with its ball, the printed layout, every chip on the felt, the
 * board of last numbers behind the wheel, the chip rack and the "take back" plaque. Betting happens
 * right here: the spot the viewer aims at lights up together with the numbers it covers.
 */
@Environment(EnvType.CLIENT)
final class RouletteView {
    private static final double FELT = TABLE_TOP + 0.12;
    private static final int RED = 0xFFB3141C, BLACK = 0xFF141417, GREEN = 0xFF1C8C3E, LINE = 0xFFE8D9A8;

    private RouletteView() {
    }

    static int numberColour(int n) {
        return RouletteMath.colour(n) == 0 ? GREEN : RouletteMath.colour(n) == 1 ? RED : BLACK;
    }

    static void draw(DevicePainter p, BlockPos pos, float partial) {
        CasinoNet.RouletteState s = CasinoClientState.ROULETTE.get(pos.asLong());
        Long received = CasinoClientState.ROULETTE_RECEIVED.get(pos.asLong());
        float since = received == null ? 0 : (CasinoClientState.clientTick - received) + partial;
        drawLayout(p, pos, s);
        drawWheel(p, s, since);
        drawBoard(p, s, since);
        drawBets(p, s);
        TrayView.draw(p, DeviceType.ROULETTE, pos);
        drawTakeBack(p, pos);
    }

    // ---- the layout ---------------------------------------------------------------------------

    private static void drawLayout(DevicePainter p, BlockPos pos, CasinoNet.RouletteState s) {
        int light = p.light;
        double gv1 = GRID_V0 + 3 * CELL_H;
        // Covered numbers of the spot the viewer aims at.
        boolean[] lit = new boolean[37];
        int litKind = -1;
        CasinoClientState.Aim aim = CasinoClientState.aim;
        if (aim != null && aim.master().equals(pos) && aim.zone().kind() == BET) {
            litKind = aim.zone().a();
            for (int n = 0; n <= 36; n++) lit[n] = RouletteMath.covers(aim.zone().a(), aim.zone().b(), n);
        }
        int winner = s != null && s.phase() == RouletteGame.RESULT ? s.result() : -1;

        // Zero.
        cell(p, ZERO_U0, GRID_V0, GRID_U0, gv1, GREEN, lit[0], winner == 0);
        p.textTop(Component.literal("0"), (ZERO_U0 + GRID_U0) / 2, GRID_V0 + 1.5 * CELL_H, FELT + 0.24, 1.5, 0xFFFFFFFF, 0, light);
        // Numbers.
        for (int n = 1; n <= 36; n++) {
            int c = (n - 1) / 3, r = (n - 1) % 3;
            double u0 = GRID_U0 + c * CELL_W, v0 = GRID_V0 + r * CELL_H;
            cell(p, u0, v0, u0 + CELL_W, v0 + CELL_H, numberColour(n), lit[n], winner == n);
            p.textTop(Component.literal(Integer.toString(n)), u0 + CELL_W / 2, v0 + CELL_H / 2, FELT + 0.24, 1.3,
                    0xFFFFFFFF, 0, light);
        }
        // Columns (2 to 1).
        for (int r = 0; r < 3; r++) {
            double v0 = GRID_V0 + r * CELL_H;
            boolean on = litKind == RouletteMath.COLUMN && aim.zone().b() == r;
            cell(p, GRID_U1, v0, COLUMN_U1, v0 + CELL_H, 0xFF0F5A2E, on, false);
            p.textTop(Component.literal("2:1"), (GRID_U1 + COLUMN_U1) / 2, v0 + CELL_H / 2, FELT + 0.24, 0.9, 0xFFF4E6B0, 0, light);
        }
        // Dozens.
        String[] dozens = {"1st 12", "2nd 12", "3rd 12"};
        for (int d = 0; d < 3; d++) {
            double u0 = GRID_U0 + d * 4 * CELL_W;
            boolean on = litKind == RouletteMath.DOZEN && aim.zone().b() == d;
            cell(p, u0, DOZEN_V0, u0 + 4 * CELL_W, GRID_V0, 0xFF0F5A2E, on, false);
            p.textTop(Component.literal(dozens[d]), u0 + 2 * CELL_W, (DOZEN_V0 + GRID_V0) / 2, FELT + 0.24, 1.2, 0xFFF4E6B0, 0, light);
        }
        // Even-money boxes.
        String[] labels = {"1-18", "EVEN", "", "", "ODD", "19-36"};
        for (int i = 0; i < 6; i++) {
            double u0 = GRID_U0 + i * 2 * CELL_W;
            boolean on = litKind == EVEN_KINDS[i];
            cell(p, u0, EVEN_V0, u0 + 2 * CELL_W, DOZEN_V0, 0xFF0F5A2E, on, false);
            if (EVEN_KINDS[i] == RouletteMath.RED || EVEN_KINDS[i] == RouletteMath.BLACK) {
                int c = EVEN_KINDS[i] == RouletteMath.RED ? RED : BLACK;
                double cu = u0 + CELL_W, cv = (EVEN_V0 + DOZEN_V0) / 2;
                p.quad(cu - 1.8, FELT + 0.24, cv, cu, FELT + 0.24, cv - 1.2, cu + 1.8, FELT + 0.24, cv, cu, FELT + 0.24, cv + 1.2, c);
            } else {
                p.textTop(Component.literal(labels[i]), u0 + CELL_W, (EVEN_V0 + DOZEN_V0) / 2, FELT + 0.24, 1.2, 0xFFF4E6B0, 0, light);
            }
        }
        // Printed lines between the boxes.
        for (int c = 0; c <= 12; c++) {
            double u = GRID_U0 + c * CELL_W;
            p.rectTop(u - 0.06, GRID_V0, u + 0.06, gv1, FELT + 0.16, LINE);
        }
        for (int r = 0; r <= 3; r++) {
            double v = GRID_V0 + r * CELL_H;
            p.rectTop(ZERO_U0, v - 0.06, COLUMN_U1, v + 0.06, FELT + 0.16, LINE);
        }
        p.rectTop(ZERO_U0 - 0.06, GRID_V0, ZERO_U0 + 0.06, gv1, FELT + 0.16, LINE);
        p.rectTop(COLUMN_U1 - 0.06, GRID_V0, COLUMN_U1 + 0.06, gv1, FELT + 0.16, LINE);
        for (double v : new double[]{EVEN_V0, DOZEN_V0}) p.rectTop(GRID_U0, v - 0.06, GRID_U1, v + 0.06, FELT + 0.16, LINE);
        for (int k = 0; k <= 6; k++) {
            double u = GRID_U0 + k * 2 * CELL_W;
            p.rectTop(u - 0.06, EVEN_V0, u + 0.06, k % 2 == 0 ? GRID_V0 : DOZEN_V0, FELT + 0.16, LINE);
        }
        // Where the aimed chip would go (a split, corner, street... sits on a line).
        if (aim != null && aim.master().equals(pos) && aim.zone().kind() == BET) {
            double[] at = betAnchor(aim.zone().a(), aim.zone().b());
            float pulse = 0.55f + 0.45f * (float) Math.sin(CasinoClientState.clientTick * 0.4);
            p.discTop(at[0], at[1], FELT + 0.3, 1.1, DevicePainter.alpha(0xFFFFFF, 0.55f * pulse), 14);
        }
    }

    private static void cell(DevicePainter p, double u0, double v0, double u1, double v1, int colour, boolean lit, boolean winner) {
        int c = colour;
        if (winner && (CasinoClientState.clientTick / 5) % 2 == 0) c = 0xFFFFD24A;
        p.rectTop(u0, v0, u1, v1, FELT, c);
        if (lit) p.rectTop(u0, v0, u1, v1, FELT + 0.08, 0x70FFFFFF);
    }

    // ---- wheel and ball -----------------------------------------------------------------------

    private static void drawWheel(DevicePainter p, CasinoNet.RouletteState s, float since) {
        CasinoWorldRenderer.WheelPose wp = CasinoWorldRenderer.wheelPose(s, since);
        double r = WHEEL_R - 0.5;
        // Wooden rim around the wheel.
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            double ri = r, ro = WHEEL_R + 0.2;
            p.quad(WHEEL_U + Math.cos(a0) * ri, 14.9, WHEEL_V + Math.sin(a0) * ri, WHEEL_U + Math.cos(a1) * ri, 14.9, WHEEL_V + Math.sin(a1) * ri,
                    WHEEL_U + Math.cos(a1) * ro, 14.9, WHEEL_V + Math.sin(a1) * ro, WHEEL_U + Math.cos(a0) * ro, 14.9, WHEEL_V + Math.sin(a0) * ro,
                    0xFF5A2A12);
            p.quad(WHEEL_U + Math.cos(a0) * ro, 14.3, WHEEL_V + Math.sin(a0) * ro, WHEEL_U + Math.cos(a1) * ro, 14.3, WHEEL_V + Math.sin(a1) * ro,
                    WHEEL_U + Math.cos(a1) * ro, 14.9, WHEEL_V + Math.sin(a1) * ro, WHEEL_U + Math.cos(a0) * ro, 14.9, WHEEL_V + Math.sin(a0) * ro,
                    0xFF3E1C0C);
        }
        // The printed wheel turns with the pose; see DevicePainter#wheel for the angle convention.
        p.wheel(WHEEL_U, WHEEL_V, 14.45, r, -wp.wheel());
        if (wp.ballVisible()) {
            // wheelPose angles run the other way round than the device frame: mirror them.
            double a = Math.PI / 2 - wp.ball();
            double rr = r * (0.66 + 0.30 * Math.max(0, Math.min(1, (wp.ballRadius() - 0.78) / 0.22)));
            double bu = WHEEL_U + Math.cos(a) * rr, bv = WHEEL_V + Math.sin(a) * rr;
            p.box(bu - 0.28, 14.5, bv - 0.28, bu + 0.28, 15.05, bv + 0.28, 0xFFF7F7F7);
        }
    }

    // ---- the board of numbers -----------------------------------------------------------------

    private static void drawBoard(DevicePainter p, CasinoNet.RouletteState s, float since) {
        // Depth layers, front to back towards the player (smaller v is closer): the board sits well
        // in front of the model's face (v 30.4), frames, boxes and text each a clear step further,
        // so nothing on it can z-fight.
        double u0 = 2.3, u1 = 15.7, y0 = 18.3, y1 = 29.1;
        double vBoard = 30.0, vFrame = 29.8, vBox = 29.65, vText = 29.45;
        p.rectFront(u0, y0, u1, y1, vBoard, 0xFF07080C);
        double cu = (u0 + u1) / 2;
        if (s == null || s.phase() == RouletteGame.WAITING) {
            SlotView.fit(p, Component.translatable("challengecraft.casino.roulette.board.place"), cu, 27.4, vText, 12.5, 1.1, 0xFFE3B35A);
        } else if (s.phase() == RouletteGame.BETTING) {
            int secs = (int) Math.ceil(Math.max(0, s.ticksLeft() - since) / 20.0);
            SlotView.fit(p, Component.translatable("challengecraft.casino.roulette.board.closing", secs), cu, 27.4, vText,
                    12.5, 1.1, secs <= 5 ? 0xFFE25B5B : 0xFF7BE0A4);
        } else if (s.phase() == RouletteGame.SPINNING) {
            SlotView.fit(p, Component.translatable("challengecraft.casino.roulette.board.no_more"), cu, 27.4, vText, 12.5, 1.1, 0xFFB7C0D0);
        } else {
            // The winning number, big, in its colour, with a gold frame so black shows on the board.
            int n = s.result();
            p.rectFront(cu - 2.6, 22.7, cu + 2.6, 27.9, vFrame, 0xFFE3B35A);
            p.rectFront(cu - 2.35, 22.95, cu + 2.35, 27.65, vBox, numberColour(n));
            p.textFront(Component.literal(Integer.toString(n)), cu, 25.3, vText, 3.0, 0xFFFFFFFF, 0);
        }
        // History, newest on the left: six framed boxes along the bottom of the board.
        if (s != null) {
            double w = 2.0, gap = 0.2;
            double hu = u0 + (u1 - u0 - (6 * w + 5 * gap)) / 2;
            int shown = 0;
            for (int i = 0; i < s.history().length && shown < 6; i++) {
                if (s.phase() == RouletteGame.RESULT && i == 0) continue; // it is the big number above
                int n = s.history()[i];
                p.rectFront(hu - 0.12, 18.68, hu + w + 0.12, 20.92, vFrame, 0xFF5A5F6B);
                p.rectFront(hu, 18.8, hu + w, 20.8, vBox, numberColour(n));
                p.textFront(Component.literal(Integer.toString(n)), hu + w / 2, 19.8, vText, 1.1, 0xFFFFFFFF, 0);
                hu += w + gap;
                shown++;
            }
            // The viewer's own stake on the table and, after the spin, what it paid.
            long mine = 0, paid = 0;
            String me = p.mc.player.getName().getString();
            for (CasinoNet.RouletteBet bet : s.bets()) {
                if (!bet.player().equals(me)) continue;
                mine += bet.amount();
                if (s.phase() == RouletteGame.RESULT) paid += RouletteMath.payout(bet.kind(), bet.target(), bet.amount(), s.result());
            }
            if (mine > 0) {
                Component line = s.phase() == RouletteGame.RESULT
                        ? Component.translatable("challengecraft.casino.roulette.board.paid", CasinoEconomy.format(paid))
                        : Component.translatable("challengecraft.casino.roulette.board.yours", CasinoEconomy.format(mine));
                SlotView.fit(p, line, cu, 21.7, vText, 12.5, 0.9, paid > 0 ? 0xFFFFC53D : 0xFFD8D8E0);
            }
        }
    }

    // ---- chips on the felt --------------------------------------------------------------------

    /** Chips drawn per stack at most; a taller pile would hide the numbers behind it. */
    private static final int MAX_STACK = 24;

    private static void drawBets(DevicePainter p, CasinoNet.RouletteState s) {
        if (s == null || s.bets().isEmpty()) return;
        // Group by spot, then one stack per player on that spot, side by side.
        Map<Long, List<CasinoNet.RouletteBet>> bySpot = new LinkedHashMap<>();
        for (CasinoNet.RouletteBet b : s.bets()) {
            bySpot.computeIfAbsent((long) b.kind() << 32 | (b.target() & 0xFFFFFFFFL), k -> new ArrayList<>()).add(b);
        }
        String me = p.mc.player.getName().getString();
        for (List<CasinoNet.RouletteBet> stacks : bySpot.values()) {
            for (int i = 0; i < stacks.size(); i++) {
                CasinoNet.RouletteBet b = stacks.get(i);
                double[] at = betAnchor(b.kind(), b.target());
                double off = (i - (stacks.size() - 1) / 2.0) * 0.9;
                // The chips exactly as they were laid, first at the bottom: each placement is one
                // chip of the stake picked (an all-in or odd amount as the chips that make it up).
                List<Integer> levels = new ArrayList<>();
                long[] placed = b.placed().length > 0 ? b.placed() : new long[]{b.amount() / 100};
                for (long c : placed) {
                    for (int level : DeviceLayouts.chipsFor(c, MAX_STACK)) levels.add(level);
                }
                if (levels.size() > MAX_STACK) levels = levels.subList(levels.size() - MAX_STACK, levels.size());
                boolean mine = b.player().equals(me);
                double cu = at[0] + off, cv = at[1] + off * 0.4;
                for (int k = 0; k < levels.size(); k++) {
                    p.chipLevel(cu, cv, FELT + 0.3 + 0.34 * (k + 1), 1.05, levels.get(k), mine ? 1f : 0.82f);
                }
                if (!levels.isEmpty()) {
                    // The top chip carries its value, like the chips in the tray.
                    int top = levels.get(levels.size() - 1);
                    p.textTop(Component.literal(DevicePainter.chipLabel(top)), cu, cv, FELT + 0.32 + 0.34 * levels.size(),
                            0.62, 0xFF101010, 0, p.light);
                }
                if (mine) p.discTop(at[0] + off, at[1] + off * 0.4, FELT + 0.24, 1.25, 0xB0FFD24A, 14);
            }
        }
    }

    private static void drawTakeBack(DevicePainter p, BlockPos pos) {
        CasinoClientState.Aim aim = CasinoClientState.aim;
        boolean hover = aim != null && aim.master().equals(pos) && aim.zone().kind() == TAKE_BACK;
        p.rectTop(TAKE_BACK_U0, TAKE_BACK_V0, TAKE_BACK_U1, TAKE_BACK_V1, FELT, hover ? 0xFFE3C170 : 0xFFB8912F);
        p.rectTop(TAKE_BACK_U0 + 0.3, TAKE_BACK_V0 + 0.3, TAKE_BACK_U1 - 0.3, TAKE_BACK_V1 - 0.3, FELT + 0.16, 0xFF3A2410);
        Component label = Component.translatable("challengecraft.casino.roulette.take_back");
        double w = p.width(label) * 1.1 / 7.0;
        double h = w > TAKE_BACK_U1 - TAKE_BACK_U0 - 1.0 ? 1.1 * (TAKE_BACK_U1 - TAKE_BACK_U0 - 1.0) / w : 1.1;
        p.textTop(label, (TAKE_BACK_U0 + TAKE_BACK_U1) / 2, (TAKE_BACK_V0 + TAKE_BACK_V1) / 2, FELT + 0.32, h,
                0xFFF4E6B0, 0, p.light);
    }
}
