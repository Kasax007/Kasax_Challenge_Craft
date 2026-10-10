package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Hunts mobs of the given kinds: finds the nearest one, runs up to it, hits it whenever the
 * weapon is recharged (full-strength hits, as a player would), and picks up the drops. Ends after
 * {@code kills} kills, or once the bot holds {@code count} of the wanted {@code loot}.
 */
public final class KillTask implements BotTask {
    private static final double REACH = 2.9;
    /** Sidestepping a drawn bow: which way this time, and whether the step is on. */
    private float dodgeSide = 1f;
    private boolean dodging;

    private final Set<EntityType<?>> types;

    /** Whether this hunt is after that kind of creature (it is the job, not a nuisance on the way). */
    public boolean after(EntityType<?> type) {
        return types.contains(type);
    }
    private final Set<Item> loot;
    private final int count, kills;
    private final Set<java.util.UUID> unreachable = new java.util.HashSet<>();
    private LivingEntity target;

    /** What it is fighting now (null between two). */
    public LivingEntity target() {
        return target;
    }
    private BlockPos deathSpot;
    private int killed, collectTicks, repath, explores, chaseTicks, direct, exploreTicks, lookCooldown;
    private final Explorer explorer = new Explorer(2400);
    private boolean triedMemory;
    private final java.util.Set<Integer> unreachableDrops = new java.util.HashSet<>();
    private boolean walking;

    /** {@code loot} may be empty when only the kill matters (then {@code kills} counts). */
    public KillTask(Set<EntityType<?>> types, Set<Item> loot, int count, int kills) {
        this.types = Set.copyOf(types);
        explorer.lookingFor(this.types);
        this.loot = Set.copyOf(loot);
        this.count = count;
        this.kills = kills;
    }

    /** Whether it is after monsters (not animals, not fish). */
    public boolean monsters() {
        for (EntityType<?> t : types) if (t.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) return true;
        return false;
    }

    /** Where a blow would not throw it into lava or off an edge (looked for every few ticks), and whether here would. */
    private net.minecraft.world.phys.Vec3 safeSpot;
    private boolean unsafe;
    private int safeCheckAt;

    /** Just this one (the monster that came for it), not the nearest of its kind. */
    private java.util.UUID only;

    public KillTask(LivingEntity one) {
        this(Set.of(one.getType()), Set.of(), 0, 1);
        this.only = one.getUUID();
        this.within = 16;
    }

    private boolean done(Bot bot) {
        if (!loot.isEmpty()) return ObtainPlanner.countAny(bot.body(), loot) >= count;
        return killed >= Math.max(1, kills);
    }

