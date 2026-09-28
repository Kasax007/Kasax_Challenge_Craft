package net.kasax.challengecraft.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.DeviceType;
import net.kasax.challengecraft.casino.SlotMath;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The slot machine as the world shows it: reels on drums behind the glass, the winning lines, the
 * display under the reels and the chip tray on the shelf. Everything a player needs is on the
 * machine — there is no screen overlay.
 *
 * <p>The reels keep showing the last result (and its winning lines) until the next spin. Reel 0 is
 * the leftmost as seen by the player, which in the north-facing model space is the <b>+x</b> end of
 * the window.
 */
@Environment(EnvType.CLIENT)
final class SlotView {
    // Reel window, in model pixels (must match models/block/slot_machine.json).
    static final float WIN_X0 = 2.0f, WIN_X1 = 14.0f, WIN_Y0 = 10.0f, WIN_Y1 = 18.0f;
    private static final float DRUM_R = 5.0f;
    private static final float DRUM_AXIS_Z = 3.2f + DRUM_R;
    private static final float ROW_ANGLE = 0.52f; // radians between two symbols on the drum
    private static final float REEL_W = (WIN_X1 - WIN_X0) / SlotMath.REELS;
    private static final int[] IDLE_STOPS = {3, 9, 28, 42, 34};

    private SlotView() {
    }

    /** Centre x (model space) of reel r: reel 0 is on the player's left, which is +x. */
    private static float reelCenterX(int r) {
        return WIN_X1 - (r + 0.5f) * REEL_W;
    }

    static void draw(DevicePainter p, BlockPos pos, float partial) {
        SlotAnimation anim = CasinoClientState.SLOTS.get(pos.asLong());
        boolean live = anim != null;
        if (anim == null) anim = CasinoClientState.SLOT_LAST.get(pos.asLong());
        // A finished play stays on the reels: show it as the timeline's final frame.
        float t = anim == null ? 0 : live ? anim.elapsed(partial) : anim.totalTicks + 1000f;
        drawReels(p, anim, t);
        drawDisplay(p, anim, live, t);
        TrayView.draw(p, DeviceType.SLOT, pos);
    }

