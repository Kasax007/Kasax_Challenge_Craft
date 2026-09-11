package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * The one place that sends a run-summary card, called from every ending a run can have.
 *
 * <p>There are six of them, and a hook on the Ender Dragon alone would miss five:
 * <ul>
 *   <li>{@code PlayerAdvancementTrackerMixin} — {@code minecraft:end/kill_dragon}</li>
 *   <li>{@code Chal_22_AllItems}, {@code Chal_23_AllEntities}, {@code Chal_26_AllAchievements}
 *       — the collection goals finish on their own last item</li>
 *   <li>{@code Chal_40_LockoutBingo} — {@code finishGame}</li>
 *   <li>{@code Chal_45_ForceItemBattle} — the results ceremony</li>
 * </ul>
 *
 * <p>Worse than merely incomplete: when All Items or All Entities is active, the dragon payout is
 * deliberately skipped ({@code PlayerAdvancementTrackerMixin}), so on those runs a dragon-only hook
 * would never fire at all.
 */
public final class RunSummary {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Summary");

    private RunSummary() {
    }

    /**
     * Whether this run beats the player's stored record.
     *
     * <p><b>Call this before {@link StatsManager#recordCompletion}.</b> Afterwards the stored best
     * has already been replaced by this very run, so the comparison would be against itself and the
     * answer is always "no". That is the single ordering hazard in this feature, which is why the
     * check lives in its own method instead of inside {@link #send}.
     *
     * <p>A run counts as a record if it beats the best for any of its active challenges, or if
     * there was no previous time at all.
     */
    public static boolean isNewBest(ServerPlayer player, List<Integer> activeIds, int ticks) {
        if (activeIds.isEmpty()) {
            return false;
        }
        Map<Integer, Integer> best = StatsManager.getBestTimes(player.getStringUUID());
        boolean anyKnown = false;
        for (int id : activeIds) {
            Integer previous = best.get(id);
            if (previous == null) {
                return true;
            }
            anyKnown = true;
            if (ticks < previous) {
                return true;
            }
        }
        return !anyKnown;
    }

    /** A timed run: the dragon and the three collection goals. */
    public static void sendTimed(ServerPlayer player, RunSummaryPacket.Kind kind,
                                 ChallengeSavedData data, long xpGained, long oldXp, long newXp,
                                 boolean newBest) {
        long ticks = ChallengeTimeUtil.getDisplayRunTicks(player.level().getServer());

        // A daily records its own personal best under a synthetic id, so "your time on daily #17"
        // is a thing separate from the per-challenge bests. Asked before writing, for the same
        // reason isNewBest is: afterwards the stored record is this run.
        // On a daily the per-challenge record is meaningless (nothing was written for it either),
        // so only the daily's own time decides whether this is a record.
        boolean best = data.isDailyRun() ? false : newBest;
        if (data.isDailyRun()) {
            boolean dailyBest = net.kasax.challengecraft.daily.DailyManager.recordCompletion(
                    player.getUUID(), data.getDailyIndex(), (int) Math.min(Integer.MAX_VALUE, ticks));
            best = best || dailyBest;
        }

        send(player, new RunSummaryPacket(kind, ticks,
                data.isTainted() ? 0.0 : data.getInitialDifficulty(), data.isTainted(),
                xpGained, oldXp, newXp, best, data.getActive(), "", 0, 0,
                data.isDailyRun(), data.getDailyIndex(), data.getDailyEpochDay()));
    }

    /**
     * A competitive run: Lockout Bingo and Force Item Battle. The card leads with the placement,
     * not the clock, and every participant gets one — a second place is worth sharing too, and
     * handing a card only to the winner reads as a punishment.
     */
    public static void sendCompetitive(ServerPlayer player, RunSummaryPacket.Kind kind,
                                       ChallengeSavedData data, long xpGained, long oldXp, long newXp,
                                       String headline, int placement, int participants) {
        long ticks = ChallengeTimeUtil.getDisplayRunTicks(player.level().getServer());
        send(player, new RunSummaryPacket(kind, ticks,
                data.isTainted() ? 0.0 : data.getInitialDifficulty(), data.isTainted(),
                xpGained, oldXp, newXp, false, data.getActive(),
                headline, placement, participants,
                data.isDailyRun(), data.getDailyIndex(), data.getDailyEpochDay()));
    }

    public static void send(ServerPlayer player, RunSummaryPacket packet) {
        try {
            ServerPlayNetworking.send(player, packet);
            LOGGER.info("[Summary] {} an {}: {} Ticks, Difficulty {}, XP {}, Rekord {}, Challenges {}",
                    packet.kind, player.getName().getString(), packet.runTicks,
                    String.format(java.util.Locale.ROOT, "%.2f", packet.difficulty),
                    packet.xpGained, packet.newBest, packet.activeIds);
            if (packet.daily) {
                LOGGER.info("[Summary] Tages-Challenge #{} vom Tag {}", packet.dailyIndex, packet.dailyEpochDay);
            }
        } catch (RuntimeException e) {
            // A summary card is a nicety. It must never be the reason a completed run fails to
            // finish paying out, so this swallows rather than propagates.
            LOGGER.error("[Summary] konnte nicht gesendet werden", e);
        }
    }
}
