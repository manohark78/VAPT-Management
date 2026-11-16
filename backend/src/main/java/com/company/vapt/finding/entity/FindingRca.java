package com.company.vapt.finding.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="finding_rca", indexes=@Index(name="idx_finding_rca_finding_time", columnList="finding_id, submitted_at"))
public class FindingRca {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="finding_id", nullable=false) private Finding finding;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="reopen_event_id", nullable=false) private FindingEvent reopenEvent;
    @Column(nullable=false, columnDefinition="CLOB") private String narrative;
    @Column(nullable=false, length=24) private String status;
    @Column(name="submitted_by_user_id", nullable=false) private String submittedByUserId;
    @Column(name="submitted_at", nullable=false) private Instant submittedAt;
    @Column(name="reviewed_by_user_id") private String reviewedByUserId;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="review_note", length=1000) private String reviewNote;
    protected FindingRca() { }
    public FindingRca(UUID id, Finding finding, FindingEvent event, String narrative, String actor, Instant at) {
        this.id=id; this.finding=finding; this.reopenEvent=event; this.narrative=narrative; this.status="PENDING"; this.submittedByUserId=actor; this.submittedAt=at;
    }
    public void review(String actor, boolean approve, String note, Instant at) { this.status=approve?"APPROVED":"REJECTED"; this.reviewedByUserId=actor; this.reviewedAt=at; this.reviewNote=note; }
    public UUID getId(){return id;} public Finding getFinding(){return finding;} public FindingEvent getReopenEvent(){return reopenEvent;}
    public String getNarrative(){return narrative;} public String getStatus(){return status;} public String getSubmittedByUserId(){return submittedByUserId;}
    public Instant getSubmittedAt(){return submittedAt;} public String getReviewedByUserId(){return reviewedByUserId;} public Instant getReviewedAt(){return reviewedAt;} public String getReviewNote(){return reviewNote;}
}
