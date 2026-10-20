package net.kasax.challengecraft.film.mixin;

import net.kasax.challengecraft.film.FilmIdentity;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

/** The film player has the same name and skin in every shot (see {@link FilmIdentity}). */
@Mixin(User.class)
public class FilmUserMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String film$name(String name) {
        return FilmIdentity.NAME;
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static UUID film$id(UUID id) {
        return FilmIdentity.ID;
    }
}
