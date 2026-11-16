package com.company.vapt.ingestion.dto.response;

import com.company.vapt.report.domain.ReportSectionId;

import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ImportBatchStage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full detail DTO for an import batch including stage breakdown and error diagnostics.
 * Never exposes physical storage paths.
 */
public record ImportBatchDetailDto(
        UUID id,
        String reportSectionId,
        UUID sourceFileId,
        String originalFilename,
        long fileSizeBytes,
        String contentType,
        String fileExtension,
        String sha256Checksum,
        String status,
        boolean active,
        int totalRows,
        int acceptedRows,
        int rejectedRows,
        String errorMessage,
        String errorDetails,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        List<ImportBatchStageDto> stages
) {
    public static ImportBatchDetailDto from(ImportBatch entity, List<ImportBatchStage> stages) {
        return new ImportBatchDetailDto(
                entity.getId(),
                entity.getReportSection().getId(),
                entity.getSourceFile().getId(),
                entity.getSourceFile().getOriginalFilename(),
                entity.getSourceFile().getFileSizeBytes(),
                entity.getSourceFile().getContentType(),
                entity.getSourceFile().getFileExtension(),
                entity.getSourceFile().getSha256Checksum(),
                entity.getStatus().name(),
                entity.isActive(),
                entity.getTotalRows(),
                entity.getAcceptedRows(),
                entity.getRejectedRows(),
                entity.getErrorMessage(),
                entity.getErrorDetails(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                stages.stream().map(ImportBatchStageDto::from).toList()
        );
    }
}
