package net.kasax.challengecraft.challenges.lockout;

public enum LockoutBingoGoalDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT;

    public boolean isAtMost(LockoutBingoGoalDifficulty other) {
        return this.ordinal() <= other.ordinal();
    }
}
