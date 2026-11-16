package com.company.vapt.finding.service;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.repository.FindingReconciliationRunRepository;
import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.report.entity.ReportSection;
import com.company.vapt.report.repository.ReportSectionRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.finding.entity.FindingReconciliationRun;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.report.domain.ReportSectionId;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class FindingReconciliationCoordinator {
    private final ImportBatchRepository batches;
    private final ReportSectionRepository sections;
    private final FindingReconciliationRunRepository runs;
    private final AppUserRepository users;
    private final AuditService audit;

    public FindingReconciliationCoordinator(ImportBatchRepository batches, ReportSectionRepository sections,
            FindingReconciliationRunRepository runs, AppUserRepository users, AuditService audit) {
        this.batches = batches;
        this.sections = sections;
        this.runs = runs;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public FindingReconciliationRun start(UUID batchId, String sectionId, String actor) {
        ImportBatch batch = batches.lockById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("ImportBatch", batchId.toString()));
        if (!batch.getReportSection().getId().equals(sectionId)) {
            throw new ResourceNotFoundException("ImportBatch", batchId.toString());
        }
        if (ReportSectionId.APPSEC_FINDINGS_PT.name().equals(sectionId)) {
            throw new ValidationException("AppSec Findings PT is excluded from UID reconciliation");
        }
        if (batch.getStatus() != ImportStatus.RECONCILING) {
            throw new ValidationException("The report import is not at the reconciliation stage");
        }
        if (runs.existsByImportBatchId(batchId)) {
            throw new ValidationException("This report import already has a reconciliation run");
        }
        var section = sections.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportSection", sectionId));
        String actorId = users.findByEmail(actor).map(user -> user.getId().toString()).orElse(actor);
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) correlationId = "SYSTEM";

        FindingReconciliationRun run = runs.saveAndFlush(new FindingReconciliationRun(
                UUID.randomUUID(), batch, section, actorId, correlationId, Instant.now()));
        audit.recordInWorkflowTransaction(actorId, "ADMIN", "FINDING_RECONCILIATION_STARTED",
                "FINDING_RECONCILIATION_RUN", run.getId().toString(),
                "section=" + sectionId + ";batchId=" + batchId);
        return run;
    }
}
