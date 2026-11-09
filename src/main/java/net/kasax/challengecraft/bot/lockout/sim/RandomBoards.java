package net.kasax.challengecraft.bot.lockout.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Boards drawn from the goal book alone (25 different goals that can be had at all), for what the
 * simulator learns on its own (the {@link OpponentModel}) without the mod's board picker.
 */
public final class RandomBoards {
    private RandomBoards() {
    }

    public static Board board(long seed) {
        List<Recipe> pool = new ArrayList<>();
        for (Recipe r : GoalBook.all()) if (!r.impossible) pool.add(r);
        SplittableRandom rng = new SplittableRandom(seed);
        List<String> ids = new ArrayList<>();
        while (ids.size() < Board.SIZE) {
            Recipe r = pool.remove(rng.nextInt(pool.size()));
            ids.add(r.id);
        }
        return Board.of(ids, null, null);
    }
}
