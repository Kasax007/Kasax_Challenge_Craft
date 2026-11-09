package net.kasax.challengecraft.bot.lockout.sim;

import java.util.List;

/**
 * The 25 tiles of a game and who holds which. A tile is claimed once and for good; a player
 * wins as soon as it has more tiles than the other could still reach (thirteen always does), else
 * the one with more tiles when the time is up.
 */
public final class Board {
    public static final int SIZE = 25;

    public final Recipe[] goal = new Recipe[SIZE];
    /** The pool's category and type of each tile (NETHER, DIMENSION...), for the rules that use them. */
    public final String[] category = new String[SIZE], type = new String[SIZE];
    /** -1 open, else the player holding it. */
    public final int[] owner = new int[SIZE];
    public final double[] claimedAt = new double[SIZE];
    public final int[] score = new int[2];
    public int open = SIZE;

    private Board() {
    }

    /** A board of these goal ids (category and type may be null: then taken from the recipe). */
    public static Board of(List<String> ids, List<String> categories, List<String> types) {
        if (ids.size() != SIZE) throw new IllegalArgumentException("a board has " + SIZE + " tiles, not " + ids.size());
        Board b = new Board();
        for (int i = 0; i < SIZE; i++) {
            Recipe r = GoalBook.get(ids.get(i));
            if (r == null) throw new IllegalArgumentException("no recipe for goal " + ids.get(i));
            b.goal[i] = r;
            b.category[i] = categories != null ? categories.get(i) : r.nether() ? "NETHER" : "ITEM";
            b.type[i] = types != null ? types.get(i) : "ITEM";
            b.owner[i] = -1;
        }
        return b;
    }

    public Board copy() {
        Board b = new Board();
        System.arraycopy(goal, 0, b.goal, 0, SIZE);
        System.arraycopy(category, 0, b.category, 0, SIZE);
        System.arraycopy(type, 0, b.type, 0, SIZE);
        System.arraycopy(owner, 0, b.owner, 0, SIZE);
        System.arraycopy(claimedAt, 0, b.claimedAt, 0, SIZE);
        b.score[0] = score[0];
        b.score[1] = score[1];
        b.open = open;
        return b;
    }

    public boolean isOpen(int tile) {
        return owner[tile] < 0;
    }

    /** The tile goes to {@code player} (for good). */
    public void claim(int tile, int player, double now) {
        owner[tile] = player;
        claimedAt[tile] = now;
        score[player]++;
        open--;
    }

    /** Takes a tile out of the game (for tests: a board where only some tiles are in play). */
    void close(int tile) {
        if (owner[tile] >= 0) return;
        owner[tile] = CLOSED;
        open--;
    }

    /** Owner of a tile taken out of the game. */
    static final int CLOSED = 9;

    /** The player who has clinched it (more tiles than the other plus all still open), or -1. */
    public int clinched() {
        for (int p = 0; p < 2; p++) if (score[p] > score[1 - p] + open) return p;
        return -1;
    }

    public boolean nether(int tile) {
        return "NETHER".equals(category[tile]);
    }

    public int index(String goalId) {
        for (int i = 0; i < SIZE; i++) if (goal[i].id.equals(goalId)) return i;
        return -1;
    }
}
