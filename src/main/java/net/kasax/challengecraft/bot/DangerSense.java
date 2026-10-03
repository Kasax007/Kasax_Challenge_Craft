package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bob's sense of danger: where lava, fire and the drops that hurt are round him, and a guard
 * over his own moves.
 *
 * <p>Every tick, after the task has pressed its keys and before the body acts on them, the guard
 * works out where they lead, with the game's own movement rules: those keys this tick, then
 * braking as hard as he can (crouched, on the ground) - or, in the air, holding on till he lands.
 * If even that ends in lava or fire, off a drop that hurts or into the void, these keys are not
 * pressed: the least change that is safe is (no jump; crouched; braking; away from it), as a
 * player stops short at the lava's edge without a thought. So a step, a run, a jump or a dodge
 * can never carry him in, whichever task made it.
 *
 * <p>The same reckoning tells what a blow would throw him into (a fight is kept off the lava's
 * edge), and the cells of lava and fire about him, kept up to date, tell which way is safe to run.
 */
public final class DangerSense {
    /** How bad the end of a move is. */
    public enum Harm { NONE, HURT, DEADLY }

    /** How a move ends: the worst on the way, from which tick, where and what. */
    public record Outcome(Harm harm, int tick, BlockPos where, String what) {
        static final Outcome SAFE = new Outcome(Harm.NONE, Integer.MAX_VALUE, null, "safe");

        public boolean safe() {
            return harm == Harm.NONE;
        }

        /** Worse: more harm, or the same sooner. */
        boolean worseThan(Outcome o) {
            return harm.ordinal() != o.harm.ordinal() ? harm.ordinal() > o.harm.ordinal() : tick < o.tick;
        }
    }

    /**
     * The keys for a tick in the world's frame: the way to push (length up to one), jump, crouch,
     * sprint - and, for {@code brake}, push against the motion just hard enough to stop it.
     */
    private record Keys(double wx, double wz, boolean jump, boolean sneak, boolean sprint, boolean brake) {
        static final Keys NONE = new Keys(0, 0, false, false, false, false);
    }

    private static final double GRAVITY = 0.08, Y_DRAG = 0.98, AIR_FRICTION = 0.91;
    /** Ticks reckoned ahead at most (a long fall, to the end). */
    private static final int MAX_TICKS = 60;

    private final Bot bot;
    /** The task's keys, while the guard pressed others for one tick (given back before the next). */
    private float savedForward, savedStrafe;
    private boolean savedJump, savedSneak, savedSprint, overridden;
    /** Ticks on end the guard has had to step in, and when it last said so. */
    private int streak, saidAt = -1000;
    /** How often it stepped in (for the benchmarks): braked, a jump not made, crouched at an edge or on magma. */
    public int brakes, jumpsStopped, crouches, magmaCrouches;
    /** Lava and fire cells about him (see {@link #scan()}) and when they were looked for. */
    private final List<BlockPos> hot = new ArrayList<>();
    private int scannedAt = -1000;
    /** Ground under columns, worked out once per reckoning: (x, start y, z) to what is first below. */
    private final Map<Long, Column> columns = new HashMap<>();
    private int columnsAt = -1;

    DangerSense(Bot bot) {
        this.bot = bot;
    }

    // ---- the map: lava and fire about him ---------------------------------------------------------

    private static final int SCAN_R = 8, SCAN_DOWN = 6, SCAN_UP = 2;

    /** The lava and fire cells within eight blocks (six down, two up), looked for every half second. */
    public List<BlockPos> hot() {
        var body = bot.body();
        if (body.tickCount - scannedAt >= 10 || scannedAt > body.tickCount) scan();
        return hot;
    }

