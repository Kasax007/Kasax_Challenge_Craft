package net.kasax.challengecraft.challenges.lockout;

/** Difficulty tier attached to a lockout goal definition. */
public enum LockoutBingoGoalDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT;

    public boolean isAtMost(LockoutBingoGoalDifficulty other) {
        return this.ordinal() <= other.ordinal();
    }
}
