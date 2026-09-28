package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CasinoSounds;
import net.kasax.challengecraft.casino.RouletteGame;
import net.kasax.challengecraft.casino.RouletteMath;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The roulette felt. Left, the wheel runs the same animation as the table in the world (same pose
 * function, so both land on the same pocket); right, the classic single-zero layout. Click a number
 * for a straight bet, the line between two for a split, a corner for four, the strip under a
 * column for a street and under the line between two columns for a six-line. The screen does not
 * pause the game — the world, and whatever the House sent after your last loss, keeps moving.
 */
@Environment(EnvType.CLIENT)
public class RouletteScreen extends Screen {
    private static final Identifier WHEEL = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/casino/roulette_wheel.png");
    private static final Identifier CHIPS = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/casino/chips.png");
    private static final Identifier FELT = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/casino/felt.png");
    private static final long[] DENOMS = {1, 5, 25, 100, 500, 1_000, 5_000, 25_000};
    private static final int W = 404, H = 236;
    private static final int CW = 18, CH = 20;

    private final BlockPos pos;
    private int x, y, bx, by;
    private int chip = 2;
    private int lastSerial = -1;
    private int lastPhase = -1;

    public RouletteScreen(BlockPos pos) {
        super(Component.translatable("challengecraft.casino.roulette.name"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        x = (width - W) / 2;
        y = (height - H) / 2;
        bx = x + 146;
        by = y + 44;
        addRenderableWidget(new CasinoButton(x + W - 172, y + H - 26, 80, 18,
                Component.translatable("challengecraft.casino.roulette.clear"), CraftUI.WARNING,
                () -> state() != null && state().phase() == RouletteGame.BETTING,
                () -> CasinoClient.send(new CasinoNet.Action(CasinoNet.Action.ROULETTE_CLEAR, pos.asLong(), 0, 0, 0L, ""))));
        addRenderableWidget(new CasinoButton(x + W - 88, y + H - 26, 76, 18,
                Component.translatable("challengecraft.casino.roulette.close"), CraftUI.GOLD, () -> true, this::onClose));
    }

    private CasinoNet.RouletteState state() {
        return CasinoClientState.ROULETTE.get(pos.asLong());
    }

    private float sinceReceived(float partial) {
        Long r = CasinoClientState.ROULETTE_RECEIVED.get(pos.asLong());
        return r == null ? 0 : (CasinoClientState.clientTick - r) + partial;
    }

    @Override
    public void onClose() {
        CasinoClient.send(CasinoNet.Action.of(CasinoNet.Action.ROULETTE_LEAVE));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing ------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);
        CraftUI.frame(ctx, x, y, W, H, 0xF20B2A18, 0xFF3D2412, CraftUI.GOLD);
        // Felt, tiled.
        for (int tx = x + 6; tx < x + W - 6; tx += 64) {
            for (int ty = y + 6; ty < y + H - 6; ty += 64) {
                int w = Math.min(64, x + W - 6 - tx), h = Math.min(64, y + H - 6 - ty);
                ctx.blit(RenderPipelines.GUI_TEXTURED, FELT, tx, ty, 0, 0, w, h, 64, 64);
            }
        }
        ctx.text(font, this.title, x + 12, y + 10, CraftUI.GOLD, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        CasinoNet.RouletteState s = state();
        float since = sinceReceived(delta);
        if (s != null && s.serial() != lastSerial) {
            lastSerial = s.serial();
            lastPhase = s.phase();
        }

        drawWheel(ctx, s, since);
        drawBoard(ctx, s, mouseX, mouseY);
        drawChips(ctx, mouseX, mouseY);
        drawStatus(ctx, s, since);
    }

    private void drawWheel(GuiGraphicsExtractor ctx, CasinoNet.RouletteState s, float since) {
        int cx = x + 74, cy = y + 116;
        int size = 124;
        CasinoWorldRenderer.WheelPose wp = CasinoWorldRenderer.wheelPose(s, since);
        // Shadow and wooden bowl.
        ctx.fill(cx - size / 2 - 6, cy - size / 2 - 6, cx + size / 2 + 6, cy + size / 2 + 6, 0x40000000);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        // The texture has pocket 0 at the top; +90° puts pocket i's centre at wheel + i·(2π/37),
        // the same convention the ball and the world renderer use.
        ctx.pose().rotate(wp.wheel() + (float) (Math.PI / 2));
        ctx.blit(RenderPipelines.GUI_TEXTURED, WHEEL, -size / 2, -size / 2, 0, 0, size, size, 256, 256, 256, 256);
        ctx.pose().popMatrix();
        if (wp.ballVisible()) {
            float r = size / 2f * (0.60f + 0.28f * (wp.ballRadius() - 0.78f) / 0.22f);
            r = Math.max(size / 2f * 0.60f, Math.min(size / 2f * 0.90f, r));
            float bxp = cx + (float) Math.cos(wp.ball()) * r;
            float byp = cy + (float) Math.sin(wp.ball()) * r;
            int ix = Math.round(bxp), iy = Math.round(byp);
            ctx.fill(ix - 3, iy - 2, ix + 3, iy + 2, 0xFFE8E8EE);
            ctx.fill(ix - 2, iy - 3, ix + 2, iy + 3, 0xFFE8E8EE);
            ctx.fill(ix - 2, iy - 2, ix, iy, 0xFFFFFFFF);
            ctx.fill(ix, iy + 1, ix + 2, iy + 3, 0xFFB0B0BC);
        }
        if (s != null && s.phase() == RouletteGame.RESULT && s.result() >= 0) {
            int col = RouletteMath.colour(s.result());
            int c = col == 0 ? 0xFF1E9E4A : col == 1 ? 0xFFC8202A : 0xFF18181C;
            ctx.fill(cx - 17, cy - 13, cx + 17, cy + 13, 0xFFE3B35A);
            ctx.fill(cx - 15, cy - 11, cx + 15, cy + 11, c);
            CraftUI.drawCenteredScaled(ctx, font, Component.literal(Integer.toString(s.result())), cx, cy + 1, 1.8f, 0xFFFFFFFF);
        }
    }

    private int cellX(int n) {
        return bx + CW + ((n - 1) / 3) * CW;
    }

    private int cellY(int n) {
        return by + (2 - (n - 1) % 3) * CH;
    }

    private void drawBoard(GuiGraphicsExtractor ctx, CasinoNet.RouletteState s, int mx, int my) {
        int result = s != null && s.phase() == RouletteGame.RESULT ? s.result() : -1;
        float blink = (System.currentTimeMillis() / 250L) % 2 == 0 ? 1f : 0.55f;
        // Zero.
        cell(ctx, bx, by, CW, CH * 3, 0xFF1E9E4A, "0", result == 0 ? blink : 0);
        for (int n = 1; n <= 36; n++) {
            int c = RouletteMath.isRed(n) ? 0xFFB8202A : 0xFF18181C;
            cell(ctx, cellX(n), cellY(n), CW, CH, c, Integer.toString(n), result == n ? blink : 0);
        }
        for (int col = 0; col < 3; col++) {
            cell(ctx, bx + CW + 12 * CW, by + (2 - col) * CH, CW + 4, CH, 0xFF0F4A28, "2:1", 0);
        }
        String[] dozen = {"1–12", "13–24", "25–36"};
        for (int d = 0; d < 3; d++) cell(ctx, bx + CW + d * 4 * CW, by + 3 * CH + 6, 4 * CW, 16, 0xFF0F4A28, dozen[d], 0);
        String[] even = {"1–18", "GERADE", "", "", "UNGER.", "19–36"};
        int[] evenColour = {0xFF0F4A28, 0xFF0F4A28, 0xFFB8202A, 0xFF18181C, 0xFF0F4A28, 0xFF0F4A28};
        for (int i = 0; i < 6; i++) {
            String label = i == 1 ? Component.translatable("challengecraft.casino.roulette.even").getString()
                    : i == 4 ? Component.translatable("challengecraft.casino.roulette.odd").getString() : even[i];
            cell(ctx, bx + CW + i * 2 * CW, by + 3 * CH + 24, 2 * CW, 16, evenColour[i], label, 0);
        }
        // Street strip under the grid.
        ctx.fill(bx + CW, by + 3 * CH, bx + CW + 12 * CW, by + 3 * CH + 4, 0x40FFFFFF);

        // Hover preview of the bet under the mouse.
        int[] hover = hitTest(mx, my);
        if (hover != null && s != null && (s.phase() == RouletteGame.BETTING || s.phase() == RouletteGame.WAITING)) {
            int[] anchor = anchor(hover[0], hover[1]);
            if (anchor != null) {
                ctx.fill(anchor[0] - 4, anchor[1] - 4, anchor[0] + 4, anchor[1] + 4, 0x80FFE9A8);
                Component tip = describe(hover[0], hover[1]);
                ctx.text(font, tip, bx, by - 12, CraftUI.TEXT_PRIMARY, false);
            }
        }

        // Chips on the felt.
        if (s != null) {
            Map<Long, Integer> stackHeight = new HashMap<>();
            String me = minecraft.player.getName().getString();
            for (CasinoNet.RouletteBet b : s.bets()) {
                int[] a = anchor(b.kind(), b.target());
                if (a == null) continue;
                long key = ((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL);
                int hgt = stackHeight.merge(key, 1, Integer::sum) - 1;
                int d = denomIndexFor(b.amount() / 100);
                int cx = a[0] - 6, cy = a[1] - 6 - hgt * 2;
                ctx.blit(RenderPipelines.GUI_TEXTURED, CHIPS, cx, cy, d * 32, 0, 12, 12, 32, 32, 256, 32);
                if (b.player().equals(me)) ctx.outline(cx - 1, cy - 1, 14, 14, 0xFFFFE9A8);
            }
        }
    }

    private void cell(GuiGraphicsExtractor ctx, int cx, int cy, int w, int h, int colour, String label, float highlight) {
        ctx.fill(cx, cy, cx + w, cy + h, 0xFFE8D9B0);
        ctx.fill(cx + 1, cy + 1, cx + w - 1, cy + h - 1, colour);
        if (highlight > 0) ctx.fill(cx + 1, cy + 1, cx + w - 1, cy + h - 1, CraftUI.applyAlpha(0xFFFFE066, 0.6f * highlight));
        if (!label.isEmpty()) {
            int tw = font.width(label);
            if (tw > w - 2) {
                CraftUI.drawCenteredScaled(ctx, font, Component.literal(label), cx + w / 2, cy + h / 2 + 1, 0.7f, 0xFFF4F0E6);
            } else {
                ctx.centeredText(font, Component.literal(label), cx + w / 2, cy + (h - font.lineHeight) / 2 + 1, 0xFFF4F0E6);
            }
        }
    }

    private static int denomIndexFor(long chips) {
        int best = 0;
        for (int i = 0; i < DENOMS.length; i++) if (chips >= DENOMS[i]) best = i;
        return best;
    }

    /** {kind, target} under the mouse, or null. */
    private int[] hitTest(int mx, int my) {
        int gx0 = bx + CW, gy0 = by, gx1 = gx0 + 12 * CW, gy1 = gy0 + 3 * CH;
        if (mx >= bx && mx < gx0 && my >= gy0 && my < gy1) return new int[]{RouletteMath.STRAIGHT, 0};
        if (mx >= gx1 && mx < gx1 + CW + 4 && my >= gy0 && my < gy1) {
            int row = (my - gy0) / CH;
            return new int[]{RouletteMath.COLUMN, 2 - row};
        }
        if (mx >= gx0 && mx < gx1 && my >= gy1 && my < gy1 + 5) {
            int col = (mx - gx0) / CW;
            int fx = (mx - gx0) % CW;
            if (fx >= CW - 3 && col < 11) return new int[]{RouletteMath.SIX_LINE, col * 3 + 1};
            if (fx <= 2 && col > 0) return new int[]{RouletteMath.SIX_LINE, (col - 1) * 3 + 1};
            return new int[]{RouletteMath.STREET, col * 3 + 1};
        }
        if (mx >= gx0 && mx < gx1 && my >= gy1 + 6 && my < gy1 + 22) {
            return new int[]{RouletteMath.DOZEN, (mx - gx0) / (4 * CW)};
        }
        if (mx >= gx0 && mx < gx1 && my >= gy1 + 24 && my < gy1 + 40) {
            int i = (mx - gx0) / (2 * CW);
            int[] kinds = {RouletteMath.LOW, RouletteMath.EVEN, RouletteMath.RED, RouletteMath.BLACK, RouletteMath.ODD, RouletteMath.HIGH};
            return new int[]{kinds[i], 0};
        }
        if (mx >= gx0 && mx < gx1 && my >= gy0 && my < gy1) {
            int col = (mx - gx0) / CW, row = (my - gy0) / CH;
            int fx = (mx - gx0) % CW, fy = (my - gy0) % CH;
            int n = col * 3 + (3 - row); // row 0 is the top row (3, 6, 9 …)
            boolean left = fx <= 2 && col > 0, right = fx >= CW - 3 && col < 11;
            boolean top = fy <= 2 && row > 0, bottom = fy >= CH - 3 && row < 2;
            if ((left || right) && (top || bottom)) {
                int c = right ? col : col - 1;
                int rBottom = bottom ? row + 1 : row; // the lower of the two rows touching the corner
                int low = c * 3 + (3 - rBottom);
                if (RouletteMath.valid(RouletteMath.CORNER, low)) return new int[]{RouletteMath.CORNER, low};
            }
            if (left || right) {
                int other = right ? n + 3 : n - 3;
                return new int[]{RouletteMath.SPLIT, Math.min(n, other) * 100 + Math.max(n, other)};
            }
            if (top || bottom) {
                int other = top ? n + 1 : n - 1;
                return new int[]{RouletteMath.SPLIT, Math.min(n, other) * 100 + Math.max(n, other)};
            }
            return new int[]{RouletteMath.STRAIGHT, n};
        }
        return null;
    }

    /** Screen point where a bet's chips sit. */
    private int[] anchor(int kind, int target) {
        switch (kind) {
            case RouletteMath.STRAIGHT -> {
                if (target == 0) return new int[]{bx + CW / 2, by + CH * 3 / 2};
                return new int[]{cellX(target) + CW / 2, cellY(target) + CH / 2};
            }
            case RouletteMath.SPLIT -> {
                int a = target / 100, b = target % 100;
                return new int[]{(cellX(a) + cellX(b)) / 2 + CW / 2, (cellY(a) + cellY(b)) / 2 + CH / 2};
            }
            case RouletteMath.CORNER -> {
                return new int[]{cellX(target) + CW, cellY(target)};
            }
            case RouletteMath.STREET -> {
                return new int[]{cellX(target) + CW / 2, by + 3 * CH};
            }
            case RouletteMath.SIX_LINE -> {
                return new int[]{cellX(target) + CW, by + 3 * CH};
            }
            case RouletteMath.COLUMN -> {
                return new int[]{bx + CW + 12 * CW + (CW + 4) / 2, by + (2 - target) * CH + CH / 2};
            }
            case RouletteMath.DOZEN -> {
                return new int[]{bx + CW + target * 4 * CW + 2 * CW, by + 3 * CH + 14};
            }
            default -> {
                int[] order = {RouletteMath.LOW, RouletteMath.EVEN, RouletteMath.RED, RouletteMath.BLACK, RouletteMath.ODD, RouletteMath.HIGH};
                for (int i = 0; i < order.length; i++) {
                    if (order[i] == kind) return new int[]{bx + CW + i * 2 * CW + CW, by + 3 * CH + 32};
                }
                return null;
            }
        }
    }

    private Component describe(int kind, int target) {
        String what = switch (kind) {
            case RouletteMath.STRAIGHT -> Integer.toString(target);
            case RouletteMath.SPLIT -> (target / 100) + "/" + (target % 100);
            case RouletteMath.STREET -> target + "–" + (target + 2);
            case RouletteMath.CORNER -> target + "/" + (target + 1) + "/" + (target + 3) + "/" + (target + 4);
            case RouletteMath.SIX_LINE -> target + "–" + (target + 5);
            case RouletteMath.DOZEN -> (target * 12 + 1) + "–" + (target * 12 + 12);
            case RouletteMath.COLUMN -> Component.translatable("challengecraft.casino.roulette.column", target + 1).getString();
            default -> "";
        };
        int covered = RouletteMath.numbersCovered(kind, target);
        int pays = kind == RouletteMath.STRAIGHT && target == 0 ? 36 : 36 / covered - 1;
        return Component.translatable("challengecraft.casino.roulette.kind." + kind, what, pays);
    }

    private void drawChips(GuiGraphicsExtractor ctx, int mx, int my) {
        int cx0 = x + 12, cy = y + H - 30;
        for (int i = 0; i < DENOMS.length; i++) {
            int cx = cx0 + i * 26;
            boolean sel = i == chip;
            int lift = sel ? 3 : 0;
            ctx.blit(RenderPipelines.GUI_TEXTURED, CHIPS, cx, cy - lift, i * 32, 0, 22, 22, 32, 32, 256, 32);
            if (sel) ctx.outline(cx - 1, cy - lift - 1, 24, 24, 0xFFFFE9A8);
            String label = DENOMS[i] >= 1000 ? (DENOMS[i] / 1000) + "K" : Long.toString(DENOMS[i]);
            CraftUI.drawCenteredScaled(ctx, font, Component.literal(label), cx + 11, cy + 11 - lift, 0.7f, 0xFF101010);
        }
        ctx.text(font, Component.translatable("challengecraft.casino.roulette.balance",
                CasinoEconomy.formatFull(CasinoClientState.balance())), x + 12, cy - 12, 0xFFFFE9A8, false);
    }

    private void drawStatus(GuiGraphicsExtractor ctx, CasinoNet.RouletteState s, float since) {
        Component status;
        float progress = 0;
        int colour = CraftUI.TEXT_SECONDARY;
        if (s == null || s.phase() == RouletteGame.WAITING) {
            status = Component.translatable("challengecraft.casino.roulette.waiting");
        } else if (s.phase() == RouletteGame.BETTING) {
            float left = Math.max(0, s.ticksLeft() - since);
            status = Component.translatable("challengecraft.casino.roulette.betting", (int) Math.ceil(left / 20f));
            progress = left / RouletteGame.BETTING_TICKS;
            colour = left < 100 ? CraftUI.WARNING : CraftUI.SUCCESS;
        } else if (s.phase() == RouletteGame.SPINNING) {
            status = Component.translatable("challengecraft.casino.roulette.spinning");
            colour = CraftUI.GOLD;
        } else {
            status = Component.translatable("challengecraft.casino.roulette.result", s.result());
            colour = CraftUI.GOLD;
        }
        ctx.text(font, status, x + W - 12 - font.width(status), y + 10, colour, false);
        CraftUI.progressBar(ctx, bx, y + 26, W - 158, 4, progress, colour);
        // History.
        if (s != null) {
            int hx = bx;
            int hy = by + 3 * CH + 46;
            ctx.text(font, Component.translatable("challengecraft.casino.roulette.history"), hx, hy + 2, CraftUI.TEXT_MUTED, false);
            hx += font.width(Component.translatable("challengecraft.casino.roulette.history")) + 4;
            for (int n : s.history()) {
                int col = RouletteMath.colour(n);
                int c = col == 0 ? 0xFF1E9E4A : col == 1 ? 0xFFB8202A : 0xFF18181C;
                ctx.fill(hx, hy, hx + 16, hy + 12, 0xFFE8D9B0);
                ctx.fill(hx + 1, hy + 1, hx + 15, hy + 11, c);
                ctx.centeredText(font, Component.literal(Integer.toString(n)), hx + 8, hy + 2, 0xFFFFFFFF);
                hx += 18;
            }
            // My stake this round.
            long mine = 0;
            String me = minecraft.player.getName().getString();
            for (CasinoNet.RouletteBet b : s.bets()) if (b.player().equals(me)) mine += b.amount();
            ctx.text(font, Component.translatable("challengecraft.casino.roulette.my_stake", CasinoEconomy.formatFull(mine)),
                    x + 12, y + 26, CraftUI.TEXT_PRIMARY, false);
        }
    }

    // ---- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int mx = (int) event.x(), my = (int) event.y();
        int cx0 = x + 12, cy = y + H - 30;
        for (int i = 0; i < DENOMS.length; i++) {
            int cx = cx0 + i * 26;
            if (mx >= cx && mx < cx + 22 && my >= cy - 3 && my < cy + 22) {
                chip = i;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(CasinoSounds.CHIP, 1.0f + i * 0.04f, 0.7f));
                return true;
            }
        }
        int[] hit = hitTest(mx, my);
        CasinoNet.RouletteState s = state();
        if (hit != null && (s == null || s.phase() == RouletteGame.WAITING || s.phase() == RouletteGame.BETTING)) {
            CasinoClient.send(new CasinoNet.Action(CasinoNet.Action.ROULETTE_BET, pos.asLong(), hit[0], hit[1], DENOMS[chip], ""));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        chip = Math.max(0, Math.min(DENOMS.length - 1, chip + (verticalAmount > 0 ? 1 : -1)));
        return true;
    }
}
