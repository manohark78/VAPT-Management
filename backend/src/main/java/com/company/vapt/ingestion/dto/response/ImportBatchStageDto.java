package com.company.vapt.ingestion.dto.response;

import com.company.vapt.ingestion.entity.ImportBatchStage;

import java.time.Instant;

/**
 * Public DTO for a single import batch stage execution record.
 */
public record ImportBatchStageDto(
        String stage,
        String status,
        long durationMs,
        Instant startedAt,
        Instant completedAt,
        String details
) {
    public static ImportBatchStageDto from(ImportBatchStage entity) {
        return new ImportBatchStageDto(
                entity.getStage(),
                entity.getStatus().name(),
                entity.getDurationMs(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getDetails()
        );
    }
}
