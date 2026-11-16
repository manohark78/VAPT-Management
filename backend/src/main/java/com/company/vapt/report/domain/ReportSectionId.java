package com.company.vapt.report.domain;

import com.company.vapt.finding.entity.Finding;

/**
 * Canonical identifiers for the six independent report sections in the VAPT platform.
 * 
 * Crucial Invariant: GDP_VA_REPORT and INTERNAL_VA_BRINQA are completely distinct sources
 * with isolated import batches, finding namespaces, and dashboards.
 */
public enum ReportSectionId {

    GDP_VA_REPORT(
            "GDP_VA_REPORT",
            "GDP VA Report",
            "General Vulnerability Assessment for GDP infrastructure",
            ReconciliationType.UID_BASED,
            true
    ),
    INTERNAL_VA_BRINQA(
            "INTERNAL_VA_BRINQA",
            "Internal VA Brinqa",
            "Internal Vulnerability Assessment via Brinqa platform",
            ReconciliationType.UID_BASED,
            true
    ),
    WIZ(
            "WIZ",
            "Wiz",
            "Wiz Cloud Security Assessment",
            ReconciliationType.UID_BASED,
            true
    ),
    VERIZON_26_NETWORK_PT(
            "VERIZON_26_NETWORK_PT",
            "VERIZON 26 Network PT",
            "Verizon 26 Network Penetration Test",
            ReconciliationType.UID_BASED,
            true
    ),
    PT_VERIZON_GCP(
            "PT_VERIZON_GCP",
            "PT Verizon GCP",
            "Verizon GCP Cloud Penetration Test",
            ReconciliationType.UID_BASED,
            true
    ),
    APPSEC_FINDINGS_PT(
            "APPSEC_FINDINGS_PT",
            "AppSec Findings PT",
            "Application Security Penetration Testing Findings",
            ReconciliationType.FINDING_ID_BASED,
            true
    );

    private final String code;
    private final String displayName;
    private final String description;
    private final ReconciliationType reconciliationType;
    private final boolean defaultEnabled;

    ReportSectionId(
            String code,
            String displayName,
            String description,
            ReconciliationType reconciliationType,
            boolean defaultEnabled
    ) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.reconciliationType = reconciliationType;
        this.defaultEnabled = defaultEnabled;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public ReconciliationType getReconciliationType() {
        return reconciliationType;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }
}
