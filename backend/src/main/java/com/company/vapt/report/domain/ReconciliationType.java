package com.company.vapt.report.domain;

import com.company.vapt.finding.entity.Finding;

/**
 * Strategy for reconciling findings across snapshot imports.
 */
public enum ReconciliationType {
    /**
     * Reconciled using stable Unique Identifier (UID) across snapshot workbooks.
     * Applicable to the first five general VA/PT sources.
     */
    UID_BASED,

    /**
     * Reconciled using Finding ID for application security penetration tests.
     * Applicable to AppSec Findings PT.
     */
    FINDING_ID_BASED
}
