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
        drawLever(p, live ? anim.leverAngle(t) : 0f);
        drawDisplay(p, anim, live, t);
        TrayView.draw(p, DeviceType.SLOT, pos);
    }

    private static void drawReels(DevicePainter p, SlotAnimation anim, float t) {
        PoseStack matrices = p.pose;
        SlotAnimation.Phase phase = anim == null ? null : anim.phaseAt(t);

        // Drum surfaces: ivory, shaded darker towards the top and bottom of the window like a cylinder.
        for (int r = 0; r < SlotMath.REELS; r++) {
            float x0 = reelCenterX(r) - REEL_W / 2 + 0.15f;
            float x1 = x0 + REEL_W - 0.3f;
            boolean glow = anim != null && anim.reelInTension(r, t);
            // A reel the lucky item spreads over glows gold while it does (only then: the expansion
            // is a moment of the free spin, afterwards the reels show their own symbols again).
            boolean expanded = anim != null && phase != null && phase.kind() == SlotAnimation.Kind.FS_EXPAND
                    && (anim.result.expandMasks()[Math.min(phase.spin(), anim.spinCount - 1)] & (1 << r)) != 0;
            int segments = 10;
            for (int s = 0; s < segments; s++) {
                float a0 = -1.1f + 2.2f * s / segments;
                float a1 = -1.1f + 2.2f * (s + 1) / segments;
                float y0 = drumY(a0), y1 = drumY(a1);
                float z0 = drumZ(a0) + 0.35f, z1 = drumZ(a1) + 0.35f;
                float shade = 0.55f + 0.45f * (float) Math.cos((a0 + a1) / 2f);
                float rr = expanded ? 1.0f : glow ? 1.0f : 0.96f;
                float gg = expanded ? 0.82f : glow ? 0.93f : 0.93f;
                float bb = expanded ? 0.35f : glow ? 0.62f : 0.84f;
                p.quadModel(x0, y0, z0, x1, y0, z0, x1, y1, z1, x0, y1, z1, DevicePainter.rgb(rr * shade, gg * shade, bb * shade));
            }
            // The drum curves away under the bottom row, so from above one looked past it onto the
            // window's dark floor (model y 10.4) and the row seemed to sit in shadow: carry the
            // reel's ivory on across that strip of floor up to the glass.
            float floorY = 10.45f, drumFoot = drumZ((float) Math.asin(((WIN_Y0 + WIN_Y1) / 2f - floorY) / DRUM_R)) + 0.35f;
            int apron = DevicePainter.rgb(0.96f * 0.78f, 0.93f * 0.78f, 0.84f * 0.78f);
            p.quadModel(x0, floorY, 2.6f, x1, floorY, 2.6f, x1, floorY, drumFoot, x0, floorY, drumFoot, apron);
            // Dark divider between reels, a little in front of the drums.
            float dx = reelCenterX(r) + REEL_W / 2;
            float dz = DRUM_AXIS_Z - DRUM_R + 0.1f;
            p.quadModel(dx - 0.08f, WIN_Y0 - 1, dz, dx + 0.08f, WIN_Y0 - 1, dz,
                    dx + 0.08f, WIN_Y1 + 1, dz, dx - 0.08f, WIN_Y1 + 1, dz, 0xFF1F140F);
        }
        p.collector.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, buf) -> {
            if (anim == null || phase == null) return;
            if (phase.kind() == SlotAnimation.Kind.FS_EXPAND) {
                // Once the lucky item stands on its reels it pays on all ten lines: they run over
                // the glass one after another, then glow all together.
                float k = phase.local() - anim.expandFillEnd(phase.spin());
                if (k < 0) return;
                int current = (int) (k / 2f);
                float pulse = 0.55f + 0.45f * (float) Math.sin(k * 0.45f);
                for (int l = 0; l < SlotMath.LINES; l++) {
                    if (current < SlotMath.LINES && l != current) continue;
                    lineGlow(buf, pose, l, SlotMath.REELS, current < SlotMath.LINES ? 0.36f : 0.14f * pulse);
                }
            } else if (showsLines(phase)) {
                // Winning lines glow across the glass, one after the other.
                SlotMath.Spin spin = anim.spins.get(anim.spinShown(t));
                if (!spin.lineWins().isEmpty()) {
                    float clock = phase.kind() == SlotAnimation.Kind.DONE ? CasinoClientState.clientTick : phase.local();
                    int idx = (int) (clock / 14f) % spin.lineWins().size();
                    SlotMath.LineWin w = spin.lineWins().get(idx);
                    float pulse = 0.55f + 0.45f * (float) Math.sin(clock * 0.45f);
                    lineGlow(buf, pose, w.line(), w.count(), 0.28f * pulse);
                }
            }
        });

        // Symbols on the drums.
        for (int r = 0; r < SlotMath.REELS; r++) {
            float position = anim == null ? IDLE_STOPS[r] : anim.reelPosition(r, t);
            boolean spinning = anim != null && anim.reelSpinning(r, t);
            int base = (int) Math.floor(position);
            float frac = position - base;
            // During an expansion the lucky item spreads over this reel cell by cell: each cell
            // turns over like a card and comes back up as the lucky item.
            boolean expanding = anim != null && phase != null && phase.kind() == SlotAnimation.Kind.FS_EXPAND
                    && (anim.result.expandMasks()[phase.spin()] & (1 << r)) != 0;
            float cx = reelCenterX(r);
            for (int k = -1; k <= 3; k++) {
                float rowPos = k - frac; // position of symbol (base + k) relative to the top row
                float angle = (rowPos - 1f) * ROW_ANGLE;
                if (Math.abs(angle) > 1.15f) continue;
                int symbol = SlotMath.symbolAt(r, base + k, 0);
                float y = drumY(angle);
                float z = drumZ(angle);
                // The symbol stays upright and is squashed by the drum's curvature instead of being
                // tilted with it: a tilted item faces the ground on the bottom row, and the world's
                // item lighting then shades it almost black when seen from above. Upright it is lit
                // like the middle row; it is pulled forward so its upper or lower half, which would
                // otherwise sink into the curved drum, stays in front of it.
                float s = REEL_W * 0.82f;
                float squash = 0.45f + 0.55f * (float) Math.cos(angle);
                float lift = 0.05f + 0.5f * s * squash * (float) Math.abs(Math.sin(angle));
                float flip = 1f, pop = 1f;
                if (expanding && k >= 0 && k < SlotMath.ROWS) {
                    float progress = anim.expandProgress(phase.spin(), r, k, phase.local());
                    if (k == anim.luckyRow(phase.spin(), r)) {
                        // The cell it spreads from beats once to show where it starts.
                        float beat = Math.max(0f, 1f - phase.local() / 10f);
                        pop = 1f + 0.25f * beat * (float) Math.abs(Math.sin(phase.local() * 0.6f));
                    } else if (progress > 0f) {
                        pop = 1f + 0.22f * (float) Math.sin(Math.PI * progress);
                        flip = (float) Math.abs(Math.cos(Math.PI * progress));
                        if (progress >= 0.5f) symbol = anim.result.luckySymbol();
                    }
                }
                matrices.pushPose();
                matrices.translate(cx, y, z - lift);
                matrices.scale(s * Math.max(0.03f, flip) * pop, s * squash * pop * (spinning ? 1.25f : 1f), s);
                matrices.rotate(Axis.YP.rotationDegrees(180f));
                CasinoWorldRenderer.submitItem(p, new ItemStack(SlotMath.SYMBOL_ITEMS[symbol]));
                matrices.popPose();
            }
        }
        if (anim != null && phase != null && phase.kind() == SlotAnimation.Kind.FS_INTRO && anim.result.luckySymbol() >= 0) {
            drawPicker(p, anim, phase.local());
        }
    }

    // ---- the lever ------------------------------------------------------------------------------

    /** The lever's pivot in its mount (model space; the mount itself is part of the block model). */
    private static final float LEVER_PY = 12.75f, LEVER_PZ = 8.3f;

    /**
     * The arm and knob of the lever on the player's right, turned by {@code degrees} about the
     * mount: negative swings the knob towards the player and down, as when it is pulled.
     */
    private static void drawLever(DevicePainter p, float degrees) {
        double a = Math.toRadians(degrees);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        leverBox(p, 0.2f, 13.5f, 8.0f, 0.8f, 20.5f, 8.6f, cos, sin, 0xFFC9CED6);
        leverBox(p, -0.2f, 20.5f, 7.6f, 1.2f, 21.9f, 9.0f, cos, sin, 0xFFC4262C);
    }

    /** A box of the lever, turned about the pivot; each face shaded as if lit from above. */
    private static void leverBox(DevicePainter p, float x0, float y0, float z0, float x1, float y1, float z1,
                                 float cos, float sin, int argb) {
        float[][] c = new float[8][];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? x0 : x1, y = (i & 2) == 0 ? y0 : y1, z = (i & 4) == 0 ? z0 : z1;
            float dy = y - LEVER_PY, dz = z - LEVER_PZ;
            c[i] = new float[]{x, LEVER_PY + dy * cos - dz * sin, LEVER_PZ + dy * sin + dz * cos};
        }
        int top = argb, side = DevicePainter.shade(argb, 0.8f), dark = DevicePainter.shade(argb, 0.6f);
        face(p, c, 2, 3, 7, 6, top);    // up
        face(p, c, 0, 4, 5, 1, dark);   // down
        face(p, c, 0, 1, 3, 2, side);   // north (front)
        face(p, c, 4, 6, 7, 5, side);   // south
        face(p, c, 0, 2, 6, 4, dark);   // -x
        face(p, c, 1, 5, 7, 3, side);   // +x
    }

    private static void face(DevicePainter p, float[][] c, int a, int b, int d, int e, int argb) {
        p.quadModel(c[a][0], c[a][1], c[a][2], c[b][0], c[b][1], c[b][2], c[d][0], c[d][1], c[d][2],
                c[e][0], c[e][1], c[e][2], argb);
    }

    /** Model depth of the pick panel in front of the reels, and of the items running on it. */
    private static final float PICK_Z = 2.75f, PICK_ITEM_Z = 2.4f;

    /**
     * The free-spins pick: a panel slides in front of the reels and a strip of the item symbols runs
     * across it, slows down and stops on the lucky item, which then flashes in its gold frame.
     */
    private static void drawPicker(DevicePainter p, SlotAnimation anim, float local) {
        if (local < 6) return;
        PoseStack matrices = p.pose;
        float open = Math.min(1f, (local - 6) / 8f); // the panel unfolds from the middle row
        float cy = (WIN_Y0 + WIN_Y1) / 2f, half = 2.7f * open;
        float x0 = WIN_X0 + 0.2f, x1 = WIN_X1 - 0.2f;
        boolean landed = local >= SlotAnimation.PICK_LAND;
        float flash = landed ? 0.5f + 0.5f * (float) Math.sin((local - SlotAnimation.PICK_LAND) * 0.7f) : 0f;
        // Panel, gold rails above and below, and the frame of the pick window in the middle.
        p.quadModel(x0, cy - half, PICK_Z, x1, cy - half, PICK_Z, x1, cy + half, PICK_Z, x0, cy + half, PICK_Z, 0xFF1B0F2E);
        int rail = 0xFFE8B83A;
        p.quadModel(x0, cy + half, PICK_Z - 0.02f, x1, cy + half, PICK_Z - 0.02f, x1, cy + half - 0.3f, PICK_Z - 0.02f,
                x0, cy + half - 0.3f, PICK_Z - 0.02f, rail);
        p.quadModel(x0, cy - half + 0.3f, PICK_Z - 0.02f, x1, cy - half + 0.3f, PICK_Z - 0.02f, x1, cy - half, PICK_Z - 0.02f,
                x0, cy - half, PICK_Z - 0.02f, rail);
        if (open < 1f) return;
        float mid = (WIN_X0 + WIN_X1) / 2f, w = 1.75f, h = 2.3f;
        int frame = landed ? DevicePainter.rgb(0.91f, 0.72f + 0.25f * flash, 0.23f + 0.6f * flash) : 0xFF8A6A2A;
        float fz = PICK_Z - 0.04f, t = 0.28f;
        p.quadModel(mid - w, cy + h, fz, mid + w, cy + h, fz, mid + w, cy + h - t, fz, mid - w, cy + h - t, fz, frame);
        p.quadModel(mid - w, cy - h + t, fz, mid + w, cy - h + t, fz, mid + w, cy - h, fz, mid - w, cy - h, fz, frame);
        p.quadModel(mid - w, cy + h, fz, mid - w + t, cy + h, fz, mid - w + t, cy - h, fz, mid - w, cy - h, fz, frame);
        p.quadModel(mid + w - t, cy + h, fz, mid + w, cy + h, fz, mid + w, cy - h, fz, mid + w - t, cy - h, fz, frame);

        // The strip: items enter on the player's right (-x) and run to the left (+x).
        float steps = anim.pickSteps(local);
        int base = (int) Math.floor(steps);
        float f = steps - base;
        for (int k = -2; k <= 3; k++) {
            float offset = k - f;
            float x = mid - offset * 3.1f;
            if (Math.abs(x - mid) > 5.0f) continue;
            float size = REEL_W * (1.05f - 0.28f * Math.min(2f, Math.abs(offset)));
            if (landed && k == 0) size *= 1.12f + 0.12f * flash;
            matrices.pushPose();
            matrices.translate(x, cy, PICK_ITEM_Z);
            matrices.scale(size, size, size * 0.5f);
            matrices.rotate(Axis.YP.rotationDegrees(180f));
            CasinoWorldRenderer.submitItem(p, new ItemStack(SlotMath.SYMBOL_ITEMS[anim.pickSymbol(base + k)]));
            matrices.popPose();
        }
    }

    /** One pay line glowing over the glass, across the first {@code reels} reels. */
    private static void lineGlow(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose pose, int line, int reels, float alpha) {
        int[] rows = SlotMath.LINE_ROWS[line];
        float z = DRUM_AXIS_Z - DRUM_R - 0.3f;
        float h = 1.2f;
        float[] c = CasinoWorldRenderer.lineColour(line);
        for (int r = 0; r < reels; r++) {
            float cx = reelCenterX(r);
            float cy = rowY(rows[r]);
            CasinoWorldRenderer.quad(buf, pose, cx - REEL_W / 2 + 0.2f, cy - h, z, cx + REEL_W / 2 - 0.2f, cy - h, z,
                    cx + REEL_W / 2 - 0.2f, cy + h, z, cx - REEL_W / 2 + 0.2f, cy + h, z,
                    c[0], c[1], c[2], c[0], c[1], c[2], alpha);
        }
    }

    /** The two-line display under the reels: the result, and the viewer's own stake. */
    private static void drawDisplay(DevicePainter p, SlotAnimation anim, boolean live, float t) {
        double u0 = 2.5, u1 = 13.5, y0 = 5.0, y1 = 9.0, v = 2.6; // clear of the cabinet face at v 3
        p.rectFront(u0 - 0.35, y0 - 0.35, u1 + 0.35, y1 + 0.35, v + 0.1, 0xFFB8912F);
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
            SlotAnimation.Celebration party = live ? anim.celebrationAt(t) : null;
            int lucky = anim.result.luckySymbol();
            if (party != null) {
                // The win's tier in its own colours: gold, then flashing, then all the colours.
                top = Component.translatable("challengecraft.casino.slot.tier." + party.tier(), CasinoEconomy.format(party.win()));
                topColour = tierColour(party.tier(), party.local());
            } else if (live && phase.kind() == SlotAnimation.Kind.FS_INTRO && lucky >= 0 && phase.local() >= SlotAnimation.PICK_START) {
                boolean landed = phase.local() >= SlotAnimation.PICK_LAND;
                top = landed ? Component.translatable("challengecraft.casino.slot.display.lucky",
                        new ItemStack(SlotMath.SYMBOL_ITEMS[lucky]).getHoverName())
                        : Component.translatable("challengecraft.casino.slot.display.pick");
                topColour = landed && (CasinoClientState.clientTick / 4) % 2 == 0 ? 0xFFFFF3A0 : 0xFFE8B83A;
            } else if (live && phase.kind() == SlotAnimation.Kind.FS_EXPAND && lucky >= 0
                    && phase.local() < anim.expandFillEnd(phase.spin())) {
                top = Component.translatable("challengecraft.casino.slot.display.expanding",
                        new ItemStack(SlotMath.SYMBOL_ITEMS[lucky]).getHoverName());
                topColour = 0xFFFFD24A;
            } else if (live && phase.kind() == SlotAnimation.Kind.BASE_SPIN) {
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

    private static int tierColour(int tier, float local) {
        int blink = (int) (local / 4) % 2;
        return switch (tier) {
            case 1 -> 0xFFFFC53D;
            case 2 -> blink == 0 ? 0xFFFFE27A : 0xFFFF9A2E;
            case 3 -> blink == 0 ? 0xFFFF6FD8 : 0xFF6FE8FF;
            default -> 0xFF000000 | java.awt.Color.HSBtoRGB((local % 30) / 30f, 0.65f, 1f);
        };
    }

    /** Front text shrunk to fit a width. */
    static void fit(DevicePainter p, Component text, double u, double y, double v, double maxWidth, double height, int argb) {
        double w = p.width(text) * height / 7.0;
        double h = w > maxWidth ? height * maxWidth / w : height;
        p.textFront(text, u, y, v, h, argb, 0);
    }

    private static boolean showsLines(SlotAnimation.Phase p) {
        return p.kind() == SlotAnimation.Kind.BASE_WIN
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
