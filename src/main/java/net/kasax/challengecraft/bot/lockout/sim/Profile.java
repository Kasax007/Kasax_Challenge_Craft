package net.kasax.challengecraft.bot.lockout.sim;

import java.util.Arrays;

/**
 * How a player plays, apart from which tile it picks: its pace at each kind of work, its dangers,
 * and the chores it does besides the board (Bob's food stock, shield, copper armour, bundle, the
 * night dug in). Two are built in:
 * <ul>
 * <li>{@link #bob()}: Bob as measured in the self-play leagues of autumn 2026 (see
 * {@code docs/bob-plan.md} 24-26 and {@link SimBench} for the calibration);</li>
 * <li>{@link #expert()}: a strong Lockout player (first tile within a minute, ten and more in
 * twenty minutes), to see whether a plan that helps Bob also holds at a faster pace.</li>
 * </ul>
 * {@link #naive()} is what Bob's own estimates assume of him: every step at a good player's pace.
 * The difference between that and {@link #bob()} is why his estimates were four to nine times
 * too low for monsters.
 */
public final class Profile {
    public final String name;
    /** Blocks per second over land, and in the Nether. */
    public double speed, netherSpeed;
    /** Seconds down from the surface to the caves, from the caves to the deep (deepslate), and per layer back up. */
    public double caveDown, deepDown, layerUp;
    /** Own work: this many times a good player's seconds, per kind of work. */
    public final double[] pace = new double[Econ.Work.values().length];
    /** Finding the first of a resource, and each one after it. */
    public double accessPace, perPace;
    /** Walking to a place not known yet: this many times its straight distance (looking about). */
    public double explore;
    /**
     * The chance that an attempt at a goal fails for reasons the plan does not see (no way through,
     * the search comes to nothing, the tool breaks), per kind of its own work: the goal is dropped
     * part of the way and rested. Bob's journal: 58 % of his tiles set out on were not got.
     */
    public final double[] fail = new double[Econ.Work.values().length];
    /** Deaths per minute: anywhere, on the surface at night, in caves, in the Nether; fights' hazard times this. */
    public double hzBase, hzNight, hzCave, hzNether, hzFight;
    /** What a shield or armour leaves of the danger (with {@link #armourGear}: the shield's share alone). */
    public double gear;
    /** What armour (copper or iron) leaves of the danger on top of the shield's share; 0: one or the other counts, as {@link #gear}. */
    public double armourGear;
    /** Seconds to respawn and get going; the chance to get the things back after a death. */
    public double respawn, recover;
    /** Food stock: at the start, the mark it is topped up at, by how much; seconds per unit eaten. */
    public int foodStart, foodLow, foodBatch;
    public double foodEvery;
    /** Bob's habits on hard: a shield first, copper armour, a bundle when the pack fills, the night dug in when not ready. */
    public boolean shieldFirst, copper, bundle, nightShelter;
    public double bundleAfter;
    /** Spare cobblestone and wood after the stone tools (seconds). */
    public double openingExtra;
    /** A tile this cheap from where things stand (seconds, by the estimate) is taken before anything else; 0: off. */
    public double quick;
    public boolean worldsense = true;
    /** Seconds a full belly lasts at the start (the first unit of food eaten then). */
    public double fedFor = 240;
    /**
     * The real brain's {@code interleave} (round 1): a dear investment (the shield, copper, the
     * iron kit dearer than {@link #investCheap} by its own figures) waits while a tile of at most
     * {@link #tileFirst} seconds is open - until {@link #investBy} seconds into the day or
     * {@link #investAfterTiles} tiles.
     */
    public boolean interleave;
    public double investCheap = 150, tileFirst = 120, investBy = 400;
    public int investAfterTiles = 2;
    /** The bundle only while it costs at most this by its own figures (the string and the leather about). */
    public double bundleMax = 120;
    /**
     * Steps begun on the surface at night take this many times as long: the fights, the box, the
     * hole while the monsters pass (Bob: about a minute a night in league 9).
     */
    public double nightSlow = 1;
    /** Each goal's own failure chance where the leagues measured one ({@link Recipe#fail}); else only its kind's. */
    public boolean goalFails;
    /** Tiles met by the way at night and in caves ({@link Recipe#chance}). */
    public boolean chances;
    /**
     * For testing a brain against a world its figures do not know: each goal's own work is this
     * much off its recipe in every game (log-normal spread, drawn per game and goal) - and no
     * plan knows by how much ({@code Sim.goalMult}). The leagues' estimates were off by factors
     * of two to eight for single goals.
     */
    public double goalNoise;
    /** Only the goals Bob knows a way to ({@link Recipe#bob}); a human player knows them all. */
    public boolean bobGoals;
    /**
     * Plans only with what has been seen: a portal is cast at a lava pool in sight, none planned at
     * one still to be found (Bob's own figures: LockoutGoals.castPortal).
     */
    public boolean seenOnly;

