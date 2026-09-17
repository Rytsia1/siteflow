package com.siteflow.domain.enums;

public enum MaterialRequestStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    PO_CREATED,
    COMPLETED;

    /**
     * Determines whether this status can legally transition to the target status.
     * Rejects self-transitions and transitions out of terminal states.
     */
    public boolean canTransitionTo(MaterialRequestStatus target) {
        if (target == null || this == target) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == PO_CREATED;
            case PO_CREATED -> target == COMPLETED;
            case REJECTED, COMPLETED -> false; // Terminal states
        };
    }

    /**
     * Checks if this status is a terminal state that cannot transition further.
     */
    public boolean isTerminal() {
        return this == REJECTED || this == COMPLETED;
    }
}
