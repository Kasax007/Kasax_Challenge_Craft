package net.kasax.challengecraft.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

/**
 * The mod's single definition of "this needs op".
 *
 * <p>26.2 replaced numeric op levels with named permissions: {@code hasPermission(2)} is gone, and
 * both {@link CommandSourceStack} and {@link Player} now expose a {@link PermissionSet} instead.
 * The tiers still line up one-for-one — vanilla itself calls level 2 {@code GAMEMASTERS} — so
 * {@link Permissions#COMMANDS_GAMEMASTER} is the exact equivalent of the level the mod used
 * everywhere before the port, and no command silently became easier or harder to run.
 *
 * <p>Kept as one constant on purpose: the tier is a policy decision, and changing it should be a
 * one-line edit here rather than a hunt through every command registration.
 */
public final class ModPermissions {
    private ModPermissions() {
    }

    /**
     * The tier the mod's operator-only commands require — the "cheat command" level that vanilla
     * gates {@code /give}, {@code /gamemode} and {@code /tp} behind.
     */
    public static final Permission OP = Permissions.COMMANDS_GAMEMASTER;

    /** For {@code .requires(...)} on a command registration. */
    public static boolean isOp(CommandSourceStack source) {
        return source.permissions().hasPermission(OP);
    }

    /**
     * For guards that hold a player rather than a command source. Works on both sides — the client's
     * {@code LocalPlayer} receives its permission set from the server, same as the old op level did.
     */
    public static boolean isOp(Player player) {
        return player.permissions().hasPermission(OP);
    }
}
