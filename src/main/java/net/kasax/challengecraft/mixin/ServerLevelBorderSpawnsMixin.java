package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_9_ExpWorldBorder;
import net.kasax.challengecraft.challenges.Chal_25_DamageWorldBorder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets natural mob spawning run in chunks outside the (tiny opening) world border while a border
 * challenge is active.
 *
 * <p>Vanilla's {@code ServerLevel.canSpawnEntitiesInChunk} refuses to even consider a chunk that is
 * not fully inside the world border, and {@code NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint}
 * consults it for every candidate spawn position. With the opening border at size 1–2 blocks no
 * chunk qualifies, so a player trapped inside it can never find the first mob to kill for XP —
 * which is exactly the XP that grows the border. Bypassing just that check (the loaded-area check
 * still applies) lets hostile mobs spawn in the chunks around the border and path in to the player.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBorderSpawnsMixin {

    @Redirect(
        method = "canSpawnEntitiesInChunk(Lnet/minecraft/world/level/ChunkPos;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/world/level/ChunkPos;)Z"
        )
    )
    private boolean challengecraft$allowSpawnsOutsideBorder(WorldBorder border, ChunkPos pos) {
        // Verified against the 26.2 bytecode: this method is the natural spawner's ONLY border
        // gate — both ServerChunkCache.tickSpawningChunk and
        // NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint go through it, and nothing else on
        // the spawn path (ChunkMap, DistanceManager, SpawnPlacements, Monster) asks the border.
        // Border damage in LivingEntity.baseTick is player-only, so what spawns out there lives.
        if (Chal_9_ExpWorldBorder.isActive() || Chal_25_DamageWorldBorder.isActive()) {
            return true;
        }
        return border.isWithinBounds(pos);
    }
}
