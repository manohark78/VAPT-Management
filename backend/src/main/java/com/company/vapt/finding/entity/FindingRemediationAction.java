package com.company.vapt.finding.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="finding_remediation_action", indexes=@Index(name="idx_finding_remediation_finding_time", columnList="finding_id, created_at"))
public class FindingRemediationAction {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="finding_id", nullable=false) private Finding finding;
    @Column(name="action_type", nullable=false, length=40) private String actionType;
    @Column(name="selected_date") private LocalDate selectedDate;
    @Column(name="actor_user_id", nullable=false) private String actorUserId;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    protected FindingRemediationAction() { }
    public FindingRemediationAction(UUID id, Finding finding, String actionType, LocalDate selectedDate, String actor, Instant createdAt) {
        this.id=id; this.finding=finding; this.actionType=actionType; this.selectedDate=selectedDate; this.actorUserId=actor; this.createdAt=createdAt;
    }
    public UUID getId(){return id;} public Finding getFinding(){return finding;} public String getActionType(){return actionType;}
    public LocalDate getSelectedDate(){return selectedDate;} public String getActorUserId(){return actorUserId;} public Instant getCreatedAt(){return createdAt;}
}
