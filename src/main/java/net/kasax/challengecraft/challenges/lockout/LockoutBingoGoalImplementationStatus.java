package net.kasax.challengecraft.challenges.lockout;

/** Whether a goal may be placed on normal boards. */
public enum LockoutBingoGoalImplementationStatus {
    IMPLEMENTED,
    TODO;

    public boolean isImplemented() {
        return this == IMPLEMENTED;
    }
}
