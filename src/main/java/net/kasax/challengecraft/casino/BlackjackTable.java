package net.kasax.challengecraft.casino;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure blackjack rules for the revival hand, no Minecraft state. The rule set was chosen by
 * simulation ({@code scripts/casino/bj_sim.py}, 20 million hands per rule set) as the one closest to
 * a fair game: <b>one deck shuffled for every hand, dealer stands on soft 17, blackjack pays 3:2,
 * double on any first two cards, no double after split, split up to four hands, split aces get one
 * card each, no surrender, dealer peeks for blackjack</b> — 99.99 % ± 0.05 return with basic
 * strategy, a player survives a revival hand ~47.7 % of the time (pushes are dealt again).
 *
 * <p>Cards are 0..51: rank {@code c % 13} (0 = ace, 1..8 = 2..9, 9..12 = ten, jack, queen, king),
 * suit {@code c / 13}.
 */
public final class BlackjackTable {
    public static final int HIT = 1, STAND = 2, DOUBLE = 4, SPLIT = 8;

    public static final class Hand {
        public final List<Integer> cards = new ArrayList<>();
        public int stake = 1;
        public boolean done;
        public boolean splitAces;
    }

    private final int[] deck = new int[52];
    private int next;
    public final List<Integer> dealer = new ArrayList<>();
    public final List<Hand> hands = new ArrayList<>();
    public int active;
    public boolean dealerRevealed;
    public boolean finished;
    /** Net result in stake units once finished (1.5 for a natural). */
    public double net;
    public boolean playerNatural;

    public BlackjackTable(RandomSource random) {
        for (int i = 0; i < 52; i++) deck[i] = i;
        for (int i = 51; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = deck[i];
            deck[i] = deck[j];
            deck[j] = t;
        }
        Hand first = new Hand();
        hands.add(first);
        first.cards.add(draw());
        dealer.add(draw());
        first.cards.add(draw());
        dealer.add(draw());

        boolean dealerBj = total(dealer) == 21;
        boolean playerBj = total(first.cards) == 21;
        if (dealerBj || playerBj) {
            first.done = true;
            dealerRevealed = true;
            finished = true;
            playerNatural = playerBj && !dealerBj;
            net = playerBj ? (dealerBj ? 0.0 : 1.5) : -1.0;
        }
    }

    private int draw() {
        return deck[next++];
    }

    public static int cardValue(int card) {
        int rank = card % 13;
        return rank == 0 ? 1 : Math.min(10, rank + 1);
    }

    /** Best total, counting one ace as 11 where that does not bust. */
    public static int total(List<Integer> cards) {
        int sum = 0;
        boolean ace = false;
        for (int c : cards) {
            int v = cardValue(c);
            sum += v;
            if (v == 1) ace = true;
        }
        return ace && sum + 10 <= 21 ? sum + 10 : sum;
    }

    public static boolean isSoft(List<Integer> cards) {
        int sum = 0;
        boolean ace = false;
        for (int c : cards) {
            int v = cardValue(c);
            sum += v;
            if (v == 1) ace = true;
        }
        return ace && sum + 10 <= 21;
    }

    public Hand current() {
        return finished || active >= hands.size() ? null : hands.get(active);
    }

    public int allowed() {
        Hand h = current();
        if (h == null) return 0;
        int mask = STAND;
        if (!h.splitAces && total(h.cards) < 21) mask |= HIT;
        if (h.cards.size() == 2 && hands.size() == 1 && !h.splitAces) mask |= DOUBLE;
        if (h.cards.size() == 2 && hands.size() < 4 && !h.splitAces
                && cardValue(h.cards.get(0)) == cardValue(h.cards.get(1))) mask |= SPLIT;
        return mask;
    }

    /** Applies an action if allowed; returns false otherwise. */
    public boolean act(int action) {
        Hand h = current();
        if (h == null || (allowed() & action) == 0) return false;
        switch (action) {
            case HIT -> {
                h.cards.add(draw());
                if (total(h.cards) >= 21) h.done = true;
            }
            case STAND -> h.done = true;
            case DOUBLE -> {
                h.stake = 2;
                h.cards.add(draw());
                h.done = true;
            }
            case SPLIT -> {
                Hand second = new Hand();
                second.cards.add(h.cards.remove(1));
                boolean aces = cardValue(h.cards.get(0)) == 1;
                h.splitAces = aces;
                second.splitAces = aces;
                h.cards.add(draw());
                second.cards.add(draw());
                hands.add(active + 1, second);
                if (aces) {
                    h.done = true;
                    second.done = true;
                } else if (total(h.cards) == 21) {
                    h.done = true;
                }
            }
            default -> {
                return false;
            }
        }
        advance();
        return true;
    }

    private void advance() {
        while (active < hands.size() && hands.get(active).done) {
            active++;
            if (active < hands.size()) {
                Hand h = hands.get(active);
                if (total(h.cards) == 21 || h.splitAces) h.done = true;
            }
        }
        if (active >= hands.size()) {
            finishDealer();
        }
    }

    private void finishDealer() {
        dealerRevealed = true;
        boolean anyLive = false;
        for (Hand h : hands) if (total(h.cards) <= 21) anyLive = true;
        if (anyLive) {
            while (total(dealer) < 17) dealer.add(draw()); // stands on every 17, soft ones included
        }
        int d = total(dealer);
        double sum = 0;
        for (Hand h : hands) {
            int t = total(h.cards);
            if (t > 21) sum -= h.stake;
            else if (d > 21 || t > d) sum += h.stake;
            else if (t < d) sum -= h.stake;
        }
        net = sum;
        finished = true;
    }
}
