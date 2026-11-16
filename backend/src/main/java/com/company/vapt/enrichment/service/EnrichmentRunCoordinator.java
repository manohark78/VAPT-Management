package com.company.vapt.enrichment.service;

import com.company.vapt.enrichment.repository.EnrichmentRunRepository;
import com.company.vapt.ingestion.repository.ImportBatchRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.enrichment.entity.EnrichmentRun;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.report.domain.ReportSectionId;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EnrichmentRunCoordinator {
    private final ImportBatchRepository batches;
    private final EnrichmentRunRepository runs;
    private final CmdbVersionSnapshotService snapshots;
    private final AppUserRepository users;
    private final AuditService audit;

    public EnrichmentRunCoordinator(ImportBatchRepository batches, EnrichmentRunRepository runs,
                                    CmdbVersionSnapshotService snapshots, AppUserRepository users,
                                    AuditService audit) {
        this.batches = batches; this.runs = runs; this.snapshots = snapshots; this.users = users; this.audit = audit;
    }

    @Transactional
    public EnrichmentRun start(UUID batchId, String sectionId, String actor, boolean backfill, boolean rerun) {
        ImportBatch batch = batches.lockById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("ImportBatch", batchId.toString()));
        if (!batch.getReportSection().getId().equals(sectionId)) {
            throw new ResourceNotFoundException("ImportBatch", batchId.toString());
        }
        if (ReportSectionId.APPSEC_FINDINGS_PT.name().equals(sectionId)) {
            throw new ValidationException("AppSec Findings PT does not have an approved CMDB mapping");
        }
        if (backfill && batch.getStatus() != ImportStatus.COMMITTED) {
            throw new ValidationException("Only committed report imports can be enriched through backfill");
        }
        if (!backfill && batch.getStatus() != ImportStatus.ENRICHING) {
            throw new ValidationException("The report import is not at the enrichment stage");
        }
        if (backfill && !rerun && runs.existsByImportBatchIdAndStatus(batchId, "COMPLETED")) {
            throw new ValidationException("This report import already has a completed enrichment; set rerun=true to retain history and create another run");
        }

        CmdbVersionSnapshot snapshot = snapshots.capture();
        String actorId = users.findByEmail(actor).map(user -> user.getId().toString()).orElse(actor);
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) correlationId = "SYSTEM";
        EnrichmentRun run = new EnrichmentRun(UUID.randomUUID(), batch, snapshot.jasVersionId(),
                snapshot.headcountVersionId(), snapshot.appInventoryVersionId(), actorId, correlationId);
        if (!snapshot.complete()) {
            run.fail("CMDB_VERSION_MISSING", "Active CMDB version is missing or not ready for: "
                    + String.join(",", snapshot.missingDatasets()));
        }
        EnrichmentRun saved = runs.saveAndFlush(run);
        if (snapshot.complete()) {
            audit.recordInWorkflowTransaction(actorId, "ADMIN", "REPORT_ENRICHMENT_STARTED", "ENRICHMENT_RUN",
                    saved.getId().toString(), safeAuditDetails(saved));
        } else {
            audit.recordInWorkflowTransaction(actorId, "ADMIN", "REPORT_ENRICHMENT_FAILED", "ENRICHMENT_RUN",
                    saved.getId().toString(), "batchId=" + batchId + ";diagnostic=CMDB_VERSION_MISSING;datasets="
                            + String.join(",", snapshot.missingDatasets()));
        }
        return saved;
    }

    private String safeAuditDetails(EnrichmentRun run) {
        return "section=" + run.getReportSectionId() + ";batchId=" + run.getImportBatch().getId()
                + ";jasVersion=" + run.getJasVersionId() + ";headcountVersion=" + run.getHeadcountVersionId()
                + ";appInventoryVersion=" + run.getAppInventoryVersionId();
    }
}
