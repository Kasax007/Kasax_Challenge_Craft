package net.kasax.challengecraft.challenges;

import net.kasax.challengecraft.ChallengeCraft;

/**
 * The House Always Wins (challenge 50): every item is worth Jetons (ProjectE values), the House
 * collects a growing fee from the team every ten minutes, and the chips can be gambled on slots,
 * crash and roulette. Lost bets summon monsters, every death costs a quarter of the chips, and a
 * hand of blackjack decides whether the player comes back with their inventory. If the team cannot
 * pay the fee, the House wins and the run is over.
 *
 * <p>This class is only the switch, following the other challenges. Everything else lives in
 * {@code net.kasax.challengecraft.casino}: {@code CasinoEconomy} (books and fee), {@code CasinoGames}
 * (wiring), {@code SlotGame}, {@code CrashGame}, {@code RouletteGame}, {@code BlackjackRevival}
 * and {@code LossWaves}.
 *
 * <p>Deliberately nothing is cleared in {@link #setActive}: the client's challenge sync switches every
 * flag off and on again, and in singleplayer that runs against the very statics the server uses.
 */
public class Chal_50_HouseAlwaysWins {
    private static volatile boolean active = false;

    public static void setActive(boolean value) {
        if (active != value) {
            ChallengeCraft.LOGGER.info("[Chal50] {}", value ? "activated" : "deactivated");
        }
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
