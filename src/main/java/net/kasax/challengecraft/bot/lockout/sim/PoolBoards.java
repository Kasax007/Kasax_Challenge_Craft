package net.kasax.challengecraft.bot.lockout.sim;

import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Boards as the mod deals them: the real goal pool and its board picker
 * ({@link LockoutBingoGoalPool#pickBoard}), so the simulated games are played on the boards Bob
 * meets. (The only class of the simulator that touches the mod's challenge code; it needs no
 * running game.)
 */
public final class PoolBoards {
    private PoolBoards() {
    }

    /** The board for this seed, as a debug match (BotVersus) deals it for a world of that seed. */
    public static Board board(long seed) {
        return of(LockoutBingoGoalPool.pickBoard(seed));
    }

    public static Board of(List<LockoutBingoGoal> goals) {
        List<String> ids = new ArrayList<>(), cats = new ArrayList<>(), types = new ArrayList<>();
        for (LockoutBingoGoal g : goals) {
            ids.add(g.id());
            cats.add(g.category().name());
            types.add(g.type().name());
        }
        return Board.of(ids, cats, types);
    }

    /** Every goal id of the pool, and whether it may be dealt on a normal board. */
    public static List<String[]> pool() {
        List<String[]> out = new ArrayList<>();
        for (LockoutBingoGoal g : LockoutBingoGoalPool.all()) out.add(new String[]{g.id(), String.valueOf(g.isSelectableOnNormalBoard())});
        return out;
    }
}
