package net.kasax.challengecraft.challenges.lockout;

public enum LockoutBingoGoalImplementationStatus {
    IMPLEMENTED,
    TODO;

    public boolean isImplemented() {
        return this == IMPLEMENTED;
    }
}
