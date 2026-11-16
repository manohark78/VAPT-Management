package com.company.vapt.ingestion.domain;

/**
 * Controlled state machine statuses for the source file import lifecycle.
 */
public enum ImportStatus {
    RECEIVED,
    VALIDATING,
    STAGING,
    ENRICHING,
    RECONCILING,
    COMMITTED,
    FAILED;

    public boolean isTerminal() {
        return this == COMMITTED || this == FAILED;
    }
}
