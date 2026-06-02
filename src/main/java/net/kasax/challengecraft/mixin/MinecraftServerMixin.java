package net.kasax.challengecraft.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.network.ChallengeWorldRestarter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

@Mixin(MinecraftServer.class)
/** Hooks dedicated restart cleanup into the server lifecycle. */
public abstract class MinecraftServerMixin {
    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void onLoadWorld(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        
        boolean loadedFromDisk = net.kasax.challengecraft.ChallengeManager.loadInitialActiveChallenges(worldDir);
        
        if (!loadedFromDisk && !server.isDedicatedServer() && !net.kasax.challengecraft.ChallengeCraftClient.LAST_CHOSEN.isEmpty()) {
            net.kasax.challengecraft.ChallengeCraft.LOGGER.info("Seeding challenges from client for new integrated world: {}", net.kasax.challengecraft.ChallengeCraftClient.LAST_CHOSEN);
            net.kasax.challengecraft.ChallengeManager.applyActiveChallenges(net.kasax.challengecraft.ChallengeCraftClient.LAST_CHOSEN, null, null);
        }
        
        Path flagFile = worldDir.resolve("challengecraft_restart_pending");
        
        if (Files.exists(flagFile)) {
            ChallengeCraft.LOGGER.info("Detected pending world restart. Performing offline file rotation...");
            try {
                ChallengeWorldRestarter.performOfflineRotation(worldDir);
                Files.deleteIfExists(flagFile);
                ChallengeWorldRestarter.setRotationPending(true);
                ChallengeWorldRestarter.setNeedsTeleport(true);
                ChallengeCraft.LOGGER.info("World rotation complete. Loading fresh world.");
            } catch (IOException e) {
                ChallengeCraft.LOGGER.error("Failed to perform offline world rotation!", e);
            }
        }
    }

    @Inject(method = "loadLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;createLevels()V"))
    private void beforeCreateWorlds(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        // Skyblock swaps generators before createWorlds consumes the dimension options.
        ChallengeWorldRestarter.initializeGenerators(server);
        ChallengeWorldRestarter.randomizeSeed(server);
    }
}
