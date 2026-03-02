package net.kasax.challengecraft.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.network.ChallengeWorldRestarter;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void onLoadWorld(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        Path worldDir = server.getSavePath(WorldSavePath.ROOT);
        Path flagFile = worldDir.resolve("challengecraft_restart_pending");
        
        if (Files.exists(flagFile)) {
            ChallengeCraft.LOGGER.info("Detected pending world restart. Performing file rotation and seed randomization...");
            try {
                ChallengeWorldRestarter.performOfflineRotation(worldDir);
                ChallengeWorldRestarter.randomizeSeed(server);
                Files.deleteIfExists(flagFile);
                ChallengeCraft.LOGGER.info("World rotation and seed randomization complete. Loading fresh world.");
            } catch (IOException e) {
                ChallengeCraft.LOGGER.error("Failed to perform offline world rotation!", e);
            }
        }
    }
}
