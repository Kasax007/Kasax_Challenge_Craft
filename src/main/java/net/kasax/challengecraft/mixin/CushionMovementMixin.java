package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_47_CushionOnly;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
/** Cushion Only (47): a player who is not sitting on something cannot move sideways at all. */
public abstract class CushionMovementMixin {

    /**
     * Same shape and the same both-sides split as {@link DiceMovementMixin}, which has the full
     * reasoning: the travel INPUT is zeroed on both logical sides, the horizontal VELOCITY only on
     * the client, which owns its position — clearing it on the server would swallow knockback
     * before the velocity packet goes out.
     *
     * <p>Passengers are exempt: sitting on a cushion is the one legal way to be somewhere else, and
     * any other vehicle is refused by {@code CushionRideRulesMixin} before it can be mounted.
     * Vertical input and velocity stay free — falling, sinking and rising in water must work or a
     * player could drown or hang in mid-air.
     */
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 challengecraft$onlyCushionsMove(Vec3 input) {
        Player self = (Player) (Object) this;
        // Only a cushion exempts; any other vehicle is taken away within a second by the sweep in
        // Chal_47_CushionOnly, and until then its rider gets no free walking either.
        if (!Chal_47_CushionOnly.isRestricted(self)
                || self.getVehicle() instanceof net.minecraft.world.entity.decoration.Cushion) {
            return input;
        }
        if (self.level().isClientSide()) {
            Vec3 velocity = self.getDeltaMovement();
            self.setDeltaMovement(0.0, velocity.y, 0.0);
        }
        return new Vec3(0.0, input.y, 0.0);
    }
}
