package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.network.RedLightSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Input;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Rotes Licht, Grünes Licht" / Red Light, Green Light (challenge 48).
 *
 * <p>One traffic light for the whole server. Green lasts 20–60 seconds, then three seconds of yellow
 * with a ticking countdown, then red for 4–9 seconds. Whoever moves on red dies. Nobody is told how
 * long green or red will last — only yellow counts down.
 *
 * <p><b>On red, nothing may move — at all</b> (user's rule, 2026-09-27). Three independent tests,
 * any one of them is death:
 * <ul>
 *   <li>position: further than {@value #POSITION_EPSILON} blocks from where the player stood when
 *       the grace ended — whatever moved them: walking, knockback, a current, a minecart, a piston,
 *       falling;</li>
 *   <li>camera: yaw or pitch changed by more than {@value #ROTATION_EPSILON}°;</li>
 *   <li>input: a movement key, jump or sneak held, as the client last reported it
 *       ({@code ServerPlayer.getLastClientInput}) — so pushing against a wall counts too — or
 *       gliding with an elytra.</li>
 * </ul>
 * Mining, fighting, eating and using items stay allowed; they move neither body nor camera.
 *
 * <p>The first {@value #GRACE_TICKS} ticks of red do not count, so the time a released key needs to
 * reach the server is never held against anyone; the reference position and view are taken when the
 * grace ends. Someone who arrives during red (join, respawn) is measured from where they appear.
 * The death goes through a damage type of its own
 * ({@code challengecraft:red_light}): the death screen says what happened, armour and Resistance
 * do not help, a Totem of Undying does — and a player it saves gets {@value #PUNISH_COOLDOWN_TICKS}
 * ticks to stand still before the rule applies again.
 *
 * <p>Server-only state. The client learns the colour from {@link RedLightSyncPacket}, draws it in
 * {@code RedLightHUD} and plays the signal tones itself ({@code SoundCues}); in singleplayer the two sides share this class's statics, so nothing here is
 * touched by the client (the sync handler only flips {@link #setActive}).
 */
public class Chal_48_RedLight {
    public enum Phase { GREEN, YELLOW, RED }

    public static final ResourceKey<DamageType> RED_LIGHT_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("challengecraft", "red_light"));

    private static final int GREEN_MIN_TICKS = 20 * 20;
    private static final int GREEN_MAX_TICKS = 60 * 20;
    private static final int YELLOW_TICKS = 3 * 20;
    private static final int RED_MIN_TICKS = 4 * 20;
    private static final int RED_MAX_TICKS = 9 * 20;
    static final int GRACE_TICKS = 10;
    static final int PUNISH_COOLDOWN_TICKS = 40;
    /** Blocks. A still player's server position does not drift at all; this only absorbs rounding. */
    static final double POSITION_EPSILON = 0.001;
    /** Degrees. One pixel of mouse movement turns the view by roughly 0.15° at default sensitivity. */
    static final float ROTATION_EPSILON = 0.01f;

    /** Where a player stood and looked when the red grace ended. Entity id catches a respawn. */
    private record Pose(int entityId, double x, double y, double z, float yRot, float xRot) {
        static Pose of(ServerPlayer p) {
            return new Pose(p.getId(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        }
    }

    private static final Map<UUID, Pose> poses = new HashMap<>();
    /**
     * How long the flag must stay off before the light is switched off. The client's challenge sync
     * turns every flag off and on again in one go, and in singleplayer it shares this flag; one
     * server tick seeing "off" in between must not reset a running red to green.
     */
    private static final int OFF_DEBOUNCE_TICKS = 20;

    private static boolean active = false;
    private static Phase phase = null;
    private static int ticksLeft;
    private static int redAge;
    private static int inactiveTicks;
    private static final Map<UUID, Integer> lastPunished = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Chal_48_RedLight::tick);
        ServerPlayerEvents.JOIN.register(player -> {
            if (active && phase != null) {
                ServerPlayNetworking.send(player, packet());
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (!active) {
            if (phase != null && ++inactiveTicks > OFF_DEBOUNCE_TICKS) {
                phase = null;
                lastPunished.clear();
                poses.clear();
                broadcast(server);
            }
            return;
        }
        inactiveTicks = 0;
        if (phase == null) {
            enter(server, Phase.GREEN);
            return;
        }

        ticksLeft--;
        if (ticksLeft <= 0) {
            enter(server, switch (phase) {
                case GREEN -> Phase.YELLOW;
                case YELLOW -> Phase.RED;
                case RED -> Phase.GREEN;
            });
            return;
        }

        if (phase == Phase.RED && ++redAge >= GRACE_TICKS) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isExempt(p)) {
                    poses.remove(p.getUUID());
                    continue;
                }
                Pose ref = poses.get(p.getUUID());
                if (ref == null || ref.entityId() != p.getId()) {
                    // The grace just ended, or they joined / respawned during red.
                    poses.put(p.getUUID(), Pose.of(p));
                    continue;
                }
                if (hasMoved(p, ref) || isPushingInput(p)) {
                    punish(server, p);
                    // A totem may have saved them: measure again from where they are now.
                    poses.put(p.getUUID(), Pose.of(p));
                }
            }
        }
    }

    private static void enter(MinecraftServer server, Phase next) {
        phase = next;
        redAge = 0;
        poses.clear();
        ticksLeft = switch (next) {
            case GREEN -> between(GREEN_MIN_TICKS, GREEN_MAX_TICKS);
            case YELLOW -> YELLOW_TICKS;
            case RED -> between(RED_MIN_TICKS, RED_MAX_TICKS);
        };
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            switch (next) {
                case GREEN -> p.sendOverlayMessage(Component.translatable("challengecraft.red_light.go")
                        .withStyle(ChatFormatting.GREEN));
                case YELLOW -> {
                }
                case RED -> {
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 8));
                    p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("challengecraft.red_light.stop")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("challengecraft.red_light.stop_sub")
                            .withStyle(ChatFormatting.GRAY)));
                }
            }
        }
        broadcast(server);
    }

    private static boolean isExempt(ServerPlayer p) {
        return p.isCreative() || p.isSpectator() || p.isDeadOrDying();
    }

    /** Body or camera — no matter what moved them. */
    private static boolean hasMoved(ServerPlayer p, Pose ref) {
        double dx = p.getX() - ref.x(), dy = p.getY() - ref.y(), dz = p.getZ() - ref.z();
        return dx * dx + dy * dy + dz * dz > POSITION_EPSILON * POSITION_EPSILON
                || net.minecraft.util.Mth.degreesDifferenceAbs(p.getYRot(), ref.yRot()) > ROTATION_EPSILON
                || Math.abs(p.getXRot() - ref.xRot()) > ROTATION_EPSILON;
    }

    /** A key held even where it moves nothing (against a wall), sneaking, or gliding. */
    private static boolean isPushingInput(ServerPlayer p) {
        Input in = p.getLastClientInput();
        return in.forward() || in.backward() || in.left() || in.right() || in.jump() || in.shift()
                || p.isFallFlying();
    }

    private static void punish(MinecraftServer server, ServerPlayer p) {
        int now = server.getTickCount();
        Integer last = lastPunished.get(p.getUUID());
        if (last != null && now - last < PUNISH_COOLDOWN_TICKS) {
            return;
        }
        lastPunished.put(p.getUUID(), now);
        ServerLevel level = p.level();
        DamageSource source = new DamageSource(
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(RED_LIGHT_DAMAGE));
        p.hurtServer(level, source, Float.MAX_VALUE);
    }

    private static int between(int min, int max) {
        return min + RANDOM.nextInt(max - min + 1);
    }

    private static RedLightSyncPacket packet() {
        if (phase == null) {
            return new RedLightSyncPacket(RedLightSyncPacket.OFF, -1);
        }
        return switch (phase) {
            case GREEN -> new RedLightSyncPacket(RedLightSyncPacket.GREEN, -1);
            case YELLOW -> new RedLightSyncPacket(RedLightSyncPacket.YELLOW, ticksLeft);
            case RED -> new RedLightSyncPacket(RedLightSyncPacket.RED, -1);
        };
    }

    private static void broadcast(MinecraftServer server) {
        RedLightSyncPacket pkt = packet();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, pkt);
        }
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }
}
