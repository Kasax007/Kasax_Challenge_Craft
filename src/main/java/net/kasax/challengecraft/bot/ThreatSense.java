package net.kasax.challengecraft.bot;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Seeing it coming (plan 16, L2): arrows, fireballs and skulls in the air about Bob, their flight
 * worked out ahead tick by tick. One that would hit him is side-stepped while there is time -
 * a step across its line, to whichever side is safe ground (the guard checks that step like any
 * other) - as a player dodges the arrow he sees coming instead of taking it.
 */
public final class ThreatSense {
    private final Bot bot;
    /** The way being dodged to (flat, length one) and until when; null: no dodge on. */
    private Vec3 dodge;
    private int dodgeUntil;
    /** For the benchmarks: dodges begun, steps round so a blow would not throw it into lava. */
    public int dodges, standsTaken;
    /** Where to stand so no blow throws it into lava, and when that was last looked at. */
    private Vec3 stand;
    private int standCheckAt;

    ThreatSense(Bot bot) {
        this.bot = bot;
    }

    /** After the task pressed its keys: a dodge, if something is coming that would hit. */
    void react() {
        var body = bot.body();
        if (!body.isAlive() || body.isPassenger() || body.isInWater() || body.isInLava()) {
            dodge = null;
            return;
        }
        if (dodge != null) {
            if (body.tickCount > dodgeUntil) dodge = null;
            else {
                bot.danger.steer(dodge.x, dodge.z);
                return;
            }
        }
        // Something about to strike with lava (or a deep drop) at its back: round to where its blow
        // throws it onto firm ground, before the blow - not only in a fight of its own choosing,
        // on the way anywhere. (Looked at every few ticks; while the lava is about.)
        if (body.onGround() && body.tickCount >= standCheckAt) {
            standCheckAt = body.tickCount + 4;
            stand = null;
            if (bot.danger.lavaNear(6)) {
                for (var m : body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, me().inflate(4.5),
                        m -> m.isAlive() && m.getTarget() == body && m instanceof net.minecraft.world.entity.monster.Enemy)) {
                    if (bot.danger.thrown(m, body.position()).safe()) continue;
                    stand = bot.danger.saferStand(m);
                    if (stand != null) {
                        standsTaken++;
                        bot.say("a " + m.getType().toShortString() + " would throw me into the lava from here: round to " + String.format("%.1f %.1f", stand.x, stand.z));
                    }
                    break;
                }
            }
        }
        if (stand != null) {
            Vec3 way = stand.subtract(body.position()).multiply(1, 0, 1);
            if (way.lengthSqr() < 0.04) stand = null;
            else {
                way = way.normalize();
                bot.danger.steer(way.x, way.z);
                return;
            }
        }
        // (Deflecting a fireball back is better than stepping aside: that task does it.)
        if (bot.current() instanceof net.kasax.challengecraft.bot.task.GhastDeflectTask) return;
        AABB me = body.getBoundingBox();
        for (Projectile p : body.level().getEntitiesOfClass(Projectile.class, me.inflate(24), pr -> pr.isAlive() && pr.getOwner() != body)) {
            int hit = hitsIn(p, me.inflate(0.35));
            // Too late to get out of the way (it hits within three ticks), or not coming: nothing to do.
            if (hit < 3 || hit > 20) continue;
            Vec3 v = p.getDeltaMovement().multiply(1, 0, 1);
            if (v.lengthSqr() < 1e-4) v = body.position().subtract(p.position()).multiply(1, 0, 1);
            if (v.lengthSqr() < 1e-4) continue;
            v = v.normalize();
            Vec3 left = new Vec3(-v.z, 0, v.x), right = left.scale(-1);
            // The side with the way open (and away from the lava, the guard sees to the rest).
            Vec3 side = open(left) >= open(right) ? left : right;
            dodge = side;
            dodgeUntil = body.tickCount + Math.min(10, hit + 2);
            dodges++;
            bot.say("dodging a " + p.getType().toShortString() + " (hits in " + hit + " ticks)");
            bot.danger.steer(side.x, side.z);
            return;
        }
    }

    private AABB me() {
        return bot.body().getBoundingBox();
    }

    /** How free the way a couple of blocks to that side is (0 to 2): no wall, firm ground, no lava. */
    private double open(Vec3 dir) {
        var body = bot.body();
        double free = 0;
        for (double d = 0.75; d <= 2.0; d += 0.625) {
            Vec3 at = body.position().add(dir.scale(d));
            if (!bot.danger.standable(at)) break;
            free = d;
        }
        return free;
    }

    /**
     * In how many ticks the projectile comes into the box (its flight worked out: arrows drop and
     * slow down, fireballs and skulls fly straight on); {@link Integer#MAX_VALUE} for never.
     */
    static int hitsIn(Projectile p, AABB box) {
        if (p instanceof AbstractArrow a && a.getDeltaMovement().lengthSqr() < 0.01) return Integer.MAX_VALUE;
        Vec3 pos = p.position();
        Vec3 v = p.getDeltaMovement();
        boolean arrow = p instanceof AbstractArrow;
        boolean straight = p instanceof AbstractHurtingProjectile;
        for (int t = 1; t <= 24; t++) {
            Vec3 next = pos.add(v);
            // (The segment of this tick, not only its end: a fast arrow crosses a body in one tick.)
            if (box.contains(next) || box.clip(pos, next).isPresent()) return t;
            pos = next;
            if (arrow) v = v.scale(0.99).add(0, -0.05, 0);
            else if (!straight) v = v.scale(0.99).add(0, -0.03, 0);
        }
        return Integer.MAX_VALUE;
    }
}