    private static void drawReels(DevicePainter p, SlotAnimation anim, float t) {
        PoseStack matrices = p.pose;
        SlotAnimation.Phase phase = anim == null ? null : anim.phaseAt(t);

        // Drum surfaces: ivory, shaded darker towards the top and bottom of the window like a cylinder.
        p.collector.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, buf) -> {
            for (int r = 0; r < SlotMath.REELS; r++) {
                float x0 = reelCenterX(r) - REEL_W / 2 + 0.15f;
                float x1 = x0 + REEL_W - 0.3f;
                boolean glow = anim != null && anim.reelInTension(r, t);
                boolean expanded = anim != null && phase != null
                        && (phase.kind() == SlotAnimation.Kind.FS_EXPAND || phase.kind() == SlotAnimation.Kind.FS_OUTRO
                        || phase.kind() == SlotAnimation.Kind.DONE)
                        && (anim.result.expandMasks()[Math.min(phase.spin(), anim.spinCount - 1)] & (1 << r)) != 0;
                int segments = 8;
                for (int s = 0; s < segments; s++) {
                    float a0 = -1.1f + 2.2f * s / segments;
                    float a1 = -1.1f + 2.2f * (s + 1) / segments;
                    float y0 = drumY(a0), y1 = drumY(a1);
                    float z0 = drumZ(a0) + 0.35f, z1 = drumZ(a1) + 0.35f;
                    float shade0 = 0.55f + 0.45f * (float) Math.cos(a0);
                    float shade1 = 0.55f + 0.45f * (float) Math.cos(a1);
                    float rr = expanded ? 1.0f : glow ? 1.0f : 0.96f;
                    float gg = expanded ? 0.82f : glow ? 0.93f : 0.93f;
                    float bb = expanded ? 0.35f : glow ? 0.62f : 0.84f;
                    CasinoWorldRenderer.quad(buf, pose, x0, y0, z0, x1, y0, z0, x1, y1, z1, x0, y1, z1,
                            rr * shade0, gg * shade0, bb * shade0, rr * shade1, gg * shade1, bb * shade1, 1f);
                }
                // Dark divider between reels.
                float dx = reelCenterX(r) + REEL_W / 2;
                float dz = DRUM_AXIS_Z - DRUM_R + 0.2f;
                CasinoWorldRenderer.quad(buf, pose, dx - 0.08f, WIN_Y0 - 1, dz, dx + 0.08f, WIN_Y0 - 1, dz,
                        dx + 0.08f, WIN_Y1 + 1, dz, dx - 0.08f, WIN_Y1 + 1, dz, 0.12f, 0.08f, 0.06f, 0.12f, 0.08f, 0.06f, 1f);
            }
            // Winning lines glow across the glass, one after the other.
            if (anim != null && phase != null && showsLines(phase)) {
                SlotMath.Spin spin = anim.spins.get(anim.spinShown(t));
                if (!spin.lineWins().isEmpty()) {
                    float clock = phase.kind() == SlotAnimation.Kind.DONE ? CasinoClientState.clientTick : phase.local();
                    int idx = (int) (clock / 14f) % spin.lineWins().size();
                    SlotMath.LineWin w = spin.lineWins().get(idx);
                    float pulse = 0.55f + 0.45f * (float) Math.sin(clock * 0.45f);
                    int[] rows = SlotMath.LINE_ROWS[w.line()];
                    for (int r = 0; r < w.count(); r++) {
                        float cx = reelCenterX(r);
                        float cy = rowY(rows[r]);
                        float z = DRUM_AXIS_Z - DRUM_R - 0.3f;
                        float h = 1.2f;
                        float[] c = CasinoWorldRenderer.lineColour(w.line());
                        CasinoWorldRenderer.quad(buf, pose, cx - REEL_W / 2 + 0.2f, cy - h, z, cx + REEL_W / 2 - 0.2f, cy - h, z,
                                cx + REEL_W / 2 - 0.2f, cy + h, z, cx - REEL_W / 2 + 0.2f, cy + h, z,
                                c[0], c[1], c[2], c[0], c[1], c[2], 0.28f * pulse);
                    }
                }
            }
        });

        // Symbols on the drums.
        for (int r = 0; r < SlotMath.REELS; r++) {
            float position = anim == null ? IDLE_STOPS[r] : anim.reelPosition(r, t);
            boolean spinning = anim != null && anim.reelSpinning(r, t);
            int base = (int) Math.floor(position);
            float frac = position - base;
            boolean expanded = false;
            int lucky = -1;
            if (anim != null && phase != null && anim.result.luckySymbol() >= 0) {
                int spinIdx = Math.min(phase.spin(), anim.spinCount - 1);
                boolean showExpand = phase.kind() == SlotAnimation.Kind.FS_EXPAND
                        || phase.kind() == SlotAnimation.Kind.FS_OUTRO || phase.kind() == SlotAnimation.Kind.DONE;
                if (showExpand && (anim.result.expandMasks()[spinIdx] & (1 << r)) != 0) {
                    expanded = true;
                    lucky = anim.result.luckySymbol();
                }
            }
            float cx = reelCenterX(r);
            if (expanded) {
                float grow = phase.kind() == SlotAnimation.Kind.FS_EXPAND ? Math.min(1f, phase.local() / 10f) : 1f;
                matrices.pushPose();
                matrices.translate(cx, (WIN_Y0 + WIN_Y1) / 2f, DRUM_AXIS_Z - DRUM_R - 0.1f);
                float s = REEL_W * (1.0f + 1.4f * CasinoWorldRenderer.easeOutBack(grow));
                matrices.scale(s, s, s);
                matrices.rotate(Axis.YP.rotationDegrees(180f));
                CasinoWorldRenderer.submitItem(p, new ItemStack(SlotMath.SYMBOL_ITEMS[lucky]));
                matrices.popPose();
                continue;
            }
            for (int k = -1; k <= 3; k++) {
                float rowPos = k - frac; // position of symbol (base + k) relative to the top row
                float angle = (rowPos - 1f) * ROW_ANGLE;
                if (Math.abs(angle) > 1.15f) continue;
                int symbol = SlotMath.symbolAt(r, base + k, 0);
                float y = drumY(angle);
                float z = drumZ(angle);
                matrices.pushPose();
                matrices.translate(cx, y, z - 0.05f);
                matrices.rotate(Axis.XP.rotation(-angle));
                float s = REEL_W * 0.82f;
                matrices.scale(s, s * (spinning ? 1.25f : 1f), s);
                matrices.rotate(Axis.YP.rotationDegrees(180f));
                CasinoWorldRenderer.submitItem(p, new ItemStack(SlotMath.SYMBOL_ITEMS[symbol]));
                matrices.popPose();
            }
        }
    }

    /** The two-line display under the reels: the result, and the viewer's own stake. */
    private static void drawDisplay(DevicePainter p, SlotAnimation anim, boolean live, float t) {
        double u0 = 2.5, u1 = 13.5, y0 = 5.0, y1 = 9.0, v = 2.9;
        p.rectFront(u0 - 0.35, y0 - 0.35, u1 + 0.35, y1 + 0.35, v + 0.02, 0xFFB8912F);
        p.rectFront(u0, y0, u1, y1, v, 0xFF07080C);

        Component top;
        int topColour;
        if (anim == null) {
            top = Component.translatable("challengecraft.casino.slot.display.idle");
            topColour = 0xFFE3B35A;
        } else {
            SlotAnimation.Phase phase = anim.phaseAt(t);
            long shown = anim.shownWin(t);
            boolean free = phase.kind() == SlotAnimation.Kind.FS_SPIN || phase.kind() == SlotAnimation.Kind.FS_EXPAND
                    || phase.kind() == SlotAnimation.Kind.FS_INTRO;
            if (live && phase.kind() == SlotAnimation.Kind.BASE_SPIN) {
                top = Component.translatable("challengecraft.casino.slot.display.spinning");
                topColour = 0xFFBBBBBB;
            } else if (live && free) {
                top = Component.translatable("challengecraft.casino.slot.display.free",
                        Math.max(1, phase.spin()), anim.spinCount - 1, CasinoEconomy.format(shown));
                topColour = 0xFFD08CFF;
            } else if (shown > 0) {
                top = Component.translatable("challengecraft.casino.slot.display.win", CasinoEconomy.format(shown));
                boolean blink = live && (CasinoClientState.clientTick / 6) % 2 == 0;
                topColour = blink ? 0xFFFFF3A0 : 0xFFFFC53D;
            } else {
                top = Component.translatable("challengecraft.casino.slot.display.no_win");
                topColour = 0xFF9A9AA5;
            }
        }
        fit(p, top, (u0 + u1) / 2, 7.55, v, u1 - u0 - 0.6, 1.25, topColour);

        CasinoNet.State s = CasinoClientState.state;
        if (s != null) {
            Component stake = Component.translatable("challengecraft.casino.display.stake", CasinoEconomy.format(s.betAmount()));
            fit(p, stake, (u0 + u1) / 2, 5.85, v, u1 - u0 - 0.6, 1.0, 0xFF7BE0A4);
        }
    }

    /** Front text shrunk to fit a width. */
    static void fit(DevicePainter p, Component text, double u, double y, double v, double maxWidth, double height, int argb) {
        double w = p.width(text) * height / 7.0;
        double h = w > maxWidth ? height * maxWidth / w : height;
        p.textFront(text, u, y, v, h, argb, 0);
    }

    private static boolean showsLines(SlotAnimation.Phase p) {
        return p.kind() == SlotAnimation.Kind.BASE_WIN || p.kind() == SlotAnimation.Kind.FS_EXPAND
                || (p.kind() == SlotAnimation.Kind.FS_SPIN && p.local() > SlotMath.FS_STOP_TICK[4] + 2)
                || p.kind() == SlotAnimation.Kind.DONE;
    }

    private static float drumY(float angle) {
        return (WIN_Y0 + WIN_Y1) / 2f - (float) Math.sin(angle) * DRUM_R;
    }

    private static float drumZ(float angle) {
        return DRUM_AXIS_Z - (float) Math.cos(angle) * DRUM_R;
    }

    private static float rowY(int row) {
        return drumY((row - 1) * ROW_ANGLE);
    }
}
