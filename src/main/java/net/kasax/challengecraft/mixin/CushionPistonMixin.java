package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_47_CushionOnly;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
/** Cushion Only (47): pistons and shulkers may lift or drop a player, but not carry them sideways. */
public abstract class CushionPistonMixin {
    @Shadow
    public abstract void move(MoverType moverType, Vec3 delta);

    /**
     * {@code PistonMovingBlockEntity} and shulkers shove entities with {@code Entity.move} directly,
     * on both sides, never passing through {@code travel} - so {@code CushionMovementMixin} cannot
     * see it, and a slime-block flying machine would carry a player across the map.
     *
     * <p>{@code MoverType.PLAYER} is deliberately NOT touched: that is the server applying the
     * position the client reported, which the client has already clamped. {@code SELF} is the
     * ordinary per-tick move, already fed a zeroed input. The vertical part stays, as everywhere in
     * this challenge.
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void challengecraft$noSidewaysShove(MoverType type, Vec3 delta, CallbackInfo ci) {
        if (type == MoverType.SELF || type == MoverType.PLAYER || (delta.x == 0.0 && delta.z == 0.0)) {
            return;
        }
        if ((Object) this instanceof Player player && Chal_47_CushionOnly.isRestricted(player) && !player.isPassenger()) {
            ci.cancel();
            this.move(type, new Vec3(0.0, delta.y, 0.0));
        }
    }
}
