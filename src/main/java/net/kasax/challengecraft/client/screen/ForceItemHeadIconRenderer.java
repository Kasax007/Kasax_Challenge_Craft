package net.kasax.challengecraft.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.kasax.challengecraft.data.ForceItemBattleSavedData;
import net.kasax.challengecraft.network.ForceItemSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
/**
 * Renders each player's current Force Item Battle target as a camera-facing item billboard
 * above their head (name-tag idiom: translate to entity, apply the dispatcher rotation).
 */
public class ForceItemHeadIconRenderer {
    private static final int FULLBRIGHT = 0xF000F0;

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) return;
            if (ForceItemClientState.get().state() != ForceItemBattleSavedData.STATE_RUNNING) return;

            // 26.2: the camera (position AND the billboard quaternion that
            // EntityRenderDispatcher.cameraOrientation() used to hand out) now lives on the render
            // state, and items are submitted through an ItemStackRenderState instead of drawn
            // immediately via ItemRenderer.renderStatic.
            float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            CameraRenderState camera = context.levelState().cameraRenderState;
            Vec3 cameraPos = camera.pos;
            PoseStack matrices = context.poseStack();
            SubmitNodeCollector collector = context.submitNodeCollector();
            // Reused across players — filled fresh for each icon.
            ItemStackRenderState iconState = new ItemStackRenderState();

            for (AbstractClientPlayer player : client.level.players()) {
                if (player.isInvisible() || player.isSpectator() || player.isSleeping()) continue;
                // The local first-person camera sits inside the icon — skip self there.
                if (player == client.player && client.options.getCameraType().isFirstPerson()) continue;
                if (player.distanceToSqr(client.player) > 48 * 48) continue;

                ForceItemSyncPacket.PlayerEntry entry = ForceItemClientState.getEntry(player.getUUID());
                if (entry == null || entry.itemId().isEmpty()) continue;
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.itemId()));
                if (item == Items.AIR) continue;

                Vec3 pos = player.getPosition(tickDelta);

                matrices.pushPose();
                matrices.translate(pos.x - cameraPos.x,
                        pos.y + player.getBbHeight() + 0.9 - cameraPos.y,
                        pos.z - cameraPos.z);
                matrices.mulPose(camera.orientation);
                matrices.scale(0.75f, 0.75f, 0.75f);
                iconState.clear();
                client.getItemModelResolver().updateForTopItem(iconState, new ItemStack(item),
                        ItemDisplayContext.GROUND, client.level, null, 0);
                iconState.submit(matrices, collector, FULLBRIGHT, OverlayTexture.NO_OVERLAY, 0);
                matrices.popPose();
            }
        });
    }
}
