package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_9_ExpWorldBorder;
import net.kasax.challengecraft.challenges.Chal_25_DamageWorldBorder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The ON_GROUND placement rule — the one every zombie, skeleton, creeper and spider goes through.
 *
 * <p>Unlike IN_WATER and IN_LAVA, which are lambdas in {@code SpawnPlacementTypes} and are handled
 * by {@code SpawnPlacementBorderMixin}, ON_GROUND is an anonymous class,
 * {@code SpawnPlacementTypes$1}, so it needs its own mixin addressed by name. Missing exactly this
 * is what left ground mobs at a 0.15 % placement pass rate (measured: 354 of 233 632 candidate
 * positions) against 8.2 % with a vanilla border, while the two lambdas were already redirected.
 * With this in place the rate and the mob count match the vanilla-border control.
 */
@Mixin(targets = "net.minecraft.world.entity.SpawnPlacementTypes$1")
public abstract class SpawnPlacementGroundBorderMixin {

    @Redirect(
        method = "isSpawnPositionOk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean challengecraft$groundPlacementIgnoresBorder(WorldBorder border, BlockPos pos) {
        if (Chal_9_ExpWorldBorder.isActive() || Chal_25_DamageWorldBorder.isActive()) {
            return true;
        }
        return border.isWithinBounds(pos);
    }
}
