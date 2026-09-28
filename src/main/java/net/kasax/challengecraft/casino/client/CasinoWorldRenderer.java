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
import net.kasax.challengecraft.casino.RouletteGame;
import net.kasax.challengecraft.casino.RouletteMath;
import net.kasax.challengecraft.challenges.Chal_50_HouseAlwaysWins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every moving and changing part of the casino into the world — the devices themselves are
 * the players' interface, there are no menus for playing. The work per device lives in
 * {@link SlotView}, {@link PlinkoView}, {@link CrashView} and {@link RouletteView}; this class runs
 * them and holds the timing helpers they share.
 *
 * <p>Everything is submitted from {@code LevelRenderEvents.COLLECT_SUBMITS}, the same hook and the
 * same primitives the mod already uses elsewhere, rather than through a block-entity renderer — the
 * devices have no block entity, and one hook for all of them is easier to keep in step with the
 * rendering changes every Minecraft version brings.
 *
 * <p>The pose handed to the views is the master block's model space, facing north and in model
 * pixels, so it lines up with the JSON models exactly; {@link DevicePainter} converts from device
 * space.
 */
@Environment(EnvType.CLIENT)
public final class CasinoWorldRenderer {
    private static final double RANGE = 48.0;

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
                if (!(state.getBlock() instanceof CasinoDeviceBlock block) || block.getDeviceType() != d.type()) continue;
                Direction facing = state.getValue(CasinoDeviceBlock.FACING);

                matrices.pushPose();
                matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                matrices.translate(0.5f, 0f, 0.5f);
                matrices.rotate(Axis.YP.rotationDegrees(180f - facing.toYRot()));
                matrices.translate(-0.5f, 0f, -0.5f);
                matrices.scale(1f / 16f, 1f / 16f, 1f / 16f); // model pixels from here on
                int light = LightCoordsUtil.getLightCoords(client.level, pos.above());
                DevicePainter painter = new DevicePainter(client, matrices, collector, itemState, light);
                switch (d.type()) {
                    case SLOT -> SlotView.draw(painter, pos, partial);
                    case PLINKO -> PlinkoView.draw(painter, pos, partial);
                    case CRASH -> CrashView.draw(painter, pos, partial);
                    case ROULETTE -> RouletteView.draw(painter, pos, partial);
                    default -> {
                    }
                }
                painter.flush();
                matrices.popPose();
            }
        });
    }

    // ---- slot lines ---------------------------------------------------------------------------

    private static final float[][] LINE_COLOURS = {
            {1f, 0.85f, 0.2f}, {0.3f, 0.9f, 1f}, {1f, 0.35f, 0.35f}, {0.5f, 1f, 0.45f}, {1f, 0.5f, 1f},
            {1f, 0.6f, 0.2f}, {0.6f, 0.6f, 1f}, {0.2f, 1f, 0.8f}, {1f, 1f, 1f}, {0.9f, 0.4f, 0.9f}};

    static float[] lineColour(int line) {
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

    /** The rocket bursts where it was when the server said so. */
    public static void onCrash(CasinoNet.CrashState s) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        BlockPos pos = BlockPos.of(s.pos());
        BlockState state = client.level.getBlockState(pos);
        Direction facing = state.getBlock() instanceof CasinoDeviceBlock ? state.getValue(CasinoDeviceBlock.FACING) : Direction.NORTH;
        Vec3 pad = net.kasax.challengecraft.casino.DeviceSpace.toWorld(pos, facing, 8, 0, 8);
        double y = pos.getY() + 0.6 + rocketHeight(s.flightTicks());
        double x = pad.x, z = pad.z;
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

    static void submitItem(DevicePainter p, ItemStack stack) {
        p.itemState.clear();
        p.mc.getItemModelResolver().updateForTopItem(p.itemState, stack, ItemDisplayContext.FIXED, p.mc.level, null, 0);
        p.itemState.submit(p.pose, p.collector, DevicePainter.FULLBRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    static void quad(VertexConsumer buf, PoseStack.Pose pose,
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

    static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
