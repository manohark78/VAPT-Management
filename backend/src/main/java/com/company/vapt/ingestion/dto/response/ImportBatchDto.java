package com.company.vapt.ingestion.dto.response;

import com.company.vapt.report.domain.ReportSectionId;

import com.company.vapt.ingestion.entity.ImportBatch;

import java.time.Instant;
import java.util.UUID;

/**
 * Public DTO for an import batch summary.
 * Does NOT include stage breakdown (see ImportBatchDetailDto).
 * Never exposes physical storage paths.
 */
public record ImportBatchDto(
        UUID id,
        String reportSectionId,
        UUID sourceFileId,
        String originalFilename,
        long fileSizeBytes,
        String contentType,
        String fileExtension,
        String status,
        boolean active,
        boolean completeSnapshot,
        int totalRows,
        int acceptedRows,
        int rejectedRows,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public static ImportBatchDto from(ImportBatch entity) {
        return new ImportBatchDto(
                entity.getId(),
                entity.getReportSection().getId(),
                entity.getSourceFile().getId(),
                entity.getSourceFile().getOriginalFilename(),
                entity.getSourceFile().getFileSizeBytes(),
                entity.getSourceFile().getContentType(),
                entity.getSourceFile().getFileExtension(),
                entity.getStatus().name(),
                entity.isActive(),
                entity.isCompleteSnapshot(),
                entity.getTotalRows(),
                entity.getAcceptedRows(),
                entity.getRejectedRows(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }
}
