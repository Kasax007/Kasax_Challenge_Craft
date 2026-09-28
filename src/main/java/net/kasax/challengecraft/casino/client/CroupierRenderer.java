package net.kasax.challengecraft.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.casino.CroupierEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * The croupier: a player-shaped model with a top hat and a bow tie, drawn from one 64×64 skin
 * ({@code textures/entity/croupier.png}, player skin layout, base layer only so it can be drawn
 * solid). He follows the nearest player with his head, breathes, taps his fingers on the counter
 * and plays a gesture — wave, take, pay out, tip the hat — whenever the server bumps
 * {@link CroupierEntity#GESTURE}.
 *
 * <p>The model is held as a baked {@link ModelPart} like the die of challenge 46, posed by hand
 * each frame; an {@code EntityModel} subclass would add nothing but indirection here.
 */
@Environment(EnvType.CLIENT)
public class CroupierRenderer extends EntityRenderer<CroupierEntity, CroupierRenderer.State> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "croupier"), "main");
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "textures/entity/croupier.png");
    private static final int GESTURE_TICKS = 30;

    /** Per-entity bookkeeping of which gesture counter was last seen and when it changed. */
    private static final Map<Integer, long[]> GESTURES = new HashMap<>();

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public static class State extends EntityRenderState {
        public float bodyYaw;
        public float headYaw;
        public float headPitch;
        public float time;
        public int gestureKind;
        public float gesture; // 0..1 progress, -1 when idle
    }

    public CroupierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.root = ctx.bakeLayer(LAYER);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.shadowRadius = 0.45f;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition head = r.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -8f, -4f, 8f, 8f, 8f), PartPose.ZERO);
        // Top hat: brim and crown, sitting slightly tilted like a showman's.
        head.addOrReplaceChild("hat_brim",
                CubeListBuilder.create().texOffs(0, 32).addBox(-5.5f, -8.6f, -5.5f, 11f, 1f, 11f),
                PartPose.rotation(-0.06f, 0f, 0.05f));
        head.addOrReplaceChild("hat_crown",
                CubeListBuilder.create().texOffs(32, 0).addBox(-3.5f, -14.5f, -3.5f, 7f, 6f, 7f),
                PartPose.rotation(-0.06f, 0f, 0.05f));
        PartDefinition body = r.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16).addBox(-4f, 0f, -2f, 8f, 12f, 4f), PartPose.ZERO);
        body.addOrReplaceChild("bow_tie",
                CubeListBuilder.create().texOffs(44, 32).addBox(-2f, 0.4f, -2.7f, 4f, 1.6f, 1f), PartPose.ZERO);
        body.addOrReplaceChild("coat_tail",
                CubeListBuilder.create().texOffs(0, 44).addBox(-4f, 11f, 1.6f, 8f, 5f, 1f), PartPose.rotation(0.12f, 0f, 0f));
        r.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16).addBox(-3f, -2f, -2f, 4f, 12f, 4f), PartPose.offset(-5f, 2f, 0f));
        r.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(32, 48).addBox(-1f, -2f, -2f, 4f, 12f, 4f), PartPose.offset(5f, 2f, 0f));
        r.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(-1.9f, 12f, 0f));
        r.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(16, 48).addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(1.9f, 12f, 0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CroupierEntity entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.bodyYaw = entity.getYRot();
        state.time = entity.tickCount + tickDelta;

        Player viewer = Minecraft.getInstance().player;
        state.headYaw = 0f;
        state.headPitch = 0f;
        if (viewer != null && viewer.distanceToSqr(entity) < 10 * 10) {
            double dx = viewer.getX() - entity.getX();
            double dz = viewer.getZ() - entity.getZ();
            double dy = viewer.getEyeY() - entity.getEyeY();
            float target = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
            state.headYaw = Mth.clamp(Mth.wrapDegrees(target - state.bodyYaw), -65f, 65f);
            state.headPitch = Mth.clamp((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI)), -30f, 30f);
        }

        int counter = entity.getEntityData().get(CroupierEntity.GESTURE);
        long[] seen = GESTURES.computeIfAbsent(entity.getId(), k -> new long[]{counter, Long.MIN_VALUE});
        if (seen[0] != counter) {
            seen[0] = counter;
            seen[1] = entity.tickCount;
        }
        float since = seen[1] == Long.MIN_VALUE ? Float.MAX_VALUE : (entity.tickCount - seen[1]) + tickDelta;
        state.gesture = since < GESTURE_TICKS ? since / GESTURE_TICKS : -1f;
        state.gestureKind = entity.getEntityData().get(CroupierEntity.GESTURE_KIND);
    }

    private void pose(State s) {
        float t = s.time;
        head.resetPose();
        body.resetPose();
        rightArm.resetPose();
        leftArm.resetPose();
        rightLeg.resetPose();
        leftLeg.resetPose();

        head.yRot = s.headYaw * Mth.DEG_TO_RAD;
        head.xRot = s.headPitch * Mth.DEG_TO_RAD;
        // Breathing and a patient, slightly theatrical idle.
        float breath = Mth.sin(t * 0.08f);
        body.y += breath * 0.12f;
        head.y += breath * 0.12f;
        rightArm.xRot = -0.35f + Mth.sin(t * 0.06f) * 0.04f;
        leftArm.xRot = -0.35f + Mth.sin(t * 0.06f + 1.3f) * 0.04f;
        rightArm.zRot = 0.06f;
        leftArm.zRot = -0.06f;
        // Drumming fingers on the counter every few seconds.
        float drum = (t % 90f) < 14f ? Mth.sin(t * 2.4f) * 0.08f : 0f;
        leftArm.xRot += drum;

        if (s.gesture >= 0f) {
            float g = s.gesture;
            float up = Mth.sin(g * Mth.PI); // 0 → 1 → 0
            switch (s.gestureKind) {
                case CroupierEntity.GESTURE_WAVE -> {
                    rightArm.xRot = -0.35f - 2.4f * up;
                    rightArm.zRot = 0.06f + Mth.sin(g * 18f) * 0.35f * up;
                    head.zRot = Mth.sin(g * 6f) * 0.08f * up;
                }
                case CroupierEntity.GESTURE_TAKE -> {
                    rightArm.xRot = -0.35f - 1.1f * up;
                    leftArm.xRot = -0.35f - 1.1f * up;
                    body.xRot = 0.15f * up;
                    head.xRot += 0.3f * up;
                }
                case CroupierEntity.GESTURE_PAY -> {
                    rightArm.xRot = -0.35f - 1.4f * up;
                    rightArm.yRot = -0.3f * up;
                    head.xRot += 0.2f * up;
                }
                case CroupierEntity.GESTURE_TIP_HAT -> {
                    rightArm.xRot = -0.35f - 2.7f * up;
                    rightArm.zRot = 0.06f + 0.35f * up;
                    head.xRot += 0.35f * up;
                    body.xRot = 0.1f * up;
                }
                default -> {
                }
            }
        }
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
        pose(state);
        matrices.pushPose();
        // The usual living-entity transform: face the body direction, flip into model space
        // (y down, 1/16 scale), and stand the feet on the ground.
        matrices.rotate(Axis.YP.rotationDegrees(180f - state.bodyYaw));
        matrices.scale(-1f, -1f, 1f);
        matrices.scale(0.9375f, 0.9375f, 0.9375f);
        matrices.translate(0f, -1.501f, 0f);
        collector.submitModelPart(this.root, matrices, RenderTypes.entitySolid(TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        matrices.popPose();
        super.submit(state, matrices, collector, camera);
    }
}
