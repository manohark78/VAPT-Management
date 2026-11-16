package com.company.vapt.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Recognised source values for one validated report row in a particular import snapshot. */
@Entity
@Table(name = "report_import_record", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uq_report_import_record_row", columnNames = {"import_batch_id", "source_row_number"})
})
public class ReportImportRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;

    @Column(name = "source_identity", nullable = false, length = 1000)
    private String sourceIdentity;

    @Column(name = "source_values", nullable = false, columnDefinition = "CLOB")
    private String sourceValues;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportImportRecord() { }

    public ReportImportRecord(ImportBatch batch, int sourceRowNumber, String sourceIdentity, String sourceValues) {
        this.id = UUID.randomUUID();
        this.importBatch = batch;
        this.sourceRowNumber = sourceRowNumber;
        this.sourceIdentity = sourceIdentity;
        this.sourceValues = sourceValues;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ImportBatch getImportBatch() { return importBatch; }
    public int getSourceRowNumber() { return sourceRowNumber; }
    public String getSourceIdentity() { return sourceIdentity; }
    public String getSourceValues() { return sourceValues; }
    public Instant getCreatedAt() { return createdAt; }
}
