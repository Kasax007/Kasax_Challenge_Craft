package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_9_ExpWorldBorder;
import net.kasax.challengecraft.challenges.Chal_25_DamageWorldBorder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The IN_WATER and IN_LAVA placement rules in {@code SpawnPlacementTypes}, which check
 * {@code level.getWorldBorder().isWithinBounds(pos)} for every candidate position before looking
 * at the blocks. They are static lambdas, so they are targeted by their synthetic names — stable
 * for a given Minecraft version and covered by Mojang's mappings; {@code require = 2} makes a
 * rename in a future version fail the boot loudly instead of silently bringing the bug back.
 *
 * <p>ON_GROUND, the rule that matters for the mobs a player actually needs to kill, is NOT a lambda
 * but the anonymous class {@code SpawnPlacementTypes$1} — see
 * {@code SpawnPlacementGroundBorderMixin}. The third gate on the spawn path,
 * {@code ServerLevel.canSpawnEntitiesInChunk}, is {@code ServerLevelBorderSpawnsMixin}. All three
 * are needed; each was found by counting how far spawn attempts got, not by reading.
 */
@Mixin(SpawnPlacementTypes.class)
public interface SpawnPlacementBorderMixin {

    @Redirect(
        method = {"lambda$static$1", "lambda$static$2"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"
        ),
        require = 2
    )
    private static boolean challengecraft$placementIgnoresBorder(WorldBorder border, BlockPos pos) {
        if (Chal_9_ExpWorldBorder.isActive() || Chal_25_DamageWorldBorder.isActive()) {
            return true;
        }
        return border.isWithinBounds(pos);
    }
}
