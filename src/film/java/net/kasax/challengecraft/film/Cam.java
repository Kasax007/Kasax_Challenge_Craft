package net.kasax.challengecraft.film;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * The camera of a shot: either the player's own eyes, or a free camera - an invisible armour
 * stand that exists on this client only and is never ticked, so it stands exactly where it is put.
 * Poses are set once per game tick; the frames between two ticks are drawn part of the way from
 * the one pose to the next, so a camera moved a little each tick glides.
 */
public final class Cam {
    private final FilmDirector d;
    private ArmorStand stand;

    Cam(FilmDirector d) {
        this.d = d;
    }

    /** Yaw (degrees) looking from {@code from} towards {@code to}; 0 is south, as in the game. */
    public static float yaw(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    public static float pitch(Vec3 from, Vec3 to) {
        double h = Math.hypot(to.x - from.x, to.z - from.z);
        return (float) -Math.toDegrees(Math.atan2(to.y - from.y, h));
    }

    /** Cuts to the free camera with its eye at {@code eye}, looking at {@code at}. */
    public void cutTo(Vec3 eye, Vec3 at) {
        pose(eye, yaw(eye, at), pitch(eye, at), true);
    }

    /** Moves the free camera on for this tick (glides from the last pose). */
    public void moveTo(Vec3 eye, Vec3 at) {
        pose(eye, yaw(eye, at), pitch(eye, at), false);
    }

    public void pose(Vec3 eye, float yaw, float pitch, boolean cut) {
        d.client(mc -> {
            if (stand == null || stand.level() != mc.level) {
                stand = new ArmorStand(EntityTypes.ARMOR_STAND, mc.level);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                stand.setId(-4_242_424);
                cut(stand, eye, yaw, pitch);
            }
            double ey = stand.getEyeHeight();
            if (cut) {
                cut(stand, eye, yaw, pitch);
            } else {
                stand.xo = stand.getX();
                stand.yo = stand.getY();
                stand.zo = stand.getZ();
                stand.xOld = stand.getX();
                stand.yOld = stand.getY();
                stand.zOld = stand.getZ();
                stand.yRotO = stand.getYRot();
                stand.xRotO = stand.getXRot();
                stand.setPos(eye.x, eye.y - ey, eye.z);
                // Turn the short way round, so a pan across south does not spin the whole circle.
                float to = stand.yRotO + wrap(yaw - stand.yRotO);
                turn(stand, to, pitch, false);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.getCameraEntity() != stand) mc.setCameraEntity(stand);
        });
    }

    private static void cut(ArmorStand s, Vec3 eye, float yaw, float pitch) {
        double ey = s.getEyeHeight();
        s.setPos(eye.x, eye.y - ey, eye.z);
        s.xo = s.xOld = eye.x;
        s.yo = s.yOld = eye.y - ey;
        s.zo = s.zOld = eye.z;
        turn(s, yaw, pitch, true);
    }

    /** A living entity looks where its head points: body, head and view all turned together. */
    private static void turn(ArmorStand s, float yaw, float pitch, boolean cut) {
        float headBefore = s.getYHeadRot();
        s.setYRot(yaw);
        s.setXRot(pitch);
        s.setYHeadRot(yaw);
        s.setYBodyRot(yaw);
        if (cut) {
            s.yRotO = yaw;
            s.xRotO = pitch;
            s.yHeadRotO = yaw;
            s.yBodyRotO = yaw;
        } else {
            s.yHeadRotO = headBefore;
        }
    }

    private static float wrap(float deg) {
        float w = deg % 360f;
        if (w >= 180f) w -= 360f;
        if (w < -180f) w += 360f;
        return w;
    }

    /** Back to the player's own eyes. */
    public void player() {
        d.client(mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.player != null) mc.setCameraEntity(mc.player);
        });
    }

    /** Third person from behind the player (the game's own F5 view). */
    public void behind() {
        d.client(mc -> {
            if (mc.player != null) mc.setCameraEntity(mc.player);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
    }

    /**
     * Puts the player (feet) at {@code feet} looking along yaw/pitch - for the player's own eyes as
     * the camera. {@code cut}: jump there; otherwise glide from where it stood the tick before.
     */
    public void playerPose(Vec3 feet, float yaw, float pitch, boolean cut) {
        // On a cut the server learns the new place too, or its next position check pulls the player back.
        if (cut && d.sp != null) d.server(server -> {
            var sp = d.player(server);
            sp.teleportTo((net.minecraft.server.level.ServerLevel) sp.level(), feet.x, feet.y, feet.z, java.util.Set.of(), yaw, pitch, false);
        });
        d.client(mc -> {
            LocalPlayer p = mc.player;
            if (p == null) return;
            float y = yaw;
            if (cut) {
                p.setPos(feet.x, feet.y, feet.z);
                p.xo = p.xOld = feet.x;
                p.yo = p.yOld = feet.y;
                p.zo = p.zOld = feet.z;
                p.yRotO = y;
                p.xRotO = pitch;
                p.yHeadRotO = y;
                p.yBodyRotO = y;
            } else {
                p.xo = p.getX();
                p.yo = p.getY();
                p.zo = p.getZ();
                p.yRotO = p.getYRot();
                p.xRotO = p.getXRot();
                p.setPos(feet.x, feet.y, feet.z);
                y = p.yRotO + wrap(y - p.yRotO);
            }
            p.setDeltaMovement(Vec3.ZERO);
            p.setYRot(y);
            p.setXRot(pitch);
            p.setYHeadRot(y);
            p.setYBodyRot(y);
        });
    }

    /** The player's eyes on {@code at}, standing at {@code feet}. */
    public void playerLook(Vec3 feet, Vec3 at, boolean cut) {
        Vec3 eye = feet.add(0, 1.62, 0);
        playerPose(feet, yaw(eye, at), pitch(eye, at), cut);
    }

    void reset() {
        d.client(mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.player != null) mc.setCameraEntity(mc.player);
        });
        stand = null;
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
