package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Danger as part of the way's price (knob {@code riskpath}, round 3): what a step risks, in ticks of
 * the game, so the path search weighs a safe way round against a short way past a skeleton, along
 * lava, or a long dive - instead of only the guardian stepping in once it is under fire.
 *
 * <p>Everything is priced in seconds-equivalent: a heart lost costs what it takes to win it back
 * (about four seconds of natural healing), more when few are left (closer to dying); a death costs
 * {@link #DEATH_TICKS} (the walk back, the lost kit, the tiles the other side takes meanwhile).
 * Each hazard is a rate of damage or a chance of a mishap:
 * <ul>
 *   <li>monsters seen (the player's view: in sight, not behind the target of a hunt) - by type:
 *   melee ones by their blow and how close the way comes (they close in from about eight
 *   blocks), bowmen within their range in a clear line, a creeper's blast close by; spiders in
 *   daylight hardly at all;</li>
 *   <li>lava beside a step: a slip or a knock-back into it, likelier with a monster near;</li>
 *   <li>the edge of a deep drop: a knock-back over it (fall damage or death);</li>
 *   <li>a leap over a drop: falling short;</li>
 *   <li>a dive: air used along the way (the search carries it from step to step) - past the
 *   budget every tick drowns;</li>
 *   <li>open ground at night: monsters spawn about (a small rate).</li>
 * </ul>
 * The search only adds these to a step's price, so its estimate stays a lower bound (admissible).
 * Work is bounded: at most {@link #MAX_THREATS} monsters, a cell's figure worked out once per
 * search (cached by the pathfinder), a clear-line test only within a bowman's range. Captured on
 * the server thread when the search starts, read by the search worker: nothing here changes after.
 */
public final class BotRisk {
    /** A death, in ticks: two hundred and forty seconds. */
    static final double DEATH_TICKS = 4800;
    /** A heart (2 hp... here one hp) at full health: four seconds of healing. */
    static final double HP_TICKS = 80;
    static final int MAX_THREATS = 16;
    /** Air a body holds (ticks), and what the search keeps in hand for surfacing. */
    static final int AIR = 300, AIR_MARGIN = 100;

    /** A monster as the search sees it: where, how it hurts (hp a second at its worst), how far. */
    record Threat(int x, int y, int z, double dps, double reach, double range, boolean ranged, boolean blast, double eyeY) {
    }

    final Threat[] threats;
    final boolean night;
    /** Ticks a hp lost is worth to this body now. */
    final double hpTicks;
    /** Air used already when the search starts (ticks). */
    final int airUsed;
    private final double hp;

    BotRisk(Threat[] threats, boolean night, double hp, int airUsed) {
        this.threats = threats;
        this.night = night;
        this.hp = hp;
        // (Few hearts left: each one lost is nearer the end - dearer, up to four times.)
        this.hpTicks = HP_TICKS * Math.min(4, 20 / Math.max(5, hp));
        this.airUsed = airUsed;
    }

    /** The same dangers with this much air used already (planning on from the end of a way). */
    BotRisk withAirUsed(int used) {
        return new BotRisk(threats, night, hp, used);
    }

    /** No monsters, daylight, full health and air: for tests of the costs alone. */
    static BotRisk calm() {
        return new BotRisk(new Threat[0], false, 20, 0);
    }

    /**
     * What a player in the body's place would know of the dangers now: the monsters it sees (not
     * those within twelve blocks of {@code target} - that one it means to reach), the time of day,
     * its health and air.
     */
    public static BotRisk capture(BotPlayer body, BlockPos target) {
        ServerLevel level = (ServerLevel) body.level();
        Difficulty diff = level.getDifficulty();
        double armour = body.getArmorValue(), tough = body.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        boolean dark = overworld && level.isDarkOutside();
        java.util.List<Mob> seen = new java.util.ArrayList<>(level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(48, 20, 48),
                m -> m.isAlive() && m instanceof Enemy && (target == null || m.blockPosition().distSqr(target) >= 12 * 12) && BotWorld.seesMob(body, m)));
        // (The nearest first: they matter most, and the cap leaves out the far ones.)
        seen.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(body)));
        java.util.List<Threat> out = new java.util.ArrayList<>();
        for (Mob m : seen) {
            if (out.size() >= MAX_THREATS) break;
            Threat t = threat(m, diff, armour, tough, dark);
            if (t != null) out.add(t);
        }
        int used = body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) ? Math.max(0, AIR - Math.max(0, body.getAirSupply())) : 0;
        return new BotRisk(out.toArray(new Threat[0]), dark, body.getHealth() + body.getAbsorptionAmount(), used);
    }

    private static Threat threat(Mob m, Difficulty diff, double armour, double tough, boolean dark) {
        BlockPos p = m.blockPosition();
        double eye = m.getEyeY();
        if (m instanceof net.minecraft.world.entity.monster.Enderman) return null; // (not unless looked at)
        if (m instanceof Creeper) {
            // A blast: some twenty hearts close by on hard, nothing past six blocks.
            return new Threat(p.getX(), p.getY(), p.getZ(), afterArmour(Guardian.scaled(22, diff), armour, tough) / 1.5, 3, 7, false, true, eye);
        }
        if (Guardian.shooter(m)) {
            double hit, every;
            if (m instanceof net.minecraft.world.entity.monster.Ghast) {
                hit = afterArmour(Guardian.scaled(6, diff), armour, tough) * 0.8;
                every = 60;
            } else if (m instanceof net.minecraft.world.entity.monster.Blaze) {
                hit = afterArmour(Guardian.scaled(5, diff), armour, tough);
                every = 35;
            } else if (m instanceof net.minecraft.world.entity.monster.Witch) {
                hit = 6;
                every = 60;
            } else {
                hit = afterArmour(Guardian.scaled(5, diff), armour, tough) * 0.75;
                every = diff == Difficulty.HARD ? 30 : 45;
            }
            return new Threat(p.getX(), p.getY(), p.getZ(), hit * 20 / every, 0, Guardian.shootingRange(m) * 0.7, true, false, eye);
        }
        double dmg = m.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? m.getAttributeValue(Attributes.ATTACK_DAMAGE) : 2;
        double dps = afterArmour(Guardian.scaled(dmg, diff), armour, tough);
        // (A spider in daylight leaves a player be unless hit.)
        if (m instanceof net.minecraft.world.entity.monster.spider.Spider && !dark) dps *= 0.2;
        return new Threat(p.getX(), p.getY(), p.getZ(), dps, 2.5, 9, false, false, eye);
    }

    private static double afterArmour(double damage, double armour, double toughness) {
        return Guardian.afterArmour(damage, armour, toughness);
    }

    /**
     * The monsters' rate of damage (hp a second) for a body with its feet at {@code (x, y, z)}:
     * full within reach, fading to nothing at the range; a bowman only in a clear line
     * ({@code sight}). Bounded: every threat once, a clear-line test only within range.
     */
    double mobRate(int x, int y, int z, SightTest sight) {
        double rate = 0;
        for (Threat t : threats) {
            int dy = y - t.y;
            if (Math.abs(dy) > (t.ranged ? 12 : 5)) continue;
            double dx = x - t.x, dz = z - t.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= t.range) continue;
            if (t.ranged) {
                if (!sight.clear(t, x, y, z)) continue;
                // (Arrows miss more the farther: most hit close by, a third at the edge of range.)
                rate += t.dps * (1 - 0.65 * d / t.range);
            } else {
                double f = d <= t.reach ? 1 : 1 - (d - t.reach) / (t.range - t.reach);
                rate += t.dps * f * f;
            }
        }
        return rate;
    }

    /** A clear line from a bowman's eye to a body's chest at the feet given. */
    interface SightTest {
        boolean clear(Threat t, int x, int y, int z);
    }

    /** Ticks a step risks with lava at {@code sides} of its sides (a slip, a knock-back). */
    double lavaTicks(int sides, double mobRate) {
        if (sides <= 0) return 0;
        // A quarter percent a side a step; with a monster at him (knock-back) five times that.
        double p = 0.0025 * sides * (mobRate > 0.2 ? 5 : 1);
        return Math.min(1, p) * DEATH_TICKS;
    }

    /** Ticks a step along the edge of a drop of {@code depth} risks (deadly: no ground or lava under it). */
    double edgeTicks(int depth, boolean deadly, double mobRate) {
        double p = 0.002 * (mobRate > 0.2 ? 8 : 1);
        if (deadly) return p * DEATH_TICKS;
        return p * fallTicks(depth);
    }

    /** Ticks a fall of {@code depth} blocks costs (damage beyond three, death beyond the hearts). */
    double fallTicks(int depth) {
        double dmg = Math.max(0, depth - 3);
        return dmg * hpTicks;
    }

    /** Ticks a leap of {@code gap} blocks over a drop of {@code depth} risks: falling short. */
    double leapTicks(int gap, int depth, double mobRate) {
        if (depth <= 3) return 0;
        double p = 0.04 * gap * (mobRate > 0.2 ? 2 : 1);
        return p * fallTicks(depth + 1);
    }

    /** Ticks a stretch of {@code ticks} on open ground at night risks: monsters spawn about. */
    double nightTicks(double ticks) {
        return night ? ticks / 20 * 0.02 * hpTicks : 0;
    }

    /** Air used after a step of {@code ticks} with the head under water ({@code under}) or not. */
    static int airAfter(int used, double ticks, boolean under) {
        if (under) return used + (int) Math.ceil(ticks);
        // (Out of the water a body breathes in four ticks of air a tick.)
        return Math.max(0, used - (int) (ticks * 4));
    }

    /** Ticks the air used past the budget costs over a step from {@code before} to {@code after}. */
    double drownTicks(int before, int after) {
        int limit = AIR - AIR_MARGIN;
        int over = Math.max(0, after - limit) - Math.max(0, before - limit);
        if (over <= 0) return 0;
        // Two hp a second once it is gone (and the margin is for surfacing, so priced as if gone).
        double ticks = over * 0.1 * hpTicks * 2;
        if (after > AIR + 20 * 6) ticks += DEATH_TICKS * 0.5;
        return ticks;
    }
}