    private void scan() {
        var body = bot.body();
        scannedAt = body.tickCount;
        hot.clear();
        Level level = body.level();
        BlockPos feet = body.blockPosition();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_R; dx <= SCAN_R; dx++) {
            for (int dz = -SCAN_R; dz <= SCAN_R; dz++) {
                for (int dy = -SCAN_DOWN; dy <= SCAN_UP; dy++) {
                    p.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    var st = level.getBlockState(p);
                    if (st.getFluidState().is(FluidTags.LAVA) || st.is(BlockTags.FIRE)) {
                        hot.add(p.immutable());
                        if (hot.size() >= 768) return;
                    }
                }
            }
        }
    }

    /** Lava or fire within {@code r} blocks across, from four below the feet to head height. */
    public boolean lavaNear(double r) {
        var body = bot.body();
        double r2 = r * r;
        for (BlockPos h : hot()) {
            double dy = h.getY() - body.getY();
            if (dy < -4 || dy > 1.5) continue;
            double dx = h.getX() + 0.5 - body.getX(), dz = h.getZ() + 0.5 - body.getZ();
            if (dx * dx + dz * dz < r2) return true;
        }
        return false;
    }

    /** The way (flat, length one, or zero) away from the lava and fire close about him, the nearest counting most. */
    public Vec3 awayFromHot() {
        var body = bot.body();
        double x = 0, z = 0;
        for (BlockPos h : hot()) {
            double dy = h.getY() - body.getY();
            if (dy < -4 || dy > 1.5) continue;
            double dx = body.getX() - (h.getX() + 0.5), dz = body.getZ() - (h.getZ() + 0.5);
            double d2 = dx * dx + dz * dz;
            if (d2 > 36) continue;
            double w = 1 / Math.max(0.25, d2 * Math.sqrt(d2));
            x += dx * w;
            z += dz * w;
        }
        double len = Math.sqrt(x * x + z * z);
        return len < 1e-6 ? Vec3.ZERO : new Vec3(x / len, 0, z / len);
    }

    /** Whether a cell (feet height) or the one below it holds lava or fire. */
    public static boolean hotCell(Level level, BlockPos p) {
        for (int dy = -1; dy <= 1; dy++) {
            var st = level.getBlockState(p.above(dy));
            if (st.getFluidState().is(FluidTags.LAVA) || dy >= 0 && st.is(BlockTags.FIRE)) return true;
        }
        return false;
    }

    // ---- the guard ----------------------------------------------------------------------------------

    /** Before the bot acts again: the task's own keys back (the guard's held for one tick only). */
    void restore() {
        if (!overridden) return;
        var body = bot.body();
        body.forward = savedForward;
        body.strafe = savedStrafe;
        body.jump = savedJump;
        body.sneak = savedSneak;
        body.sprintNow = savedSprint;
        overridden = false;
    }

    /** After the task pressed its keys: where they lead worked out, and other keys pressed if that is into harm. */
    void guard() {
        var body = bot.body();
        if (!body.isAlive() || body.isSpectator() || body.getAbilities().flying || body.isPassenger() || body.isFallFlying()
                || body.onClimbable() || body.isInWater() || body.isInLava()) {
            streak = 0;
            return;
        }
        // (Getting out of the lava, the fire: that task knows where it is going.)
        BotTask top = bot.current();
        // (Nor a jump off a pillar on purpose: the water bucket breaks that fall, or a tile wants it.)
        if (top instanceof net.kasax.challengecraft.bot.task.LavaEscapeTask || top instanceof net.kasax.challengecraft.bot.task.AirTask
                || BotTask.innermost(top) instanceof net.kasax.challengecraft.bot.task.FallTask) {
            streak = 0;
            return;
        }
        // (Standing still, nothing pressed, no way on: nothing to work out.)
        Vec3 v = body.getDeltaMovement();
        if (body.onGround() && body.forward == 0 && body.strafe == 0 && !body.jump && v.x * v.x + v.z * v.z < 1e-6 && !onMagma()) {
            streak = 0;
            return;
        }
        fresh();
        Keys own = own();
        // Lava (flowing out, a pocket opened) a step off: back from it now, whatever the task
        // wants - unless its keys already lead away.
        // (Running lava comes on a block at a time: kept a block further off.)
        if (!body.hasEffect(MobEffects.FIRE_RESISTANCE) && (hotAt(body.getX(), body.getY(), body.getZ(), 0.6) != null
                || flowingNear(body.getX(), body.getY(), body.getZ(), 1.3))) {
            scan();
            Vec3 away = awayFromHot();
            if (away.lengthSqr() > 0 && own.wx * away.x + own.wz * away.z < 0.3) {
                Keys back = new Keys(away.x, away.z, false, false, false, false);
                if (reckon(back).safe()) {
                    press(back);
                    brakes++;
                    if (body.tickCount - saidAt > 60) {
                        saidAt = body.tickCount;
                        bot.say("guard: lava right beside me - stepping back");
                    }
                    return;
                }
            }
        }
        Outcome first = reckon(own);
        if (first.safe()) {
            // On magma: crouched, it does not burn (the game's own rule) - and changes nothing else
            // while the next step is level.
            if (onMagma() && !own.sneak && !own.jump && nextLevel(own) && reckon(withSneak(own)).safe()) {
                press(withSneak(own));
                magmaCrouches++;
            }
            streak = 0;
            return;
        }
        // Into harm: the least change that is safe, else the one that ends least badly.
        List<Keys> tries = new ArrayList<>(5);
        if (own.jump) tries.add(new Keys(own.wx, own.wz, false, own.sneak, own.sprint, false));
        if (body.onGround()) tries.add(new Keys(own.wx, own.wz, false, true, false, false));
        tries.add(new Keys(0, 0, false, body.onGround(), false, true));
        Vec3 away = first.where == null ? Vec3.ZERO
                : body.position().subtract(Vec3.atBottomCenterOf(first.where)).multiply(1, 0, 1);
        if (away.lengthSqr() > 1e-6) {
            away = away.normalize();
            tries.add(new Keys(away.x, away.z, false, body.onGround(), false, false));
        }
        Keys best = own;
        Outcome bestOut = first;
        for (Keys k : tries) {
            Outcome o = reckon(k);
            if (o.safe()) {
                best = k;
                bestOut = o;
                break;
            }
            if (bestOut.worseThan(o)) {
                best = k;
                bestOut = o;
            }
        }
        if (best == own) return;
        press(best);
        if (own.jump && !best.jump) jumpsStopped++;
        if (best.brake) brakes++;
        else if (best.sneak && !own.sneak) crouches++;
        streak++;
        if (body.tickCount - saidAt > 60) {
            saidAt = body.tickCount;
            bot.say("guard: " + first.what + " ahead" + (first.where == null ? "" : " at " + first.where.toShortString())
                    + " - " + (best.brake ? "braking" : best.jump != own.jump ? "no jump" : best.sneak ? "crouched" : "turned away")
                    + " (" + (top == null ? "-" : top.describe()) + ")");
        }
        // The way keeps leading in: the walk is told, and looks for another.
        if (streak >= 20 && bot.navigator().status() == BotNavigator.Status.MOVING) {
            streak = 0;
            bot.navigator().guardStop();
        }
    }

    /** Pressed for this tick by a reflex (a dodge): a way to go, in the world's frame - checked by the guard after. */
    void steer(double wx, double wz) {
        press(new Keys(wx, wz, false, false, false, false));
    }

    /** Pressed for this tick by a reflex: a run that way, sprinting (facing it), checked by the guard after. */
    void run(double wx, double wz) {
        press(new Keys(wx, wz, false, false, true, false));
        var body = bot.body();
        if (body.forward > 0.8f) body.sprintNow = true;
    }

    private boolean onMagma() {
        var body = bot.body();
        return body.onGround() && !body.hasEffect(MobEffects.FIRE_RESISTANCE)
                && body.level().getBlockState(body.getBlockPosBelowThatAffectsMyMovement()).is(Blocks.MAGMA_BLOCK);
    }

    /** Whether the next step on these keys is on level ground (crouching would hold it at a step down). */
    private boolean nextLevel(Keys k) {
        var body = bot.body();
        Vec3 v = body.getDeltaMovement();
        double a = groundAccel(body.getX(), body.getY(), body.getZ(), k.sprint);
        double x = body.getX() + v.x + k.wx * a * 3, z = body.getZ() + v.z + k.wz * a * 3;
        return floorUnder(x, z, body.getY()) >= body.getY() - 0.01;
    }

    private static Keys withSneak(Keys k) {
        return new Keys(k.wx, k.wz, k.jump, true, false, k.brake);
    }

    /** The keys the task pressed, in the world's frame. */
    private Keys own() {
        var body = bot.body();
        double ix = body.strafe * 0.98, iz = body.forward * 0.98;
        double l2 = ix * ix + iz * iz;
        if (l2 > 1) {
            double l = Math.sqrt(l2);
            ix /= l;
            iz /= l;
        }
        double yaw = body.getYRot() * Mth.DEG_TO_RAD;
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        boolean sprint = (body.sprintWhenRunning || body.sprintNow || body.hurry) && body.forward > 0.8f && !body.sneak
                && body.getFoodData().getFoodLevel() > 6 && !body.isUsingItem();
        return new Keys(ix * cos - iz * sin, iz * cos + ix * sin, body.jump, body.sneak, sprint, false);
    }

    /** Presses these keys for this tick (the task's own given back before the next). */
    private void press(Keys k) {
        var body = bot.body();
        if (!overridden) {
            savedForward = body.forward;
            savedStrafe = body.strafe;
            savedJump = body.jump;
            savedSneak = body.sneak;
            savedSprint = body.sprintNow;
            overridden = true;
        }
        double wx = k.wx, wz = k.wz;
        if (k.brake) {
            Vec3 v = body.getDeltaMovement();
            double sp = Math.sqrt(v.x * v.x + v.z * v.z);
            double a = groundAccel(body.getX(), body.getY(), body.getZ(), false);
            double push = sp < 1e-4 ? 0 : Math.min(1, sp / Math.max(1e-4, a));
            wx = sp < 1e-4 ? 0 : -v.x / sp * push;
            wz = sp < 1e-4 ? 0 : -v.z / sp * push;
        }
        double yaw = body.getYRot() * Mth.DEG_TO_RAD;
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        body.forward = (float) Mth.clamp(-wx * sin + wz * cos, -1, 1);
        body.strafe = (float) Mth.clamp(wx * cos + wz * sin, -1, 1);
        body.jump = k.jump;
        body.sneak = k.sneak;
        // (A brake or a turn is no sprint: the body works that out from the keys - forward under 0.8.)
        if (!k.sprint && body.forward > 0.8f) body.forward = 0.8f;
    }

    // ---- the reckoning ------------------------------------------------------------------------------

    /** A body in the reckoning: where, how fast, on the ground or not, and the highest point of the fall. */
    private static final class State {
        double x, y, z, vx, vy, vz, top;
        boolean ground;
    }

    /** Where these keys lead: pressed this tick, then braking on the ground (crouched), held in the air. */
    private Outcome reckon(Keys first) {
        var body = bot.body();
        State s = new State();
        s.x = body.getX();
        s.y = body.getY();
        s.z = body.getZ();
        Vec3 v = body.getDeltaMovement();
        s.vx = v.x;
        s.vy = v.y;
        s.vz = v.z;
        s.ground = body.onGround();
        s.top = s.ground ? s.y : s.y + body.fallDistance;
        if (s.ground) s.vy = 0;
        Keys air = new Keys(first.wx, first.wz, false, first.sneak, first.sprint, first.brake);
        Keys brake = new Keys(0, 0, false, true, false, true);
        return run(s, first, air, brake);
    }

    /**
     * What a blow from {@code attacker} would throw him into, standing at {@code at} (on the
     * ground): thrown up and back, with no keys in the air and braking once down.
     */
    public Outcome thrown(LivingEntity attacker, Vec3 at) {
        var body = bot.body();
        fresh();
        double dx = at.x - attacker.getX(), dz = at.z - attacker.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3) {
            double yaw = body.getYRot() * Mth.DEG_TO_RAD;
            dx = Math.sin(yaw);
            dz = -Math.cos(yaw);
            len = 1;
        }
        double resist = Mth.clamp(body.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        double kb = attacker.getAttributes().hasAttribute(Attributes.ATTACK_KNOCKBACK) ? attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK) : 0;
        boolean tosser = attacker instanceof net.minecraft.world.entity.monster.hoglin.HoglinBase;
        double k = (0.4 + 0.5 * kb + (tosser ? 0.4 : 0)) * (1 - resist);
        State s = new State();
        s.x = at.x;
        s.y = at.y;
        s.z = at.z;
        s.vx = dx / len * k;
        s.vz = dz / len * k;
        s.vy = tosser ? 0.7 : 0.4;
        s.ground = false;
        s.top = at.y;
        Keys brake = new Keys(0, 0, false, true, false, true);
        return run(s, Keys.NONE, Keys.NONE, brake);
    }

    /** Whether he could stand at {@code at} (flat ground, no wall, nothing hot) - for a step aside in a fight. */
    public boolean standable(Vec3 at) {
        var body = bot.body();
        fresh();
        double hw = body.getBbWidth() / 2;
        AABB box = new AABB(at.x - hw, at.y + 0.01, at.z - hw, at.x + hw, at.y + body.getBbHeight(), at.z + hw).deflate(1e-3);
        if (!body.level().noCollision(body, box)) return false;
        if (hotAt(at.x, at.y, at.z) != null) return false;
        return supported(at.x, at.y, at.z);
    }

    /**
     * Where to stand instead, close by, so that a blow from {@code attacker} throws him onto safe
     * ground: a step or two round or back, near the edge of sword reach from it; null if nowhere.
     */
    public Vec3 saferStand(LivingEntity attacker) {
        var body = bot.body();
        Vec3 at = body.position();
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (double r : new double[]{1.0, 1.75, 2.5}) {
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                Vec3 p = at.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                double score = r + Math.abs(Math.sqrt(p.distanceToSqr(attacker.getX(), p.y, attacker.getZ())) - 2.6) * 0.7;
                if (score >= bestScore) continue;
                // (The way there on firm ground too: half way checked.)
                if (!standable(p) || !standable(at.add(p.subtract(at).scale(0.5)))) continue;
                if (!thrown(attacker, p).safe()) continue;
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    /** The reckoning itself, tick by tick. */
    private Outcome run(State s, Keys first, Keys air, Keys ground) {
        var body = bot.body();
        Level level = body.level();
        boolean fireProof = body.hasEffect(MobEffects.FIRE_RESISTANCE);
        double hp = body.getHealth() + body.getAbsorptionAmount();
        double safeFall = body.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        double fallMult = body.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        double hw = body.getBbWidth() / 2;
        double h = body.getBbHeight();
        double yaw = body.getYRot() * Mth.DEG_TO_RAD;
        double jumpSin = Math.sin(yaw), jumpCos = Math.cos(yaw);
        Outcome worst = Outcome.SAFE;
        for (int t = 0; t < MAX_TICKS; t++) {
            Keys k = t == 0 ? first : s.ground ? ground : air;
            // The push: on the ground the block's grip decides it, in the air a little.
            double accel, fric;
            if (s.ground) {
                float bf = level.getBlockState(BlockPos.containing(s.x, s.y - 0.5000001, s.z)).getBlock().getFriction();
                accel = groundAccel(s.x, s.y, s.z, k.sprint);
                fric = bf * 0.91;
            } else {
                accel = k.sprint ? 0.026 : 0.02;
                fric = AIR_FRICTION;
            }
            double ax, az;
            if (k.brake) {
                double sp = Math.sqrt(s.vx * s.vx + s.vz * s.vz);
                double a = Math.min(accel, sp);
                ax = sp < 1e-6 ? 0 : -s.vx / sp * a;
                az = sp < 1e-6 ? 0 : -s.vz / sp * a;
            } else {
                ax = k.wx * accel;
                az = k.wz * accel;
            }
            s.vx += ax;
            s.vz += az;
            if (k.jump && s.ground) {
                float jf = level.getBlockState(BlockPos.containing(s.x, s.y - 0.5000001, s.z)).getBlock().getJumpFactor();
                s.vy = body.getAttributeValue(Attributes.JUMP_STRENGTH) * jf
                        + (body.hasEffect(MobEffects.JUMP_BOOST) ? 0.1 * (body.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) : 0);
                if (k.sprint) {
                    s.vx += -jumpSin * 0.2;
                    s.vz += jumpCos * 0.2;
                }
                s.ground = false;
                s.top = s.y;
            }
            // Across, stopped by walls (steps up to a little over half a block are walked up);
            // crouched on the ground, never over an edge.
            double nx = s.x + s.vx;
            if (wall(nx, s.y, s.z, hw, h) || s.ground && k.sneak && !supported(nx, s.y, s.z)) s.vx = 0;
            else s.x = nx;
            double nz = s.z + s.vz;
            if (wall(s.x, s.y, nz, hw, h) || s.ground && k.sneak && !supported(s.x, s.y, nz)) s.vz = 0;
            else s.z = nz;
            // Up and down.
            if (s.ground) {
                if (!supported(s.x, s.y, s.z)) {
                    s.ground = false;
                    s.top = s.y;
                    s.vy = -GRAVITY * Y_DRAG;
                }
            } else {
                double ny = s.y + s.vy;
                double floor = floorUnder(s.x, s.z, s.y);
                if (s.vy <= 0 && ny <= floor) {
                    s.y = Math.min(s.y, floor);
                    s.ground = true;
                    s.vy = 0;
                    double fall = s.top - s.y;
                    int dmg = (int) Math.ceil((fall - safeFall) * fallMult);
                    // (A tile wants a fall to hurt: only one that kills counts.)
                    if (dmg > 0 && (bot.waterLandingAllowed || dmg >= hp - 1)) {
                        Outcome o = new Outcome(dmg >= hp - 1 ? Harm.DEADLY : Harm.HURT, t, BlockPos.containing(s.x, s.y, s.z), "a fall of " + Math.round(fall));
                        if (o.worseThan(worst)) worst = o;
                        if (o.harm == Harm.DEADLY) return worst;
                    }
                } else {
                    s.y = ny;
                    s.vy = (s.vy - GRAVITY) * Y_DRAG;
                    s.top = Math.max(s.top, s.y);
                    if (s.y < level.getMinY() - 1) return new Outcome(Harm.DEADLY, t, BlockPos.containing(s.x, level.getMinY(), s.z), "the void");
                }
            }
            // The block it is in: slows it down (soul sand, honey).
            float speed = level.getBlockState(BlockPos.containing(s.x, s.y, s.z)).getBlock().getSpeedFactor();
            if (speed == 1f) speed = level.getBlockState(BlockPos.containing(s.x, s.y - 0.5000001, s.z)).getBlock().getSpeedFactor();
            s.vx *= speed * fric;
            s.vz *= speed * fric;
            // In it?
            String in = fireProof ? null : hotAt(s.x, s.y, s.z);
            if (in != null) {
                Outcome o = new Outcome(in.equals("lava") ? Harm.DEADLY : Harm.HURT, t, BlockPos.containing(s.x, s.y, s.z), in);
                if (o.worseThan(worst)) worst = o;
                return worst;
            }
            // Water: no fall hurts from there on.
            if (wet(s.x, s.y, s.z)) return worst;
            if (s.ground && s.vx * s.vx + s.vz * s.vz < 1e-5) return worst;
        }
        if (!s.ground) {
            // Still falling: where it comes down.
            Column c = landing(s.x, s.z, s.y);
            if (c == null) return new Outcome(Harm.DEADLY, MAX_TICKS, BlockPos.containing(s.x, s.y, s.z), "the void");
            if (c.lava && !fireProof) return new Outcome(Harm.DEADLY, MAX_TICKS, c.at, "lava below");
            if (!c.water) {
                int dmg = (int) Math.ceil((s.top - c.top - safeFall) * fallMult);
                if (dmg > 0) {
                    Outcome o = new Outcome(dmg >= hp - 1 ? Harm.DEADLY : Harm.HURT, MAX_TICKS, c.at, "a fall of " + Math.round(s.top - c.top));
                    if (o.worseThan(worst)) worst = o;
                }
            }
        }
        return worst;
    }

    /** How hard a push on the ground accelerates (the game's: speed by the grip of the block). */
    private double groundAccel(double x, double y, double z, boolean sprint) {
        var body = bot.body();
        float bf = body.level().getBlockState(BlockPos.containing(x, y - 0.5000001, z)).getBlock().getFriction();
        double speed = body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (body.isSprinting()) speed /= 1.3;
        if (sprint) speed *= 1.3;
        return speed * (0.21600002 / (bf * bf * bf));
    }

    /** Whether the body at this spot runs into a wall (from just over a step up to the head). */
    private boolean wall(double x, double y, double z, double hw, double h) {
        var body = bot.body();
        AABB box = new AABB(x - hw, y + 0.6, z - hw, x + hw, y + h, z + hw).deflate(1e-3);
        return !body.level().noCollision(body, box);
    }

    /** Lava or fire the body would touch at this spot ("lava", "fire"), or null. */
    private String hotAt(double x, double y, double z) {
        return hotAt(x, y, z, 0.05);
    }

    private String hotAt(double x, double y, double z, double margin) {
        var body = bot.body();
        Level level = body.level();
        double hw = body.getBbWidth() / 2 + margin;
        int x0 = Mth.floor(x - hw), x1 = Mth.floor(x + hw), z0 = Mth.floor(z - hw), z1 = Mth.floor(z + hw);
        int y0 = Mth.floor(y), y1 = Mth.floor(y + body.getBbHeight() - 1e-3);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        String fire = null;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                for (int cy = y0; cy <= y1; cy++) {
                    p.set(cx, cy, cz);
                    var st = level.getBlockState(p);
                    var fl = st.getFluidState();
                    if (fl.is(FluidTags.LAVA) && (cy > y0 || y < cy + fl.getHeight(level, p) + 0.05)) return "lava";
                    if (st.is(BlockTags.FIRE)) fire = "fire";
                }
            }
        }
        return fire;
    }

    /** Running (not still) lava within {@code margin} of the body at feet or head height. */
    private boolean flowingNear(double x, double y, double z, double margin) {
        var body = bot.body();
        Level level = body.level();
        double hw = body.getBbWidth() / 2 + margin;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = Mth.floor(x - hw); cx <= Mth.floor(x + hw); cx++) {
            for (int cz = Mth.floor(z - hw); cz <= Mth.floor(z + hw); cz++) {
                for (int cy = Mth.floor(y); cy <= Mth.floor(y + body.getBbHeight() - 1e-3); cy++) {
                    var fl = level.getFluidState(p.set(cx, cy, cz));
                    if (fl.is(FluidTags.LAVA) && !fl.isSource()) return true;
                }
            }
        }
        return false;
    }

    /** Whether the body at this spot is in water (a fall ends there unhurt). */
    private boolean wet(double x, double y, double z) {
        var body = bot.body();
        Level level = body.level();
        double hw = body.getBbWidth() / 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = Mth.floor(x - hw); cx <= Mth.floor(x + hw); cx++) {
            for (int cz = Mth.floor(z - hw); cz <= Mth.floor(z + hw); cz++) {
                p.set(cx, Mth.floor(y), cz);
                if (level.getFluidState(p).is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }

    /** What is first below a column: a floor (its top), water or lava; null for nothing (the void). */
    private record Column(double top, boolean water, boolean lava, BlockPos at) {}

    /** Ground under any corner of the body within a little over half a block. */
    private boolean supported(double x, double y, double z) {
        return floorUnder(x, z, y) >= y - 0.6 || wetUnder(x, z, y);
    }

    private boolean wetUnder(double x, double z, double y) {
        double hw = bot.body().getBbWidth() / 2 - 1e-3;
        for (int i = 0; i < 4; i++) {
            Column c = column(Mth.floor(x + ((i & 1) == 0 ? -hw : hw)), Mth.floor(y + 0.01), Mth.floor(z + ((i & 2) == 0 ? -hw : hw)));
            if (c != null && c.water && c.top >= y - 0.6) return true;
        }
        return false;
    }

    /** The highest floor under any corner of the body, at or below {@code y} (minus infinity: none). */
    private double floorUnder(double x, double z, double y) {
        double hw = bot.body().getBbWidth() / 2 - 1e-3;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            Column c = column(Mth.floor(x + ((i & 1) == 0 ? -hw : hw)), Mth.floor(y + 0.01), Mth.floor(z + ((i & 2) == 0 ? -hw : hw)));
            if (c != null && !c.water && !c.lava) best = Math.max(best, c.top);
        }
        return best;
    }

    /** Where a fall from here comes down: the highest of what is under the corners. */
    private Column landing(double x, double z, double y) {
        double hw = bot.body().getBbWidth() / 2 - 1e-3;
        Column best = null;
        for (int i = 0; i < 4; i++) {
            Column c = column(Mth.floor(x + ((i & 1) == 0 ? -hw : hw)), Mth.floor(y + 0.01), Mth.floor(z + ((i & 2) == 0 ? -hw : hw)));
            if (c != null && (best == null || c.top > best.top)) best = c;
        }
        return best;
    }

    /** The ground worked out last tick may have changed since. */
    private void fresh() {
        int now = bot.body().tickCount;
        if (now != columnsAt) {
            columns.clear();
            columnsAt = now;
        }
    }

    /** What is first below (x, y, z), looking down at most sixty-four blocks. */
    private Column column(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Column c = columns.get(key);
        if (c != null || columns.containsKey(key)) return c;
        Level level = bot.body().level();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int min = Math.max(level.getMinY(), y - 64);
        for (int cy = y; cy >= min; cy--) {
            p.set(x, cy, z);
            var st = level.getBlockState(p);
            var fl = st.getFluidState();
            if (fl.is(FluidTags.LAVA)) {
                c = new Column(cy + fl.getHeight(level, p), false, true, p.immutable());
                break;
            }
            if (fl.is(FluidTags.WATER)) {
                c = new Column(cy + fl.getHeight(level, p), true, false, p.immutable());
                break;
            }
            var shape = st.getCollisionShape(level, p);
            if (!shape.isEmpty()) {
                c = new Column(cy + shape.max(net.minecraft.core.Direction.Axis.Y), false, false, p.immutable());
                break;
            }
        }
        columns.put(key, c);
        return c;
    }
}
