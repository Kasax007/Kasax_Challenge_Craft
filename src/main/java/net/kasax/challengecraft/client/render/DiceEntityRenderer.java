package net.kasax.challengecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.entity.DiceEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws the tumbling d6 as a single textured cuboid rotated by the entity's orientation
 * quaternion. No {@code EntityModel} subclass is involved — it would contribute nothing here
 * (its {@code setAngles} is just {@code resetTransforms()}), so the baked {@link ModelPart} is
 * held directly.
 */
@Environment(EnvType.CLIENT)
public class DiceEntityRenderer extends EntityRenderer<DiceEntity, DiceEntityRenderState> {
    public static final ModelLayerLocation DICE_LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "dice"), "main");

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "textures/entity/dice.png");

    /** Cuboid positions are divided by 16 when baked, so a 16-unit cube is 1.0 block. */
    private static final float MODEL_EDGE_BLOCKS = 1.0F;

    /** Visual edge length. Hardcoded on purpose — deriving it from the hitbox would silently
     *  stretch the die if the hitbox is ever made non-cubic. */
    public static final float DIE_EDGE_BLOCKS = 0.28F;

    private final ModelPart cube;

    public DiceEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.cube = ctx.bakeLayer(DICE_LAYER);
        this.shadowRadius = 0.18F;
        this.shadowStrength = 0.60F;
    }

    /**
     * One 16×16×16 cuboid centred on the origin. Vanilla's box unwrap fits exactly inside a
     * 64×32 sheet, giving 16×16 px per face:
     * <pre>
     *   DOWN  x16..32 y0..16      UP    x32..48 y0..16
     *   WEST  x0..16  y16..32     NORTH x16..32 y16..32
     *   EAST  x32..48 y16..32     SOUTH x48..64 y16..32
     * </pre>
     * The texture generator writes exactly these regions, and
     * {@code DiePhysics.pipsForLocalFace} maps the same faces to pip counts — the three must
     * stay in agreement or the die shows a different number than it awards.
     */
    public static LayerDefinition getTexturedModelData() {
        MeshDefinition data = new MeshDefinition();
        PartDefinition root = data.getRoot();
        root.addOrReplaceChild("cube",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F),
                PartPose.ZERO);
        return LayerDefinition.create(data, 64, 32);
    }

    @Override
    public DiceEntityRenderState createRenderState() {
        return new DiceEntityRenderState();
    }

    @Override
    public void extractRenderState(DiceEntity entity, DiceEntityRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.orientation.set(entity.getLastOrientation())
                .slerp(entity.getOrientation(), tickDelta)
                .normalize();
    }

    /**
     * 26.2 replaced immediate-mode entity drawing with the submit-node model: instead of pulling a
     * {@code VertexConsumer} out of a {@code MultiBufferSource} (both gone) and writing vertices,
     * the renderer hands the collector a model part plus a pose and the engine batches it.
     * {@code PoseStack} itself is unchanged — only the GUI moved to a 2D matrix.
     */
    @Override
    public void submit(DiceEntityRenderState state, PoseStack matrices,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        matrices.pushPose();

        // The dispatcher has already translated to the entity origin (bottom-centre); lift to the
        // cube's centre so it spins about itself rather than about its base.
        matrices.translate(0.0F, DIE_EDGE_BLOCKS * 0.5F, 0.0F);
        matrices.mulPose(state.orientation);

        float scale = DIE_EDGE_BLOCKS / MODEL_EDGE_BLOCKS;
        matrices.scale(scale, scale, scale);

        // Convention A: deliberately NO scale(-1,-1,1), so model axes == world axes and the
        // physics side can use plain world normals for up-face detection. The cost is that the
        // four SIDE texture regions are sampled bottom-up — that is intentional, don't "fix" it
        // without also negating the model-space normals in DiePhysics.
        collector.submitModelPart(this.cube, matrices, RenderTypes.entitySolid(TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, null);

        matrices.popPose();
        super.submit(state, matrices, collector, camera);
    }
}
