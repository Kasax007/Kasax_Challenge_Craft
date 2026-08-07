package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_46_Dice;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
/** Freezes horizontal movement once the Würfel movement budget is spent. */
public abstract class DiceMovementMixin {

    /**
     * {@code LivingEntity.tickMovement()} builds the argument as
     * {@code new Vec3d(sidewaysSpeed, upwardSpeed, forwardSpeed)} and only then calls
     * {@code travel(...)}, so the movement input must be modified in place — an {@code @Inject}
     * at HEAD would act one tick late.
     *
     * <p>Zeroing the <b>input</b> happens on both logical sides, per the mod's both-sides rule for
     * movement (the client simulates and is authoritative for its own position, so a server-only
     * cancel does nothing visible).
     *
     * <p>Zeroing the <b>velocity</b>, however, is deliberately client-only. {@code travel()} does
     * run on the server copy of a player — {@code PlayerEntity.canMoveVoluntarily()} is
     * {@code isClient ? isMainPlayer() : true} — so clearing server-side velocity every tick would
     * swallow knockback before {@code EntityVelocityUpdateS2CPacket} is ever emitted, silently
     * deleting explosion and mob knockback for everyone.
     */
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d challengecraft$clampDiceMovement(Vec3d input) {
        if (!Chal_46_Dice.isActive()) {
            return input;
        }

        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.isCreative() || self.isSpectator() || self.hasVehicle()) {
            return input;
        }
        if (Chal_46_Dice.getBudgetFor(self) > 0.0) {
            return input;
        }

        if (self.getWorld().isClient) {
            Vec3d velocity = self.getVelocity();
            self.setVelocity(0.0, velocity.y, 0.0);
        }
        // Keep Y: falling, jumping and swimming ascent stay free, only walking is spent.
        return new Vec3d(0.0, input.y, 0.0);
    }
}
