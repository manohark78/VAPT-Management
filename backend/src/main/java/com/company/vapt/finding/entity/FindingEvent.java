package com.company.vapt.finding.entity;

import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ReportImportRecord;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of the lifecycle classifications supported in Phase 6A. */
@Entity
@Table(name = "finding_event", indexes =
        @Index(name = "idx_finding_event_finding_time", columnList = "finding_id, occurred_at"))
public class FindingEvent {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private Finding finding;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_import_record_id")
    private ReportImportRecord sourceRecord;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(length = 1000)
    private String details;

    protected FindingEvent() { }

    public FindingEvent(UUID id, Finding finding, ImportBatch batch, ReportImportRecord sourceRecord,
                        String eventType, Instant occurredAt) {
        this(id, finding, batch, sourceRecord, eventType, occurredAt, null, null);
    }

    public FindingEvent(UUID id, Finding finding, ImportBatch batch, ReportImportRecord sourceRecord,
                        String eventType, Instant occurredAt, String actorUserId, String details) {
        this.id = id;
        this.finding = finding;
        this.importBatch = batch;
        this.sourceRecord = sourceRecord;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.details = details;
    }

    public UUID getId() { return id; }
    public Finding getFinding() { return finding; }
    public ImportBatch getImportBatch() { return importBatch; }
    public ReportImportRecord getSourceRecord() { return sourceRecord; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActorUserId() { return actorUserId; }
    public String getDetails() { return details; }
}
