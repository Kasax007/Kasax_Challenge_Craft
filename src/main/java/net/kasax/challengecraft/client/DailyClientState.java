package net.kasax.challengecraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.daily.DailyChallenges;
import net.kasax.challengecraft.daily.DailyEntry;
import net.kasax.challengecraft.daily.DailyManager;

/**
 * Whether the player is currently creating a world <em>as</em> today's daily challenge.
 *
 * <p>Armed by the title-screen card, read by the world-creation screen, and cleared as soon as that
 * screen is left again. It is a client-side intent, not game state: the actual ruleset still travels
 * to the server through the ordinary {@code ChallengePacket} that world creation already sends, and
 * the server checks it against its own rotation before accepting it.
 *
 * <p>Deliberately not persisted anywhere. Backing out of world creation must leave no trace, or the
 * next ordinary world would silently become a daily.
 */
@Environment(EnvType.CLIENT)
public final class DailyClientState {
    private static int armedIndex = -1;

    private DailyClientState() {
    }

    /** Arms today's daily for the next world creation. */
    public static void arm() {
        armedIndex = DailyManager.todayIndex();
    }

    public static void clear() {
        armedIndex = -1;
    }

    public static boolean isArmed() {
        return armedIndex >= 0;
    }

    /** Rotation index, or -1. Sent with the challenge packet so the server can verify the ruleset. */
    public static int index() {
        return armedIndex;
    }

    /** The armed entry, or null when nothing is armed. */
    public static DailyEntry entry() {
        return armedIndex < 0 ? null : DailyChallenges.get(armedIndex);
    }
}