    @Override
    public Result tick(Bot bot) {
        // (Crouching only while fighting by an edge: set again below each tick it is.)
        bot.body().sneak = false;
        roundToShooter = false;
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (collectTicks > 0) {
            collectTicks--;
            List<ItemEntity> drops = BotWorld.drops(level, deathSpot, 5, loot.isEmpty() ? null : loot);
            if (!drops.isEmpty()) {
                if (!walking || bot.navigator().status() != BotNavigator.Status.MOVING) {
                    bot.navigator().goPickUp(drops.get(0));
                    walking = true;
                }
                if (bot.navigator().tick() == BotNavigator.Status.FAILED) collectTicks = 0;
                return Result.RUNNING;
            }
            collectTicks = 0;
            walking = false;
            bot.navigator().stop();
        }
        if (done(bot)) return Result.DONE;

        if (target == null || !target.isAlive() || target.distanceTo(body) > (within > 0 ? within * 1.5 : BotWorld.MOB_SIGHT)) {
            if (target != null && !target.isAlive() && target.getLastHurtByMob() == body) {
                killed++;
                deathSpot = target.blockPosition();
                collectTicks = 40;
                target = null;
                return Result.RUNNING;
            }
            if (--lookCooldown > 0) return explore(bot);
            lookCooldown = 20;
            // What it is after already lying about (killed in the same sweep, or by something else):
            // picked up first, before hunting for more.
            if (!loot.isEmpty()) {
                ItemEntity lying = BotWorld.drops(level, body.blockPosition(), 16, loot).stream()
                        .filter(e -> !unreachableDrops.contains(e.getId())).findFirst().orElse(null);
                if (lying != null) {
                    if (walking) bot.navigator().stop();
                    walking = false;
                    deathSpot = lying.blockPosition();
                    collectTicks = 60;
                    unreachableDrops.add(lying.getId()); // (once: if it cannot be had, not again)
                    return Result.RUNNING;
                }
            }
            target = nearest(body, level);
            chaseTicks = 0;
            if (target == null) return explore(bot);
            explorer.pause(bot);
            if (walking) bot.navigator().stop();
            walking = false;
            equipWeapon(bot);
        }
        // On lava (a strider), or long out of reach: shot at, if there is a bow and arrows.
        if ((target.isInLava() || target.level().getBlockState(target.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.LAVA)
                || chaseTicks > 600) && shoot(bot, target)) return Result.RUNNING;
        // Deep down in the water (a squid, a glow squid far below): not one to chase from the
        // surface for long.
        boolean deep = target.isInWater() && target.getY() < body.getY() - 5 && body.isInWater();
        if (deep && chaseTicks > 240) chaseTicks = 1200;
        if (++chaseTicks > 1200) {
            // Could not get at this one (behind water, up a cliff): try another.
            unreachable.add(target.getUUID());
            target = null;
            return Result.RUNNING;
        }

        double dist = body.distanceTo(target);
        // What reach is about: from the eyes to the nearest bit of its body (a spider is wide).
        double hit = hitDistance(body, target);
        // Up in the air (a phantom, a ghast, a blaze over the lava): arrows, if it has a bow.
        if (target.getY() - body.getY() > 3.5 && dist > REACH && shoot(bot, target)) return Result.RUNNING;
        body.strafe = 0f; // (a sidestep lasts only while the bow is drawn, below)
        // A creeper is hit from just outside the three blocks at which it starts to hiss (the
        // arm reaches its body, not its middle), with a running blow that throws it back.
        boolean creeperTarget = target instanceof net.minecraft.world.entity.monster.Creeper;
        if (creeperTarget && hit <= 3.9 && hit > HIT) {
            if (walking) {
                bot.navigator().stop();
                walking = false;
            }
            body.lookAt(target.getEyePosition());
            boolean ready = body.getAttackStrengthScale(0.5f) >= 1f;
            if (!ready) {
                // Waiting for the arm: just out of its range, backing off if it comes on.
                body.forward = hit < 3.3 ? -1f : 0f;
                keepFooting(body);
                return Result.RUNNING;
            }
            if (hit > 2.9) {
                // Ready: one step in at a run, the blow lands as it comes into reach.
                body.forward = 1f;
                body.sprintNow = body.getFoodData().getFoodLevel() > 6;
                return Result.RUNNING;
            }
            body.attack(target);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            body.forward = -1f;
            body.sprintNow = false;
            keepFooting(body);
            if (!target.isAlive()) {
                killed++;
                deathSpot = target.blockPosition();
                collectTicks = 40;
                target = null;
            }
            return Result.RUNNING;
        }
        if (hit <= HIT) {
            // (Crouched while trading blows by an edge: the push of a blow does not carry it off.)
            body.sneak = edgeNear(body);
            if (walking) {
                bot.navigator().stop();
                walking = false;
            }
            // (The weapon in hand: after a dig or a meal it may be the pickaxe or the bread.)
            if (++weaponCheck % 10 == 1) equipWeapon(bot);
            body.lookAt(target.getEyePosition());
            boolean creeper = target instanceof net.minecraft.world.entity.monster.Creeper;
            body.forward = 0f;
            float strength = body.getAttackStrengthScale(0.5f);
            boolean ready = strength >= 1f;
            boolean shield = body.getOffhandItem().is(Items.SHIELD);
            // How many are on it close by: more than one, and it gives ground between blows (they
            // line up in front instead of closing round it).
            int pack = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(4.5),
                    m -> m.isAlive() && (m.getTarget() == body || m instanceof net.minecraft.world.entity.monster.Enemy && m.distanceTo(body) < 3)).size();
            if (!ready) {
                // Recharging: a creeper is kept at arm's length (it goes off close up).
                if (creeper) {
                    body.forward = hit < 2.6 ? -1f : 0f;
                    keepFooting(body);
                    return Result.RUNNING;
                }
                // Anything else: kept at the edge of reach - in reach of the sword, out of reach
                // of its arms - and circled round meanwhile (side to side, a new side now and
                // then or at a wall), as players fight: what comes at it walks into the blow.
                // (A magma cube or a slime: at the very edge of reach, it leaps the gap.)
                // By lava or a drop: every blow throws it back a block or two - so not with that
                // at its back. Round to where a blow lands it on firm ground first (as a player
                // turns a fight so the lava is to his side), else back off away from the lava.
                if (bot.danger.lavaNear(6) || edgeNear(body)) {
                    if (body.tickCount >= safeCheckAt) {
                        safeCheckAt = body.tickCount + 5;
                        safeSpot = bot.danger.thrown(target, body.position()).safe() ? null : bot.danger.saferStand(target);
                        unsafe = safeSpot != null || !bot.danger.thrown(target, body.position()).safe();
                    }
                    if (unsafe) {
                        net.minecraft.world.phys.Vec3 way = safeSpot != null ? safeSpot.subtract(body.position()) : bot.danger.awayFromHot();
                        if (way.horizontalDistanceSqr() > 0.04) {
                            way = way.multiply(1, 0, 1).normalize();
                            double yaw = Math.toRadians(body.getYRot());
                            double sin = Math.sin(yaw), cos = Math.cos(yaw);
                            body.forward = (float) Mth.clamp(way.x * -sin + way.z * cos, -1, 1);
                            body.strafe = (float) Mth.clamp(way.x * cos + way.z * sin, -1, 1);
                            body.jump = false;
                            keepFooting(body);
                            return Result.RUNNING;
                        }
                    }
                } else unsafe = false;
                boolean cubeMob = target instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
                double want = cubeMob ? 2.85 : pack >= 2 ? 2.75 : 2.55;
                body.forward = (float) Mth.clamp((hit - want) * 2.5, -1, 1);
                if (++strafeTicks > strafeFor || body.horizontalCollision) {
                    strafeSide = -strafeSide;
                    strafeTicks = 0;
                    strafeFor = 15 + body.getRandom().nextInt(11);
                }
                body.strafe = 0.7f * strafeSide;
                keepFooting(body);
                if (awayFromEdge(body)) return Result.RUNNING;
                // Alone with it: a jump as the arm comes back (at about half), so that the blow
                // lands on the way down - a critical hit, half again as hard.
                if (!cubeMob && pack <= 1 && crit == 0 && strength >= 0.55f && body.onGround() && !body.isInWater() && hit < HIT - 0.2) {
                    body.jump = true;
                    crit = 1;
                    return Result.RUNNING;
                }
                body.jump = false;
                // Not jumping: the shield up meanwhile, against what is out to hurt it.
                if (crit == 0 && shield && !body.isUsingItem() && threatens(target, body)) {
                    body.gameMode.useItem(body, level, body.getOffhandItem(), InteractionHand.OFF_HAND);
                }
                return Result.RUNNING;
            }
            body.strafe = 0f;
            // Ready: shield down, then the blow; a jump first for a critical hit (half again as
            // hard) when there is time for one (not at a creeper: it would close in).
            if (body.isUsingItem()) {
                body.releaseUsingItem();
                return Result.RUNNING;
            }
            if (!creeper && !(target instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob)
                    && pack <= 1 && crit == 0 && body.onGround() && !body.isInWater() && hit < HIT - 0.4 && opened) {
                body.jump = true;
                crit = 1;
                return Result.RUNNING;
            }
            if (crit == 1) {
                body.jump = false;
                // (On the way down: that is the critical hit. Landed again: hit anyway.)
                if (!body.onGround() && body.getDeltaMovement().y >= 0 && ++critWait < 12) return Result.RUNNING;
            }
            // More than one at it: a sprinting blow instead (it knocks the mob well back, time
            // for the next): a step forward at a run, then the hit.
            if (!creeper && pack >= 2 && crit == 0 && !body.isSprinting() && body.getFoodData().getFoodLevel() > 6) {
                body.sprintNow = true;
                body.forward = 1f;
                crit = 2;
                return Result.RUNNING;
            }
            body.sprintNow = false;
            crit = 0;
            critWait = 0;
            if (pack <= 1) body.setSprinting(false);
            body.attack(target);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            opened = true;
            if (!target.isAlive()) {
                killed++;
                deathSpot = target.blockPosition();
                collectTicks = 40;
                target = null;
            }
            return Result.RUNNING;
        }
        crit = 0;
        // A shooter (skeleton, stray, pillager) in sight on open ground: straight at it at a run,
        // a step aside each time its bow is drawn full (the arrow goes where it stood), as a
        // player closes in on one. Then the blows, with the jump for the critical hit.
        boolean shooter = target instanceof net.minecraft.world.entity.monster.RangedAttackMob;
        // (Straight runs only over safe ground: the way to it checked a few steps ahead for a
        // drop or lava - else the path search, which knows the way round.)
        boolean openGround = groundTo(body, target);
        boolean charge = shooter && openGround && dist < 16 && Math.abs(target.getY() - body.getY()) < 2.5 && body.hasLineOfSight(target);
        boolean shieldOn = body.getOffhandItem().is(Items.SHIELD);
        // The way round to a shooter (no straight run over this ground - a cave, a slope): the
        // shield up towards it all the same, turned to it by the threat sense while the path is
        // walked (seed 77, hard: two arrows from four and three blocks in a cave, the shield down
        // on the path).
        roundToShooter = shooter && shieldOn && !charge && dist > 4 && body.hasLineOfSight(target);
        // (The shield down, unless it is held up running at a shooter: it needs a quarter second
        // up before it blocks, so it is not lowered and raised again each tick.)
        if (body.isUsingItem() && body.getUseItem().is(Items.SHIELD) && !(charge && shieldOn) && !roundToShooter) body.releaseUsingItem();
        if (charge && shieldOn) {
            // With a shield: up and walk straight at it - the arrows hit the shield (a bot is not
            // slowed holding it, only kept from sprinting), lowered in reach for the blow.
            if (walking) {
                bot.navigator().stop();
                walking = false;
            }
            body.lookAt(target.getEyePosition());
            if (!body.isUsingItem()) body.gameMode.useItem(body, level, body.getOffhandItem(), InteractionHand.OFF_HAND);
            body.forward = 1f;
            body.strafe = 0f;
            body.sprintNow = false;
            body.jump = body.horizontalCollision;
            return Result.RUNNING;
        }
        if (charge) {
            if (walking) {
                bot.navigator().stop();
                walking = false;
            }
            body.lookAt(target.getEyePosition());
            body.forward = 1f;
            body.sprintNow = body.getFoodData().getFoodLevel() > 6;
            boolean drawn = target.isUsingItem() && target.getTicksUsingItem() >= 12;
            if (drawn && !dodging) dodgeSide = -dodgeSide; // (each arrow the other way: no pattern)
            dodging = drawn;
            body.strafe = drawn ? dodgeSide : 0f;
            body.jump = body.horizontalCollision;
            return Result.RUNNING;
        }
        // Close and on about the same level: just run at it. Otherwise path there.
        if (openGround && (dist < 6 && Math.abs(target.getY() - body.getY()) < 1.5 || direct > 0)) {
            direct--;
            if (walking) {
                bot.navigator().stop();
                walking = false;
            }
            body.lookAt(target.getEyePosition());
            body.forward = 1f;
            // (At a run: the first blow lands sprinting, which throws it well back.)
            body.sprintNow = body.getFoodData().getFoodLevel() > 6 && !(target instanceof net.minecraft.world.entity.monster.Creeper);
            body.jump = body.horizontalCollision;
            return Result.RUNNING;
        }
        if (!walking || --repath <= 0) {
            bot.navigator().goNear(target.blockPosition(), 2.8);
            walking = true;
            repath = 30;
        }
        if (bot.navigator().tick() == BotNavigator.Status.FAILED) {
            walking = false;
            // No path, but close (a mob in tall grass or bamboo): go straight at it for a while.
            if (dist < 10) direct = 40;
            else if (shoot(bot, target)) return Result.RUNNING;
            else {
                unreachable.add(target.getUUID());
                target = null;
            }
        }
        return Result.RUNNING;
    }

    private final Set<java.util.UUID> shotAt = new java.util.HashSet<>();

    /** Walking the way round to a shooter this tick: the threat sense keeps the shield up towards it meanwhile. */
    private boolean roundToShooter;

    public boolean shieldWalkWanted() {
        return roundToShooter;
    }

    /** A bow or a crossbow and arrows: shoot it instead (once per mob). Whether it set about that. */
    private boolean shoot(Bot bot, LivingEntity mob) {
        // (Close by, the sword: a bow drawn at arm's length is a free hit for it - a magma cube
        // jumping about the lava's edge, a strider at the shore.)
        if (shotAt.contains(mob.getUUID()) || bot.body().distanceTo(mob) > 32 || bot.body().distanceTo(mob) < 5) return false;
        // (Never at a magma cube or a slime: it leaps the distance while the bow is drawn, and
        // splits into more anyway - the sword, ready, as it lands.)
        if (mob instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob) return false;
        if (net.kasax.challengecraft.bot.BotInventory.slotOf(bot.body(), Items.ARROW) < 0) return false;
        Item weapon = net.kasax.challengecraft.bot.BotInventory.slotOf(bot.body(), Items.BOW) >= 0 ? Items.BOW
                : net.kasax.challengecraft.bot.BotInventory.slotOf(bot.body(), Items.CROSSBOW) >= 0 ? Items.CROSSBOW : null;
        if (weapon == null) return false;
        shotAt.add(mob.getUUID());
        if (walking) bot.navigator().stop();
        walking = false;
        java.util.UUID id = mob.getUUID();
        bot.interject(new ShootTask(weapon, e -> e.getUUID().equals(id), 8));
        return true;
    }

    /** Only what is close by (a fight that came to it): no search for more when that is gone. */
    private double within;

    public KillTask nearby(double radius) {
        this.within = radius;
        return this;
    }

    private LivingEntity nearest(BotPlayer body, ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(body.blockPosition()).inflate(within > 0 ? within : BotWorld.MOB_SIGHT),
                        e -> e.isAlive() && types.contains(e.getType()) && e != body && !unreachable.contains(e.getUUID()) && BotWorld.seesMob(body, e)
                                && (only == null || e.getUUID().equals(only)) && !downInTheDark(body, e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(body))).orElse(null);
    }

    /**
     * A monster down in a cave, hunted from up on the surface with no armour worth the name and
     * no shield: not that one - the dark below is where the others are (a zombie for its tile,
     * followed down to y 38, brought a skeleton's arrows and a creeper with it on seed 22). The
     * night brings them up to the surface. (One that comes for it is the reflexes' business.)
     */
    private boolean downInTheDark(BotPlayer body, LivingEntity e) {
        if (only != null || e.getType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) return false;
        if (body.getArmorValue() >= 6 || body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)
                || body.getInventory().countItem(net.minecraft.world.item.Items.SHIELD) > 0) return false;
        if (SurfaceTask.underground(body)) return false;
        return e.getY() < body.getY() - 6 && !body.level().canSeeSky(e.blockPosition().above());
    }

    private int crit, critWait, weaponCheck, strafeTicks, strafeFor = 20;
    private float strafeSide = 1f;
    /** The first blow has landed (a running one); from then on, jumps for critical hits. */
    private boolean opened;
    /** Reach, eyes to the target's body (the game allows three). */
    private static final double HIT = 2.85;

    /** From the eyes to the nearest point of the target's box. */
    public static double hitDistance(BotPlayer body, LivingEntity t) {
        var eye = body.getEyePosition();
        AABB b = t.getBoundingBox();
        double x = Mth.clamp(eye.x, b.minX, b.maxX), y = Mth.clamp(eye.y, b.minY, b.maxY), z = Mth.clamp(eye.z, b.minZ, b.maxZ);
        return eye.distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
    }

    /**
     * Steps back and aside only onto ground: where the move would take it (a block on) there
     * must be footing within two blocks down and no lava; else that part of the move is dropped
     * (and the side switched), at an edge sneaking.
     */
    static void keepFooting(BotPlayer body) {
        if (body.forward == 0 && body.strafe == 0) return;
        double yaw = Math.toRadians(body.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        // (Forward too: closing in on something that stands by a drop walks off it.)
        if (body.forward > 0 && !footing(body, -sin, cos)) body.forward = 0;
        // (The game's own: forward is (-sin, cos), to the left (cos, sin).)
        double bx = -sin * Math.min(0, body.forward), bz = cos * Math.min(0, body.forward);
        if (body.forward < 0 && !footing(body, bx, bz)) body.forward = 0;
        if (body.strafe != 0 && !footing(body, cos * Math.signum(body.strafe), sin * Math.signum(body.strafe))) body.strafe = -body.strafe;
        if (body.strafe != 0 && !footing(body, cos * Math.signum(body.strafe), sin * Math.signum(body.strafe))) body.strafe = 0;
    }

    /** Firm, safe ground all the way along the straight line to it (one step in four checked). */
    private static boolean groundTo(BotPlayer body, LivingEntity target) {
        double dx = target.getX() - body.getX(), dz = target.getZ() - body.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) return true;
        var level = body.level();
        for (double t = 0.8; t < Math.min(len, 16); t += 0.8) {
            BlockPos at = BlockPos.containing(body.getX() + dx / len * t, body.getY() + 0.2, body.getZ() + dz / len * t);
            boolean firm = false;
            for (int dy = 0; dy <= 3; dy++) {
                BlockPos q = at.below(dy);
                var st = level.getBlockState(q);
                if (st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || st.is(net.minecraft.world.level.block.Blocks.FIRE)
                        || st.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) && dy == 1) return false;
                if (dy > 0 && !st.getCollisionShape(level, q).isEmpty()) {
                    firm = true;
                    break;
                }
            }
            if (!firm) return false;
        }
        return true;
    }

    /**
     * Fighting by an edge (a drop of more than two, lava under it - the Nether's ledges): a step
     * in from it first, as a player keeps the wall at his back rather than the void, since every
     * blow it takes throws it back. Returns whether it moved.
     */
    static boolean awayFromEdge(BotPlayer body) {
        if (!body.onGround()) return false;
        double vx = 0, vz = 0;
        boolean edge = false;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, dx = Math.cos(a), dz = Math.sin(a);
            if (footing(body, dx, dz)) {
                vx += dx;
                vz += dz;
            } else edge = true;
        }
        if (!edge || vx * vx + vz * vz < 0.01) return false;
        double yaw = Math.toRadians(body.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double len = Math.sqrt(vx * vx + vz * vz);
        vx /= len;
        vz /= len;
        body.forward = (float) Mth.clamp(vx * -sin + vz * cos, -1, 1);
        body.strafe = (float) Mth.clamp(vx * cos + vz * sin, -1, 1);
        return true;
    }

    /** A drop or lava a step off to any side. */
    static boolean edgeNear(BotPlayer body) {
        if (!body.onGround()) return false;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            if (!footing(body, Math.cos(a), Math.sin(a))) return true;
        }
        return false;
    }

    private static boolean footing(BotPlayer body, double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3) return true;
        var level = body.level();
        BlockPos at = BlockPos.containing(body.getX() + dx / len * 1.1, body.getY() + 0.2, body.getZ() + dz / len * 1.1);
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos q = at.below(dy);
            var st = level.getBlockState(q);
            if (st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || st.is(net.minecraft.world.level.block.Blocks.FIRE)
                    || st.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) || st.is(net.minecraft.world.level.block.Blocks.CACTUS)) return false;
            if (dy == 0) continue;
            if (!st.getCollisionShape(level, q).isEmpty()) return dy <= 2;
            if (!st.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** Whether it is out to hurt the bot (so the shield is worth raising). */
    private static boolean threatens(LivingEntity target, BotPlayer body) {
        return target instanceof net.minecraft.world.entity.Mob m && m.getTarget() == body
                || target instanceof net.minecraft.world.entity.monster.Enemy;
    }

    /** Best melee weapon into the hand: the most damage per second (a sword beats an axe). */
    public static void equipWeapon(Bot bot) {
        var inv = bot.body().getInventory().getNonEquipmentItems();
        int best = -1;
        double bestDps = 4; // a fist: 1 damage, 4 blows a second
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s.isEmpty()) continue;
            double d = dps(s);
            if (d > bestDps) {
                bestDps = d;
                best = i;
            }
        }
        if (best >= 0) bot.tools().select(best);
    }

    /** Damage a second of full-strength blows: damage times attacks a second. */
    static double dps(ItemStack s) {
        var mods = s.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods == null) return 4;
        double[] dmg = {1}, speed = {4};
        mods.forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) dmg[0] += mod.amount();
            if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) speed[0] += mod.amount();
        });
        return dmg[0] * Math.max(0.5, speed[0]);
    }

    /** None in sight: walk out (up from a mine first) for up to two minutes. */
    private boolean fortressTried;

    private Result explore(Bot bot) {
        // (A fight close by, over: nothing to go looking for.)
        if (within > 0) return killed > 0 ? Result.DONE : Result.FAILED;
        // Blazes and wither skeletons live in fortresses (nowhere else): to the fortress first,
        // exploring the Nether for one if none is known.
        if (!fortressTried && (types.contains(net.minecraft.world.entity.EntityTypes.BLAZE) || types.contains(net.minecraft.world.entity.EntityTypes.WITHER_SKELETON))
                && bot.body().level().dimension() == net.minecraft.world.level.Level.NETHER) {
            fortressTried = true;
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) bot.body().level();
            var fortress = VisitStructureTask.resolve(level, "minecraft:fortress");
            if (!VisitStructureTask.inside(bot, fortress)) {
                bot.say("to a fortress for " + describeTypes());
                bot.interject(new VisitStructureTask(level, "minecraft:fortress"));
                return Result.RUNNING;
            }
        }
        // Where it last saw one: the herd is likely still about there.
        if (!triedMemory) {
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) bot.body().level();
            BlockPos seen = null;
            for (EntityType<?> t : types) {
                BlockPos p = bot.memory().lastSeen(level, t, bot.body().blockPosition());
                // (Not one seen far down in a cave or under a lake: no quick way there.)
                if (p != null && (Math.abs(p.getY() - bot.body().getBlockY()) > 24 || !level.getFluidState(p).isEmpty())) p = null;
                if (p != null && (seen == null || p.distSqr(bot.body().blockPosition()) < seen.distSqr(bot.body().blockPosition()))) seen = p;
            }
            triedMemory = true;
            if (seen != null && seen.distSqr(bot.body().blockPosition()) > 16 * 16) {
                bot.interject(new GoToTask(seen, 6));
                return Result.RUNNING;
            }
        }
        Result r = explorer.tick(bot);
        if (r == Result.FAILED) bot.say("found no " + describeTypes());
        return r;
    }

    private String describeTypes() {
        return types.size() == 1 ? BuiltInRegistries.ENTITY_TYPE.getKey(types.iterator().next()).getPath() : "mobs";
    }

    @Override
    public String describe() {
        String what = types.size() == 1 ? BuiltInRegistries.ENTITY_TYPE.getKey(types.iterator().next()).getPath() : types.size() + " kinds of mob";
        return loot.isEmpty() ? "kill " + what + (kills > 1 ? " x" + kills : "")
                : "hunt " + what + " for " + ObtainPlanner.names(loot) + " (" + count + ")";
    }
}
