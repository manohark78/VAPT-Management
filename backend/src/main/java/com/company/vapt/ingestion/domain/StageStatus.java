package com.company.vapt.ingestion.domain;

/**
 * Execution status for an individual stage in the import lifecycle.
 */
public enum StageStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}
