package com.company.vapt.ingestion.entity;

import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.report.entity.ReportSection;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an import batch lifecycle record.
 * Supports optimistic locking, explicit state tracking, and DB-level active snapshot uniqueness.
 */
@Entity
@Table(name = "import_batch", indexes = {
        @Index(name = "idx_import_batch_section_created", columnList = "report_section_id, created_at DESC"),
        @Index(name = "idx_import_batch_status", columnList = "status"),
        @Index(name = "idx_import_batch_active", columnList = "report_section_id, is_active")
})
public class ImportBatch {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_section_id", nullable = false)
    private ReportSection reportSection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_file_id", nullable = false)
    private SourceFile sourceFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private ImportStatus status;

    @Version
    @Column(name = "version_number", nullable = false)
    private Long versionNumber;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "complete_snapshot", nullable = false)
    private boolean completeSnapshot;

    @Column(name = "active_section_id", length = 50)
    private String activeSectionId;

    @Column(name = "total_rows")
    private int totalRows;

    @Column(name = "accepted_rows")
    private int acceptedRows;

    @Column(name = "rejected_rows")
    private int rejectedRows;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Lob
    @Column(name = "error_details")
    private String errorDetails;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportBatch() {
        // Required by JPA
    }

    public ImportBatch(UUID id, ReportSection reportSection, SourceFile sourceFile) {
        this.id = id != null ? id : UUID.randomUUID();
        this.reportSection = reportSection;
        this.sourceFile = sourceFile;
        this.status = ImportStatus.RECEIVED;
        this.active = false;
        this.activeSectionId = null;
        this.totalRows = 0;
        this.acceptedRows = 0;
        this.rejectedRows = 0;
        this.createdAt = Instant.now();
    }

    public void markCommittedAndActive() {
        this.status = ImportStatus.COMMITTED;
        this.active = true;
        this.activeSectionId = this.reportSection.getId();
        this.completedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.activeSectionId = null;
    }

    public void markFailed(String message, String details) {
        this.status = ImportStatus.FAILED;
        this.active = false;
        this.activeSectionId = null;
        this.errorMessage = message;
        this.errorDetails = details;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ReportSection getReportSection() {
        return reportSection;
    }

    public SourceFile getSourceFile() {
        return sourceFile;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public Long getVersionNumber() {
        return versionNumber;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isCompleteSnapshot() { return completeSnapshot; }
    public void setCompleteSnapshot(boolean completeSnapshot) { this.completeSnapshot = completeSnapshot; }

    public String getActiveSectionId() {
        return activeSectionId;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getAcceptedRows() {
        return acceptedRows;
    }

    public void setAcceptedRows(int acceptedRows) {
        this.acceptedRows = acceptedRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(int rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
