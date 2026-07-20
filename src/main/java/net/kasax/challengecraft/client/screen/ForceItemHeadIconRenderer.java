package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.kasax.challengecraft.data.ForceItemBattleSavedData;
import net.kasax.challengecraft.network.ForceItemSyncPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
/**
 * Renders each player's current Force Item Battle target as a camera-facing item billboard
 * above their head (name-tag idiom: translate to entity, apply the dispatcher rotation).
 */
public class ForceItemHeadIconRenderer {
    private static final int FULLBRIGHT = 0xF000F0;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (ForceItemClientState.get().state() != ForceItemBattleSavedData.STATE_RUNNING) return;

            float tickDelta = context.tickCounter().getTickProgress(false);
            Vec3d cameraPos = context.camera().getPos();
            MatrixStack matrices = context.matrixStack();

            for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                if (player.isInvisible() || player.isSpectator() || player.isSleeping()) continue;
                // The local first-person camera sits inside the icon — skip self there.
                if (player == client.player && client.options.getPerspective().isFirstPerson()) continue;
                if (player.squaredDistanceTo(client.player) > 48 * 48) continue;

                ForceItemSyncPacket.PlayerEntry entry = ForceItemClientState.getEntry(player.getUuid());
                if (entry == null || entry.itemId().isEmpty()) continue;
                Item item = Registries.ITEM.get(Identifier.of(entry.itemId()));
                if (item == Items.AIR) continue;

                Vec3d pos = player.getLerpedPos(tickDelta);

                matrices.push();
                matrices.translate(pos.x - cameraPos.x,
                        pos.y + player.getHeight() + 0.9 - cameraPos.y,
                        pos.z - cameraPos.z);
                matrices.multiply(client.getEntityRenderDispatcher().getRotation());
                matrices.scale(0.75f, 0.75f, 0.75f);
                client.getItemRenderer().renderItem(new ItemStack(item), ItemDisplayContext.GROUND,
                        FULLBRIGHT, OverlayTexture.DEFAULT_UV, matrices, context.consumers(), client.world, 0);
                matrices.pop();
            }
        });
    }
}
