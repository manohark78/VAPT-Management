package com.company.vapt.report.domain;

/**
 * Immutable metadata record for a report section.
 */
public record ReportSectionMetadata(
        ReportSectionId id,
        String displayName,
        String description,
        ReconciliationType reconciliationType,
        boolean enabled
) {
    public static ReportSectionMetadata from(ReportSectionId sectionId, boolean enabled) {
        return new ReportSectionMetadata(
                sectionId,
                sectionId.getDisplayName(),
                sectionId.getDescription(),
                sectionId.getReconciliationType(),
                enabled
        );
    }

    public static ReportSectionMetadata from(ReportSectionId sectionId) {
        return from(sectionId, sectionId.isDefaultEnabled());
    }
}
