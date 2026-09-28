package net.kasax.challengecraft.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.kasax.challengecraft.casino.CasinoDeviceBlock;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CrashGame;
import net.kasax.challengecraft.casino.DeviceType;
import net.kasax.challengecraft.casino.RouletteGame;
import net.kasax.challengecraft.casino.RouletteMath;
import net.kasax.challengecraft.casino.SlotMath;
import net.kasax.challengecraft.challenges.Chal_50_HouseAlwaysWins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every moving part of the casino into the world: the slot reels behind the machine's glass,
 * the spinning roulette wheel and ball, and the crash rocket.
 *
 * <p>Everything is submitted from {@code LevelRenderEvents.COLLECT_SUBMITS}, the same hook and the
 * same two primitives the mod already uses elsewhere ({@code ForceItemHeadIconRenderer} for items,
 * {@code DiceReachRenderer} for coloured quads), rather than through a block-entity renderer — the
 * devices have no block entity, and one hook for all of them is easier to keep in step with the
 * rendering changes every Minecraft version brings.
 *
 * <p>All geometry is written in the block model's own pixel space (0..16, front of the machine
 * towards -Z, i.e. facing north) and rotated with the block's {@code facing}, so it lines up with
 * the JSON models exactly.
 */
@Environment(EnvType.CLIENT)
public final class CasinoWorldRenderer {
    private static final int FULLBRIGHT = 0xF000F0;
    private static final double RANGE = 48.0;

    // Slot machine reel window, in model pixels (must match models/block/slot_machine.json).
    public static final float WIN_X0 = 2.0f, WIN_X1 = 14.0f, WIN_Y0 = 10.0f, WIN_Y1 = 18.0f;
    private static final float DRUM_R = 5.0f;
    private static final float DRUM_AXIS_Z = 3.2f + DRUM_R;
    private static final float ROW_ANGLE = 0.52f; // radians between two symbols on the drum

    private CasinoWorldRenderer() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (!Chal_50_HouseAlwaysWins.isActive() || client.level == null || client.player == null) return;
            CameraRenderState camera = context.levelState().cameraRenderState;
            Vec3 cam = camera.pos;
            PoseStack matrices = context.poseStack();
            SubmitNodeCollector collector = context.submitNodeCollector();
            float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            ItemStackRenderState itemState = new ItemStackRenderState();

