package com.company.vapt.ingestion.entity;

import com.company.vapt.ingestion.domain.StageStatus;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a structured, queryable operational record of an individual stage duration.
 */
@Entity
@Table(name = "import_batch_stage", indexes = {
        @Index(name = "idx_import_batch_stage_batch_id", columnList = "batch_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_import_batch_stage", columnNames = {"batch_id", "stage"})
})
public class ImportBatchStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Column(name = "stage", length = 50, nullable = false)
    private String stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private StageStatus status;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "details", length = 1000)
    private String details;

    protected ImportBatchStage() {
        // Required by JPA
    }

    public ImportBatchStage(ImportBatch batch, String stage, StageStatus status, long durationMs, Instant startedAt, Instant completedAt, String details) {
        this.batch = batch;
        this.stage = stage;
        this.status = status;
        this.durationMs = durationMs;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public ImportBatch getBatch() {
        return batch;
    }

    public String getStage() {
        return stage;
    }

    public StageStatus getStatus() {
        return status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getDetails() {
        return details;
    }
}
