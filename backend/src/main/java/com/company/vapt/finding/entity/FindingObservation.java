package com.company.vapt.finding.entity;

import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ReportImportRecord;
import com.company.vapt.report.entity.ReportSection;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable row-level evidence from a specific report import. */
@Entity
@Table(name = "finding_observation", indexes = {
        @Index(name = "idx_finding_observation_finding_time", columnList = "finding_id, observed_at"),
        @Index(name = "idx_finding_observation_batch_row", columnList = "import_batch_id, report_import_record_id")
})
public class FindingObservation {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id")
    private Finding finding;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_section_id", nullable = false)
    private ReportSection reportSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_import_record_id")
    private ReportImportRecord sourceRecord;

    @Column(nullable = false, length = 32)
    private String classification;

    @Column(name = "previous_state_classification", length = 32)
    private String previousStateClassification;

    @Column(name = "identity_issue", length = 48)
    private String identityIssue;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "source_values", columnDefinition = "CLOB") private String sourceValues;
    @Column(name = "risk_rating", length = 120) private String riskRating;
    @Column(name = "risk_score", length = 120) private String riskScore;
    @Column(name = "compliance_status", length = 120) private String complianceStatus;
    @Column(name = "severity", length = 120) private String severity;
    @Column(name = "finding_type", length = 500) private String findingType;
    @Column(name = "port", length = 120) private String port;
    @Column(name = "results", columnDefinition = "CLOB") private String results;
    @Column(name = "targets", columnDefinition = "CLOB") private String targets;
    @Column(name = "display_name", length = 2000) private String displayName;
    @Column(name = "app_tag", length = 500) private String appTag;
    @Column(name = "ip_address", length = 255) private String ipAddress;
    @Column(name = "application_name", length = 1000) private String applicationName;
    @Column(name = "environment", length = 500) private String environment;
    @Column(name = "owned_by", length = 1000) private String ownedBy;
    @Column(name = "application_id", length = 500) private String applicationId;
    @Column(name = "remediation_date") private LocalDate remediationDate;
    @Column(name = "sla_due_at") private Instant slaDueAt;
    @Column(name = "headcount_email", length = 1000) private String headcountEmail;
    @Column(name = "cm4", length = 1000) private String cm4;
    @Column(name = "cm5", length = 1000) private String cm5;
    @Column(name = "cm6", length = 1000) private String cm6;
    @Column(name = "plan_application_owner_1", length = 1000) private String planApplicationOwner1;
    @Column(name = "plan_application_owner_2", length = 1000) private String planApplicationOwner2;

    public record SnapshotValues(String sourceValues, String riskRating, String riskScore,
            String complianceStatus, String severity, String findingType, String port,
            String results, String targets, String displayName, String appTag, String ipAddress,
            String applicationName, String environment, String ownedBy, String applicationId,
            LocalDate remediationDate, Instant slaDueAt, String headcountEmail, String cm4, String cm5, String cm6,
            String planApplicationOwner1, String planApplicationOwner2) { }

    protected FindingObservation() { }

    public FindingObservation(UUID id, Finding finding, ImportBatch batch, ReportSection section,
                              ReportImportRecord sourceRecord, String classification,
                              String identityIssue, Instant observedAt) {
        this(id, finding, batch, section, sourceRecord, classification, identityIssue, observedAt, null);
    }

    public FindingObservation(UUID id, Finding finding, ImportBatch batch, ReportSection section,
                              ReportImportRecord sourceRecord, String classification,
                              String identityIssue, Instant observedAt, SnapshotValues values) {
        this.id = id;
        this.finding = finding;
        this.importBatch = batch;
        this.reportSection = section;
        this.sourceRecord = sourceRecord;
        this.classification = classification;
        this.identityIssue = identityIssue;
        this.observedAt = observedAt;
        if (values != null) {
            this.sourceValues = values.sourceValues(); this.riskRating = values.riskRating();
            this.riskScore = values.riskScore(); this.complianceStatus = values.complianceStatus();
            this.severity = values.severity(); this.findingType = values.findingType(); this.port = values.port();
            this.results = values.results(); this.targets = values.targets(); this.displayName = values.displayName();
            this.appTag = values.appTag(); this.ipAddress = values.ipAddress();
            this.applicationName = values.applicationName(); this.environment = values.environment();
            this.ownedBy = values.ownedBy(); this.applicationId = values.applicationId();
            this.remediationDate = values.remediationDate();
            this.slaDueAt = values.slaDueAt();
            this.headcountEmail = values.headcountEmail(); this.cm4 = values.cm4(); this.cm5 = values.cm5(); this.cm6 = values.cm6();
            this.planApplicationOwner1 = values.planApplicationOwner1(); this.planApplicationOwner2 = values.planApplicationOwner2();
        }
    }

    public UUID getId() { return id; }
    public Finding getFinding() { return finding; }
    public ImportBatch getImportBatch() { return importBatch; }
    public ReportSection getReportSection() { return reportSection; }
    public ReportImportRecord getSourceRecord() { return sourceRecord; }
    public String getClassification() { return classification; }
    public String getPreviousStateClassification() { return previousStateClassification; }
    public void setPreviousStateClassification(String classification) { this.previousStateClassification=classification; }
    public String getIdentityIssue() { return identityIssue; }
    public Instant getObservedAt() { return observedAt; }
    public String getSourceValues() { return sourceValues; }
    public String getRiskRating() { return riskRating; }
    public String getRiskScore() { return riskScore; }
    public String getComplianceStatus() { return complianceStatus; }
    public String getSeverity() { return severity; }
    public String getFindingType() { return findingType; }
    public String getPort() { return port; }
    public String getResults() { return results; }
    public String getTargets() { return targets; }
    public String getDisplayName() { return displayName; }
    public String getAppTag() { return appTag; }
    public String getIpAddress() { return ipAddress; }
    public String getApplicationName() { return applicationName; }
    public String getEnvironment() { return environment; }
    public String getOwnedBy() { return ownedBy; }
    public String getApplicationId() { return applicationId; }
    public LocalDate getRemediationDate() { return remediationDate; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public String getHeadcountEmail() { return headcountEmail; }
    public String getCm4() { return cm4; } public String getCm5() { return cm5; } public String getCm6() { return cm6; }
    public String getPlanApplicationOwner1() { return planApplicationOwner1; }
    public String getPlanApplicationOwner2() { return planApplicationOwner2; }
    public SnapshotValues snapshotValues() {
        return new SnapshotValues(sourceValues,riskRating,riskScore,complianceStatus,severity,findingType,port,results,targets,
                displayName,appTag,ipAddress,applicationName,environment,ownedBy,applicationId,remediationDate,slaDueAt,
                headcountEmail,cm4,cm5,cm6,planApplicationOwner1,planApplicationOwner2);
    }
}
