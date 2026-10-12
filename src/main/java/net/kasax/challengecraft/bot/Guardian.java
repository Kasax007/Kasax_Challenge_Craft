package net.kasax.challengecraft.bot;

import net.kasax.challengecraft.bot.task.HideTask;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Bob's guardian: every reflex check, how long he has left at the rate everything after him hurts
 * him (each one's blows after his armour, how often, from when it is in reach or has him in its
 * sights), against how long a fight he can win would take - and when the fight is not to be won in
 * time, the quickest way out of reach that works where he stands, taken at once, whatever the plan:
 * walled in, up a pillar out of their reach, dug in under a lid. Inside, he eats and waits for the
 * hearts (the hiding does that).
 *
 * <p>The reflexes go by fixed marks ("low: away"), and in a game against a player that came too
 * late three times: seven hearts in a ravine with two zombies at him is a second and a half, not
 * "low"; a ghast's fireballs were no "bowman", so he ate under them; bare-handed at full health a
 * zombie wore him down while a run went nowhere, and then the fist fight.
 */
public final class Guardian {
    /** How far ahead the reckoning looks (ten seconds). */
    static final int HORIZON = 200;

    /** One thing after him: its blows after his armour, every how many ticks, from which tick on. */
    public record Threat(Mob mob, double perHit, int every, int from, boolean ranged) {
    }

    /** The reckoning: what is after him, the ticks till the hearts are gone at that rate, and a fight's length. */
    public record Reckoning(List<Threat> threats, int ticksLeft, int fightTicks, String why) {
        public boolean pressing() {
            return ticksLeft < HORIZON;
        }
    }

    private final Bot bot;
    /** How often it stepped in, and with what (for the benchmarks). */
    public int boxes, pillars, digs;
    private int saidAt = -1000, lastActAt = -1000;

    Guardian(Bot bot) {
        this.bot = bot;
    }

    // ---- what hurts from afar ----------------------------------------------------------------------

    /**
     * Something that hurts from afar: a bow or crossbow drawn, a trident, a witch's potions, a
     * ghast's or a blaze's fireballs, a breeze's wind, a shulker's bullets. (Ghasts and blazes are
     * no "ranged attack mob" to the game: to the reflexes they were no bowmen.)
     */
    public static boolean shooter(Entity e) {
        if (e instanceof Ghast || e instanceof Blaze || e instanceof net.minecraft.world.entity.monster.breeze.Breeze
                || e instanceof Shulker || e instanceof Witch) return true;
        if (!(e instanceof RangedAttackMob) || !(e instanceof Mob m)) return false;
        var hand = m.getMainHandItem();
        return hand.getItem() instanceof ProjectileWeaponItem || hand.is(Items.TRIDENT);
    }

    /** How far it hurts from: a ghast from far off, the rest from bow range. */
    public static double shootingRange(Entity e) {
        return e instanceof Ghast ? 64 : e instanceof Blaze ? 32 : 24;
    }

    // ---- the reckoning -------------------------------------------------------------------------------

    /** Damage after armour (the game's own rule: armour takes a share, less of a big blow). */
    static double afterArmour(double damage, double armour, double toughness) {
        double f = 2 + toughness / 4;
        double kept = Math.max(armour * 0.2, Math.min(20, armour - damage / f));
        return damage * (1 - kept / 25);
    }