    private Profile(String name) {
        this.name = name;
        Arrays.fill(pace, 1);
    }

    public Profile copy(String newName) {
        Profile p = new Profile(newName);
        p.speed = speed;
        p.netherSpeed = netherSpeed;
        p.caveDown = caveDown;
        p.deepDown = deepDown;
        p.layerUp = layerUp;
        System.arraycopy(pace, 0, p.pace, 0, pace.length);
        System.arraycopy(fail, 0, p.fail, 0, fail.length);
        p.accessPace = accessPace;
        p.perPace = perPace;
        p.explore = explore;
        p.hzBase = hzBase;
        p.hzNight = hzNight;
        p.hzCave = hzCave;
        p.hzNether = hzNether;
        p.hzFight = hzFight;
        p.gear = gear;
        p.armourGear = armourGear;
        p.respawn = respawn;
        p.recover = recover;
        p.foodStart = foodStart;
        p.foodLow = foodLow;
        p.foodBatch = foodBatch;
        p.foodEvery = foodEvery;
        p.shieldFirst = shieldFirst;
        p.copper = copper;
        p.bundle = bundle;
        p.nightShelter = nightShelter;
        p.bundleAfter = bundleAfter;
        p.openingExtra = openingExtra;
        p.quick = quick;
        p.worldsense = worldsense;
        p.fedFor = fedFor;
        p.interleave = interleave;
        p.investCheap = investCheap;
        p.tileFirst = tileFirst;
        p.investBy = investBy;
        p.investAfterTiles = investAfterTiles;
        p.bundleMax = bundleMax;
        p.nightSlow = nightSlow;
        p.ownTruth = ownTruth;
        p.goalFails = goalFails;
        p.chances = chances;
        p.goalNoise = goalNoise;
        p.bobGoals = bobGoals;
        p.seenOnly = seenOnly;
        return p;
    }

    /**
     * How near the player's own figures are to what things really take it: 0, every step at a
     * good player's pace (Bob before round 1: monsters four to nine times his estimate); 1, its
     * true paces. Since round 1's knowledge (batches, mob searches by the spawn rules) Bob's own
     * estimate is in the median 0.94 times the simulator's true figure (league 10).
     */
    public double ownTruth;

    /** The same player at the pace its own plan figures assume ({@link #ownTruth} of the way from a good player's to its own). */
    public Profile naive() {
        Profile p = copy(name + "-naive");
        p.seenOnly = bobGoals;
        for (int k = 0; k < p.pace.length; k++) p.pace[k] = Math.pow(pace[k], ownTruth);
        p.accessPace = Math.pow(accessPace, ownTruth);
        p.perPace = Math.pow(perPace, ownTruth);
        return p;
    }

    /** A key for what this profile plays like (for models learnt per profile). */
    public String hashKey() {
        return Arrays.toString(pace) + Arrays.toString(fail) + speed + "/" + accessPace + "/" + perPace + "/" + hzBase + "/" + hzFight
                + "/" + foodEvery + "/" + shieldFirst + copper + bundle + nightShelter + bobGoals + "/" + fedFor + interleave + bundleMax + nightSlow + "/" + ownTruth + goalFails + chances + "/" + goalNoise + "/" + gear + "/" + armourGear;
    }

    public double pace(Econ.Work w) {
        return pace[w.ordinal()];
    }

    /** Seconds from one layer to another (0 surface, 1 caves, 2 deep). */
    public double layers(int from, int to) {
        if (to < from) return (from - to) * layerUp;
        double s = 0;
        if (from < 1 && to >= 1) s += caveDown;
        if (from < 2 && to >= 2) s += deepDown;
        return s;
    }

    /**
     * Bob as measured in leagues 9 and 10 (9./10.11.2026: round 1's knowledge, estimates and
     * opening; SimBench "calibrate --leagues ... --params -"): his tiles per game (2.91 against the
     * leagues' 2.86), first tile (median 292 s against 265), deaths (0.28 against 0.28); the claim
     * rates per goal correlate at 0.78 (177 goals). On top of {@link #bob1()}: no food stock at the
     * start (the stone tools first), the dear investments after the quick tiles (interleave), the
     * bundle only when cheap, a slower night on the surface, his own estimates near the truth (they
     * are since round 1), each goal's own failure chance from the journals, the monsters met by the
     * way, the staircase to the deepslate at the leagues' pace, shield and armour each a share.
     */
    public static Profile bob() {
        Profile p = bob1();
        p.foodStart = 0;
        p.fedFor = 300;
        p.foodEvery = 260;
        p.interleave = true;
        p.investCheap = 100;
        p.bundleMax = 40;
        p.nightSlow = 1.3;
        p.ownTruth = 0.9;
        p.openingExtra = 70;
        p.hzBase = 0.006;
        p.hzNight = 0.02;
        p.hzCave = 0.016;
        p.goalFails = true;
        p.chances = true;
        p.deepDown = 130;
        p.pace[Econ.Work.GATHER.ordinal()] = 2.2;
        p.gear = 0.7;
        p.armourGear = 0.75;
        return p;
    }

