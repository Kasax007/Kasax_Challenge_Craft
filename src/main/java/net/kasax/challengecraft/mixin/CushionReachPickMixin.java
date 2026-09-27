package net.kasax.challengecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.kasax.challengecraft.challenges.Chal_47_CushionOnly;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
/** Cushion Only (47): the crosshair reaches a cushion as far as it reaches a block. */
public abstract class CushionReachPickMixin {

    /**
     * Vanilla places a cushion with the block reach (4.5) but aims at it with the entity reach
     * (3.0), both measured from the eye to where the crosshair ray enters the target. A cushion is
     * a 0.25-high box lying on the floor, so from standing height one three blocks away could only
     * be sat on through the front sixth of it, and one four blocks away - placed without trouble a
     * moment earlier - not at all. For a player whose only transport it is, a cushion is judged
     * like the block it lies on: anything they could place, they can sit on, along the same aim.
     *
     * <p>Only the entity branch of {@code pick} (ordinal 0) is widened, and only for cushions; mobs
     * keep the normal attack reach. {@code CushionReachUseMixin} widens the second check the click
     * makes. The server needs nothing - it accepts entity interactions up to entity reach + 3.
     */
    @WrapOperation(
            method = "pick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;filterHitResult(Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/HitResult;",
                    ordinal = 0)
    )
    private static HitResult challengecraft$cushionAsFarAsBlocks(HitResult hit, Vec3 from, double range,
                                                                 Operation<HitResult> original,
                                                                 @Local(argsOnly = true) Entity cameraEntity,
                                                                 @Local(argsOnly = true, ordinal = 0) double blockInteractionRange) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Cushion
                && cameraEntity instanceof Player player && Chal_47_CushionOnly.isRestricted(player)) {
            return original.call(hit, from, Math.max(range, blockInteractionRange));
        }
        return original.call(hit, from, range);
    }
}
