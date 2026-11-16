package com.company.vapt.enrichment.entity;

import com.company.vapt.report.domain.ReportSectionId;

import com.company.vapt.ingestion.entity.ImportBatch;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrichment_run")
public class EnrichmentRun {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false) private ImportBatch importBatch;
    @Column(name = "report_section_id", nullable = false, length = 50) private String reportSectionId;
    @Column(name = "jas_version_id") private UUID jasVersionId;
    @Column(name = "headcount_version_id") private UUID headcountVersionId;
    @Column(name = "app_inventory_version_id") private UUID appInventoryVersionId;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "mapping_method", nullable = false, length = 80) private String mappingMethod;
    @Column(name = "actor_user_id", nullable = false, length = 255) private String actorUserId;
    @Column(name = "correlation_id", nullable = false, length = 255) private String correlationId;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "processed_rows", nullable = false) private long processedRows;
    @Column(name = "matched_rows", nullable = false) private long matchedRows;
    @Column(name = "unmatched_rows", nullable = false) private long unmatchedRows;
    @Column(name = "ambiguous_rows", nullable = false) private long ambiguousRows;
    @Column(name = "diagnostic_code", length = 80) private String diagnosticCode;
    @Column(name = "diagnostic_message", length = 500) private String diagnosticMessage;

    protected EnrichmentRun() { }

    public EnrichmentRun(UUID id, ImportBatch batch, UUID jasVersionId, UUID headcountVersionId,
                         UUID appInventoryVersionId, String actorUserId, String correlationId) {
        this.id = id;
        this.importBatch = batch;
        this.reportSectionId = batch.getReportSection().getId();
        this.jasVersionId = jasVersionId;
        this.headcountVersionId = headcountVersionId;
        this.appInventoryVersionId = appInventoryVersionId;
        this.actorUserId = actorUserId;
        this.correlationId = correlationId;
        this.mappingMethod = "REPORT_TO_CMDB_V1";
        this.status = "RUNNING";
        this.startedAt = Instant.now();
    }

    public void addCounts(long processed, long matched, long unmatched, long ambiguous) {
        this.processedRows += processed;
        this.matchedRows += matched;
        this.unmatchedRows += unmatched;
        this.ambiguousRows += ambiguous;
    }

    public void complete() { this.status = "COMPLETED"; this.completedAt = Instant.now(); }
    public void fail(String code, String message) {
        this.status = "FAILED";
        this.completedAt = Instant.now();
        this.diagnosticCode = code;
        this.diagnosticMessage = message;
    }

    public UUID getId() { return id; }
    public ImportBatch getImportBatch() { return importBatch; }
    public String getReportSectionId() { return reportSectionId; }
    public UUID getJasVersionId() { return jasVersionId; }
    public UUID getHeadcountVersionId() { return headcountVersionId; }
    public UUID getAppInventoryVersionId() { return appInventoryVersionId; }
    public String getStatus() { return status; }
    public String getMappingMethod() { return mappingMethod; }
    public String getActorUserId() { return actorUserId; }
    public String getCorrelationId() { return correlationId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getProcessedRows() { return processedRows; }
    public long getMatchedRows() { return matchedRows; }
    public long getUnmatchedRows() { return unmatchedRows; }
    public long getAmbiguousRows() { return ambiguousRows; }
    public String getDiagnosticCode() { return diagnosticCode; }
    public String getDiagnosticMessage() { return diagnosticMessage; }
}
