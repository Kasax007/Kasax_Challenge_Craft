package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.kasax.challengecraft.casino.DeviceType;
import net.kasax.challengecraft.casino.PlinkoGame;
import net.kasax.challengecraft.casino.PlinkoMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The plinko board: twelve rows of pegs, thirteen buckets with their multipliers, every ball in the
 * air, and under the board the display (the viewer's stake and last result) and the chip tray.
 *
 * <p>A ball's path is known from the moment it is dropped ({@link CasinoNet.PlinkoBall}); it falls
 * {@link PlinkoGame#FALL_TICKS} ticks — a short drop into the funnel, then one hop per row, each
 * landing on the next peg — and lands in the bucket the server paid.
 */
@Environment(EnvType.CLIENT)
final class PlinkoView {
    private static final double FACE_V = DeviceLayouts.PLINKO_BOARD_V + 1.2; // 0.3 px in front of the board face
    private static final int DROP_TICKS = 8;
    private static final int HOP_TICKS = 4;

    private PlinkoView() {
    }

    /** Where a ball is after {@code t} ticks: {u, y}. */
    static double[] ballAt(int path, float t) {
        double dx = DeviceLayouts.PLINKO_PEG_DX;
        double topY = DeviceLayouts.PLINKO_TOP_Y + 0.7;
        if (t < DROP_TICKS) {
            double k = t / DROP_TICKS;
            return new double[]{DeviceLayouts.PLINKO_CENTER_U, 31.2 - (31.2 - topY) * k * k};
        }
        double s = (t - DROP_TICKS) / HOP_TICKS;
        int step = (int) Math.min(PlinkoMath.ROWS - 1, Math.floor(s));
        double f = Math.min(1.0, s - step);
        // Position above the peg of row `step`, then hop to the peg of the next row (or the bucket).
        int rights = Integer.bitCount(path & ((1 << step) - 1));
        double u0 = DeviceLayouts.PLINKO_CENTER_U + (rights - step / 2.0) * dx;
        double y0 = DeviceLayouts.plinkoPegY(step) + 0.7;
        boolean right = (path >> step & 1) == 1;
        double u1 = u0 + (right ? dx / 2 : -dx / 2);
        double y1 = step + 1 < PlinkoMath.ROWS ? DeviceLayouts.plinkoPegY(step + 1) + 0.7 : DeviceLayouts.PLINKO_BUCKET_Y + 0.4;
        double hop = 0.55 * Math.sin(Math.PI * f) * (step + 1 < PlinkoMath.ROWS ? 1 : 0.4);
        return new double[]{u0 + (u1 - u0) * f, y0 + (y1 - y0) * f * f + hop};
    }

    static void draw(DevicePainter p, BlockPos pos, float partial) {
        // Pegs.
        for (int row = 0; row < PlinkoMath.ROWS; row++) {
            for (int i = 0; i < row + 3; i++) {
                double u = DeviceLayouts.plinkoPegU(row, i), y = DeviceLayouts.plinkoPegY(row);
                p.rectFront(u - 0.2, y - 0.2, u + 0.2, y + 0.2, FACE_V - 0.08, 0xFF9AA3B5);
                p.rectFront(u - 0.1, y + 0.02, u + 0.1, y + 0.16, FACE_V - 0.16, 0xFFE6ECF5);
            }
        }
        // Buckets with their multipliers; the one that just caught a ball lights up.
        CasinoClientState.PlinkoDrop last = CasinoClientState.PLINKO_LAST.get(pos.asLong());
        long now = CasinoClientState.clientTick;
        int flashBucket = last != null && now - last.landedAt() < 30 ? PlinkoMath.bucket(last.ball().path()) : -1;
        double dx = DeviceLayouts.PLINKO_PEG_DX;
        for (int b = 0; b < PlinkoMath.BUCKETS; b++) {
            double u = DeviceLayouts.plinkoBucketU(b);
            int mult = PlinkoMath.MULT100[b];
            int c = mult >= 1000 ? 0xFFD8342C : mult >= 300 ? 0xFFE8742A : mult >= 200 ? 0xFFE8A92A
                    : mult >= 100 ? 0xFFC8C23A : 0xFF4A5A78;
            if (b == flashBucket && (now / 3) % 2 == 0) c = 0xFFFFFFFF;
            p.rectFront(u - dx / 2 + 0.12, 6.4, u + dx / 2 - 0.12, 8.8, FACE_V - 0.08, c);
            p.textFront(Component.literal(PlinkoMath.label(b)), u, 7.6, FACE_V - 0.2, 0.8, 0xFF101010, 0);
        }
        // Balls in the air: the viewer's in gold, everybody else's in white.
        String me = p.mc.player.getName().getString();
        for (CasinoClientState.PlinkoDrop d : CasinoClientState.PLINKO_BALLS) {
            if (d.ball().pos() != pos.asLong()) continue;
            float t = (now - d.startTick()) + partial;
            if (t > PlinkoGame.FALL_TICKS) continue;
            double[] at = ballAt(d.ball().path(), t);
            // The viewer's balls are gold, everybody else's pink, with a dark rim against the pegs.
            int c = d.ball().player().equals(me) ? 0xFFFFC53D : 0xFFFF7AB8;
            p.discFront(at[0], at[1], FACE_V - 0.3, 0.68, 0xFF1A1206, 12);
            p.discFront(at[0], at[1], FACE_V - 0.32, 0.56, c, 12);
            p.discFront(at[0] - 0.17, at[1] + 0.17, FACE_V - 0.34, 0.18, 0xFFFFFFFF, 6);
        }
        drawDisplay(p, pos);
        TrayView.draw(p, DeviceType.PLINKO, pos);
    }

    private static void drawDisplay(DevicePainter p, BlockPos pos) {
        double u0 = 2.0, u1 = 30.0, y0 = 1.0, y1 = 4.6, v = 2.6; // clear of the base face at v 3
        p.rectFront(u0 - 0.3, y0 - 0.3, u1 + 0.3, y1 + 0.3, v + 0.1, 0xFFB8912F);
        p.rectFront(u0, y0, u1, y1, v, 0xFF07080C);
        CasinoNet.State s = CasinoClientState.state;
        if (s != null) {
            p.textFront(Component.translatable("challengecraft.casino.display.stake", CasinoEconomy.format(s.betAmount())),
                    u0 + 0.8, 2.8, v, 1.2, 0xFF7BE0A4, -1);
        }
        CasinoClientState.PlinkoDrop mine = CasinoClientState.PLINKO_MINE.get(pos.asLong());
        WinShows.Show show = WinShows.at(pos.asLong());
        if (show != null) {
            // A big multiplier is being celebrated: its tier, in its colours.
            p.textFront(WinShows.label(show.tier(), show.win()), u1 - 0.8, 2.8, v, 1.2,
                    WinShows.tierColour(show.tier(), show.local(0)), 1);
        } else if (mine != null) {
            CasinoNet.PlinkoBall b = mine.ball();
            long net = b.payout() - b.bet();
            Component last = Component.translatable("challengecraft.casino.plinko.display.last",
                    PlinkoMath.label(PlinkoMath.bucket(b.path())), (net >= 0 ? "+" : "−") + CasinoEconomy.format(Math.abs(net)));
            p.textFront(last, u1 - 0.8, 2.8, v, 1.2, net >= 0 ? 0xFFFFC53D : 0xFFB0B0BA, 1);
        } else {
            p.textFront(Component.literal("PLINKO"), u1 - 0.8, 2.8, v, 1.2, 0xFFE3B35A, 1);
        }
    }
}
