package net.kasax.challengecraft.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * A computer-controlled player. It is a real {@link ServerPlayer} on the server's player list —
 * in the tab list, on teams, hungry, hurt by mobs, dropping its items on death, counted by every
 * challenge like anyone else — only its "keyboard and mouse" are the fields set by its brain:
 * {@link #forward}, {@link #strafe}, {@link #jump} and the look direction.
 *
 * <p>A client-driven player moves because its client simulates the movement and reports it; a
 * bot has no client, so it simulates its own movement on the server from those inputs, with the
 * same physics a client would use (players are "effective AI" on the server).
 */
public class BotPlayer extends ServerPlayer {
    /** Inputs for the next tick, like WASD and space: -1..1. */
    public float forward, strafe;
    public boolean jump, sneak;
    /** Sprint whenever running straight ahead and fed enough (a player holding the sprint key). */
    public boolean sprintWhenRunning;
    /** Sprint now whatever the difficulty (a leap needs the run-up). */
    public boolean sprintNow;
    /** Running away: sprint whenever moving forward, whatever the path step. */
    public boolean hurry;

    BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);
    }

    /** Joins a new bot to the server at {@code pos}, as if a player with that name logged in there. */
    static BotPlayer join(MinecraftServer server, String name, ServerLevel level, Vec3 pos) {
        GameProfile profile = net.minecraft.core.UUIDUtil.createOfflineProfile(name);
        BotPlayer bot = new BotPlayer(server, level, profile, clientInformation(server));
        server.getPlayerList().placeNewPlayer(new BotConnection(), bot, CommonListenerCookie.createInitial(profile, false));
        bot.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), bot.getYRot(), 0f, true);
        return bot;
    }

    /**
     * What a player's client tells the server about itself, the view distance above all: the
     * default (2 chunks) would have the server load and run only a patch of 32 blocks round the
     * bot. It asks for 24 chunks, and sees as far as the server allows.
     */
    static ClientInformation clientInformation(MinecraftServer server) {
        ClientInformation d = ClientInformation.createDefault();
        int view = BotWorld.VIEW_CHUNKS;
        return new ClientInformation(d.language(), view, d.chatVisibility(), d.chatColors(), d.modelCustomisation(), d.mainHand(),
                d.textFilteringEnabled(), d.allowsListing(), d.particleStatus());
    }

    @Override
    public void tick() {
        // Through a portal: a real client confirms it has arrived; without that the game counts
        // the player as still on the way (and takes it through no further portal).
        if (isChangingDimension()) hasChangedDimension();
        // The inputs drive the vanilla movement code (LivingEntity#travel) during doTick.
        this.xxa = strafe;
        this.zza = forward;
        this.setJumping(jump);
        this.setShiftKeyDown(sneak);
        this.setSprinting((sprintWhenRunning || sprintNow || hurry) && forward > 0.8f && !sneak && getFoodData().getFoodLevel() > 6 && !isUsingItem());
        super.tick();
        // (A client does this for its player on the sneak key in water; a bot has none.)
        if (sinkInWater && isInWater()) goDownInWater();
        // A client player is ticked by its network handler; a bot has none, so tick it here.
        this.doTick();
        // What the network handler does after each movement packet: the chunk tickets follow the
        // player. Without it the world around a bot that walked off stays unloaded and frozen
        // (drops that never fall or get picked up, animals that never move, nothing to see).
        if (!isRemoved()) level().getChunkSource().move(this);
    }

    /** The server simulates this player's movement; there is no client to trust. */
    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    /** Pressed down in water (what a client's sneak key does there): it goes under. */
    public boolean sinkInWater;

    /** Releases all keys. */
    public void stopInputs() {
        forward = 0;
        strafe = 0;
        jump = false;
        sneak = false;
        sprintNow = false;
        sinkInWater = false;
        setSprinting(false);
    }

    /** Turns the head (and body) to look at a point. */
    public void lookAt(Vec3 target) {
        Vec3 eye = getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(dy, flat) * Mth.RAD_TO_DEG));
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setXRot(Mth.clamp(pitch, -90f, 90f));
    }

    public void lookAt(BlockPos pos) {
        lookAt(Vec3.atCenterOf(pos));
    }
}
