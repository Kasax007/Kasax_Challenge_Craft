package net.kasax.challengecraft.daily;

import net.kasax.challengecraft.ChallengeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The hand-maintained daily rotation.
 *
 * <p>Deliberately a Java constant rather than a JSON resource: the challenge ids are then checked by
 * the compiler, the IDE completes them, and there is no load path that can fail at runtime. The list
 * is not meant to be overridable by players — it is curated.
 *
 * <p>The rotation wraps automatically. {@link DailyManager} takes the day number modulo this list's
 * size, so appending an entry extends the cycle and nothing else has to change.
 *
 * <h2>Rules for editing this list</h2>
 * <ul>
 *   <li><b>No conflicting pairs.</b> {@link #validate()} runs at server start and refuses to stay
 *       quiet about a bad entry — but check {@link ChallengeManager#hasConflict} while editing
 *       rather than waiting for the boot log.</li>
 *   <li><b>No open-ended collection challenges</b> (22 All Items, 23 All Entities, 26 All
 *       Achievements). A daily is meant to be finishable in one sitting; those take days.</li>
 *   <li><b>No lobby minigames</b> (40 Lockout Bingo, 45 Force Item Battle). They need two players
 *       and an explicit start, which a solo daily cannot provide.</li>
 *   <li>Unlock levels are irrelevant here. A daily deliberately hands the player challenges they
 *       have not levelled to yet — that is the point of it.</li>
 * </ul>
 */
public final class DailyChallenges {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Daily");

    /** Ids that must never appear in a daily, with the reason, used by {@link #validate()}. */
    private static final List<int[]> FORBIDDEN = List.of(
            new int[]{22}, new int[]{23}, new int[]{26}, new int[]{49},   // open-ended collections
            new int[]{40}, new int[]{45}                    // lobby minigames
    );

    /**
     * The rotation. Every seed here was measured against this version's worldgen by
     * {@link SeedProbe} — the figures in the comments are what it reported, not what a seed list on
     * the web claims. Those lists are almost all written for older versions and copied forward, and
     * worldgen changes between versions, so their claims do not transfer. A daily seed reaches every
     * player and cannot be corrected afterwards.
     *
     * <p>Re-measured on 26.3 (2026-09-26): spawn biome, height range, water share and every structure
     * distance quoted below are unchanged from 26.2. Five seeds now also see a dappled forest in
     * their sample, so a biome count went up by one where one is quoted.
     *
     * <p>The seed is chosen to argue with the ruleset rather than to be pretty: a village you may
     * not trade with, a mountain when you may only go down, a desert when you have no crafting
     * table. That is where a daily gets its character from.
     */
    private static final List<DailyEntry> ENTRIES = List.of(
            // Meadow, height 22–242 and only 12 % water — the biggest vertical range measured, and
            // hardly any ocean to interrupt it. Going only downwards needs somewhere to fall from.
            DailyEntry.of("challengecraft.daily.only_down", 43)
                    .withSeed(69420L),
            // Birch forest with a trial chamber AND trail ruins both 72 blocks out. When the die
            // decides how far you may walk, everything worth reaching has to be close.
            DailyEntry.of("challengecraft.daily.dice_luck", 46, 5)
                    .withSeed(777L),
            // Forest, plains village 200 blocks away. With no drops and no chest loot at all, that
            // village is the only source of anything in the world.
            DailyEntry.of("challengecraft.daily.empty_world", 2, 3, 4)
                    .withSeed(3000L),
            // Savanna, open sightlines, ocean monument 227. Three hearts and one life want a start
            // where nothing reaches you before you see it.
            DailyEntry.of("challengecraft.daily.fragile", 21, 7)
                    .withMaxHearts(6)
                    .withSeed(1010101L),
            // Plains, height 22–135. Drops fly upwards here, and the deep valleys decide whether you
            // ever get them back.
            DailyEntry.of("challengecraft.daily.weightless", 34, 30)
                    .withSeed(500000L),
            // Dark forest, 22 % water, surface down to y = -4. Dark canopy, five times the spawns.
            DailyEntry.of("challengecraft.daily.chaos_night", 35, 33, 42)
                    .withDoubleTrouble(5)
                    .withSeed(987654321L),
            // Dark forest with 31 distinct biomes around spawn (30 in 26.2; 26.3 added the dappled
            // forest to the sample) — the most of everything measured.
            // A quiz run should send you past as many different blocks as possible.
            DailyEntry.of("challengecraft.daily.quiz_run", 36, 5, 27)
                    .withSeed(12345L),
            // Plains, 29 biomes, village 306. Burning ground and no jumping needs flat land to be a
            // challenge rather than a dead end.
            DailyEntry.of("challengecraft.daily.hot_floor", 29, 41)
                    .withSeed(4242424L),
            // Forest, 25 biomes. Unlocking blocks one at a time only works if the next target is
            // findable, which is a question of biome variety.
            DailyEntry.of("challengecraft.daily.step_by_step", 44)
                    .withSeed(8675309L),
            // Birch forest, 20 % water, ruined portal 129. Triple speed wants land, not coastline.
            DailyEntry.of("challengecraft.daily.turbo", 37, 10)
                    .withGameSpeed(3)
                    .withSeed(777777L),
            // Plains, trial chamber 170, two villages at 301 and 377. A shared health pool is
            // punishing enough without a hostile start.
            DailyEntry.of("challengecraft.daily.shared_fate", 32, 5)
                    .withSeed(314159L),
            // Forest, jungle temple 443, 22 biomes. Scrambled blocks and recipes read best in
            // terrain you think you recognise.
            DailyEntry.of("challengecraft.daily.scrambled", 16, 19, 20)
                    .withSeed(66666L),
            // Grove, plains village 209 and an ancient city 205. The village is right there and
            // every single thing in it is off limits.
            DailyEntry.of("challengecraft.daily.no_deals", 6, 8, 4)
                    .withSeed(505050L),
            // The find of the sweep: the trial chamber is at distance 0 — spawn sits on top of it.
            // No armour, no healing, triple-health mobs, and the fight is already under your feet.
            DailyEntry.of("challengecraft.daily.bare_bones", 27, 24, 5)
                    .withMobHealth(3)
                    .withSeed(2026L),
            // Forest with surface down to y = 5. Two hearts turns every one of those drops lethal,
            // while each hit taken pays out an item.
            DailyEntry.of("challengecraft.daily.glass_cannon", 7, 18)
                    .withMaxHearts(4)
                    .withSeed(2718281L),
            // 79 % water, the wettest measured, ocean monument 355. Nine inventory slots matter far
            // more when everything is a boat trip.
            DailyEntry.of("challengecraft.daily.overloaded", 12, 30)
                    .withInventorySlots(9)
                    .withSeed(121212L),
            // A desert spawn — no trees in reach. No crafting table on top of that, tools decaying
            // as you use them, and the desert village 288 blocks out as the only way back.
            DailyEntry.of("challengecraft.daily.bare_hands", 8, 31)
                    .withSeed(262262L),
            // Grove with an ancient city 107 blocks away, the closest measured. Mobs in every size
            // and a potion effect per block mined.
            DailyEntry.of("challengecraft.daily.deep_dark", 33, 19)
                    .withSeed(31337L),
            // Forest whose woodland mansion is 975 blocks out — the nearest of all 29 seeds. Every
            // block walked costs a heart and pays an item, so the walk itself is the run.
            DailyEntry.of("challengecraft.daily.long_way", 28, 17)
                    .withSeed(1000000L),
            // A frozen peaks spawn, height 31–169. Freezing, no food, no regeneration.
            DailyEntry.of("challengecraft.daily.frostbite", 39, 5)
                    .withSeed(90210L),
            // Dark forest with a swamp hut 426 away. A new enchantment every 30 seconds on tools
            // that keep degrading under you.
            DailyEntry.of("challengecraft.daily.hexed", 13, 31)
                    .withSeed(42L),
            // A wooded badlands spawn: little water, hard terrain, desert village 458. The border
            // closes in with every heart lost and there is no armour to stop it.
            DailyEntry.of("challengecraft.daily.closing_in", 25, 27)
                    .withSeed(1337L),
            // Skyblock replaces the terrain, so this is the one entry where the seed says nothing
            // about the world — no claim is made about it.
            DailyEntry.of("challengecraft.daily.sky_islands", 11, 10)
                    .withSeed(123L),
            // Bamboo jungle spawn, jungle temple 285. Both challenges hang off your XP level, so the
            // world opens up exactly as fast as you do.
            DailyEntry.of("challengecraft.daily.level_bound", 1, 9)
                    .withSeed(404L),
            // Found by hand, then measured: savanna spawn at 0,0 with a savanna village 80 blocks
            // out and a ruined portal at 132. The village is a zombie village — that part is the
            // finder's report, not the probe's: whether a village generates infested is a per-
            // village roll the structure search cannot see. It is the reason for the ruleset, so a
            // whole village of blocks to break and zombies to kill, and neither gives what it
            // should. These are the last two challenges the rotation had not used yet.
            DailyEntry.of("challengecraft.daily.dead_village", 14, 15)
                    .withSeed(2501522797323854522L)
    );

    private DailyChallenges() {
    }

    public static List<DailyEntry> all() {
        return ENTRIES;
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static DailyEntry get(int index) {
        return ENTRIES.get(Math.floorMod(index, ENTRIES.size()));
    }

    /**
     * Checks every entry against the rules above. Called once at server start.
     *
     * <p>Returns the problems rather than throwing: a bad daily should be loud in the log and skipped,
     * not a reason the server refuses to boot. Someone editing this list should find out immediately,
     * but a player should never be locked out of the whole mod by a typo in a rotation entry.
     */
    public static List<String> validate() {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < ENTRIES.size(); i++) {
            DailyEntry e = ENTRIES.get(i);
            String where = "Eintrag " + i + " (" + e.nameKey() + ")";

            if (e.challengeIds().isEmpty()) {
                problems.add(where + ": keine Challenge-IDs");
            }
            if (e.challengeIds().size() != e.challengeIds().stream().distinct().count()) {
                problems.add(where + ": doppelte Challenge-ID");
            }
            for (int[] forbidden : FORBIDDEN) {
                if (e.challengeIds().contains(forbidden[0])) {
                    problems.add(where + ": Challenge " + forbidden[0]
                            + " ist für Dailies gesperrt (Sammelziel oder Lobby-Minispiel)");
                }
            }
            if (ChallengeManager.hasConflict(e.challengeIds(), List.of())) {
                problems.add(where + ": die Challenges " + e.challengeIds()
                        + " schließen sich gegenseitig aus");
            }
        }
        return problems;
    }

    /** Logs the outcome of {@link #validate()}. Safe to call more than once. */
    public static boolean validateAndLog() {
        List<String> problems = validate();
        if (problems.isEmpty()) {
            LOGGER.info("Daily-Rotation in Ordnung: {} Einträge", ENTRIES.size());
            return true;
        }
        LOGGER.error("Daily-Rotation hat {} Problem(e):", problems.size());
        problems.forEach(p -> LOGGER.error("  {}", p));
        return false;
    }
}