    /** A monster's blow at this difficulty (mobs hit harder on hard, softer on easy). */
    static double scaled(double damage, Difficulty d) {
        return switch (d) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(damage / 2 + 1, damage);
            case HARD -> damage * 1.5;
            default -> damage;
        };
    }

    /** Ticks for a mob to cover a block on the ground: its speed works on its own push (the game's rule for mobs). */
    static double chaseSpeed(Mob m) {
        double s = m.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED) ? m.getAttributeValue(Attributes.MOVEMENT_SPEED) : 0.25;
        return Math.max(0.06, s * s / 0.454);
    }

    /** Everything after him within reach of hurting him in the next ten seconds, and how long he has. */
    public Reckoning reckon() {
        BotPlayer body = bot.body();
        var level = body.level();
        Difficulty diff = level.getDifficulty();
        double armour = body.getArmorValue(), tough = body.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        List<Threat> threats = new ArrayList<>();
        // (Not only monsters: a polar bear by its cub, a wolf, a golem, bees - after him, they are
        // as deadly; a polar bear took eighteen hearts in two blows in a self-play game, unseen.)
        for (Mob m : level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(64, 24, 64),
                m -> m.isAlive() && (m instanceof Enemy || m.getTarget() == body))) {
            double d = m.distanceTo(body);
            boolean after = m.getTarget() == body;
            boolean sees = body.hasLineOfSight(m);
            if (shooter(m)) {
                if (!sees || d > shootingRange(m) || !after) continue;
                double hit;
                int every;
                if (m instanceof Ghast) {
                    hit = afterArmour(scaled(6, diff), armour, tough);
                    every = 60;
                } else if (m instanceof Blaze) {
                    hit = afterArmour(scaled(5, diff), armour, tough);
                    every = 35;
                } else if (m instanceof Witch) {
                    hit = 6; // (a potion of harming: armour does nothing against it)
                    every = 60;
                } else if (m instanceof Shulker) {
                    hit = afterArmour(4, armour, tough);
                    every = 50;
                } else if (m instanceof net.minecraft.world.entity.monster.breeze.Breeze) {
                    hit = 1;
                    every = 40;
                } else {
                    // (Not every arrow finds a target on the move: some three in four. A full draw
                    // hits for more than the base - 7.5 of an arrow on hard in a self-play game.)
                    hit = afterArmour(scaled(m.getMainHandItem().is(Items.TRIDENT) ? 8 : 5, diff), armour, tough) * 0.75;
                    every = diff == Difficulty.HARD ? 30 : 45;
                }
                if (m instanceof Ghast) hit *= 0.8;
                // (A shield raised against an arrow: most of them end there. Not a fireball's
                // blast - it comes round the shield, as in the game against a player.)
                if (body.isBlocking() && !(m instanceof Ghast) && !(m instanceof Blaze) && !(m instanceof Witch)) hit *= 0.3;
                boolean drawing = m.isUsingItem() || m instanceof Ghast g && g.isCharging();
                threats.add(new Threat(m, hit, every, drawing ? 10 : every / 2, true));
                continue;
            }
            // A creeper: the reflexes see to those (away, or behind the shield); a blast is no rate.
            if (m instanceof Creeper) continue;
            if (!after && !(d < 6 && sees && !(m instanceof Enderman))) continue;
            if (d > 24) continue;
            double dmg = m.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? m.getAttributeValue(Attributes.ATTACK_DAMAGE) : 2;
            double hit = afterArmour(scaled(dmg, diff), armour, tough);
            // (Slimes and magma cubes hurt on touch, every half second.)
            int every = m instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob ? 10 : 20;
            double reach = 1.9 + m.getBbWidth() / 2;
            int from = (int) Math.ceil(Math.max(0, d - reach) / chaseSpeed(m)) + (after ? 0 : 20);
            threats.add(new Threat(m, hit, every, from, false));
        }
        double hp = body.getHealth() + body.getAbsorptionAmount();
        double[] at = new double[HORIZON + 1];
        // (Blows in the same tick are one blow: the biggest.)
        for (Threat t : threats) {
            for (int tick = t.from; tick <= HORIZON; tick += t.every) at[tick] = Math.max(at[tick], t.perHit);
        }
        // Burning (out of the lava, a fireball's flames): a heart a second while it lasts.
        if (body.isOnFire() && !body.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            for (int tick = 20; tick <= Math.min(HORIZON, body.getRemainingFireTicks()); tick += 20) at[tick] += 1;
        }
        if (body.hasEffect(MobEffects.WITHER)) {
            for (int tick = 40; tick <= HORIZON; tick += 40) at[tick] += 1;
        }
        int left = Integer.MAX_VALUE;
        double sum = 0, lastHit = 0;
        int lastAt = -100;
        for (int tick = 0; tick <= HORIZON; tick++) {
            if (at[tick] <= 0) continue;
            // (Hurt, he cannot be hurt again for half a second - only by more than the last blow,
            // and only the difference: two zombies striking together cost one blow, not two.)
            double take = tick - lastAt < 10 ? Math.max(0, at[tick] - lastHit) : at[tick];
            if (take <= 0) continue;
            sum += take;
            lastHit = tick - lastAt < 10 ? Math.max(lastHit, at[tick]) : at[tick];
            if (tick - lastAt >= 10) lastAt = tick;
            // (Kept a heart in hand: the reckoning is rough.)
            if (sum >= hp - 2) {
                left = tick;
                break;
            }
        }
        return new Reckoning(threats, left, fightTicks(threats), "");
    }

    /**
     * Ticks a fight with everything that strikes close would take him: their health over the
     * damage a second of his best weapon (fists: a heart a quarter-second, twenty blows for a
     * zombie). A shooter in sight makes it no fight to stand in; then never.
     */
    private int fightTicks(List<Threat> threats) {
        BotPlayer body = bot.body();
        double dps = 4; // (the fist: one damage, four blows a second)
        boolean armed = false;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (!st.is(net.minecraft.tags.ItemTags.SWORDS) && !st.is(net.minecraft.tags.ItemTags.AXES)) continue;
            armed = true;
            dps = Math.max(dps, net.kasax.challengecraft.bot.task.KillTask.dps(st));
        }
        // (Blows miss, the knockback walks them off: a fifth again as long as the arithmetic.)
        double ticks = 0;
        int shooters = 0;
        for (Threat t : threats) {
            ticks += t.mob.getHealth() / Math.max(0.5, dps) * 20 * 1.2;
            if (!t.ranged) continue;
            shooters++;
            // A shooter is a fight only rushed, with a weapon, and only one that stands on the
            // ground within reach of a run (a bowman, a witch, a blaze low enough): the run to it
            // counts. A ghast is none - its fireball hit back is a task of its own.
            if (!armed || t.mob instanceof Ghast || Math.abs(t.mob.getY() - body.getY()) > 3) return Integer.MAX_VALUE;
            ticks += Math.max(0, t.mob.distanceTo(body) - 3) / 0.25;
        }
        // (Two shooters and next to no armour: the second shoots it down on the way to the first.)
        if (shooters >= 2 && body.getArmorValue() < 6) return Integer.MAX_VALUE;
        return (int) Math.ceil(ticks);
    }

    // ---- stepping in -----------------------------------------------------------------------------

    /**
     * A task that keeps him safe already, or is the way out of something worse: while one is on
     * the stack (a meal in the hole is on top of the hiding), no stepping in over it.
     */
    public static boolean protective(BotTask t) {
        BotTask in = BotTask.innermost(t);
        return t instanceof HideTask || in instanceof HideTask
                || in instanceof net.kasax.challengecraft.bot.task.PillarFightTask
                || in instanceof net.kasax.challengecraft.bot.task.LavaEscapeTask
                || in instanceof net.kasax.challengecraft.bot.task.AirTask
                || in instanceof net.kasax.challengecraft.bot.task.ShieldUpTask
                || in instanceof net.kasax.challengecraft.bot.task.GhastDeflectTask;
    }

    /**
     * When the hearts run out before a fight could be won (or there is none to win): the quickest
     * cover that works here, to be taken now; null when nothing presses, or nothing works.
     */
    public BotTask stepIn(BotTask top) {
        BotPlayer body = bot.body();
        if (!bot.params.on("guardian")) return null;
        if (!body.isAlive() || body.isSpectator() || body.isInLava() || body.isPassenger()) return null;
        if (top != null && protective(top)) return null;
        // (A creeper hissing close by: the reflexes run or raise the shield - a box is no help.)
        for (Creeper c : body.level().getEntitiesOfClass(Creeper.class, body.getBoundingBox().inflate(8), Creeper::isAlive)) {
            if (c.getSwellDir() > 0) return null;
        }
        Reckoning r = reckon();
        if (!r.pressing()) return null;
        // A fight he wins before the hearts run out is his to fight (the reflexes see to it) -
        // unless they run out in under three seconds and the fight takes more than half of that.
        boolean losing = r.fightTicks() >= r.ticksLeft();
        boolean dire = r.ticksLeft() < 60 && r.fightTicks() > r.ticksLeft() / 2;
        if (!losing && !dire) return null;
        // Only once it has begun: a shooter with him in its sights, one at arm's length, or a
        // blow taken just now - not a zombie standing about (the walk goes round it, or the run).
        boolean begun = body.tickCount - body.getLastHurtByMobTimestamp() < 30 && body.getLastHurtByMob() != null;
        for (Threat t : r.threats()) begun |= t.ranged || t.from == 0 && t.mob.getTarget() == body;
        if (!begun) return null;
        // (In the water no cover can be made: the reflexes take him ashore. Falling: once down. A
        // jump in a fight, a step: the cover starts as he lands.)
        if (body.isInWater()) return none(r, "in the water");
        if (!body.onGround() && body.fallDistance > 2) return none(r, "falling");
        boolean shooters = false, climbers = false, jumpers = false, tall = false;
        for (Threat t : r.threats()) {
            shooters |= t.ranged;
            climbers |= t.mob instanceof net.minecraft.world.entity.monster.spider.Spider;
            jumpers |= t.mob instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
            tall |= t.mob instanceof Enderman || t.mob.getBbHeight() > 2.4;
        }
        // The ways out, quickest first. A box of blocks is up in half a second and keeps out
        // everything; a pillar takes a second and a half, only against what strikes close and
        // cannot climb or leap (and nothing shooting: up there he is the easiest target); dug in,
        // as long as the ground takes (against a ghast the best: its blast tears a box of soft
        // blocks open, the hole's lid is three blocks over his head).
        boolean ghast = r.threats().stream().anyMatch(t -> t.mob instanceof Ghast);
        // Nothing but zombies and their kind (their arm reaches no higher than their head): up a
        // pillar, and each one hit from above as it comes to the foot - safe, and the end of them,
        // fists or not. (Unless the hearts go in less than two seconds: the box is quicker.)
        Mob reachless = null;
        boolean onlyReachless = !shooters;
        for (Threat t : r.threats()) {
            if (!net.kasax.challengecraft.bot.task.PillarFightTask.reachless(t.mob)) onlyReachless = false;
            else if (reachless == null || t.mob.distanceToSqr(body) < reachless.distanceToSqr(body)) reachless = t.mob;
        }
        BotTask choice = null;
        String how = null;
        int digTicks = digTicks();
        // (At night with nothing to fight with, the hole is for the night: out in the morning,
        // when the zombies burn - out at midnight, the same ones are waiting.)
        boolean bare = body.getInventory().getNonEquipmentItems().stream()
                .noneMatch(st -> st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES));
        boolean night = bare && body.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && body.level().isDarkOutside();
        if (ghast && digTicks < r.ticksLeft()) {
            choice = new HideTask();
            how = "dug in";
            digs++;
        } else if (onlyReachless && reachless != null && r.ticksLeft() >= 40 && HideTask.pillarPossible(bot)) {
            choice = new net.kasax.challengecraft.bot.task.PillarFightTask(reachless).untilClear();
            how = "up a pillar, and from above";
            pillars++;
        } else if (HideTask.boxPossible(bot, true)) {
            choice = HideTask.boxed();
            how = "walled in";
            boxes++;
        } else if (!shooters && !climbers && !jumpers && !tall && HideTask.pillarPossible(bot)) {
            choice = HideTask.upward();
            how = "up a pillar";
            pillars++;
        } else if (digTicks < Integer.MAX_VALUE) {
            choice = night ? HideTask.shelter() : new HideTask();
            how = night ? "dug in for the night" : "dug in";
            digs++;
        }
        if (choice == null) return none(r, "no cover here (blocks " + net.kasax.challengecraft.bot.BotActions.buildingBlocks(body) + ", ground not to dig)");
        lastActAt = body.tickCount;
        if (body.tickCount - saidAt > 40) {
            saidAt = body.tickCount;
            java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
            for (Threat t : r.threats()) kinds.merge(t.mob.getType().toShortString(), 1, Integer::sum);
            bot.say("guardian: " + kinds + " at me, " + Math.round(body.getHealth()) + " hp - gone in ~"
                    + String.format("%.1f", r.ticksLeft() / 20.0) + " s, a fight "
                    + (r.fightTicks() == Integer.MAX_VALUE ? "not to be won" : "~" + String.format("%.1f", r.fightTicks() / 20.0) + " s")
                    + ": " + how);
        }
        return choice;
    }

    private int noneSaidAt = -1000;

    /** A fight being lost, and no cover taken: why, in the log (once in two seconds) - the reflexes go on. */
    private BotTask none(Reckoning r, String why) {
        BotPlayer body = bot.body();
        if (body.tickCount - noneSaidAt > 40) {
            noneSaidAt = body.tickCount;
            java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
            for (Threat t : r.threats()) kinds.merge(t.mob.getType().toShortString(), 1, Integer::sum);
            BotManager.LOG.info("[Bot] {}: guardian: {} at me, {} hp - gone in ~{} s, no cover: {}", bot.name, kinds,
                    Math.round(body.getHealth()), String.format("%.1f", r.ticksLeft() / 20.0), why);
        }
        return null;
    }

    /** Ticks to dig in right here (three blocks down and the lid), or MAX_VALUE when it cannot be done. */
    private int digTicks() {
        if (!HideTask.possible(bot)) return Integer.MAX_VALUE;
        BotPlayer body = bot.body();
        var feet = body.blockPosition();
        double ticks = 10;
        for (int i = 1; i <= 3; i++) ticks += bot.tools().breakTicks(body.level().getBlockState(feet.below(i))) + 4;
        return (int) Math.ceil(ticks);
    }

    /** When it last stepped in (game ticks of the body), for the reflexes not to undo it at once. */
    public int lastActAt() {
        return lastActAt;
    }
}