    /** Bob as calibrated in round 1 against leagues 1 to 8 (the code before round 1's knowledge and opening). */
    public static Profile bob1() {
        Profile p = new Profile("bob");
        p.speed = 3.0;
        p.netherSpeed = 2.2;
        p.caveDown = 55;
        p.deepDown = 90;
        p.layerUp = 25;
        p.pace[Econ.Work.CRAFT.ordinal()] = 2.2;
        p.pace[Econ.Work.GATHER.ordinal()] = 2.0;
        p.pace[Econ.Work.ANIMAL.ordinal()] = 3.0;
        p.pace[Econ.Work.MONSTER.ordinal()] = 3.0;
        p.pace[Econ.Work.EXPLORE.ordinal()] = 1.2;
        p.pace[Econ.Work.NETHER.ordinal()] = 2.5;
        p.pace[Econ.Work.TRADE.ordinal()] = 2.0;
        p.pace[Econ.Work.STUNT.ordinal()] = 2.0;
        p.accessPace = 1.6;
        p.perPace = 1.3;
        p.fail[Econ.Work.CRAFT.ordinal()] = 0.2;
        p.fail[Econ.Work.GATHER.ordinal()] = 0.35;
        p.fail[Econ.Work.ANIMAL.ordinal()] = 0.4;
        p.fail[Econ.Work.MONSTER.ordinal()] = 0.3;
        p.fail[Econ.Work.EXPLORE.ordinal()] = 0.2;
        p.fail[Econ.Work.NETHER.ordinal()] = 0.4;
        p.fail[Econ.Work.TRADE.ordinal()] = 0.4;
        p.fail[Econ.Work.STUNT.ordinal()] = 0.3;
        p.explore = 1.8;
        p.hzBase = 0.004;
        p.hzNight = 0.015;
        p.hzCave = 0.012;
        p.hzNether = 0.03;
        p.hzFight = 1.0;
        p.gear = 0.6;
        p.respawn = 15;
        p.recover = 0.4;
        p.foodStart = 2;
        p.foodLow = 1;
        p.foodBatch = 3;
        p.foodEvery = 200;
        p.shieldFirst = true;
        p.copper = true;
        p.bundle = true;
        p.bundleAfter = 240;
        p.nightShelter = true;
        p.openingExtra = 40;
        p.quick = 30;
        p.bobGoals = true;
        return p;
    }

    /** A strong Lockout player: no chores beyond a bite to eat, fast at everything, rarely dies. */
    public static Profile expert() {
        Profile p = new Profile("expert");
        p.speed = 5.0;
        p.netherSpeed = 3.6;
        p.caveDown = 20;
        p.deepDown = 30;
        p.layerUp = 12;
        p.accessPace = 1;
        p.perPace = 1;
        p.explore = 1.4;
        java.util.Arrays.fill(p.fail, 0.03);
        p.hzBase = 0.0005;
        p.hzNight = 0.002;
        p.hzCave = 0.002;
        p.hzNether = 0.004;
        p.hzFight = 0.3;
        p.gear = 0.6;
        p.respawn = 10;
        p.recover = 0.8;
        // (Eats what it meets on the way: no stock to keep.)
        p.foodStart = 0;
        p.foodLow = 0;
        p.foodBatch = 3;
        p.foodEvery = 0;
        p.quick = 30;
        return p;
    }

    /**
     * Changes knobs by name, for calibrating: {@code "speed=3.2,pace.MONSTER=3.5,fail.GATHER=0.3"}
     * (any public number or flag of this class; {@code pace.X} and {@code fail.X} per kind of work).
     */
    public Profile tune(String spec) {
        if (spec == null || spec.isBlank()) return this;
        for (String part : spec.split(",")) {
            String[] kv = part.split("=", 2);
            String key = kv[0].trim();
            double v = Double.parseDouble(kv[1].trim());
            try {
                if (key.startsWith("pace.")) pace[Econ.Work.valueOf(key.substring(5)).ordinal()] = v;
                else if (key.startsWith("fail.")) fail[Econ.Work.valueOf(key.substring(5)).ordinal()] = v;
                else {
                    var f = Profile.class.getField(key);
                    if (f.getType() == double.class) f.setDouble(this, v);
                    else if (f.getType() == int.class) f.setInt(this, (int) v);
                    else if (f.getType() == boolean.class) f.setBoolean(this, v != 0);
                    else throw new IllegalArgumentException(key);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("no knob " + key, e);
            }
        }
        return this;
    }

    public static Profile named(String name) {
        return switch (name) {
            case "bob" -> bob();
            case "bob1" -> bob1();
            case "expert" -> expert();
            default -> throw new IllegalArgumentException("no profile " + name + " (bob, bob1, expert)");
        };
    }
}
