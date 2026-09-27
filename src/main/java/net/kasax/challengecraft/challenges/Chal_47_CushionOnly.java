package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.util.ServerDrops;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Nur Kissen" / Cushion Only (challenge 47) — the 26.3 cushion is the player's only way to move.
 *
 * <p>Walking, sprinting, swimming sideways and jumping are gone. To get anywhere the player places
 * a cushion (top face of a block, normal block reach) and sits on it (the same block reach, not
 * vanilla's shorter entity reach - see {@code CushionReachPickMixin}): sitting down puts the player
 * onto the cushion, and getting up leaves them standing where it was. Every journey is a chain of those hops. Cushions are unlimited - placing one never uses it
 * up - because counting them only produced chores, not challenge.
 *
 * <p>The rules live in mixins, each checking {@link #isActive()} on both logical sides, because the
 * client simulates and owns its own position (see CLAUDE.md, "Mixins that block player
 * movement/input must cancel on BOTH logical sides"):
 * <ul>
 *   <li>{@code CushionMovementMixin} — zero horizontal travel input; horizontal velocity cleared
 *       client-side only, as in {@code DiceMovementMixin} (a server-side clear would swallow
 *       knockback before it is ever sent). Knockback, water currents and ice therefore cannot
 *       carry a player sideways either.</li>
 *   <li>{@code NoJumpingMixin} — ground jumps. Rising in water stays possible, so nobody drowns.</li>
 *   <li>{@code CushionRideRulesMixin} — no vehicle but a cushion: boats, minecarts, horses, pigs,
 *       striders, happy ghasts.</li>
 *   <li>{@code CushionElytraMixin} — no elytra flight.</li>
 *   <li>{@code CushionPistonMixin} — pistons and shulkers move entities with {@code Entity.move}
 *       directly, past {@code travel}; their sideways part is dropped.</li>
 *   <li>{@code CushionInfiniteMixin} — placing a cushion never consumes it.</li>
 *   <li>{@code CushionBreakMixin} — a cushion the player breaks just disappears, as for creative
 *       players; with unlimited cushions a drop would only print new ones.</li>
 *   <li>{@code CushionReachPickMixin} + {@code CushionReachUseMixin} (client) — a cushion can be sat
 *       on as far away as it can be placed. Vanilla aims at it with the 3.0 entity reach, which on
 *       a flat box on the floor left one three blocks out sittable only through its front edge.</li>
 * </ul>
 *
 * <p>Deliberately allowed: ender pearls and chorus fruit (user decision, 2026-09-27), falling (mine
 * the block you stand on), rising in water and bubble columns, portals and gateways, and vertical
 * launches (wind charge, riptide) - the horizontal part of those is cleared by the movement mixin.
 *
 * <p>This class makes sure a restricted player always has a cushion: one on joining, and one again
 * within a second whenever the inventory holds none - after a death, after putting it in a chest.
 * A player without a cushion could never move again.
 */
public class Chal_47_CushionOnly {
    public static final Component BLOCKED_MESSAGE =
            Component.translatable("challengecraft.cushion.blocked").withStyle(ChatFormatting.GOLD);

    private static boolean active = false;

    public static void register() {
        ServerPlayerEvents.JOIN.register(Chal_47_CushionOnly::ensureCushion);
        // Death empties the inventory; the sweep below would notice within a second anyway, but a
        // respawn should not start with a moment of being stuck.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ensureCushion(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(Chal_47_CushionOnly::tickSweep);
    }

    /** Called when the challenge is switched on in a running world, so nobody starts empty-handed. */
    public static void onActivated(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            leaveForbiddenVehicle(player);
            ensureCushion(player);
        }
    }

    private static void tickSweep(MinecraftServer server) {
        if (!active || server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            leaveForbiddenVehicle(player);
            ensureCushion(player);
        }
    }

    /**
     * Gets a player off anything that is not a cushion.
     *
     * <p>The ride rule only refuses NEW mounts. A player who was already on a horse, boat or happy
     * ghast when the challenge came on - or was put there by an operator's {@code /ride} - would
     * otherwise keep that vehicle for the rest of the run.
     */
    private static void leaveForbiddenVehicle(ServerPlayer player) {
        if (isRestricted(player) && player.isPassenger() && !(player.getVehicle() instanceof Cushion)) {
            player.stopRiding();
            hint(player);
        }
    }

    /** Hands a restricted player one white cushion if they hold none of any colour. */
    private static void ensureCushion(ServerPlayer player) {
        if (!isRestricted(player) || hasCushion(player.getInventory())) {
            return;
        }
        ItemStack stack = new ItemStack(Items.CUSHION.pick(DyeColor.WHITE));
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            ServerDrops.dropQuietly(player, stack);
        }
    }

    private static boolean hasCushion(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ItemTags.CUSHIONS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the rules apply to this player right now. Creative and spectator players are exempt,
     * as in every movement challenge — an operator setting a world up must be able to fly.
     */
    public static boolean isRestricted(Player player) {
        return active && !player.isCreative() && !player.isSpectator();
    }

    /** Sends the "cushions only" hint, from the server copy only so singleplayer does not show it twice. */
    public static void hint(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(BLOCKED_MESSAGE);
        }
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }
}
