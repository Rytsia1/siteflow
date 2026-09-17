package com.siteflow.domain.enums;

public enum PurchaseOrderStatus {
    ISSUED,
    PARTIAL_RECEIVED,
    FULFILLED;

    /**
     * Determines whether this status can legally transition to the target status.
     * Rejects regressing back to ISSUED or modifying after FULFILLED.
     */
    public boolean canTransitionTo(PurchaseOrderStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            // Successive deliveries can re-affirm PARTIAL_RECEIVED status
            return this == PARTIAL_RECEIVED;
        }
        return switch (this) {
            case ISSUED -> target == PARTIAL_RECEIVED || target == FULFILLED;
            case PARTIAL_RECEIVED -> target == FULFILLED;
            case FULFILLED -> false; // Terminal state
        };
    }

    /**
     * Checks if this status is a terminal state that cannot transition further.
     */
    public boolean isTerminal() {
        return this == FULFILLED;
    }
}