            for (CasinoClientState.DevicePos d : CasinoClientState.DEVICES) {
                BlockPos pos = d.pos();
                if (cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RANGE * RANGE) continue;
                BlockState state = client.level.getBlockState(pos);
                if (!(state.getBlock() instanceof CasinoDeviceBlock)) continue;
                Direction facing = state.getValue(CasinoDeviceBlock.FACING);

                matrices.pushPose();
                matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                matrices.translate(0.5f, 0f, 0.5f);
                matrices.rotate(Axis.YP.rotationDegrees(180f - facing.toYRot()));
                matrices.translate(-0.5f, 0f, -0.5f);
                matrices.scale(1f / 16f, 1f / 16f, 1f / 16f); // model pixels from here on
                switch (d.type()) {
                    case SLOT -> drawSlot(client, matrices, collector, itemState, pos, partial);
                    case ROULETTE -> drawRoulette(matrices, collector, pos, partial);
                    case CRASH -> drawCrashPad(client, matrices, collector, itemState, camera, pos, partial);
                    default -> {
                    }
                }
                matrices.popPose();
            }
        });
    }

    // ---- slot machine -------------------------------------------------------------------------

    private static final int[] IDLE_STOPS = {3, 9, 28, 42, 34};

    private static void drawSlot(Minecraft client, PoseStack matrices, SubmitNodeCollector collector,
                                 ItemStackRenderState itemState, BlockPos pos, float partial) {
        SlotAnimation anim = CasinoClientState.SLOTS.get(pos.asLong());
        float t = anim == null ? 0 : anim.elapsed(partial);
        SlotAnimation.Phase phase = anim == null ? null : anim.phaseAt(t);
        float reelW = (WIN_X1 - WIN_X0) / SlotMath.REELS;

        // Drum surfaces: ivory, shaded darker towards the top and bottom of the window like a cylinder.
        collector.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, buf) -> {
            for (int r = 0; r < SlotMath.REELS; r++) {
                float x0 = WIN_X0 + r * reelW + 0.15f;
                float x1 = x0 + reelW - 0.3f;
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
                    quad(buf, pose, x0, y0, z0, x1, y0, z0, x1, y1, z1, x0, y1, z1,
                            rr * shade0, gg * shade0, bb * shade0, rr * shade1, gg * shade1, bb * shade1, 1f);
                }
                // Dark divider between reels.
                float dx = WIN_X0 + r * reelW;
                quad(buf, pose, dx - 0.08f, WIN_Y0 - 1, DRUM_AXIS_Z - DRUM_R + 0.2f, dx + 0.08f, WIN_Y0 - 1,
                        DRUM_AXIS_Z - DRUM_R + 0.2f, dx + 0.08f, WIN_Y1 + 1, DRUM_AXIS_Z - DRUM_R + 0.2f,
                        dx - 0.08f, WIN_Y1 + 1, DRUM_AXIS_Z - DRUM_R + 0.2f, 0.12f, 0.08f, 0.06f, 0.12f, 0.08f, 0.06f, 1f);
            }
            // Winning lines glow across the glass.
            if (anim != null && phase != null && showsLines(phase)) {
                SlotMath.Spin spin = anim.spins.get(anim.spinShown(t));
                if (!spin.lineWins().isEmpty()) {
                    int idx = (int) ((phase.local() / 10f)) % spin.lineWins().size();
                    SlotMath.LineWin w = spin.lineWins().get(idx);
                    float pulse = 0.55f + 0.45f * (float) Math.sin(t * 0.6f);
                    int[] rows = SlotMath.LINE_ROWS[w.line()];
                    for (int r = 0; r < w.count(); r++) {
                        float cx = WIN_X0 + (r + 0.5f) * reelW;
                        float cy = rowY(rows[r]);
                        float z = DRUM_AXIS_Z - DRUM_R - 0.3f;
                        float h = 1.2f;
                        float[] c = lineColour(w.line());
                        quad(buf, pose, cx - reelW / 2 + 0.2f, cy - h, z, cx + reelW / 2 - 0.2f, cy - h, z,
                                cx + reelW / 2 - 0.2f, cy + h, z, cx - reelW / 2 + 0.2f, cy + h, z,
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
            float cx = WIN_X0 + (r + 0.5f) * reelW;
            if (expanded) {
                float grow = phase.kind() == SlotAnimation.Kind.FS_EXPAND ? Math.min(1f, phase.local() / 10f) : 1f;
                matrices.pushPose();
                matrices.translate(cx, (WIN_Y0 + WIN_Y1) / 2f, DRUM_AXIS_Z - DRUM_R - 0.1f);
                float s = reelW * (1.0f + 1.4f * easeOutBack(grow));
                matrices.scale(s, s, s);
                matrices.rotate(Axis.YP.rotationDegrees(180f));
                submitItem(client, matrices, collector, itemState, new ItemStack(SlotMath.SYMBOL_ITEMS[lucky]));
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
                float s = reelW * 0.82f;
                matrices.scale(s, s * (spinning ? 1.25f : 1f), s);
                matrices.rotate(Axis.YP.rotationDegrees(180f));
                submitItem(client, matrices, collector, itemState, new ItemStack(SlotMath.SYMBOL_ITEMS[symbol]));
                matrices.popPose();
            }
        }
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

    private static final float[][] LINE_COLOURS = {
            {1f, 0.85f, 0.2f}, {0.3f, 0.9f, 1f}, {1f, 0.35f, 0.35f}, {0.5f, 1f, 0.45f}, {1f, 0.5f, 1f},
            {1f, 0.6f, 0.2f}, {0.6f, 0.6f, 1f}, {0.2f, 1f, 0.8f}, {1f, 1f, 1f}, {0.9f, 0.4f, 0.9f}};

    private static float[] lineColour(int line) {
        return LINE_COLOURS[line % LINE_COLOURS.length];
    }

    // ---- roulette -----------------------------------------------------------------------------

    /** Wheel and ball angles (radians) and ball radius (0..1 of the rim) for a table at time t. */
    public record WheelPose(float wheel, float ball, float ballRadius, boolean ballVisible) {
    }

    public static WheelPose wheelPose(CasinoNet.RouletteState s, float ticksSinceReceived) {
        float idle = (CasinoClientState.clientTick + ticksSinceReceived) * 0.004f;
        if (s == null) return new WheelPose(idle, 0, 1, false);
        if (s.phase() != RouletteGame.SPINNING && s.phase() != RouletteGame.RESULT) {
            return new WheelPose(idle, 0, 1, false);
        }
        float total = RouletteGame.SPIN_TICKS;
        float elapsed = s.phase() == RouletteGame.SPINNING
                ? (total - s.ticksLeft()) + ticksSinceReceived
                : total + (RouletteGame.RESULT_TICKS - s.ticksLeft()) + ticksSinceReceived;
        float tau = Math.min(1f, elapsed / total);
        float wheelEnd = (s.serial() % 360) * 0.0174f;
        float wheel = wheelEnd - 9.0f * (1f - tau) * (1f - tau);
        if (tau >= 1f) wheel = wheelEnd + (elapsed - total) * 0.0035f;
        float pocket = (float) (RouletteMath.wheelIndex(Math.max(0, s.result())) * (Math.PI * 2 / 37.0));
        float ballEnd = wheelEnd + pocket;
        float ball = ballEnd + 22.0f * (float) Math.pow(1f - tau, 2.2);
        float radius;
        if (tau < 0.62f) {
            radius = 1f;
        } else if (tau < 0.88f) {
            float k = (tau - 0.62f) / 0.26f;
            // Rattling over the diamonds on the way down.
            radius = 1f - 0.22f * k + 0.05f * (float) Math.abs(Math.sin(k * 23f)) * (1f - k);
        } else {
            radius = 0.78f;
        }
        if (tau >= 1f) ball = wheel + pocket;
        return new WheelPose(wheel, ball, radius, true);
    }

    private static void drawRoulette(PoseStack matrices, SubmitNodeCollector collector, BlockPos pos, float partial) {
        CasinoNet.RouletteState s = CasinoClientState.ROULETTE.get(pos.asLong());
        Long received = CasinoClientState.ROULETTE_RECEIVED.get(pos.asLong());
        float since = received == null ? 0 : (CasinoClientState.clientTick - received) + partial;
        WheelPose wp = wheelPose(s, since);
        final float cx = 8f, cz = 8f, top = 13.2f, rim = 6.6f, pocketIn = 4.7f, cone = 3.0f;

        collector.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, buf) -> {
            int n = 37;
            for (int i = 0; i < n; i++) {
                // Pocket i is centred on wheel + i·(2π/37), matching wheelPose and the GUI wheel.
                double a0 = wp.wheel() + (i - 0.5) * Math.PI * 2 / n;
                double a1 = wp.wheel() + (i + 0.5) * Math.PI * 2 / n;
                int num = RouletteMath.WHEEL[i];
                int col = RouletteMath.colour(num);
                float r = col == 0 ? 0.1f : col == 1 ? 0.78f : 0.07f;
                float g = col == 0 ? 0.62f : col == 1 ? 0.08f : 0.07f;
                float b = col == 0 ? 0.22f : col == 1 ? 0.1f : 0.08f;
                // Pocket wedge.
                quad(buf, pose,
                        cx + (float) Math.cos(a0) * pocketIn, top, cz + (float) Math.sin(a0) * pocketIn,
                        cx + (float) Math.cos(a1) * pocketIn, top, cz + (float) Math.sin(a1) * pocketIn,
                        cx + (float) Math.cos(a1) * rim, top, cz + (float) Math.sin(a1) * rim,
                        cx + (float) Math.cos(a0) * rim, top, cz + (float) Math.sin(a0) * rim,
                        r, g, b, r, g, b, 1f);
                // Brass fret between pockets.
                float fr = 0.85f, fg = 0.68f, fb = 0.3f;
                double af = a0 + 0.012;
                quad(buf, pose,
                        cx + (float) Math.cos(a0) * pocketIn, top + 0.02f, cz + (float) Math.sin(a0) * pocketIn,
                        cx + (float) Math.cos(af) * pocketIn, top + 0.02f, cz + (float) Math.sin(af) * pocketIn,
                        cx + (float) Math.cos(af) * rim, top + 0.02f, cz + (float) Math.sin(af) * rim,
                        cx + (float) Math.cos(a0) * rim, top + 0.02f, cz + (float) Math.sin(a0) * rim,
                        fr, fg, fb, fr, fg, fb, 1f);
                // Wooden cone.
                float wr = 0.45f + 0.08f * (i % 2), wg = 0.26f + 0.04f * (i % 2), wb = 0.12f;
                quad(buf, pose,
                        cx + (float) Math.cos(a0) * cone, top + 0.35f, cz + (float) Math.sin(a0) * cone,
                        cx + (float) Math.cos(a1) * cone, top + 0.35f, cz + (float) Math.sin(a1) * cone,
                        cx + (float) Math.cos(a1) * pocketIn, top + 0.01f, cz + (float) Math.sin(a1) * pocketIn,
                        cx + (float) Math.cos(a0) * pocketIn, top + 0.01f, cz + (float) Math.sin(a0) * pocketIn,
                        wr, wg, wb, wr * 0.8f, wg * 0.8f, wb, 1f);
            }
            // Golden turret in the middle, with its four spokes turning with the wheel.
            float t0 = 0.9f;
            quad(buf, pose, cx - t0, top + 0.9f, cz - t0, cx + t0, top + 0.9f, cz - t0, cx + t0, top + 0.9f, cz + t0,
                    cx - t0, top + 0.9f, cz + t0, 1f, 0.84f, 0.35f, 0.85f, 0.65f, 0.2f, 1f);
            for (int k = 0; k < 4; k++) {
                double a = wp.wheel() + k * Math.PI / 2;
                float ex = cx + (float) Math.cos(a) * 2.6f, ez = cz + (float) Math.sin(a) * 2.6f;
                double p = a + Math.PI / 2;
                float px = (float) Math.cos(p) * 0.18f, pz = (float) Math.sin(p) * 0.18f;
                quad(buf, pose, cx - px, top + 0.95f, cz - pz, cx + px, top + 0.95f, cz + pz,
                        ex + px, top + 0.5f, ez + pz, ex - px, top + 0.5f, ez - pz, 1f, 0.86f, 0.4f, 0.8f, 0.6f, 0.2f, 1f);
            }
            // The ball.
            if (wp.ballVisible()) {
                float br = pocketIn + (rim + 0.4f - pocketIn) * (wp.ballRadius() - 0.78f) / 0.22f;
                br = Math.max(pocketIn + 0.6f, Math.min(rim + 0.4f, br));
                float bx = cx + (float) Math.cos(wp.ball()) * br;
                float bz = cz + (float) Math.sin(wp.ball()) * br;
                float h = 0.32f;
                float y = top + 0.25f;
                quad(buf, pose, bx - h, y, bz - h, bx + h, y, bz - h, bx + h, y, bz + h, bx - h, y, bz + h,
                        1f, 1f, 1f, 0.85f, 0.85f, 0.88f, 1f);
                quad(buf, pose, bx - h * 0.7f, y + 0.25f, bz - h * 0.7f, bx + h * 0.7f, y + 0.25f, bz - h * 0.7f,
                        bx + h * 0.7f, y + 0.25f, bz + h * 0.7f, bx - h * 0.7f, y + 0.25f, bz + h * 0.7f,
                        1f, 1f, 1f, 1f, 1f, 1f, 1f);
            }
        });
    }

    // ---- crash --------------------------------------------------------------------------------

    public static float flightTicks(CasinoNet.CrashState s, float partial) {
        Long received = CasinoClientState.CRASH_RECEIVED.get(s.pos());
        float since = received == null ? 0 : (CasinoClientState.clientTick - received) + partial;
        return s.flightTicks() + (s.phase() == CrashGame.FLYING ? since : 0);
    }

    /** Height of the rocket above its pad after the given flight time, in blocks. */
    public static double rocketHeight(float flightTicks) {
        return Math.min(150.0, 0.0022 * flightTicks * flightTicks + 0.09 * flightTicks);
    }

    private static void drawCrashPad(Minecraft client, PoseStack matrices, SubmitNodeCollector collector,
                                     ItemStackRenderState itemState, CameraRenderState camera, BlockPos pos, float partial) {
        CasinoNet.CrashState s = CasinoClientState.CRASH.get(pos.asLong());
        int phase = s == null ? CrashGame.WAITING : s.phase();
        if (phase == CrashGame.CRASHED) return; // the rocket is gone; the explosion was spawned as particles
        double height = 0.35;
        float wobble = 0f;
        if (phase == CrashGame.FLYING) {
            height += rocketHeight(flightTicks(s, partial));
        } else if (phase == CrashGame.BETTING) {
            wobble = (float) Math.sin((CasinoClientState.clientTick + partial) * 1.3f) * 0.6f;
            if (client.level.getRandom().nextFloat() < 0.3f) {
                client.level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5, 0, 0.02, 0);
            }
        }
        matrices.pushPose();
        matrices.translate(8f, (float) (5.5 + height * 16.0), 8f);
        matrices.scale(16f, 16f, 16f); // back to blocks for the item
        matrices.rotate(Axis.ZP.rotationDegrees(wobble));
        matrices.rotate(camera.orientation);
        matrices.rotate(Axis.ZP.rotationDegrees(45f));
        matrices.scale(1.5f, 1.5f, 1.5f);
        submitItem(client, matrices, collector, itemState, new ItemStack(Items.FIREWORK_ROCKET));
        matrices.popPose();
    }

    /** The rocket bursts where it was when the server said so. */
    public static void onCrash(CasinoNet.CrashState s) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        BlockPos pos = BlockPos.of(s.pos());
        double y = pos.getY() + 0.5 + rocketHeight(s.flightTicks());
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
        client.level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
        for (int i = 0; i < 90; i++) {
            double a = client.level.getRandom().nextDouble() * Math.PI * 2;
            double b = client.level.getRandom().nextDouble() * Math.PI - Math.PI / 2;
            double v = 0.25 + client.level.getRandom().nextDouble() * 0.25;
            client.level.addParticle(ParticleTypes.FIREWORK, x, y, z,
                    Math.cos(a) * Math.cos(b) * v, Math.sin(b) * v, Math.sin(a) * Math.cos(b) * v);
        }
        for (int i = 0; i < 25; i++) {
            client.level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z,
                    (client.level.getRandom().nextDouble() - 0.5) * 0.2, (client.level.getRandom().nextDouble() - 0.5) * 0.2,
                    (client.level.getRandom().nextDouble() - 0.5) * 0.2);
        }
    }

    // ---- primitives ---------------------------------------------------------------------------

    private static void submitItem(Minecraft client, PoseStack matrices, SubmitNodeCollector collector,
                                   ItemStackRenderState state, ItemStack stack) {
        state.clear();
        client.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.FIXED, client.level, null, 0);
        state.submit(matrices, collector, FULLBRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    private static void quad(VertexConsumer buf, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r0, float g0, float b0, float r1, float g1, float b1, float a) {
        buf.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a);
        buf.addVertex(pose, x1, y1, z1).setColor(r0, g0, b0, a);
        buf.addVertex(pose, x2, y2, z2).setColor(r1, g1, b1, a);
        buf.addVertex(pose, x3, y3, z3).setColor(r1, g1, b1, a);
        // Back face as well, so the quad is visible from both sides regardless of winding.
        buf.addVertex(pose, x3, y3, z3).setColor(r1, g1, b1, a);
        buf.addVertex(pose, x2, y2, z2).setColor(r1, g1, b1, a);
        buf.addVertex(pose, x1, y1, z1).setColor(r0, g0, b0, a);
        buf.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
