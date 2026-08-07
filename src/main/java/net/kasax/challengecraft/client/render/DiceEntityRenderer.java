package net.kasax.challengecraft.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.entity.DiceEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Draws the tumbling d6 as a single textured cuboid rotated by the entity's orientation
 * quaternion. No {@code EntityModel} subclass is involved — it would contribute nothing here
 * (its {@code setAngles} is just {@code resetTransforms()}), so the baked {@link ModelPart} is
 * held directly.
 */
@Environment(EnvType.CLIENT)
public class DiceEntityRenderer extends EntityRenderer<DiceEntity, DiceEntityRenderState> {
    public static final EntityModelLayer DICE_LAYER =
            new EntityModelLayer(Identifier.of(ChallengeCraft.MOD_ID, "dice"), "main");

    private static final Identifier TEXTURE =
            Identifier.of(ChallengeCraft.MOD_ID, "textures/entity/dice.png");

    /** Cuboid positions are divided by 16 when baked, so a 16-unit cube is 1.0 block. */
    private static final float MODEL_EDGE_BLOCKS = 1.0F;

    /** Visual edge length. Hardcoded on purpose — deriving it from the hitbox would silently
     *  stretch the die if the hitbox is ever made non-cubic. */
    public static final float DIE_EDGE_BLOCKS = 0.28F;

    private final ModelPart cube;

    public DiceEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.cube = ctx.getPart(DICE_LAYER);
        this.shadowRadius = 0.18F;
        this.shadowOpacity = 0.60F;
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
    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("cube",
                ModelPartBuilder.create()
                        .uv(0, 0)
                        .cuboid(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 64, 32);
    }

    @Override
    public DiceEntityRenderState createRenderState() {
        return new DiceEntityRenderState();
    }

    @Override
    public void updateRenderState(DiceEntity entity, DiceEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.orientation.set(entity.getLastOrientation())
                .slerp(entity.getOrientation(), tickDelta)
                .normalize();
    }

    @Override
    public void render(DiceEntityRenderState state, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();

        // The dispatcher has already translated to the entity origin (bottom-centre); lift to the
        // cube's centre so it spins about itself rather than about its base.
        matrices.translate(0.0F, DIE_EDGE_BLOCKS * 0.5F, 0.0F);
        matrices.multiply(state.orientation);

        float scale = DIE_EDGE_BLOCKS / MODEL_EDGE_BLOCKS;
        matrices.scale(scale, scale, scale);

        // Convention A: deliberately NO scale(-1,-1,1), so model axes == world axes and the
        // physics side can use plain world normals for up-face detection. The cost is that the
        // four SIDE texture regions are sampled bottom-up — that is intentional, don't "fix" it
        // without also negating the model-space normals in DiePhysics.
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE));
        this.cube.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);

        matrices.pop();
        super.render(state, matrices, vertexConsumers, light);
    }
}
