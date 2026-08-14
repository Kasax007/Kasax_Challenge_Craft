package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_41_NoJumping;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
/** Cancels the vanilla jump impulse for players while No Jumping is active. */
public abstract class NoJumpingMixin {

    /**
     * Must cancel on BOTH logical sides: player movement is simulated on the client and the
     * client is authoritative for its own position, so a server-only cancel still lets the
     * player jump (the server's copy of jump() fires and gets cancelled — which is why the
     * blocked message appeared — while the client-side jump proceeds untouched). The active
     * flag is synced to clients via ChallengeSyncPacket, so isActive() is correct here on
     * either side.
     */
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        if (!Chal_41_NoJumping.isActive()) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player) || player.isCreative() || player.isSpectator()) {
            return;
        }

        ci.cancel();
        // Only the server copy messages, so singleplayer doesn't show it twice.
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Chal_41_NoJumping.BLOCKED_MESSAGE);
        }
    }
}
