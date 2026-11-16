package com.company.vapt.finding.entity;

import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.report.entity.ReportSection;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "finding_reconciliation_run", indexes =
        @Index(name = "idx_finding_reconciliation_batch", columnList = "import_batch_id, started_at"))
public class FindingReconciliationRun {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_section_id", nullable = false)
    private ReportSection reportSection;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "actor_user_id", nullable = false)
    private String actorUserId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "processed_rows", nullable = false)
    private long processedRows;

    @Column(name = "new_findings", nullable = false)
    private long newFindings;

    @Column(name = "existing_open", nullable = false)
    private long existingOpen;

    @Column(name = "unmatched_identity", nullable = false)
    private long unmatchedIdentity;

    @Column(name = "existing_closed", nullable = false) private long existingClosed;
    @Column(name = "reopened_findings", nullable = false) private long reopenedFindings;
    @Column(name = "returned_unresolved", nullable = false) private long returnedUnresolved;
    @Column(name = "missing_snapshot", nullable = false) private long missingSnapshot;

    @Column(name = "diagnostic_code", length = 80)
    private String diagnosticCode;

    @Column(name = "diagnostic_message", length = 500)
    private String diagnosticMessage;

    protected FindingReconciliationRun() { }

    public FindingReconciliationRun(UUID id, ImportBatch batch, ReportSection section,
                                   String actorUserId, String correlationId, Instant startedAt) {
        this.id = id;
        this.importBatch = batch;
        this.reportSection = section;
        this.actorUserId = actorUserId;
        this.correlationId = correlationId;
        this.startedAt = startedAt;
        this.status = "RUNNING";
    }

    public void complete(long processed, long newRows, long existingRows, long unmatchedRows, Instant completed) {
        complete(processed, newRows, existingRows, unmatchedRows, 0, 0, 0, 0, completed);
    }

    public void complete(long processed, long newRows, long existingRows, long unmatchedRows,
                         long closedRows, long reopenedRows, long returnedRows, long missingRows, Instant completed) {
        this.status = "COMPLETED";
        this.processedRows = processed;
        this.newFindings = newRows;
        this.existingOpen = existingRows;
        this.unmatchedIdentity = unmatchedRows;
        this.existingClosed = closedRows; this.reopenedFindings = reopenedRows;
        this.returnedUnresolved = returnedRows; this.missingSnapshot = missingRows;
        this.completedAt = completed;
        this.diagnosticCode = null;
        this.diagnosticMessage = null;
    }

    public void fail(String code, String message, Instant completed) {
        this.status = "FAILED";
        this.completedAt = completed;
        this.diagnosticCode = code;
        this.diagnosticMessage = message;
    }

    public UUID getId() { return id; }
    public ImportBatch getImportBatch() { return importBatch; }
    public ReportSection getReportSection() { return reportSection; }
    public String getStatus() { return status; }
    public String getActorUserId() { return actorUserId; }
    public String getCorrelationId() { return correlationId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getProcessedRows() { return processedRows; }
    public long getNewFindings() { return newFindings; }
    public long getExistingOpen() { return existingOpen; }
    public long getUnmatchedIdentity() { return unmatchedIdentity; }
    public long getExistingClosed() { return existingClosed; }
    public long getReopenedFindings() { return reopenedFindings; }
    public long getReturnedUnresolved() { return returnedUnresolved; }
    public long getMissingSnapshot() { return missingSnapshot; }
    public String getDiagnosticCode() { return diagnosticCode; }
    public String getDiagnosticMessage() { return diagnosticMessage; }
}
