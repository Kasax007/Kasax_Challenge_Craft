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
    public int boxes, pillars, digs, runs, fights;
    /** When it last sent him running (no cover to be had in time, room to run): the reflexes take him off. */
    private int runAt = -1000;

    /** Whether it wants him away from what is after him, this tick (see {@link #stepIn}). */
    public boolean wantsRun() {
        return bot.body().tickCount - runAt <= 2;
    }

    /** The middle of what is after him. */
    private static net.minecraft.world.phys.Vec3 centre(Reckoning r) {
        net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.ZERO;
        for (Threat t : r.threats()) c = c.add(t.mob.position());
        return r.threats().isEmpty() ? c : c.scale(1.0 / r.threats().size());
    }
    private int saidAt = -1000, lastActAt = -1000;

    Guardian(Bot bot) {
        this.bot = bot;
    }

    /**
     * A new body (after a death): its ticks start again at nought, so every mark kept in the old
     * body's ticks goes. (Kept, "it wants him away" stayed true for the new body until its clock
     * passed the old one's: in a game on the user's laptop Bob ran from every lone zombie and
     * from the very creeper he was hunting for its powder, for an hour after his first death.)
     */
    void newBody() {
        runAt = -1000;
        saidAt = -1000;
        lastActAt = -1000;
        noneSaidAt = -1000;
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

    /** A shooter whose arrows (or bolts, or trident) a raised shield stops: a skeleton, a pillager - not a witch, a ghast, a blaze. */
    static boolean bowman(Threat t) {
        return t.ranged && t.mob instanceof RangedAttackMob && t.mob instanceof Enemy && !(t.mob instanceof Witch);
    }

    /** Walls round the feet and the head, and a lid: nothing that walks gets at him. */
    static boolean enclosed(BotPlayer body) {
        var level = body.level();
        var feet = body.blockPosition();
        if (!HideTask.shut(level, feet.above(2))) return false;
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (!HideTask.shut(level, feet.relative(d)) || !HideTask.shut(level, feet.above().relative(d))) return false;
        }
        return true;
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
        // Shut in - a box round him, a hole with a lid: what only strikes close cannot get at him.
        // (Two zombies at a sealed box were "at him" all the same, and walled him in again and
        // again: fourteen times in a self-play game, five minutes of it.)
        boolean shutIn = enclosed(body);
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
            if (m instanceof Creeper || shutIn) continue;
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
        // A shield on the arm and nothing but bowmen after him, all to one side: the threat sense
        // keeps it up towards them on his way, and most of the arrows end in it - as good as
        // raised. (In a self-play game a lone skeleton twelve blocks off, a ledge higher, "was not
        // to be fought": walled in, the box would not shut, five arrows through the gap - and the
        // shield on the arm, down.)
        if (!body.isBlocking() && !threats.isEmpty() && threats.stream().allMatch(Guardian::bowman) && bot.threats.shieldWalkOpen()) {
            Mob nearest = null;
            for (Threat t : threats) if (nearest == null || t.mob.distanceToSqr(body) < nearest.distanceToSqr(body)) nearest = t.mob;
            var to = nearest.position().subtract(body.position()).multiply(1, 0, 1);
            boolean oneSide = to.lengthSqr() >= 16;
            for (Threat t : threats) {
                var d = t.mob.position().subtract(body.position()).multiply(1, 0, 1);
                oneSide &= d.lengthSqr() > 0.01 && d.normalize().dot(to.normalize()) > 0.2;
            }
            if (oneSide) threats.replaceAll(t -> new Threat(t.mob, t.perHit * 0.3, t.every, t.from, true));
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
        // Something already standing in the sides of the box (a wolf at his heels, a zombie, a
        // magma cube): the blocks will not go in there, and it strikes through the gap while he
        // tries - three bites through the gaps of a box killed a self-play game, four zombies in
        // its sides another, a test brawl lost all but a heart to magma cubes that way. No box
        // then: a pillar, where that works, else away from them while there is room to run.
        boolean boxTaken = HideTask.boxSidesTaken(bot) > 0;
        int digTicks = digTicks();
        // (At night with nothing to fight with, the hole is for the night: out in the morning,
        // when the zombies burn - out at midnight, the same ones are waiting.)
        boolean bare = body.getInventory().getNonEquipmentItems().stream()
                .noneMatch(st -> st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES));
        boolean dark = body.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && body.level().isDarkOutside();
        boolean night = bare && dark;
        boolean boxPossible = HideTask.boxPossible(bot, true), pillarPossible = HideTask.pillarPossible(bot);
        boolean roomToRun = bot.roomToRun(centre(r), 8);
        // The rules' way out.
        net.kasax.challengecraft.bot.learn.CoverOption rule;
        String ruleWhy = null;
        if (ghast && digTicks < r.ticksLeft()) {
            rule = net.kasax.challengecraft.bot.learn.CoverOption.DIG;
        } else if (onlyReachless && reachless != null && r.ticksLeft() >= 40 && pillarPossible) {
            rule = net.kasax.challengecraft.bot.learn.CoverOption.PILLAR_FIGHT;
        } else if (crossbows(r) >= 2 && roomToRun) {
            // Pillagers: where two shoot, more come (an outpost spawns them for as long as one
            // stands in it, a patrol is five) - and the box is no cover from bolts out of every
            // side: boxed in four times at an outpost on the user's laptop, the last time shot
            // dead in it through the shield. Away, out of their range: they do not follow far.
            rule = net.kasax.challengecraft.bot.learn.CoverOption.RUN;
            ruleWhy = "pillagers keep coming: away out of their range, not a box";
        } else if (boxPossible && !boxTaken) {
            rule = net.kasax.challengecraft.bot.learn.CoverOption.BOX;
        } else if (!shooters && !climbers && !jumpers && !tall && pillarPossible) {
            rule = net.kasax.challengecraft.bot.learn.CoverOption.PILLAR;
        } else if (boxTaken && boxPossible && !climbers && !tall && roomToRun) {
            // (Not from a spider, nor from an enderman: both are quicker than he is - two
            // spiders bit him six times on a run in a game on the user's laptop, and an
            // enderman after him closes any gap in a blink.)
            // (The reflexes run: the same way off as from a crowd, the way it came if it can.
            // Only instead of the box it would have built: with no blocks for one, the fight goes
            // on as before - two zombies chased round a test arena took two thirds of the hearts.)
            rule = net.kasax.challengecraft.bot.learn.CoverOption.RUN;
            ruleWhy = "something in the sides of the box: away from them instead";
        } else if ((climbers || tall) && !shooters && armed(body) && body.getHealth() >= 8) {
            // A spider or an enderman at him, armed, with the hearts for it: the fight, toe to toe
            // and behind the shield - a hole is dug too slowly with a spider at the rim (bitten
            // through the digging, dead in it), and there is no running from either.
            rule = net.kasax.challengecraft.bot.learn.CoverOption.FIGHT;
            ruleWhy = "too quick to run from, too close to dig in front of: standing to fight";
        } else if (digTicks < Integer.MAX_VALUE) {
            rule = night ? net.kasax.challengecraft.bot.learn.CoverOption.SHELTER : net.kasax.challengecraft.bot.learn.CoverOption.DIG;
        } else {
            rule = net.kasax.challengecraft.bot.learn.CoverOption.FIGHT;
            ruleWhy = "no cover here (box: " + (boxTaken ? "something in its sides" : HideTask.boxProblem(bot, true)) + "; ground not to dig)";
        }
        // The ways that can be taken here at all (for the learnt choice, and for the journal).
        var possible = java.util.EnumSet.of(net.kasax.challengecraft.bot.learn.CoverOption.FIGHT);
        if (boxPossible && !boxTaken) possible.add(net.kasax.challengecraft.bot.learn.CoverOption.BOX);
        if (pillarPossible) possible.add(net.kasax.challengecraft.bot.learn.CoverOption.PILLAR);
        if (pillarPossible && reachless != null) possible.add(net.kasax.challengecraft.bot.learn.CoverOption.PILLAR_FIGHT);
        if (roomToRun) possible.add(net.kasax.challengecraft.bot.learn.CoverOption.RUN);
        if (digTicks < Integer.MAX_VALUE) possible.add(net.kasax.challengecraft.bot.learn.CoverOption.DIG);
        if (digTicks < Integer.MAX_VALUE && dark) possible.add(net.kasax.challengecraft.bot.learn.CoverOption.SHELTER);
        possible.add(rule);
        // Which one: the rules, the learnt model, or (in the arena) a random one - and kept to
        // for the rest of the fight once chosen otherwise than by the rules.
        net.kasax.challengecraft.bot.learn.CoverOption pick;
        String how;
        if (episode != null && episode.committed != null && possible.contains(episode.committed)) {
            pick = episode.committed;
            how = episode.how;
        } else {
            double[] x = net.kasax.challengecraft.bot.learn.Situation.cover(bot, r, boxPossible, boxTaken, pillarPossible, digTicks, roomToRun);
            var choice = bot.cover.choose(bot, x, possible, rule);
            // (The model may not stand to fight a fight the reckoning says is lost before it is won,
            // when the rules know a way out: one bad guess there is a death.)
            if (choice.how().equals("model") && choice.option() == net.kasax.challengecraft.bot.learn.CoverOption.FIGHT
                    && rule != net.kasax.challengecraft.bot.learn.CoverOption.FIGHT && r.fightTicks() >= r.ticksLeft())
                choice = new net.kasax.challengecraft.bot.learn.CoverPolicy.Choice(rule, "rule", choice.predicted());
            pick = choice.option();
            how = choice.how();
            if (episode == null) episode = new Episode(bot, r, x, possible, rule, choice);
            else if (!episode.path.get(episode.path.size() - 1).equals(pick.name())) episode.path.add(pick.name());
            if (!how.equals("rule")) episode.committed = pick;
        }
        if (episode != null) episode.decisions++;
        if (pick != rule) ruleWhy = null;
        return take(r, pick, how, reachless, ruleWhy);
    }

    /** The way out chosen, set going: the task for it (a cover), or none (the reflexes fight on, or run). */
    private BotTask take(Reckoning r, net.kasax.challengecraft.bot.learn.CoverOption pick, String how, Mob reachless, String why) {
        BotPlayer body = bot.body();
        String by = how.equals("rule") ? "" : " (" + how + ")";
        BotTask choice;
        String said;
        switch (pick) {
            case DIG -> {
                choice = new HideTask();
                said = "dug in";
                digs++;
            }
            case SHELTER -> {
                choice = HideTask.shelter();
                said = "dug in for the night";
                digs++;
            }
            case PILLAR_FIGHT -> {
                choice = new net.kasax.challengecraft.bot.task.PillarFightTask(reachless).untilClear();
                said = "up a pillar, and from above";
                pillars++;
            }
            case BOX -> {
                choice = HideTask.boxed();
                said = "walled in";
                boxes++;
            }
            case PILLAR -> {
                choice = HideTask.upward();
                said = "up a pillar";
                pillars++;
            }
            case RUN -> {
                runAt = body.tickCount;
                runs++;
                return none(r, (why != null ? why : "away from them") + by);
            }
            default -> {
                if (why == null || !why.startsWith("no cover")) fights++;
                return none(r, (why != null ? why : "standing to fight") + by);
            }
        }
        lastActAt = body.tickCount;
        if (body.tickCount - saidAt > 40) {
            saidAt = body.tickCount;
            java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
            for (Threat t : r.threats()) kinds.merge(t.mob.getType().toShortString(), 1, Integer::sum);
            bot.say("guardian: " + kinds + " at me, " + Math.round(body.getHealth()) + " hp - gone in ~"
                    + String.format("%.1f", r.ticksLeft() / 20.0) + " s, a fight "
                    + (r.fightTicks() == Integer.MAX_VALUE ? "not to be won" : "~" + String.format("%.1f", r.fightTicks() / 20.0) + " s")
                    + ": " + said + by);
        }
        return choice;
    }

    // ---- the journal: each fight gone badly, the way chosen and how it ended ------------------

    /**
     * One fight gone badly: the situation when the guardian first stepped in, what could be done,
     * what the rules would have done and what was done, and - once it is over (they are dead or
     * gone, half a minute passed, or he died) - how it ended.
     */
    private static final class Episode {
        final String id = net.kasax.challengecraft.bot.learn.Journal.newId();
        final long start;
        final double[] x;
        final java.util.Set<net.kasax.challengecraft.bot.learn.CoverOption> possible;
        final net.kasax.challengecraft.bot.learn.CoverOption rule, first;
        final String how;
        final double[][] predicted;
        final float hp0;
        float minHp;
        final int damage0;
        final List<Mob> mobs = new ArrayList<>();
        final List<String> path = new ArrayList<>();
        net.kasax.challengecraft.bot.learn.CoverOption committed;
        int decisions;
        long calmSince = -1;

        Episode(Bot bot, Reckoning r, double[] x, java.util.Set<net.kasax.challengecraft.bot.learn.CoverOption> possible,
                net.kasax.challengecraft.bot.learn.CoverOption rule, net.kasax.challengecraft.bot.learn.CoverPolicy.Choice choice) {
            BotPlayer body = bot.body();
            this.start = body.level().getGameTime();
            this.x = x;
            this.possible = java.util.EnumSet.copyOf(possible);
            this.rule = rule;
            this.first = choice.option();
            this.how = choice.how();
            this.predicted = choice.predicted();
            this.hp0 = body.getHealth();
            this.minHp = hp0;
            this.damage0 = body.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DAMAGE_TAKEN));
            for (Threat t : r.threats()) mobs.add(t.mob);
            path.add(first.name());
        }
    }

    private Episode episode;
    /** Extra context for the journal (the arena's scenario), or null. */
    public com.google.gson.JsonObject context;
    /** Called when a fight's record is written (the arena counts its episodes by it). */
    public java.util.function.Consumer<com.google.gson.JsonObject> onEpisode;

    /** Whether a fight gone badly is being followed (from the first stepping in to its end). */
    public boolean inEpisode() {
        return episode != null;
    }

    /** Each tick: the lowest the hearts went, and whether the fight is over. */
    public void track() {
        Episode e = episode;
        if (e == null) return;
        BotPlayer body = bot.body();
        e.minHp = Math.min(e.minHp, body.getHealth());
        long now = body.level().getGameTime();
        if (body.tickCount % 10 != 0) return;
        boolean after = false;
        for (Mob m : e.mobs) {
            if (m.isAlive() && !m.isRemoved() && m.level() == body.level() && m.distanceToSqr(body) < 24 * 24 && m.getTarget() == body) after = true;
        }
        if (after) e.calmSince = -1;
        else if (e.calmSince < 0) e.calmSince = now;
        if (e.calmSince >= 0 && now - e.calmSince >= 60) finish("calm");
        else if (now - e.start > 600) finish("timeout");
    }

    /** Ends the fight's record now (the arena's fight is over), if one is open. */
    public void endEpisode(String why) {
        if (episode != null) finish(why);
    }

    /** He died: the fight's record ends with that. */
    public void died() {
        if (episode != null) finish("died");
    }

    private void finish(String end) {
        Episode e = episode;
        episode = null;
        if (e == null) return;
        BotPlayer body = bot.body();
        long now = body.level().getGameTime();
        boolean died = end.equals("died");
        int kills = 0;
        for (Mob m : e.mobs) if (!m.isAlive()) kills++;
        var o = new com.google.gson.JsonObject();
        o.addProperty("id", e.id);
        o.addProperty("bot", bot.name);
        o.addProperty("how", e.how);
        o.addProperty("option", e.first.name());
        o.addProperty("rule", e.rule.name());
        o.add("possible", net.kasax.challengecraft.bot.learn.Journal.array(e.possible.stream().map(Enum::name).toList()));
        o.add("path", net.kasax.challengecraft.bot.learn.Journal.array(e.path));
        o.addProperty("decisions", e.decisions);
        o.add("x", net.kasax.challengecraft.bot.learn.Journal.array(e.x));
        if (e.predicted != null) {
            var pred = new com.google.gson.JsonObject();
            for (var opt : net.kasax.challengecraft.bot.learn.CoverOption.ALL) {
                if (e.predicted[opt.ordinal()] != null) pred.add(opt.name(), net.kasax.challengecraft.bot.learn.Journal.array(e.predicted[opt.ordinal()]));
            }
            o.add("predicted", pred);
        }
        o.addProperty("end", end);
        o.addProperty("died", died ? 1 : 0);
        o.addProperty("hp0", e.hp0);
        o.addProperty("hp_end", died ? 0 : body.getHealth());
        o.addProperty("hp_lost", died ? e.hp0 : Math.max(0, e.hp0 - e.minHp));
        int damage = body.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DAMAGE_TAKEN)) - e.damage0;
        o.addProperty("damage", damage / 10.0);
        o.addProperty("secs", (now - e.start) / 20.0);
        o.addProperty("kills", kills);
        o.addProperty("mobs", e.mobs.size());
        o.addProperty("difficulty", body.level().getDifficulty().getSerializedName());
        o.addProperty("dim", body.level().dimension().identifier().getPath());
        if (context != null) o.add("ctx", context.deepCopy());
        net.kasax.challengecraft.bot.learn.Journal.write("cover", o);
        if (onEpisode != null) onEpisode.accept(o);
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

    /** How many of the threats are illagers with a crossbow (pillagers). */
    private static int crossbows(Reckoning r) {
        int n = 0;
        for (Threat t : r.threats()) if (t.ranged && t.mob instanceof net.minecraft.world.entity.monster.illager.Pillager) n++;
        return n;
    }

    /** A sword or an axe in the hand or the pack. */
    public static boolean armed(BotPlayer body) {
        if (body.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS) || body.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)) return true;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) return true;
        }
        return false;
    }

    /** Ticks to dig in right here (three blocks down and the lid), or MAX_VALUE when it cannot be done. */
    private int digTicks() {
        if (!HideTask.possible(bot)) return Integer.MAX_VALUE;
        BotPlayer body = bot.body();
        var feet = HideTask.standing(bot);
        double ticks = 10;
        for (int i = 1; i <= 3; i++) ticks += bot.tools().breakTicks(body.level().getBlockState(feet.below(i))) + 4;
        return (int) Math.ceil(ticks);
    }

    /** When it last stepped in (game ticks of the body), for the reflexes not to undo it at once. */
    public int lastActAt() {
        return lastActAt;
    }
}
