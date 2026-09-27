package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_41_NoJumping;
import net.kasax.challengecraft.challenges.Chal_43_OnlyDown;
import net.kasax.challengecraft.challenges.Chal_46_Dice;
import net.kasax.challengecraft.challenges.Chal_47_CushionOnly;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
/**
 * Who may sit on what, across every movement challenge.
 *
 * <p>Sitting down moves a player onto the seat without walking or jumping, and getting up leaves
 * them standing where it was. With vehicles that was always true, but 26.3's cushion makes it a
 * pocket item that works on any flat top face, which turned it into a loophole for three existing
 * challenges:
 * <ul>
 *   <li><b>Dice (46)</b> exempts passengers from the step budget, so a cushion carried a player
 *       with no roll left. Cushions are refused outright — the die decides how far you get.</li>
 *   <li><b>No Jumping (41)</b> — a cushion on a ledge is a jump up the ledge. Refused when it would
 *       lift the player by more than they could simply walk up (their step height).</li>
 *   <li><b>Only Down (43)</b> — would count as climbing and kill the player; refused with the
 *       challenge's own tolerance instead, so a misclick is not a death.</li>
 * </ul>
 * And for <b>Cushion Only (47)</b> the other half: nothing BUT a cushion may be ridden.
 *
 * <p>Every mount — boat, minecart, horse, strider, happy ghast, cushion — ends in this overload,
 * and it is refused on both logical sides so the client never predicts a mount the server will not
 * honour. {@code force} is left alone: that is {@code /ride} and data-driven passengers, an
 * operator placing someone, not a player travelling.
 */
public abstract class CushionRideRulesMixin {

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void challengecraft$rideRules(Entity vehicle, boolean force, boolean sendEventAndTriggers,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (force || !((Object) this instanceof Player player) || player.isCreative() || player.isSpectator()) {
            return;
        }

        if (!(vehicle instanceof Cushion cushion)) {
            if (Chal_47_CushionOnly.isRestricted(player)) {
                Chal_47_CushionOnly.hint(player);
                cir.setReturnValue(false);
            }
            return;
        }

        if (Chal_46_Dice.isActive()) {
            deny(player, cir, "challengecraft.cushion.denied_dice");
            return;
        }
        // Where the player ends up: on top of the cushion, which is where getting up leaves them.
        double lift = cushion.getBoundingBox().maxY - player.getY();
        if (Chal_43_OnlyDown.isActive() && lift > Chal_43_OnlyDown.UP_TOLERANCE) {
            deny(player, cir, "challengecraft.cushion.denied_climb");
        } else if (Chal_41_NoJumping.isActive() && lift > player.maxUpStep()) {
            deny(player, cir, "challengecraft.cushion.denied_climb");
        }
    }

    private static void deny(Player player, CallbackInfoReturnable<Boolean> cir, String key) {
        cir.setReturnValue(false);
        // Server copy only, so singleplayer shows it once.
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
        }
    }
}
