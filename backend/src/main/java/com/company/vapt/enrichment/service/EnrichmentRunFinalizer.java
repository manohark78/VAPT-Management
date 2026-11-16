package com.company.vapt.enrichment.service;

import com.company.vapt.enrichment.repository.EnrichmentRunRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.enrichment.entity.EnrichmentRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class EnrichmentRunFinalizer {
    private final EnrichmentRunRepository runs;
    private final AuditService audit;

    public EnrichmentRunFinalizer(EnrichmentRunRepository runs, AuditService audit) {
        this.runs = runs; this.audit = audit;
    }

    @Transactional
    public void complete(UUID runId, Map<String, Long> stageDurations) {
        EnrichmentRun run = runs.findById(runId).orElseThrow();
        run.complete();
        String base = "batchId=" + run.getImportBatch().getId() + ";processed=" + run.getProcessedRows();
        audit.recordInWorkflowTransaction(run.getActorUserId(), "ADMIN", "REPORT_ENRICHMENT_STAGE_COMPLETED",
                "ENRICHMENT_RUN", runId.toString(), base + ";stage=JAS_MATCH;durationMs=" + stageDurations.getOrDefault("JAS_MATCH", 0L));
        audit.recordInWorkflowTransaction(run.getActorUserId(), "ADMIN", "REPORT_ENRICHMENT_STAGE_COMPLETED",
                "ENRICHMENT_RUN", runId.toString(), base + ";stage=HEADCOUNT_RESOLUTION;durationMs=" + stageDurations.getOrDefault("HEADCOUNT_RESOLUTION", 0L));
        audit.recordInWorkflowTransaction(run.getActorUserId(), "ADMIN", "REPORT_ENRICHMENT_STAGE_COMPLETED",
                "ENRICHMENT_RUN", runId.toString(), base + ";stage=APPLICATION_RESOLUTION;durationMs=" + stageDurations.getOrDefault("APPLICATION_RESOLUTION", 0L));
        audit.recordInWorkflowTransaction(run.getActorUserId(), "ADMIN", "REPORT_ENRICHMENT_COMPLETED",
                "ENRICHMENT_RUN", runId.toString(), base + ";matched=" + run.getMatchedRows()
                        + ";unmatched=" + run.getUnmatchedRows() + ";ambiguous=" + run.getAmbiguousRows());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID runId, String code, String safeMessage) {
        EnrichmentRun run = runs.findById(runId).orElseThrow();
        run.fail(code, safeMessage);
        audit.recordInWorkflowTransaction(run.getActorUserId(), "ADMIN", "REPORT_ENRICHMENT_FAILED",
                "ENRICHMENT_RUN", runId.toString(), "batchId=" + run.getImportBatch().getId()
                        + ";diagnostic=" + code + ";processed=" + run.getProcessedRows());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedWithoutAudit(UUID runId, String code, String safeMessage) {
        runs.findById(runId).ifPresent(run -> run.fail(code, safeMessage));
    }
}
