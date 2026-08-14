package net.kasax.challengecraft.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MinecraftServer.class)
/** Accessor for server internals needed by the controlled world restart flow. */
public interface MinecraftServerAccessor {
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getWorlds();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getSession();

    @Accessor("worldData")
    net.minecraft.world.level.storage.WorldData getSaveProperties();
}
