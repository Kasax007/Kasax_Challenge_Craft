package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.network.LockoutBingoSyncPacket;

import java.util.List;
import java.util.UUID;

/** Client cache for the latest lockout sync packet. */
public final class LockoutBingoClientState {
    private static LockoutBingoSyncPacket latest = new LockoutBingoSyncPacket(List.of(), List.of(), List.of(), List.of(), false, false, -1, 0L, 0);
    private static long lastSyncMillis = 0L;

    private LockoutBingoClientState() {
    }

    public static void update(LockoutBingoSyncPacket packet) {
        latest = packet;
        lastSyncMillis = System.currentTimeMillis();
    }

    public static LockoutBingoSyncPacket get() {
        return latest;
    }

    public static void clear() {
        latest = new LockoutBingoSyncPacket(List.of(), List.of(), List.of(), List.of(), false, false, -1, 0L, 0);
        lastSyncMillis = 0L;
    }

    public static long getElapsedTicks() {
        if (!latest.started()) {
            return 0L;
        }
        if (latest.ended()) {
            return latest.elapsedTicks();
        }
        long extraTicks = Math.max(0L, (System.currentTimeMillis() - lastSyncMillis) / 50L);
        return latest.elapsedTicks() + extraTicks;
    }

    public static LockoutBingoTeam getTeam(UUID uuid) {
        for (LockoutBingoSyncPacket.PlayerState player : latest.players()) {
            if (player.uuid().equals(uuid)) {
                return LockoutBingoTeam.fromOrdinal(player.teamId());
            }
        }
        return null;
    }

    public static boolean isReady(UUID uuid) {
        for (LockoutBingoSyncPacket.PlayerState player : latest.players()) {
            if (player.uuid().equals(uuid)) {
                return player.ready();
            }
        }
        return false;
    }

    public static int getScore(LockoutBingoTeam team) {
        int score = 0;
        for (Integer claimedTeam : latest.claimedTeams()) {
            if (claimedTeam == team.ordinal()) {
                score++;
            }
        }
        return score;
    }

    public static int getRemainingTiles() {
        return Math.max(0, 25 - (int) latest.claimedTeams().stream().filter(teamId -> teamId >= 0).count());
    }

    public static int getClinchTarget(LockoutBingoTeam team) {
        if (team == null) {
            return 26;
        }

        int maxOtherScore = 0;
        for (LockoutBingoTeam otherTeam : LockoutBingoTeam.values()) {
            if (otherTeam == team) {
                continue;
            }
            maxOtherScore = Math.max(maxOtherScore, getScore(otherTeam));
        }

        return maxOtherScore + getRemainingTiles() + 1;
    }

    public static LockoutBingoTeam getLeadingTeam() {
        LockoutBingoTeam leader = null;
        int bestScore = -1;
        boolean tie = false;
        for (LockoutBingoTeam team : LockoutBingoTeam.values()) {
            int score = getScore(team);
            if (score > bestScore) {
                bestScore = score;
                leader = team;
                tie = false;
            } else if (score == bestScore) {
                tie = true;
            }
        }
        return tie ? null : leader;
    }

    public static boolean isDraw() {
        return latest.ended() && latest.winnerTeamId() < 0;
    }
}
