package net.kasax.challengecraft.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
/** Accessor for server internals needed by the controlled world restart flow. */
public interface MinecraftServerAccessor {
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getSession();

    @Accessor("worldData")
    net.minecraft.world.level.storage.WorldData getSaveProperties();
}
