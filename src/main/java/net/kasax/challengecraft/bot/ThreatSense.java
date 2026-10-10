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
    public int dodges, standsTaken, creeperEscapes;
    /** For the benchmarks: ticks walked behind the raised shield, arrows met with it instead of a dodge. */
    public int shieldWalkTicks, shieldBlocks;
    private int shieldSaidAt = -1000;
    /** The shield is up on this sense's account (not a task's) until this tick; then it comes down. */
    private int shieldMineUntil = -1;
    private int creeperSaidAt = -1000;
    /** Where to stand so no blow throws it into lava, and when that was last looked at. */
    private Vec3 stand;
    private int standCheckAt;

    int creeperHits;

    private static boolean armedNow(net.kasax.challengecraft.bot.BotPlayer body) {
        if (body.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS) || body.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)) return true;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) return true;
        }
        return false;
    }

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
        // A creeper hissing close: before anything else, arrows too (its blast takes far more).
        // Close and a shield on the arm: turned to it, behind the shield (it takes the blast);
        // else away at a sprint - the fuse is a second and a half, the blast reaches some six blocks.
        if (!bot.welcomeExplosion) {
            for (var c : body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, body.getBoundingBox().inflate(7),
                    c -> c.isAlive() && c.getSwellDir() > 0)) {
                double d = c.distanceTo(body);
                if (d < 4 && body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)) {
                    body.lookAt(c.getEyePosition());
                    if (!body.isUsingItem()) body.gameMode.useItem(body, body.level(), body.getOffhandItem(), net.minecraft.world.InteractionHand.OFF_HAND);
                    bot.danger.steer(0, 0);
                } else {
                    // Too close to outrun (under three blocks, the fuse half gone) and a weapon in
                    // the pack: a blow first - it throws the creeper back out of its blast range
                    // and resets nothing, but buys the steps - then away (seed 11, 1234: it ran
                    // from 2.8 blocks four times over and was blown up each time).
                    // (Whatever the arm's charge - just switched from the pickaxe it is low: the
                    // throw-back of a blow does not depend on it, only its damage does.)
                    if (armedNow(body) && net.kasax.challengecraft.bot.task.KillTask.hitDistance(body, c) <= 3.0
                            && c.hurtTime == 0 && body.hasLineOfSight(c)) {
                        net.kasax.challengecraft.bot.task.KillTask.equipWeapon(bot);
                        body.lookAt(c.getEyePosition());
                        body.attack(c);
                        body.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                        creeperHits++;
                    }
                    Vec3 away = bot.openWayFrom(c.position(), 8).multiply(1, 0, 1);
                    if (away.lengthSqr() < 1e-4) continue;
                    away = away.normalize();
                    body.lookAt(body.getEyePosition().add(away.scale(4)));
                    bot.danger.run(away.x, away.z);
                }
                if (body.tickCount - creeperSaidAt > 30) {
                    creeperSaidAt = body.tickCount;
                    creeperEscapes++;
                    bot.say("creeper hissing " + String.format("%.1f", d) + " blocks off: " + (d < 4 && body.isBlocking() ? "shield up" : "running"));
                }
                dodge = null;
                return;
            }
        }
        // (Out to be shot - a tile wants it: no shield, no dodge.)
        if (bot.current() instanceof net.kasax.challengecraft.bot.task.GetShotTask) return;
        if (shieldWalk()) {
            dodge = null;
            return;
        }
        // Raised here and no longer wanted: down again (a task that wants it raises it itself).
        if (shieldMineUntil >= 0 && body.tickCount > shieldMineUntil) {
            shieldMineUntil = -1;
            if (body.isUsingItem() && body.getUseItem().is(net.minecraft.world.item.Items.SHIELD)
                    && !(bot.current() instanceof net.kasax.challengecraft.bot.task.ShieldUpTask)
                    && !(bot.current() instanceof net.kasax.challengecraft.bot.task.KillTask)) body.releaseUsingItem();
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
            // (Behind a raised shield, facing it: the shield takes it.)
            if (body.isBlocking() && p.getDeltaMovement().dot(body.getLookAngle()) < 0) continue;
            int hit = hitsIn(p, me.inflate(0.35));
            // Too late to get out of the way (it hits within three ticks), or not coming: nothing to do.
            if (hit < 3 || hit > 20) continue;
            // A shield on the arm and time to raise it (it blocks after five ticks): turned to the
            // arrow behind the shield - a step aside misses as often as not, the shield never.
            boolean raising = body.isUsingItem() && body.getUseItem().is(net.minecraft.world.item.Items.SHIELD);
            if ((hit >= 6 || raising) && shieldOn()) {
                Vec3 from = p.position().subtract(p.getDeltaMovement().scale(4));
                raiseShieldTowards(from, true);
                if (!raising) shieldBlocks++;
                if (!raising) say("a " + p.getType().toShortString() + " coming (in " + hit + " ticks): shield up");
                return;
            }
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

    private boolean shieldOn() {
        return bot.body().getOffhandItem().is(net.minecraft.world.item.Items.SHIELD);
    }

    /**
     * A shooter (skeleton, stray, pillager) after Bob in sight, and a shield on the arm: the shield
     * stays up towards it while Bob goes on his way - a bot loses only the sprint with the shield
     * raised, and every arrow ends in it. Not while something strikes close: then the sword is wanted.
     */
    private boolean shieldWalk() {
        var body = bot.body();
        // (Not in a fight - the sword is wanted - except on the way round to a shooter.)
        if (!shieldOn() || BotTask.innermost(bot.current()) instanceof net.kasax.challengecraft.bot.task.KillTask k && !k.shieldWalkWanted()) return false;
        net.minecraft.world.entity.Mob shooter = null;
        double best = Double.MAX_VALUE;
        for (var m : body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(24),
                m -> m.isAlive() && m.getTarget() == body && m instanceof net.minecraft.world.entity.monster.RangedAttackMob
                        && m instanceof net.minecraft.world.entity.monster.Enemy)) {
            double d = m.distanceToSqr(body);
            if (d < best && body.hasLineOfSight(m)) {
                best = d;
                shooter = m;
            }
        }
        // (One within four blocks is a fight, not a shield walk: at arm's length it shoots point-blank.)
        if (shooter == null || best < 16) return false;
        // Something close enough to strike: a fight, not a shield walk (the reflexes start it).
        if (!body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(3),
                m -> m.isAlive() && m.getTarget() == body && m instanceof net.minecraft.world.entity.monster.Enemy
                        && !(m instanceof net.minecraft.world.entity.monster.RangedAttackMob)).isEmpty()) return false;
        raiseShieldTowards(shooter.getEyePosition(), true);
        shieldWalkTicks++;
        if (body.tickCount - shieldSaidAt > 100) {
            shieldSaidAt = body.tickCount;
            say("a " + shooter.getType().toShortString() + " shooting at me " + Math.round(Math.sqrt(best)) + " blocks off: on my way behind the shield");
        }
        return true;
    }

    /** Turned to {@code at} with the shield up; the keys the task pressed still go the same way over the ground. */
    private void raiseShieldTowards(Vec3 at, boolean keepGoing) {
        var body = bot.body();
        double yaw = body.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double wx = -body.forward * sin + body.strafe * cos, wz = body.forward * cos + body.strafe * sin;
        body.lookAt(at);
        if (!body.isUsingItem() || !body.getUseItem().is(net.minecraft.world.item.Items.SHIELD)) {
            if (body.isUsingItem()) body.releaseUsingItem();
            body.gameMode.useItem(body, body.level(), body.getOffhandItem(), net.minecraft.world.InteractionHand.OFF_HAND);
        }
        shieldMineUntil = body.tickCount + (keepGoing ? 2 : 14);
        if (keepGoing) bot.danger.steer(wx, wz);
        else bot.danger.steer(0, 0);
    }

    private void say(String what) {
        bot.say(what);
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
