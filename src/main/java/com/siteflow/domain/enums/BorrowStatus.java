package com.siteflow.domain.enums;

public enum BorrowStatus {
    PENDING,
    BORROWED,
    PARTIAL_RETURN,
    COMPLETED;

    /**
     * Determines whether this status can legally transition to the target status.
     * Rejects resurrection from COMPLETED, regression to PENDING, and invalid shortcuts.
     */
    public boolean canTransitionTo(BorrowStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            // Successive partial returns can re-affirm PARTIAL_RETURN status
            return this == PARTIAL_RETURN;
        }
        return switch (this) {
            case PENDING -> target == BORROWED;
            case BORROWED -> target == PARTIAL_RETURN || target == COMPLETED;
            case PARTIAL_RETURN -> target == COMPLETED;
            case COMPLETED -> false; // Terminal state
        };
    }

    /**
     * Checks if this status is a terminal state that cannot transition further.
     */
    public boolean isTerminal() {
        return this == COMPLETED;
    }
}
