package com.siteflow.domain.enums;

public enum ApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED;

    /**
     * Determines whether this status can legally transition to the target status.
     * Decisions on approval or rejection are irreversible.
     */
    public boolean canTransitionTo(ApprovalStatus target) {
        if (target == null || this == target) {
            return false;
        }
        return switch (this) {
            case PENDING_APPROVAL -> target == APPROVED || target == REJECTED;
            case APPROVED, REJECTED -> false; // Terminal states
        };
    }

    /**
     * Checks if this status is a terminal state that cannot transition further.
     */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}
