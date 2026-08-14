package net.kasax.challengecraft.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;

/**
 * Per-frame snapshot for the die.
 *
 * <p>{@code EntityRenderer.getAndUpdateRenderState} is final and hands back the renderer's single
 * reused state instance, so this object is shared by every die on screen — keep the quaternion
 * final and mutable and {@code .set()} into it rather than reassigning, and never stash a
 * reference to this object anywhere.
 */
@Environment(EnvType.CLIENT)
public class DiceEntityRenderState extends EntityRenderState {
    public final Quaternionf orientation = new Quaternionf();
}
