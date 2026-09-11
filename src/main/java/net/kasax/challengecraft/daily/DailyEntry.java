package net.kasax.challengecraft.daily;

import java.util.List;

/**
 * One entry in the hand-maintained daily rotation.
 *
 * <p>The tunable fields use {@link #UNSET} rather than a sensible default so an entry that does not
 * mention a slider leaves whatever the world already had, instead of silently forcing a value the
 * author never chose. Only the five sliders that actually exist are represented — max hearts,
 * inventory slots, mob health, double trouble and game speed.
 *
 * <p>Instances are built by chaining, because a positional constructor with eight arguments is
 * unreadable in a hand-edited list and invites silent mistakes:
 * <pre>{@code
 * DailyEntry.of("challengecraft.daily.chaos_night", 35, 33, 42).withDoubleTrouble(5)
 * }</pre>
 */
public record DailyEntry(
        String nameKey,
        List<Integer> challengeIds,
        Long seed,
        int maxHearts,
        int inventorySlots,
        int mobHealthMultiplier,
        int doubleTroubleMultiplier,
        int gameSpeedMultiplier
) {
    /** Sentinel for "this entry does not touch that slider". */
    public static final int UNSET = -1;

    public DailyEntry {
        challengeIds = List.copyOf(challengeIds);
    }

    public static DailyEntry of(String nameKey, int... ids) {
        return new DailyEntry(nameKey, java.util.Arrays.stream(ids).boxed().toList(),
                null, UNSET, UNSET, UNSET, UNSET, UNSET);
    }

    /**
     * Pins the world seed so every player worldwide gets the same terrain on this day. Without it
     * the run is still playable, but two players' times are not comparable and the personal-best
     * board becomes decoration.
     */
    public DailyEntry withSeed(long seed) {
        return new DailyEntry(nameKey, challengeIds, seed, maxHearts, inventorySlots,
                mobHealthMultiplier, doubleTroubleMultiplier, gameSpeedMultiplier);
    }

    public DailyEntry withMaxHearts(int hearts) {
        return new DailyEntry(nameKey, challengeIds, seed, hearts, inventorySlots,
                mobHealthMultiplier, doubleTroubleMultiplier, gameSpeedMultiplier);
    }

    public DailyEntry withInventorySlots(int slots) {
        return new DailyEntry(nameKey, challengeIds, seed, maxHearts, slots,
                mobHealthMultiplier, doubleTroubleMultiplier, gameSpeedMultiplier);
    }

    public DailyEntry withMobHealth(int multiplier) {
        return new DailyEntry(nameKey, challengeIds, seed, maxHearts, inventorySlots,
                multiplier, doubleTroubleMultiplier, gameSpeedMultiplier);
    }

    public DailyEntry withDoubleTrouble(int multiplier) {
        return new DailyEntry(nameKey, challengeIds, seed, maxHearts, inventorySlots,
                mobHealthMultiplier, multiplier, gameSpeedMultiplier);
    }

    public DailyEntry withGameSpeed(int multiplier) {
        return new DailyEntry(nameKey, challengeIds, seed, maxHearts, inventorySlots,
                mobHealthMultiplier, doubleTroubleMultiplier, multiplier);
    }

    public String descriptionKey() {
        return nameKey + ".desc";
    }

    public boolean hasFixedSeed() {
        return seed != null;
    }
}
