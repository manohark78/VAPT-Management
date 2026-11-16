package com.company.vapt.finding.entity;

import com.company.vapt.report.entity.ReportSection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Internal finding identity; source UID lookup is deliberately non-unique pending profiling. */
@Entity
@Table(name = "finding", indexes = @Index(name = "idx_finding_section_uid", columnList = "report_section_id, source_uid"))
public class Finding {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_section_id", nullable = false)
    private ReportSection reportSection;

    @Column(name = "source_uid", nullable = false, length = 1000)
    private String sourceUid;

    @Column(name = "lifecycle_state", nullable = false, length = 24)
    private String lifecycleState;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_user_id")
    private String closedByUserId;

    @Column(name = "closed_reason", length = 500)
    private String closedReason;

    @Version
    @Column(name = "lifecycle_version", nullable = false)
    private Long lifecycleVersion;

    protected Finding() { }

    public Finding(UUID id, ReportSection reportSection, String sourceUid, Instant now) {
        this.id = id;
        this.reportSection = reportSection;
        this.sourceUid = sourceUid;
        this.lifecycleState = "OPEN";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public ReportSection getReportSection() { return reportSection; }
    public String getSourceUid() { return sourceUid; }
    public String getLifecycleState() { return lifecycleState; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
    public String getClosedByUserId() { return closedByUserId; }
    public String getClosedReason() { return closedReason; }
    public void close(String actor, String reason, Instant now) {
        this.lifecycleState = "CLOSED"; this.closedByUserId = actor; this.closedReason = reason;
        this.closedAt = now; this.updatedAt = now;
    }
    public void reopen(Instant now) {
        this.lifecycleState = "OPEN"; this.closedByUserId = null; this.closedReason = null;
        this.closedAt = null; this.updatedAt = now;
    }
}
