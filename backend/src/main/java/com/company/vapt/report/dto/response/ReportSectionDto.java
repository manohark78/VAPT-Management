package com.company.vapt.report.dto.response;

import com.company.vapt.report.domain.ReconciliationType;

import com.company.vapt.report.entity.ReportSection;

import java.time.Instant;

/**
 * Public DTO for a canonical report section.
 * Never exposes JPA internals.
 */
public record ReportSectionDto(
        String id,
        String displayName,
        String description,
        String reconciliationType,
        boolean enabled,
        Instant createdAt
) {
    public static ReportSectionDto from(ReportSection entity) {
        return new ReportSectionDto(
                entity.getId(),
                entity.getDisplayName(),
                entity.getDescription(),
                entity.getReconciliationType().name(),
                entity.isEnabled(),
                entity.getCreatedAt()
        );
    }
}
