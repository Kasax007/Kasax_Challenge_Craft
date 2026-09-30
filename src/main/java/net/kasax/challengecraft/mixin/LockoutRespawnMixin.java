package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Set Spawn Point": a bed (slept in or clicked by day) or a charged respawn anchor sets the
 * respawn position through here. Only a new position counts, so sleeping in the same bed again,
 * or the game clearing a spawn whose bed is gone, does not.
 */
@Mixin(ServerPlayer.class)
public class LockoutRespawnMixin {
    @Inject(method = "setRespawnPosition", at = @At("HEAD"))
    private void challengecraft$onSetRespawn(ServerPlayer.RespawnConfig config, boolean sendMessage, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ServerPlayer.RespawnConfig before = self.getRespawnConfig();
        if (config != null && (before == null || !before.isSamePosition(config))) {
            Chal_40_LockoutBingo.onRespawnPointSet(self);
        }
    }
}
