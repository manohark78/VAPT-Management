package com.company.vapt.enrichment.entity;

import com.company.vapt.ingestion.entity.ReportImportRecord;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_record_enrichment")
public class ReportRecordEnrichment {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrichment_run_id", nullable = false) private EnrichmentRun enrichmentRun;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_import_record_id", nullable = false) private ReportImportRecord sourceRecord;
    @Column(name = "source_row_number", nullable = false) private int sourceRowNumber;
    @Column(name = "target_ordinal", nullable = false) private int targetOrdinal;
    @Column(name = "original_target", columnDefinition = "CLOB") private String originalTarget;
    @Column(name = "target_type", nullable = false, length = 24) private String targetType;
    @Column(name = "target_match_status", nullable = false, length = 24) private String targetMatchStatus;
    @Column(name = "match_reason", length = 80) private String matchReason;
    @Column(name = "match_method", length = 80) private String matchMethod;
    @Column(name = "application_display", length = 1000) private String applicationDisplay;
    @Column(name = "application_name_status", length = 40) private String applicationNameStatus;
    @Column(name = "owner_resolution_status", nullable = false, length = 24) private String ownerResolutionStatus;
    @Column(name = "application_resolution_status", nullable = false, length = 24) private String applicationResolutionStatus;
    @Column(name = "enriched_values_json", nullable = false, columnDefinition = "CLOB") private String enrichedValuesJson;
    @Column(name = "candidate_details_json", nullable = false, columnDefinition = "CLOB") private String candidateDetailsJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ReportRecordEnrichment() { }

    public ReportRecordEnrichment(EnrichmentRun run, ReportImportRecord source, int targetOrdinal,
                                  String originalTarget, String targetType, String targetMatchStatus,
                                  String matchReason, String matchMethod, String applicationDisplay,
                                  String applicationNameStatus, String ownerResolutionStatus,
                                  String applicationResolutionStatus, String enrichedValuesJson,
                                  String candidateDetailsJson) {
        this.id = UUID.randomUUID();
        this.enrichmentRun = run;
        this.sourceRecord = source;
        this.sourceRowNumber = source.getSourceRowNumber();
        this.targetOrdinal = targetOrdinal;
        this.originalTarget = originalTarget;
        this.targetType = targetType;
        this.targetMatchStatus = targetMatchStatus;
        this.matchReason = matchReason;
        this.matchMethod = matchMethod;
        this.applicationDisplay = applicationDisplay;
        this.applicationNameStatus = applicationNameStatus;
        this.ownerResolutionStatus = ownerResolutionStatus;
        this.applicationResolutionStatus = applicationResolutionStatus;
        this.enrichedValuesJson = enrichedValuesJson;
        this.candidateDetailsJson = candidateDetailsJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setEnrichmentRun(EnrichmentRun enrichmentRun) { this.enrichmentRun = enrichmentRun; }
    public void setSourceRecord(ReportImportRecord sourceRecord) { this.sourceRecord = sourceRecord; }
    public EnrichmentRun getEnrichmentRun() { return enrichmentRun; }
    public ReportImportRecord getSourceRecord() { return sourceRecord; }
    public int getSourceRowNumber() { return sourceRowNumber; }
    public String getOriginalTarget() { return originalTarget; }
    public String getTargetType() { return targetType; }
    public String getTargetMatchStatus() { return targetMatchStatus; }
    public String getMatchReason() { return matchReason; }
    public String getMatchMethod() { return matchMethod; }
    public String getApplicationDisplay() { return applicationDisplay; }
    public String getApplicationNameStatus() { return applicationNameStatus; }
    public String getOwnerResolutionStatus() { return ownerResolutionStatus; }
    public String getApplicationResolutionStatus() { return applicationResolutionStatus; }
    public String getEnrichedValuesJson() { return enrichedValuesJson; }
    public String getCandidateDetailsJson() { return candidateDetailsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
