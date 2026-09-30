package net.kasax.challengecraft.mixin;

import com.mojang.authlib.GameProfile;
import net.kasax.challengecraft.bot.BotManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Respawning builds a fresh player object; for a bot it must be a bot again, or the respawned
 * body would stand still forever.
 */
@Mixin(PlayerList.class)
public class BotRespawnMixin {
    @Redirect(method = "respawn", at = @At(value = "NEW", target = "net/minecraft/server/level/ServerPlayer"))
    private ServerPlayer challengecraft$respawnBot(MinecraftServer server, ServerLevel level, GameProfile profile,
                                                   ClientInformation info, ServerPlayer old, boolean keepAll,
                                                   Entity.RemovalReason reason) {
        return BotManager.isBot(old) ? BotManager.newBody(server, level, profile, info)
                : new ServerPlayer(server, level, profile, info);
    }
}
